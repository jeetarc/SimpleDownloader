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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.WeakHashMap;

final class TaskManager {
	private final SimpleDownloader downloader;
	private final ConcurrentHashMap<Long, DownloadTask> registry = new ConcurrentHashMap<Long, DownloadTask>();
	private final List<DownloadTask> taskList = new ArrayList<DownloadTask>();
	private final Map<Object, List<Long>> contextTaskMap = new WeakHashMap<Object, List<Long>>();
	private final AtomicLong idGenerator = new AtomicLong(System.currentTimeMillis());
	private final List<TaskListObserver> observerList = new ArrayList<TaskListObserver>();
	private final Map<Object, List<TaskListObserver>> contextObserverMap = new WeakHashMap<Object, List<TaskListObserver>>();
	private boolean enableSorting = true;
	private boolean tasksChangedPending;
	private long mTotalActiveSpeedForAutoConcurrency;
	private int mActiveSpeedTaskCountForAutoConcurrency;
	
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
	
	private Comparator<DownloadTask> taskComparator = DEFAULT_TASK_ORDER;
	
	TaskManager(SimpleDownloader downloader) {
		this.downloader = downloader;
	}
	
	void addObserver(Object owner, TaskListObserver observer) {
		if (observer == null) return;
		
		synchronized (downloader.mLock) {
			if (!observerList.contains(observer)) observerList.add(observer);
			
			if (owner != null) {
				List<TaskListObserver> ownedObservers = contextObserverMap.get(owner);
				
				if (ownedObservers == null) {
					ownedObservers = new ArrayList<TaskListObserver>();
					contextObserverMap.put(owner, ownedObservers);
				}
				
				if (!ownedObservers.contains(observer)) ownedObservers.add(observer);
			}
		}
		
		requestTasksChanged();
	}
	
	void removeObserver(TaskListObserver observer) {
		if (observer == null) return;
		
		synchronized (downloader.mLock) {
			observerList.remove(observer);
			for (List<TaskListObserver> ownedObservers : contextObserverMap.values()) {
				ownedObservers.remove(observer);
			}
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
	
	long nextId() {
		return idGenerator.incrementAndGet();
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
		validateFieldValue(field, value);
		
		synchronized (downloader.mLock) {
			DownloadTask latest = null;
			for (DownloadTask task : taskList) {
				if (!matches(task, field, value)) continue;
				if (latest == null || task.mCreatedAt > latest.mCreatedAt) latest = task;
			}
			
			return latest;
		}
	}
	
	<T> ArrayList<DownloadTask> getTasks(TaskField<T> field, T value) {
		validateFieldValue(field, value);
		ArrayList<DownloadTask> result = new ArrayList<DownloadTask>();
		
		synchronized (downloader.mLock) {
			for (DownloadTask task : taskList) {
				if (matches(task, field, value)) result.add(task);
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
	
	private static int getTaskSortGroup(DownloadTask task) {
		if (task == null) return 99;
		if (task.isOccupiedSlot() || task.isActive()) return 1;
		if (task.isQueued() || task.isPaused()) return 2;
		if (task.isFinished()) return 3;
		return 4;
	}
	
	void trackListenerOwnerLocked(Object owner, long id) {
		if (owner == null) return;
		List<Long> ids = contextTaskMap.get(owner);
		
		if (ids == null) {
			ids = new ArrayList<Long>();
			contextTaskMap.put(owner, ids);
		}
		
		if (!ids.contains(id)) ids.add(id);
	}
	
	void releaseCallbacks(Object owner) {
		if (owner == null) return;
		
		synchronized (downloader.mLock) {
			List<Long> ids = contextTaskMap.remove(owner);
			
			if (ids != null) {
				for (Long id : ids) {
					if (id == null) continue;
					DownloadTask task = registry.get(id);
					if (task != null) task.releaseCallbacks();
				}
			}
			
			List<TaskListObserver> ownedObservers = contextObserverMap.remove(owner);
			if (ownedObservers != null) observerList.removeAll(ownedObservers);
		}
	}
	
	void shutdownLocked() {
		for (DownloadTask task : registry.values()) {
			if (task != null) task.releaseCallbacks();
		}
		
		registry.clear();
		taskList.clear();
		contextTaskMap.clear();
		observerList.clear();
		contextObserverMap.clear();
		tasksChangedPending = false;
		
		mTotalActiveSpeedForAutoConcurrency = 0;
		mActiveSpeedTaskCountForAutoConcurrency = 0;
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
	
	boolean hasBusyOverwriteTarget(String overwriteKey, long ignoreTaskId) {
		if (overwriteKey == null || overwriteKey.trim().isEmpty()) return false;
		
		for (DownloadTask task : getTasks()) {
			if (task == null) continue;
			if (task.mId == ignoreTaskId) continue;
			if (task.isFinished()) continue;
			String taskOverwriteKey = task.getOverwriteKey();
			if (overwriteKey.equals(taskOverwriteKey)) return true;
		}
		
		return false;
	}
	
	List<DownloadTask> restoreTasks(SimpleDownloader requester, List<TaskState> states) {
		List<DownloadTask> restored = new ArrayList<DownloadTask>();
		if (requester == null || states == null || states.isEmpty()) return restored;
		
		synchronized (downloader.mLock) {
			for (TaskState state : states) {
				DownloadTask task = restoreTaskFromStateLocked(requester, state);
				if (task != null) restored.add(task);
			}
			
			addTasksLocked(restored);
			downloader.slotManager.sortHeldQueueLocked();
		}
		
		return restored;
	}
	
	DownloadTask restoreTask(SimpleDownloader requester, TaskState state) {
		if (requester == null || state == null) return null;
		
		synchronized (downloader.mLock) {
			DownloadTask task = restoreTaskFromStateLocked(requester, state);
			
			if (task != null) {
				addTaskLocked(task);
				downloader.slotManager.sortHeldQueueLocked();
			}
			
			return task;
		}
	}
	
	private DownloadTask restoreTaskFromStateLocked(SimpleDownloader requester, TaskState state) {
		if (state == null) return null;
		DownloadTask existing = getTask(state.id);
		
		if (existing != null) {
			attachRestoreListenerLocked(requester, existing);
			return existing;
		}
		
		DownloadTask task = DownloadTask.restore(requester, state);
		if (task == null) return null;
		
		attachRestoreListenerLocked(requester, task);
		putTaskLocked(task);
		restoreTaskPositionLocked(task, state.status);
		notifyRestoredTask(task);
		return task;
	}
	
	private void attachRestoreListenerLocked(SimpleDownloader requester, DownloadTask task) {
		if (requester == null || task == null || requester.mRequestBuilder.listener == null) return;
		task.addListener(requester.mRequestBuilder.listener);
		trackListenerOwnerLocked(requester.mListenerOwnerKey, task.mId);
	}
	
	private void restoreTaskPositionLocked(DownloadTask task, Status restored) {
		if (task == null) return;
		if (restored == null) restored = Status.PAUSED;
		boolean networkAvailable = downloader.networkManager.isNetworkAvailable();
		int networkType = downloader.networkManager.getNetworkType();
		boolean wasActiveBeforeClose = restored == Status.CONNECTING || restored == Status.DOWNLOADING || restored == Status.RETRYING;
		
		if (wasActiveBeforeClose || restored == Status.PAUSED || restored == Status.QUEUED) {
			if (!downloader.slotManager.pauseRestoredTaskLocked(task)) {
				downloader.slotManager.restoreQueuedTaskLocked(task);
			}
			
			return;
		}
		
		if (restored == Status.WAITING_FOR_NETWORK) {
			boolean noPreferredNetwork = networkAvailable && task.mWifiOnly && networkType != SimpleDownloader.NETWORK_TYPE_WIFI;
			
			if (noPreferredNetwork) {
				task.setStatusRestored(Status.WAITING_FOR_NETWORK);
				
				if (!downloader.networkManager.getWaitingForPreferredNetwork().contains(task)) {
					downloader.networkManager.getWaitingForPreferredNetwork().add(task);
				}
				
			} else if (!downloader.slotManager.pauseRestoredTaskLocked(task)) {
				downloader.slotManager.restoreQueuedTaskLocked(task);
			}
			
			return;
		}
		
		task.setStatusRestored(restored);
	}
	
	private void notifyRestoredTask(DownloadTask task) {
		if (task == null) return;
		
		if (task.status == Status.PAUSED) {
			EventDispatcher.onPaused(task);
			
		} else if (task.status == Status.QUEUED) {
			EventDispatcher.onQueued(task);
			
		} else if (task.status == Status.WAITING_FOR_NETWORK) {
			EventDispatcher.onWaitingForNetwork(task);
		}
	}
	
	private static <T> void validateFieldValue(TaskField<T> field, T value) {
		if (field == null) throw new IllegalArgumentException("TaskField cannot be null.");
		
		if (value != null && !field.type.isInstance(value)) {
			throw new IllegalArgumentException("Expected "
			+ field.type.getSimpleName()
			+ " for field "
			+ field.column
			+ ", but received "
			+ value.getClass().getSimpleName()
			+ ".");
		}
	}
	
	private static <T> boolean matches(DownloadTask task, TaskField<T> field, T expected) {
		if (task == null) return false;
		Object actual;
		
		switch (field.column) {
			case "id":
			actual = task.mId;
			break;
			
			case "file_url":
			actual = task.mFileUrl;
			break;
			
			case "status":
			actual = task.status;
			break;
			
			case "priority":
			actual = task.mPriority;
			break;
			
			case "mime_type":
			actual = task.mMimeType;
			break;
			
			case "output_file_name":
			actual = task.getFileName();
			break;
			
			case "created_at":
			actual = task.mCreatedAt;
			break;
			
			case "wifi_only":
			actual = task.mWifiOnly;
			break;
			
			case "buffer_size":
			actual = task.mBufferSize;
			break;
			
			case "progress":
			actual = task.mProgress;
			break;
			
			case "bytes_downloaded":
			actual = task.mBytesDownloaded;
			break;
			
			case "total_bytes":
			actual = task.mTotalBytes;
			break;
			
			case "output_uri":
			actual = task.mOutputUri;
			break;
			
			case "output_path":
			actual = task.mOutputPath;
			break;
			
			case "overwrite_uri":
			actual = task.mOverwriteUri;
			break;
			
			case "overwrite_path":
			actual = task.mOverwritePath;
			break;
			
			case "tree_uri":
			actual = task.mTreeUri;
			break;
			
			case "output_folder_path":
			actual = task.mOutputFolderPath;
			break;
			
			case "delete_on_removal":
			actual = task.mDeleteOnRemoval;
			break;
			
			case "locked_in_queue":
			actual = task.mLockedInQueue;
			break;
			
			default:
			throw new IllegalArgumentException("Unsupported TaskField: " + field.column);
		}
		
		return actual == expected || (actual != null && actual.equals(expected));
	}
	
	void updateAutoSpeedLocked(DownloadTask task, long newSpeed) {
		if (task == null) return;
		long oldSpeed = task.mLastSpeedForAutoConcurrency;
		if (oldSpeed < 0) oldSpeed = 0;
		if (newSpeed < 0) newSpeed = 0;
		
		boolean wasActiveForAuto = oldSpeed > 0;
		boolean isActiveForAuto = newSpeed > 0;
		
		mTotalActiveSpeedForAutoConcurrency -= oldSpeed;
		mTotalActiveSpeedForAutoConcurrency += newSpeed;
		
		if (!wasActiveForAuto && isActiveForAuto) {
			mActiveSpeedTaskCountForAutoConcurrency++;
		} else if (wasActiveForAuto && !isActiveForAuto) {
			mActiveSpeedTaskCountForAutoConcurrency--;
		}
		
		if (mTotalActiveSpeedForAutoConcurrency < 0) {
			mTotalActiveSpeedForAutoConcurrency = 0;
		}
		if (mActiveSpeedTaskCountForAutoConcurrency < 0) {
			mActiveSpeedTaskCountForAutoConcurrency = 0;
		}
		
		task.mLastSpeedForAutoConcurrency = newSpeed;
	}
	
	void clearAutoSpeedLocked(DownloadTask task) {
		if (task == null) return;
		long oldSpeed = task.mLastSpeedForAutoConcurrency;
		
		if (oldSpeed > 0) {
			mTotalActiveSpeedForAutoConcurrency -= oldSpeed;
			mActiveSpeedTaskCountForAutoConcurrency--;
		}
		
		if (mTotalActiveSpeedForAutoConcurrency < 0) {
			mTotalActiveSpeedForAutoConcurrency = 0;
		}
		
		if (mActiveSpeedTaskCountForAutoConcurrency < 0) {
			mActiveSpeedTaskCountForAutoConcurrency = 0;
		}
		
		task.mLastSpeedForAutoConcurrency = 0;
	}
	
	long getCachedTotalActiveSpeedLocked() {
		return Math.max(0, mTotalActiveSpeedForAutoConcurrency);
	}
	
	int getCachedActiveSpeedTaskCountLocked() {
		return Math.max(0, mActiveSpeedTaskCountForAutoConcurrency);
	}
}
