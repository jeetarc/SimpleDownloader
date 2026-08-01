package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class SlotManager {
	private final SimpleDownloader downloader;
	private DownloadExecutor executor;
	private final List<DownloadTask> heldQueue = new ArrayList<DownloadTask>();
	private final HashSet<DownloadTask> occupiedSlots = new HashSet<DownloadTask>();
	private final HashSet<DownloadTask> forcedTasks = new HashSet<DownloadTask>();
	private final AtomicLong sequence = new AtomicLong(0);
	
	private static final Comparator<DownloadTask> QUEUE_ORDER = new Comparator<DownloadTask>() {
		@Override
		public int compare(DownloadTask a, DownloadTask b) {
			int priorityCompare = b.getPriority().getWeight() - a.getPriority().getWeight();
			if (priorityCompare != 0) return priorityCompare;
			return Long.compare(a.getCreatedAt(), b.getCreatedAt());
		}
	};
	
	SlotManager(SimpleDownloader downloader) {
		this.downloader = downloader;
	}
	
	int getQueuedCount() {
		synchronized (downloader.mLock) {
			return heldQueue.size() + (executor != null ? executor.getQueuedCount() : 0);
		}
	}
	
	int getOccupiedCount() {
		synchronized (downloader.mLock) {
			return occupiedSlots.size();
		}
	}
	
	boolean hasWork() {
		synchronized (downloader.mLock) {
			return !heldQueue.isEmpty() || !occupiedSlots.isEmpty() || (executor != null && (executor.getActiveCount() > 0 || executor.getQueuedCount() > 0));
		}
	}
	
	boolean hasFreeSlotLocked() {
		return occupiedSlots.size() < SimpleDownloader.getEffectiveMaxConcurrentLocked();
	}
	
	boolean occupySlotLocked(DownloadTask task) {
		if (task == null) return false;
		if (occupiedSlots.contains(task)) return true;
		if (!hasFreeSlotLocked()) return false;
		occupiedSlots.add(task);
		downloader.taskManager.sortTasksLocked();
		return true;
	}
	
	void releaseSlotLocked(DownloadTask task) {
		if (task != null && occupiedSlots.remove(task)) downloader.taskManager.sortTasksLocked();
	}
	
	boolean isOccupiedSlot(DownloadTask task) {
		synchronized (downloader.mLock) {
			return hasOccupiedSlotLocked(task);
		}
	}
	
	boolean hasOccupiedSlotLocked(DownloadTask task) {
		return task != null && occupiedSlots.contains(task);
	}
	
	void dispatchReadyTasks() {
		synchronized (downloader.mLock) {
			submitReadyHeldTasksLocked();
		}
	}
	
	void submitReadyHeldTasksLocked() {
		if (!downloader.networkManager.getWaitingForPreferredNetwork().isEmpty()) {
			for (int i = 0; i < downloader.networkManager.getWaitingForPreferredNetwork().size(); i++) {
				DownloadTask task = downloader.networkManager.getWaitingForPreferredNetwork().get(i);
				
				if (task == null) {
					downloader.networkManager.getWaitingForPreferredNetwork().remove(i);
					i--;
					continue;
				}
				
				if (!downloader.networkManager.canRunNow(task)) continue;
				if (!task.mForceDownload && !hasFreeSlotLocked() && !hasOccupiedSlotLocked(task)) continue;
				downloader.networkManager.getWaitingForPreferredNetwork().remove(i);
				i--;
				enqueueOrSubmitLocked(task, true);
			}
		}
		
		if (!downloader.mDownloadOnSlotFree) {
			downloader.taskManager.sortTasksLocked();
			return;
		}
		
		for (int i = 0; i < heldQueue.size(); i++) {
			DownloadTask task = heldQueue.get(i);
			if (task == null || task.status == Status.CANCELLED || task.status == Status.COMPLETED || task.status == Status.FAILED) {
				heldQueue.remove(i);
				i--;
				continue;
			}
			
			if (!task.mLockedInQueue && (task.mForceDownload || hasFreeSlotLocked() || hasOccupiedSlotLocked(task))) {
				heldQueue.remove(i);
				i--;
				submitTaskLocked(task);
			}
		}
		
		downloader.taskManager.sortTasksLocked();
	}
	
	void enqueueOrSubmit(DownloadTask task, boolean force) {
		synchronized (downloader.mLock) {
			enqueueOrSubmitLocked(task, force);
		}
	}
	
	void enqueueOrSubmitLocked(DownloadTask task, boolean force) {
		if (task == null) return;
		downloader.networkManager.getWaitingForPreferredNetwork().remove(task);
		
		if (!force && (task.mLockedInQueue || !downloader.mDownloadOnSlotFree)) {
			holdTaskLocked(task);
			return;
		}
		
		heldQueue.remove(task);
		submitTaskLocked(task);
	}
	
	void holdTaskLocked(final DownloadTask task) {
		if (task == null) return;
		boolean wasAlreadyQueued = task.status == Status.QUEUED;
		removeFromExecutorQueueLocked(task);
		releaseSlotLocked(task);
		if (!heldQueue.contains(task)) heldQueue.add(task);
		task.setStatus(Status.QUEUED);
		sortHeldQueueLocked();
		if (!wasAlreadyQueued) EventDispatcher.onQueued(task);
	}
	
	boolean pauseRestoredTaskLocked(DownloadTask task) {
		if (task == null) return false;
		removeFromExecutorQueueLocked(task);
		heldQueue.remove(task);
		if (!occupySlotLocked(task)) return false;
		task.setStatusRestored(Status.PAUSED);
		return true;
	}
	
	void restoreQueuedTaskLocked(DownloadTask task) {
		if (task == null) return;
		removeFromExecutorQueueLocked(task);
		downloader.networkManager.getWaitingForPreferredNetwork().remove(task);
		releaseSlotLocked(task);
		if (!heldQueue.contains(task)) heldQueue.add(task);
		task.setStatusRestored(Status.QUEUED);
		sortHeldQueueLocked();
	}
	
	void submitTask(DownloadTask task, boolean force) {
		synchronized (downloader.mLock) {
			enqueueOrSubmitLocked(task, force);
		}
	}
	
	void submitTaskLocked(DownloadTask task) {
		if (task == null || task.status == Status.CONNECTING || task.status == Status.DOWNLOADING) return;
		
		if (!downloader.networkManager.canRunNow(task)) {
			downloader.networkManager.moveToWaitingForNetwork(task);
			return;
		}
		
		removeFromExecutorQueueLocked(task);
		task.clearFuture();
		
		if (task.mForceDownload) {
			forcedTasks.add(task);
			
		} else if (!occupySlotLocked(task)) {
			holdTaskLocked(task);
			return;
		}
		
		ensureExecutorLocked();
		startPendingManualRetryLocked(task);
		task.setFuture(executor.submitTask(task, sequence.incrementAndGet()));
	}
	
	void resumeOccupiedTask(DownloadTask task) {
		synchronized (downloader.mLock) {
			if (task == null || task.status != Status.PAUSED) return;
			removeFromExecutorQueueLocked(task);
			heldQueue.remove(task);
			task.clearFuture();
			
			if (!downloader.networkManager.canRunNow(task)) {
				downloader.networkManager.moveToWaitingForNetwork(task);
				return;
			}
			
			if (task.mForceDownload) {
				forcedTasks.add(task);
				
			} else if (!hasOccupiedSlotLocked(task) && !occupySlotLocked(task)) {
				holdTaskLocked(task);
				return;
			}
			
			ensureExecutorLocked();
			EventDispatcher.onResumed(task);
			startPendingManualRetryLocked(task);
			task.setFuture(executor.submitTask(task, sequence.incrementAndGet()));
		}
	}
	
	private void startPendingManualRetryLocked(DownloadTask task) {
		if (task == null || !task.mManualRetryPending) return;
		task.mManualRetryPending = false;
		task.mLifecycleStarted = false;
		task.mLifecycleEnded = false;
		task.setStatus(Status.RETRYING);
		EventDispatcher.onRetry(task, 0);
	}
	
	void resumeOccupiedWaiting(DownloadTask task) {
		synchronized (downloader.mLock) {
			if (task == null || task.status != Status.WAITING_FOR_NETWORK) return;
			task.resetStopFlags();
			submitTaskLocked(task);
		}
	}
	
	void removeQueuedTask(DownloadTask task) {
		if (task == null) return;
		
		synchronized (downloader.mLock) {
			heldQueue.remove(task);
			downloader.networkManager.getWaitingForPreferredNetwork().remove(task);
			removeFromExecutorQueueLocked(task);
		}
	}
	
	boolean removeFromExecutorQueueLocked(DownloadTask task) {
		if (task == null || executor == null) return false;
		if (!executor.removeTask(task)) return false;
		
		task.clearFuture();
		releaseSlotLocked(task);
		boolean removedForced = forcedTasks.remove(task);
		if (removedForced && !executor.isShutdown()) ensureExecutorLocked();
		
		return true;
	}
	
	void reorderQueuedTask(DownloadTask task) {
		synchronized (downloader.mLock) {
			if (heldQueue.contains(task)) {
				sortHeldQueueLocked();
				EventDispatcher.onQueued(task);
				return;
			}
			
			if (task != null && task.status == Status.QUEUED && executor != null && executor.removeTask(task)) {
				submitTaskLocked(task);
			}    
		}
	}
	
	void onLockedStateChanged(DownloadTask task) {
		synchronized (downloader.mLock) {
			if (task == null || task.status != Status.QUEUED) return;
			if (task.mLockedInQueue) {
				holdTaskLocked(task);
			} else {
				heldQueue.remove(task);
				enqueueOrSubmitLocked(task, false);
			}
		}
	}
	
	void ensureExecutorLocked() {
		int normalThreads = SimpleDownloader.isAutoConcurrentLocked() ? SimpleDownloader.AUTO_MAX_SLOT : SimpleDownloader.getEffectiveMaxConcurrentLocked();
		int maxThreads = normalThreads + forcedTasks.size();
		
		if (executor == null || executor.isShutdown()) {
			executor = new DownloadExecutor(maxThreads);
			return;
		}
		
		executor.setMaxThreads(maxThreads);
	}
	
	void sortHeldQueueLocked() {
		Collections.sort(heldQueue, QUEUE_ORDER);
	}
	
	boolean hasRunnableQueuedTaskLocked() {
		for (DownloadTask task : heldQueue) {
			if (task == null) continue;
			if (task.status == Status.CANCELLED || task.status == Status.COMPLETED || task.status == Status.FAILED) continue;
			if (task.mLockedInQueue) continue;
			if (downloader.networkManager.canRunNow(task)) return true;
		}
		
		if (executor != null && executor.hasRunnableQueuedTask()) return true;
		return false;
	}
	
	int getQueuePosition(DownloadTask task) {
		synchronized (downloader.mLock) {
			int heldIndex = heldQueue.indexOf(task);
			if (heldIndex >= 0) return heldIndex + 1;
			int executorPos = executor != null ? executor.getQueuePosition(task) : 0;
			return executorPos > 0 ? heldQueue.size() + executorPos : 0;
		}
	}
	
	DownloadExecutor shutdownLocked() {
		heldQueue.clear();
		occupiedSlots.clear();
		forcedTasks.clear();
		DownloadExecutor oldExecutor = executor;
		
		if (oldExecutor != null) {
			oldExecutor.shutdownNow();
			executor = null;
		}
		
		return oldExecutor;
	}
	
	void finishTask(DownloadTask task, boolean removeTask, boolean releaseSlot) {
		if (task == null) return;
		
		synchronized (downloader.mLock) {
			task.clearFuture();
			boolean forcedTaskStopped = forcedTasks.remove(task);
			downloader.taskManager.clearAutoSpeedLocked(task);
			if (task.isFinished()) task.clearFinishedRuntimeData();
			heldQueue.remove(task);
			downloader.networkManager.getWaitingForPreferredNetwork().remove(task);
			
			if (releaseSlot) releaseSlotLocked(task);
			if (forcedTaskStopped && executor != null && !executor.isShutdown()) ensureExecutorLocked();
			
			if (removeTask) {
				if (downloader.taskManager.isCurrentTaskLocked(task)) {
					
					if (!downloader.mEnableHistory) {
						if (downloader.taskDatabase != null) downloader.taskDatabase.removeTask(task.mId);
						
					} else if (downloader.taskDatabase != null && (task.status == Status.COMPLETED || task.status == Status.CANCELLED)) {
						downloader.taskDatabase.clearFinishedInternalData(task.mId);
					}
				}
			}
			
			if (SimpleDownloader.isAutoConcurrentLocked()) {
				SimpleDownloader.autoConcurrencyController.evaluateAfterTaskFinishedLocked();
			}
			
			submitReadyHeldTasksLocked();
			
			if (SimpleDownloader.isAutoConcurrentLocked() && occupiedSlots.isEmpty() && heldQueue.isEmpty() && downloader.networkManager.getWaitingForPreferredNetwork().isEmpty()) {
				SimpleDownloader.autoConcurrencyController.resetLocked();
				SimpleDownloader.setEffectiveMaxConcurrentLocked(1);
			}
		}
	}
	
}


// AUTO CONCURRANCY //

final class AutoConcurrencyController {
	private static final long SPEED_SAMPLE_INTERVAL_MS = 500L;
	private static final long FIRST_ALLOCATION_DELAY_MS = 2500L;
	private static final long SAMPLE_MAX_AGE_MS = 15_000L;
	
	private boolean initialized;
	private boolean firstAllocationDone;
	private long lastRecordTime;
	private long firstSampleTime;
	private long lastSampleTime;
	private int lastSampleActiveCount;
	private long smoothedTotalSpeed;
	
	void resetLocked() {
		initialized = false;
		firstAllocationDone = false;
		
		firstSampleTime = 0;
		lastRecordTime = 0;
		lastSampleTime = 0;
		smoothedTotalSpeed = 0;
		lastSampleActiveCount = 0;
	}
	
	void ensureInitializedLocked() {
		if (initialized) return;
		SimpleDownloader.setEffectiveMaxConcurrentLocked(1);
		initialized = true;
		firstAllocationDone = false;
		firstSampleTime = 0;
		lastRecordTime = 0;
		lastSampleTime = 0;
		smoothedTotalSpeed = 0;
		lastSampleActiveCount = 0;
	}
	
	void markInitializedLocked() {
		initialized = true;
	}
	
	void recordSpeedSampleLocked() {
		if (!SimpleDownloader.isAutoConcurrentLocked()) return;
		if (SimpleDownloader.taskManager == null) return;
		
		ensureInitializedLocked();
		long now = System.currentTimeMillis();
		if (now - lastRecordTime < SPEED_SAMPLE_INTERVAL_MS) return;
		lastRecordTime = now;
		long totalSpeed = SimpleDownloader.taskManager.getCachedTotalActiveSpeedLocked();
		int activeCount = SimpleDownloader.taskManager.getCachedActiveSpeedTaskCountLocked();
		if (totalSpeed <= 0 || activeCount <= 0) return;
		
		if (smoothedTotalSpeed <= 0) {
			smoothedTotalSpeed = totalSpeed;
		} else {
			smoothedTotalSpeed = smoothSpeed(smoothedTotalSpeed, totalSpeed);
		}
		
		lastSampleTime = now;
		lastSampleActiveCount = activeCount;
		
		// Auto mode will start with only one task frist.
		if (!firstAllocationDone) doFirstAllocationLocked(now);
	}
	
	private void doFirstAllocationLocked(long now) {
		if (firstAllocationDone) return;
		if (firstSampleTime <= 0) {
			firstSampleTime = now;
			return;
		}
		
		if (now - firstSampleTime < FIRST_ALLOCATION_DELAY_MS) return;
		int targetSlot = calculateSlotFromSpeed(smoothedTotalSpeed);
		targetSlot = clamp(targetSlot, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
		SimpleDownloader.setEffectiveMaxConcurrentLocked(targetSlot);
		firstAllocationDone = true;
		
		if (SimpleDownloader.slotManager != null) {
			SimpleDownloader.slotManager.submitReadyHeldTasksLocked();
		}
	}
	
	void evaluateAfterTaskFinishedLocked() {
		if (!SimpleDownloader.isAutoConcurrentLocked()) return;
		ensureInitializedLocked();
		if (!firstAllocationDone) return;
		
		long now = System.currentTimeMillis();
		if (lastSampleTime <= 0 || now - lastSampleTime > SAMPLE_MAX_AGE_MS) return;
		
		int targetSlot = calculateSlotFromSpeed(smoothedTotalSpeed);
		targetSlot = clamp(targetSlot, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
		SimpleDownloader.setEffectiveMaxConcurrentLocked(targetSlot);
	}
	
	private int calculateSlotFromSpeed(long bytesPerSecond) {
		if (bytesPerSecond <= 0) return 1;
		double mbps = (bytesPerSecond * 8.0) / 1_000_000.0;
		int roundedMbps = (int) (Math.round(mbps / 10.0) * 10);
		int slots = roundedMbps / 10;
		return clamp(slots, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
	}
	
	private long smoothSpeed(long oldSpeed, long newSpeed) {
		if (oldSpeed <= 0) return newSpeed;
		return (long) ((oldSpeed * 0.65f) + (newSpeed * 0.35f));
	}
	
	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
