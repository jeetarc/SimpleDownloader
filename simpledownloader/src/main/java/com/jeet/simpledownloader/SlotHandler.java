package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static com.jeet.simpledownloader.SimpleDownloader.networkManager;
import android.util.Log;

final class SlotHandler {
	private static DownloadExecutor sExecutor;
	private static final List<DownloadTask> sHeldQueue = new ArrayList<>();
	private static final HashSet<DownloadTask> sOccupiedSlots = new HashSet<>();
	private static final AtomicLong sSequence = new AtomicLong(0);
	private SlotHandler() {}
	
	static int getQueuedCount() {
		synchronized (SimpleDownloader.class) {
			return sHeldQueue.size() + (sExecutor != null ? sExecutor.getQueuedCount() : 0);
		}
	}
	
	static int getOccupiedCount() {
		synchronized (SimpleDownloader.class) {
			return sOccupiedSlots.size();
		}
	}
	
	static boolean hasWork() {
		synchronized (SimpleDownloader.class) {
			return !sHeldQueue.isEmpty() || !sOccupiedSlots.isEmpty() || (sExecutor != null && (sExecutor.getActiveCount() > 0 || sExecutor.getQueuedCount() > 0));
		}
	}
	
	static boolean hasFreeSlotLocked() {
		return SimpleDownloader.sMaxConcurrent <= 0 || sOccupiedSlots.size() < SimpleDownloader.sMaxConcurrent;
	}
	
	static boolean occupySlotLocked(DownloadTask task) {
		if (task == null) return false;
		if (sOccupiedSlots.contains(task)) return true;
		if (!hasFreeSlotLocked()) return false;
		sOccupiedSlots.add(task);
		return true;
	}
	
	static void releaseSlotLocked(DownloadTask task) {
		if (task != null) sOccupiedSlots.remove(task);
	}
	
	static boolean hasOccupiedSlotLocked(DownloadTask task) {
		return task != null && sOccupiedSlots.contains(task);
	}
	
	static void dispatchReadyTasks() {
		synchronized (SimpleDownloader.class) {
			submitReadyHeldTasksLocked();
		}
	}
	
	static void submitReadyHeldTasksLocked() {
		if (!networkManager.getWaitingForPreferredNetwork().isEmpty()) {
			for (int i = 0; i < networkManager.getWaitingForPreferredNetwork().size(); i++) {
				DownloadTask task = networkManager.getWaitingForPreferredNetwork().get(i);
				if (task == null) {
					networkManager.getWaitingForPreferredNetwork().remove(i);
					i--;
					continue;
				}
				
				if (!networkManager.canRunNow(task)) continue;
				if (!hasFreeSlotLocked() && !hasOccupiedSlotLocked(task)) break;
				networkManager.getWaitingForPreferredNetwork().remove(i);
				i--;
				enqueueOrSubmitLocked(task, true);
			}
		}
		
		if (!SimpleDownloader.sDownloadOnSlotFree) {
			SimpleDownloader.sortTaskListLocked();
			return;
		}
		
		for (int i = 0; i < sHeldQueue.size(); i++) {
			DownloadTask task = sHeldQueue.get(i);
			if (task == null || task.status == Status.CANCELLED || task.status == Status.COMPLETED || task.status == Status.FAILED) {
				sHeldQueue.remove(i);
				i--;
				continue;
			}
			
			if (!task.mLockedInQueue && (hasFreeSlotLocked() || hasOccupiedSlotLocked(task))) {
				sHeldQueue.remove(i);
				i--;
				submitTaskLocked(task);
			}
			if (!hasFreeSlotLocked()) break;
		}
		SimpleDownloader.sortTaskListLocked();
	}
	
	static void enqueueOrSubmit(DownloadTask task, boolean force) {
		synchronized (SimpleDownloader.class) {
			enqueueOrSubmitLocked(task, force);
		}
	}
	
	static void enqueueOrSubmitLocked(DownloadTask task, boolean force) {
		if (task == null) return;
		networkManager.getWaitingForPreferredNetwork().remove(task);
		if (!force && (task.mLockedInQueue || !SimpleDownloader.sDownloadOnSlotFree)) {
			holdTaskLocked(task);
			return;
		}
		sHeldQueue.remove(task);
		submitTaskLocked(task);
	}
	
	static void holdTaskLocked(final DownloadTask task) {
		if (task == null) return;
		removeFromExecutorQueueLocked(task);
		if (!sHeldQueue.contains(task)) sHeldQueue.add(task);
		task.setStatus(Status.QUEUED);
		sortHeldQueueLocked();
		ListenerDispatcher.onQueued(task);
	}
	
	static boolean pauseRestoredTaskLocked(DownloadTask task) {
		if (task == null) return false;
		if (!occupySlotLocked(task)) return false;
		removeFromExecutorQueueLocked(task);
		sHeldQueue.remove(task);
		task.setStatus(Status.PAUSED);
		return true;
	}
	
	static void restoreQueuedTaskLocked(final DownloadTask task) {
		if (task == null) return;
		removeFromExecutorQueueLocked(task);
		if (!sHeldQueue.contains(task)) sHeldQueue.add(task);
		task.setStatus(Status.QUEUED);
		sortHeldQueueLocked();
	}
	
	static void submitTask(DownloadTask task, boolean force) {
		synchronized (SimpleDownloader.class) {
			enqueueOrSubmitLocked(task, force);
		}
	}
	
	static void submitTaskLocked(DownloadTask task) {
		if (task == null || task.status == Status.CONNECTING || task.status == Status.DOWNLOADING || task.status == Status.RETRYING) return;
		if (!networkManager.canRunNow(task) && task.status != Status.WAITING_FOR_NETWORK) {
			networkManager.moveToWaitingForNetwork(task);
			return;
		}
		
		ensureExecutorLocked();
		removeFromExecutorQueueLocked(task);
		task.clearFuture();
		if (!occupySlotLocked(task)) {
			holdTaskLocked(task);
			return;
		}
		
		android.util.Log.d("SLOT_SUBMIT", "ACTUAL SUBMIT taskObj=" + System.identityHashCode(task) + " id=" + task.mId + " status=" + task.status);
		task.setFuture(sExecutor.submitTask(task, sSequence.incrementAndGet()));
	}
	
	static void resumeOccupiedTask(DownloadTask task) {
		synchronized (SimpleDownloader.class) {
			if (task == null) return;
			ensureExecutorLocked();
			removeFromExecutorQueueLocked(task);
			sHeldQueue.remove(task);
			task.clearFuture();
			
			if (!hasOccupiedSlotLocked(task) && !occupySlotLocked(task)) {
				holdTaskLocked(task);
				return;
			}
			
			task.setFuture(sExecutor.submitTask(task, sSequence.incrementAndGet()));
		}
	}
	
	static void removeQueuedTask(DownloadTask task) {
		synchronized (SimpleDownloader.class) {
			sHeldQueue.remove(task);
			networkManager.getWaitingForPreferredNetwork().remove(task);
			removeFromExecutorQueueLocked(task);
		}
	}
	
	static void removeFromExecutorQueueLocked(DownloadTask task) {
		if (task == null || sExecutor == null) return;
		if (sExecutor.removeTask(task)) {
			task.clearFuture();
		}
	}
	
	static void reorderQueuedTask(DownloadTask task) {
		synchronized (SimpleDownloader.class) {
			if (sHeldQueue.contains(task)) {
				sortHeldQueueLocked();
				ListenerDispatcher.onQueued(task);
				return;
			}
			
			if (task != null && task.status == Status.QUEUED && sExecutor != null && sExecutor.removeTask(task)) {
				submitTaskLocked(task);
			}
		}
	}
	
	static void onLockedStateChanged(DownloadTask task) {
		synchronized (SimpleDownloader.class) {
			if (task == null || task.status != Status.QUEUED) return;
			if (task.mLockedInQueue) {
				holdTaskLocked(task);
			} else {
				sHeldQueue.remove(task);
				enqueueOrSubmitLocked(task, false);
			}
		}
	}
	
	static void ensureExecutorLocked() {
		int max = SimpleDownloader.sMaxConcurrent <= 0 ? 64 : SimpleDownloader.sMaxConcurrent;
		if (sExecutor == null || sExecutor.isShutdown()) {
			sExecutor = new DownloadExecutor(max);
			return;
		}
		
		if (!sExecutor.canResizeTo(max)) {
			if (sExecutor.getActiveCount() == 0 && sExecutor.getQueuedCount() == 0) {
				sExecutor.shutdownNow();
				sExecutor = new DownloadExecutor(max);
			}
			return;
		}
		
		sExecutor.setMaxThreads(max);
	}
	
	static void sortHeldQueueLocked() {
		Collections.sort(sHeldQueue, new Comparator<DownloadTask>() {
			@Override
			public int compare(DownloadTask a, DownloadTask b) {
				int priorityCompare = b.getPriority().getWeight() - a.getPriority().getWeight();
				if (priorityCompare != 0) return priorityCompare;
				return Long.compare(a.getCreatedAt(), b.getCreatedAt());
			}
		});
	}
	
	static boolean hasRunnableQueuedTaskLocked() {
		for (DownloadTask task : sHeldQueue) {
			if (task == null) continue;
			if (task.status == Status.CANCELLED || task.status == Status.COMPLETED || task.status == Status.FAILED) continue;
			if (task.mLockedInQueue) continue;
			if (networkManager.canRunNow(task)) return true;
		}
		
		if (sExecutor != null && sExecutor.hasRunnableQueuedTask()) return true;
		return false;
	}
	
	static int getQueuePosition(DownloadTask task) {
		synchronized (SimpleDownloader.class) {
			int heldIndex = sHeldQueue.indexOf(task);
			if (heldIndex >= 0) return heldIndex + 1;
			int executorPos = sExecutor != null ? sExecutor.getQueuePosition(task) : 0;
			return executorPos > 0 ? sHeldQueue.size() + executorPos : 0;
		}
	}
	
	static void finishTask(DownloadTask task, boolean removeTask, boolean releaseSlot) {
		if (task == null) return;
		
		synchronized (SimpleDownloader.class) {
			task.clearFuture();
			sHeldQueue.remove(task);
			networkManager.getWaitingForPreferredNetwork().remove(task);
			if (releaseSlot) releaseSlotLocked(task);
			
			if (removeTask) {
				if (SimpleDownloader.isCurrentTaskLocked(task)) {
					if (SimpleDownloader.sEnableHistory != null && !SimpleDownloader.sEnableHistory) {
						DownloadTask removed = SimpleDownloader.sRegistry.remove(task.mId);
						SimpleDownloader.removeTaskFromListLocked(removed);
					}
					
					if (SimpleDownloader.sEnableHistory == null || !SimpleDownloader.sEnableHistory) {
						if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(task.mId);
					}
				}
			}
			
			submitReadyHeldTasksLocked();
			networkManager.release();
		}
	}
}
