package com.jeet.simpledownloader;


/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.List;

/**
 * Observes ordering and task updates in the list.
 *
 * <p>All callbacks are run on the main thread.</p>
 */
public interface TaskListObserver {

	/**
	 * Called when the task list order changes.
	 *
	 * <p>This happens when tasks added, removed, restored, changes
	 * status, priority, comparator, etc that affect list order.</p>
	 *
	 * @param tasks an read only snapshot of the current task list oder,
     * The {@code DownloadTask} objects inside are still live and can update.
     * @param size is the count of the DownloadTask inside the tasks list.
	 */
	default void onTasksChanged(int size, List<DownloadTask> tasks) {}

	/**
	 * Called when a task's data changes, (eg. status, progress, bytes, speed, ETA, etc).
	 *
	 * <p>Use it to update only the matching item.</p>
	 *
	 * @param id the updated task's ID
	 * @param task the updated task instance
	 */
	default void onTaskUpdated(long id, DownloadTask task) {}
}
