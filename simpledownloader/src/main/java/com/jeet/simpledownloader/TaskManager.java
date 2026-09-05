package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class TaskManager {
	private final SimpleDownloader downloader;
	private final ConcurrentHashMap<Long, DownloadTask> registry = new ConcurrentHashMap<Long, DownloadTask>();
	private final List<DownloadTask> taskList = new ArrayList<DownloadTask>();
	private final List<TaskListObserver> observerList = new ArrayList<TaskListObserver>();
	private boolean enableSorting = true;
	private boolean tasksChangedPending;
	private volatile long mTotalActiveSpeedForAutoConcurrency;
	private volatile int mActiveSpeedTaskCountForAutoConcurrency;
	private Comparator<DownloadTask> taskComparator = DEFAULT_TASK_ORDER;
	
	TaskManager(SimpleDownloader downloader) {
		this.downloader = downloader;
	}
	
	long nextId() {
		return downloader.nextTaskId();
	}
	
	DownloadTask getTask(long id) {
		return registry.get(id);
	}
	
	ArrayList<DownloadTask> snapshot() {
		return new ArrayList<DownloadTask>(registry.values());
	}
	
	ArrayList<DownloadTask> getTasks() {
		synchronized (downloader.mLock) {
			return new ArrayList<DownloadTask>(taskList);
		}
	}
	
	<T> DownloadTask getTask(TaskField<T> field, T value) {
		field.validateValue(value);
		
		synchronized (downloader.mLock) {
			DownloadTask latest = null;
			for (DownloadTask task : taskList) {
				if (!field.matches(task, value)) continue;
				if (latest == null || task.mCreatedAt > latest.mCreatedAt) latest = task;
			}
			
			return latest;
		}
	}
	
	<T> ArrayList<DownloadTask> getTasks(TaskField<T> field, T value) {
		field.validateValue(value);
		ArrayList<DownloadTask> result = new ArrayList<DownloadTask>();
		
		synchronized (downloader.mLock) {
			for (DownloadTask task : taskList) {
				if (field.matches(task, value)) result.add(task);
			}
		}
		
		return result;
	}
	
	boolean hasTask(long id) {
		return registry.containsKey(id);
	}
	
	boolean hasTask(String fileUrl) {
		if (fileUrl == null) return false;
		for (DownloadTask task : registry.values()) {
			if (task != null && fileUrl.equals(task.mFileUrl)) return true;
		}
		return false;
	}
	
	int getTotalCount() {
		return registry.size();
	}
	
	int getActiveCount() {
		int count = 0;
		for (DownloadTask task : registry.values()) {
			if (task != null && task.isActive()) count++;
		}
		return count;
	}
	
	boolean isDownloading(long id) {
		DownloadTask task = registry.get(id);
		return task != null && task.getStatus() == Status.DOWNLOADING;
	}
	
	boolean isDownloading() {
		for (DownloadTask task : registry.values()) {
			if (task != null && task.getStatus() == Status.DOWNLOADING) return true;
		}
		return false;
	}
	
	boolean isEmpty() {
		return registry.isEmpty();
	}
	
	void putTaskLocked(DownloadTask task) {
		if (task != null) registry.put(task.mId, task);
	}
	
	DownloadTask removeFromRegistryLocked(long id) {
		return registry.remove(id);
	}
	
	void removeTaskCompletelyLocked(DownloadTask task) {
		if (task == null) return;
		registry.remove(task.mId);
		removeTaskLocked(task);
	}
	
	boolean isCurrentTaskLocked(DownloadTask task) {
		return task != null && registry.get(task.mId) == task;
	}
	
	void addTasksLocked(List<DownloadTask> tasks) {
		if (tasks == null || tasks.isEmpty()) return;
		boolean added = false;
		
		for (DownloadTask task : tasks) {
			if (task == null || taskList.contains(task)) continue;
			taskList.add(task);
			added = true;
		}
		
		if (!added) return;
		sortTasksLocked();
		requestTasksChangedLocked();
	}
	
	void addTaskLocked(DownloadTask task) {
		if (task == null || taskList.contains(task)) return;
		
		taskList.add(task);
		sortTasksLocked();
		requestTasksChangedLocked();
	}
	
	void removeTaskLocked(DownloadTask task) {
		if (task == null || !taskList.remove(task)) return;
		sortTasksLocked();
		requestTasksChangedLocked();
	}
	
	List<DownloadTask> restoreTasks(SimpleDownloader requester, List<TaskState> states, boolean autoRestore) {
		List<DownloadTask> restored = new ArrayList<DownloadTask>();
		if (requester == null || states == null || states.isEmpty()) return restored;
		
		synchronized (downloader.mLock) {
			for (TaskState state : states) {
				DownloadTask task = restoreTaskFromStateLocked(requester, state, autoRestore);
				if (task != null) restored.add(task);
			}
			
			addTasksLocked(restored);
			downloader.slotManager.sortHeldQueueLocked();
			if (autoRestore) downloader.slotManager.submitReadyHeldTasksLocked();
		}
		
		return restored;
	}
	
	private DownloadTask restoreTaskFromStateLocked(SimpleDownloader requester, TaskState state, boolean autoRestore) {
		if (state == null) return null;
		if (requester != null && !requester.getOwnerId().equals(state.ownerId)) return null;
		
		synchronized (SimpleDownloader.GLOBAL_LOCK) {
			DownloadTask existing = getTask(state.id);
			if (existing != null) return existing;
			
			for (SimpleDownloader downloader : SimpleDownloader.snapshotInstancesLockedForTaskRestore()) {
				if (downloader == null || downloader == requester || downloader.mShutdown) continue;
				DownloadTask other = downloader.taskManager.getTask(state.id);
				if (other != null) return null;
			}
			
			DownloadTask task = DownloadTask.restore(requester, state);
			if (task == null) return null;
			
			putTaskLocked(task);
			restoreTaskPositionLocked(task, state.status, autoRestore);
			notifyRestoredTask(task);
			return task;
		}
	}
	
	
	private void restoreTaskPositionLocked(DownloadTask task, Status restored, boolean autoRestore) {
		if (task == null) return;
		if (restored == null) restored = Status.PAUSED;
		
		if (restored == Status.PAUSED) {
			if (!downloader.slotManager.pauseRestoredTaskLocked(task)) downloader.slotManager.restoreQueuedTaskLocked(task);
			return;
		}
		
		boolean unfinished = restored == Status.STARTING || restored == Status.CONNECTING || restored == Status.DOWNLOADING
		|| restored == Status.RETRYING || restored == Status.QUEUED || restored == Status.WAITING_FOR_NETWORK;
		
		if (unfinished) {
			if (autoRestore) downloader.slotManager.restoreQueuedTaskLocked(task);
			else if (!downloader.slotManager.pauseRestoredTaskLocked(task)) downloader.slotManager.restoreQueuedTaskLocked(task);
			return;
		}
		
		// Completed, failed and cancelled remain same
		task.setStatusRestored(restored);
	}
	
	private void notifyRestoredTask(DownloadTask task) {
		if (task == null) return;
		if (task.status == Status.PAUSED) EventDispatcher.onPaused(task);
		else if (task.status == Status.QUEUED) EventDispatcher.onQueued(task);
		else if (task.status == Status.WAITING_FOR_NETWORK) EventDispatcher.onWaitingForNetwork(task);
	}
	
	
	void releaseAllCallbacks() {
		synchronized (downloader.mLock) {
			for (DownloadTask task : registry.values()) {
				if (task != null) task.removeAllListeners();
			}
			observerList.clear();
		}
	}
	
	void shutdownLocked() {
		for (DownloadTask task : registry.values()) {
			if (task != null) task.removeAllListeners();
		}
		
		registry.clear();
		taskList.clear();
		observerList.clear();
		tasksChangedPending = false;
		mTotalActiveSpeedForAutoConcurrency = 0;
		mActiveSpeedTaskCountForAutoConcurrency = 0;
	}
	
	void requestTasksChanged() {
		synchronized (downloader.mLock) {
			requestTasksChangedLocked();
		}
	}
	
	void requestTasksChangedLocked() {
		if (tasksChangedPending || observerList.isEmpty()) return;
		tasksChangedPending = true;
		EventDispatcher.onTasksChanged(this);
	}
	
	List<DownloadTask> consumeTasksChangedSnapshot() {
		synchronized (downloader.mLock) {
			tasksChangedPending = false;
			return new ArrayList<DownloadTask>(taskList);
		}
	}
	
	void setSortingEnabled(boolean enable) {
		synchronized (downloader.mLock) {
			enableSorting = enable;
			if (enable) sortTasksLocked();
		}
	}
	
	void sortTasks() {
		synchronized (downloader.mLock) {
			sortTasksLocked();
		}
	}
	
	void sortTasksLocked() {
		if (!enableSorting || taskList.size() <= 1) return;
		boolean orderChanged = false;
		
		for (int i = 1; i < taskList.size(); i++) {
			DownloadTask previous = taskList.get(i - 1);
			DownloadTask current = taskList.get(i);
			if (taskComparator.compare(previous, current) > 0) {
				orderChanged = true;
				break;
			}
		}
		
		if (!orderChanged) return;
		Collections.sort(taskList, taskComparator);
		requestTasksChangedLocked();
	}
	
	private static int getTaskSortGroup(DownloadTask task) {
		if (task == null) return 99;
		if (task.isOccupiedSlot() || task.isActive()) return 1;
		if (task.isQueued() || task.isPaused()) return 2;
		if (task.isFinished()) return 3;
		return 4;
	}
	
	void addObserver(TaskListObserver observer) {
		if (observer == null) return;
		synchronized (downloader.mLock) {
			if (!observerList.contains(observer)) observerList.add(observer);
		}
		
		requestTasksChanged();
	}
	
	void removeObserver(TaskListObserver observer) {
		if (observer == null) return;
		synchronized (downloader.mLock) {
			observerList.remove(observer);
		}
	}
	
	List<TaskListObserver> snapshotObservers() {
		synchronized (downloader.mLock) {
			return new ArrayList<TaskListObserver>(observerList);
		}
	}
	
	boolean hasObserver(TaskListObserver observer) {
		synchronized (downloader.mLock) {
			return observerList.contains(observer);
		}
	}
	
	void setTaskComparator(Comparator<DownloadTask> comparator) {
		synchronized (downloader.mLock) {
			taskComparator = comparator != null ? comparator : DEFAULT_TASK_ORDER;
			sortTasksLocked();
		}
	}
	
	private static final Comparator<DownloadTask> DEFAULT_TASK_ORDER = new Comparator<DownloadTask>() {
		@Override
		public int compare(DownloadTask a, DownloadTask b) {
			int groupA = getTaskSortGroup(a);
			int groupB = getTaskSortGroup(b);
			if (groupA != groupB) return Integer.compare(groupA, groupB);
			
			// Queued: priority first, then oldest first.
			if (groupA == 2) {
				int priorityCompare = Integer.compare(b.getPriority().getWeight(), a.getPriority().getWeight());
				if (priorityCompare != 0) return priorityCompare;
				return Long.compare(a.getCreatedAt(), b.getCreatedAt());
			}
			
			// Finished: newest first.
			if (groupA == 3) return Long.compare(b.getCreatedAt(), a.getCreatedAt());
			
            int priorityCompare = Integer.compare(b.getPriority().getWeight(), a.getPriority().getWeight());
			if (priorityCompare != 0) return priorityCompare;
			return Long.compare(b.getCreatedAt(), a.getCreatedAt());
		}
	};
	
	void updateAutoSpeedLocked(DownloadTask task, long newSpeed) {
		if (task == null) return;
		long oldSpeed = task.mLastSpeedForAutoConcurrency;
		if (oldSpeed < 0) oldSpeed = 0;
		if (newSpeed < 0) newSpeed = 0;
		
		boolean wasActiveForAuto = oldSpeed > 0;
		boolean isActiveForAuto = newSpeed > 0;
		
		mTotalActiveSpeedForAutoConcurrency -= oldSpeed;
		mTotalActiveSpeedForAutoConcurrency += newSpeed;
		
		if (!wasActiveForAuto && isActiveForAuto) mActiveSpeedTaskCountForAutoConcurrency++;
		else if (wasActiveForAuto && !isActiveForAuto) mActiveSpeedTaskCountForAutoConcurrency--;
		
        if (mTotalActiveSpeedForAutoConcurrency < 0) mTotalActiveSpeedForAutoConcurrency = 0;
		if (mActiveSpeedTaskCountForAutoConcurrency < 0) mActiveSpeedTaskCountForAutoConcurrency = 0;
		task.mLastSpeedForAutoConcurrency = newSpeed;
	}
	
	void clearAutoSpeedLocked(DownloadTask task) {
		if (task == null) return;
		long oldSpeed = task.mLastSpeedForAutoConcurrency;
		
		if (oldSpeed > 0) {
			mTotalActiveSpeedForAutoConcurrency -= oldSpeed;
			mActiveSpeedTaskCountForAutoConcurrency--;
		}
		
		if (mTotalActiveSpeedForAutoConcurrency < 0) mTotalActiveSpeedForAutoConcurrency = 0;
		if (mActiveSpeedTaskCountForAutoConcurrency < 0) mActiveSpeedTaskCountForAutoConcurrency = 0;
		task.mLastSpeedForAutoConcurrency = 0;
	}
	
	long getCachedTotalActiveSpeedLocked() {
		return Math.max(0, mTotalActiveSpeedForAutoConcurrency);
	}
	
	int getCachedActiveSpeedTaskCountLocked() {
		return Math.max(0, mActiveSpeedTaskCountForAutoConcurrency);
	}
}
