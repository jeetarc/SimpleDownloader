package com.jeetarc.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/** DownloaderRegistry Keeps track of every running SimpleDownloader instance. */
final class DownloaderRegistry {
	static final Object LOCK = new Object();
	static final DownloaderRegistry INSTANCE = new DownloaderRegistry();
	static final int AUTO_MIN_SLOT = 1;
	static final int AUTO_MAX_SLOT = 10;
	static final ExecutorService OUTPUT_VALIDATION_EXECUTOR = Executors.newCachedThreadPool();
	private static final ExecutorService ADAPTIVE_DISPATCH_EXECUTOR = Executors.newSingleThreadExecutor();
	
	private final Set<SimpleDownloader> instances = new HashSet<SimpleDownloader>();
	private final AtomicLong nextTaskId = new AtomicLong(System.currentTimeMillis());
	private final AtomicInteger manualOccupied = new AtomicInteger();
	private final AtomicInteger autoOccupied = new AtomicInteger();
	private final AutoConcurrencyController adaptive = new AutoConcurrencyController();
	
	private volatile int globalConcurrentCap;
	private volatile SimpleDownloader defaultDownloader;
	private TaskDatabase sharedDatabase;
	private int databaseUsers;
	
	private DownloaderRegistry() {}
	
	TaskDatabase acquireDatabaseLocked(Context context) {
		synchronized (LOCK) {
			if (sharedDatabase == null) sharedDatabase = new TaskDatabase(context);
			databaseUsers++;
			return sharedDatabase;
		}
	}
	
	void registerLocked(SimpleDownloader downloader) {
		if (downloader == null) return;
		synchronized (LOCK) {
			instances.add(downloader);
			if (SimpleDownloader.DEFAULT_OWNER_ID.equals(downloader.getOwnerId()) && defaultDownloader == null) defaultDownloader = downloader;
		}
	}
	
	void unregister(SimpleDownloader downloader, TaskDatabase database) {
		synchronized (LOCK) {
			instances.remove(downloader);
			if (defaultDownloader == downloader) defaultDownloader = null;
			releaseDatabaseLocked(database);
			adaptive.onInstanceRemovedLocked();
			
			if (defaultDownloader == null) {
				for (SimpleDownloader candidate : instances) {
					if (candidate != null && !candidate.mShutdown && SimpleDownloader.DEFAULT_OWNER_ID.equals(candidate.getOwnerId())) {
						defaultDownloader = candidate;
						break;
					}
				}
			}
		}
	}
	
	private void releaseDatabaseLocked(TaskDatabase database) {
		if (database != sharedDatabase) return;
		databaseUsers--;
		
		if (databaseUsers <= 0 && instances.isEmpty()) {
			databaseUsers = 0;
			sharedDatabase = null;
			database.close();
		}
	}
	
	ArrayList<SimpleDownloader> snapshotInstances() {
		synchronized (LOCK) {
			return new ArrayList<SimpleDownloader>(instances);
		}
	}
	
	void ensureUniqueTaskIdLocked(long id, SimpleDownloader requester) {
		synchronized (LOCK) {
			for (SimpleDownloader downloader : instances) {
				if (downloader == null || downloader == requester || downloader.mShutdown) continue;
				if (downloader.taskManager.hasTask(id)) throw new IllegalStateException("Task ID " + id + " is already used by another SimpleDownloader instance.");
			}
		}
	}
	
	boolean canOccupySlot(SimpleDownloader downloader) {
		if (downloader == null) return false;
		synchronized (LOCK) {
			return canReserveSlotLocked(downloader);
		}
	}
	
	boolean tryOccupySlot(SimpleDownloader downloader) {
		if (downloader == null) return false;
		
		synchronized (LOCK) {
			if (!canReserveSlotLocked(downloader)) return false;
			if (downloader.isAutoConcurrentLocked()) autoOccupied.incrementAndGet();
			else manualOccupied.incrementAndGet();
			return true;
		}
	}
	
	private boolean canReserveSlotLocked(SimpleDownloader downloader) {
		int manual = manualOccupied.get();
		int auto = autoOccupied.get();
		
		// Global concurrent cap apply to both fixed and adaptive slots.
		if (globalConcurrentCap > 0 && manual + auto >= globalConcurrentCap) return false;
		
		if (downloader.isAutoConcurrentLocked()) {
			int target = adaptive.getTargetSlotsLocked();
			int availableForAuto = Math.max(0, target - manual);
			if (auto >= availableForAuto) return false;
		}
		
		return true;
	}
	
	boolean hasBusyOverwriteTargetLocked(String overwriteKey, long ignoreTaskId, SimpleDownloader requester) {
		if (overwriteKey == null || overwriteKey.isEmpty()) return false;
		synchronized (LOCK) {
			for (SimpleDownloader downloader : instances) {
				if (downloader == null) continue;
				
				for (DownloadTask task : downloader.taskManager.snapshot()) {
					if (task == null) continue;
					if (task.mId == ignoreTaskId && downloader == requester) continue;
					if (task.isFinished()) continue;
					if (overwriteKey.equals(task.getOverwriteKey())) return true;
				}
			}
		}
		
		return false;
	}
	
	SimpleDownloader findTaskOwner(long id) {
		synchronized (LOCK) {
			SimpleDownloader found = null;
			
			for (SimpleDownloader downloader : instances) {
				if (downloader == null || downloader.mShutdown) continue;
				if (downloader.taskManager.getTask(id) == null) continue;
				if (found != null) return null;
				found = downloader;
			}
			
			return found;
		}
	}
	
	DownloadTask findTaskAcrossInstances(long id) {
		SimpleDownloader owner = findTaskOwner(id);
		return owner == null ? null : owner.taskManager.getTask(id);
	}
	
	Context appContextOrNull() {
		synchronized (LOCK) {
			if (defaultDownloader != null && !defaultDownloader.mShutdown) return defaultDownloader.mContext;
			for (SimpleDownloader downloader : instances) {
				if (downloader != null && !downloader.mShutdown) return downloader.mContext;
			}
			return null;
		}
	}
	
	SimpleDownloader defaultDownloaderOrNull() {
		synchronized (LOCK) {
			return defaultDownloader;
		}
	}
	
	long nextTaskId() {
		return nextTaskId.incrementAndGet();
	}
	
	void setGlobalConcurrent(int max) {
		if (max < 0) throw new IllegalArgumentException("globalConcurrent cannot be negative. " + "Use 0 to disable the global cap.");
		
		synchronized (LOCK) {
			globalConcurrentCap = max;
		}
		
		onAdaptiveStateChangedLocked();
	}
	
	boolean hasGlobalCapacityLocked() {
		return globalConcurrentCap <= 0 || getGlobalManualOccupiedLocked() + getGlobalAutoOccupiedLocked() < globalConcurrentCap;
	}
	
	int getGlobalAutoActiveCountLocked() {
		int count = 0;
		synchronized (LOCK) {
			for (SimpleDownloader downloader : instances) {
				if (downloader == null || downloader.mShutdown || !downloader.isAutoConcurrentLocked()) continue;
				count += downloader.taskManager.getCachedActiveSpeedTaskCountLocked();
			}
		}
		return count;
	}
	
	long getGlobalAutoSpeedLocked() {
		long total = 0;
		synchronized (LOCK) {
			for (SimpleDownloader downloader : instances) {
				if (downloader == null || downloader.mShutdown || !downloader.isAutoConcurrentLocked()) continue;
				total += downloader.taskManager.getCachedTotalActiveSpeedLocked();
			}
		}
		return total;
	}
	
	int getGlobalManualOccupiedLocked() {
		return Math.max(0, manualOccupied.get());
	}
	
	int getGlobalAutoOccupiedLocked() {
		return Math.max(0, autoOccupied.get());
	}
	
	void onSlotOccupiedLocked(SimpleDownloader downloader) {
		if (downloader == null) return;
		if (downloader.isAutoConcurrentLocked()) autoOccupied.incrementAndGet();
		else manualOccupied.incrementAndGet();
	}
	
	void onSlotReleased(SimpleDownloader downloader) {
		if (downloader == null) return;
		
		synchronized (LOCK) {
			if (downloader.isAutoConcurrentLocked()) decrement(autoOccupied);
			else decrement(manualOccupied);
		}
	}
	
	void rebalanceOccupiedCountersLocked(int count, boolean oldAuto, boolean newAuto) {
		if (count <= 0 || oldAuto == newAuto) return;
		AtomicInteger oldCounter = oldAuto ? autoOccupied : manualOccupied;
		AtomicInteger newCounter = newAuto ? autoOccupied : manualOccupied;
		for (int i = 0; i < count; i++) decrement(oldCounter);
		newCounter.addAndGet(count);
	}
	
	private static void decrement(AtomicInteger value) {
		while (true) {
			int current = value.get();
			if (current <= 0) return;
			if (value.compareAndSet(current, current - 1)) return;
		}
	}
	
	int getGlobalAdaptiveTargetLocked() {
		return adaptive.getTargetSlotsLocked();
	}
	
	void onAdaptiveStateChangedLocked() {
		ADAPTIVE_DISPATCH_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				ArrayList<SimpleDownloader> snapshot = snapshotInstances();
				
				for (SimpleDownloader downloader : snapshot) {
					if (downloader == null || downloader.mShutdown) continue;
					synchronized (downloader.mLock) {
						if (!downloader.isAutoConcurrentLocked()) continue;
						downloader.slotManager.ensureExecutorLocked();
						downloader.slotManager.submitReadyHeldTasksLocked();
					}
				}
			}
		});
	}
	
	AutoConcurrencyController autoConcurrencyController() {
		return adaptive;
	}
	
	TaskDatabase sharedDatabase() {
		synchronized (LOCK) {
			return sharedDatabase;
		}
	}
}
