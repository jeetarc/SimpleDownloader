package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;

/**
 * Receives lifecycle, status, progress, complete, failure updates for every {@code DownloadTask}.
 *
 * <p>All callbacks are run on the main thread.</p>
 */
public interface DownloadListener {
	
    /**
    * Called whenever the task starts download.
    *
    * <p>This may be called multiple times, like after resuming or retrying. It's not the task's first start.
    * Use {@link #onLifecycleChanged(long, int, DownloadTask)} to get updates of the
    * actual task lifecycle.</p>
    *
    * @param id the task ID
    * @param task the task instance
    */	
	default void onStart(long id, DownloadTask task) {}
	
	/**
    * Called when the task enters the queue or its queued state changes.
    *
    * <p>This may be called again when the task becomes locked or unlocked
    * in the queue via {@code setLockedInQueue()}</p>
    *
    * @param id the task ID
    * @param position the task's current position in the queue
    * @param lockedInQueue is the task locked in the queue or not?
    * @param task the task instance
    */	
	default void onQueued(long id, int position, boolean lockedInQueue, DownloadTask task) {}
	
	/**
    * Called when the download progress updates.
    *
    * @param id the task ID
    * @param progress the completed percentage, from {@code 0} to {@code 100}
    * @param speed the current download speed in bytes per second
    * @param etaMs the estimated remaining time in milliseconds, or {@code -1} when unknown
    * @param task the live task instance; use {@code getDownloadedBytes()} and {@code getTotalBytes()} for byte counts.
    */	
	default void onProgress(long id, int progress, long speed, long etaMs, DownloadTask task) {}
	
	/**
    * Called when the task is paused.
    *
    * @param id the task ID
    * @param task the task instance
    */	
	default void onPaused(long id, DownloadTask task) {}
	
	/**
    * Called when the task is resumed.
    *
    * @param id the task ID
    * @param task the task instance
    */	
	default void onResumed(long id, DownloadTask task) {}
	
	/**
    * Called when the task is cancelled.
    *
    * @param id the task ID
    * @param task the task instance
    */	
	default void onCancelled(long id, DownloadTask task) {}
	
	/**
    * Called when the download completes successfully.
    *
    * @param id the task ID
    * @param outputUri the resolved output URI, or {@code null} when unavailable
    * @param task the task instance
    */	
	default void onComplete(long id, Uri outputUri, DownloadTask task) {}
	
	/**
    * Called when the download fails.
    *
    * @param id the task ID
    * @param outputUri the resolved output URI, or {@code null} when unavailable
    * @param error the failure, provided as a {@link DownloadException} instance
    * @param task the task instance
    */	
	default void onError(long id, Uri outputUri, Exception error, DownloadTask task) {}
	
	/**
    * Called when the task is removed.
    *
    * @param id the task ID
    * @param outputDeleted {@code true} if the output was successfully deleted; {@code false} if deletion was not requested or failed
    * @param task the task instance
    */	
	default void onRemoved(long id, boolean outputDeleted, DownloadTask task) {}
	
	/**
    * Called when a manual or automatic retry is requested.
    *
    * @param id the task ID
    * @param attempt the retry attempt count
    * @param task the task instance; use {@code getMaxRetries()} to get the maximum retry count
    */	
	default void onRetry(long id, int attempt, DownloadTask task) {}
	
	/**
    * Called when the task starts waiting for a network connection it needs.
    *
    * @param id the task ID
    * @param networkType the currently available network type, not the network type required by the task
    * @param task the task instance
    */	
	default void onWaitingForNetwork(long id, int networkType, DownloadTask task) {}
	
	/**
    * Called whenever the task status changes.
    *
    * @param id the task ID
    * @param status the task's new status
    * @param task the task instance
    */	
	default void onStatusChanged(long id, Status status, DownloadTask task) {}
	
	/**
    * Called when the task starts or stops actively downloading.
    *
    * <p>Active means it's CONNECTING, DOWNLOADING, or RETRYING.
    * This can fire multiple times during pause, resume, or network changes.</p>
    *
    * @param id the task ID
    * @param isActive true when active, false when not
    * @param task the task instance
    */
	default void onActiveChanged(long id, boolean isActive, DownloadTask task) {}
	
	/**
    * Called when the full task lifecycle starts or ends.
    *
    * <p>Fires {@link DownloadTask#LIFECYCLE_STARTED} once when
    * it begins and {@link DownloadTask#LIFECYCLE_ENDED} once when it ends.</p>
    *
    * @param id the task ID
    * @param lifecycle {@link DownloadTask#LIFECYCLE_STARTED} or {@link DownloadTask#LIFECYCLE_ENDED}
    * @param task the task instance
    */	
	default void onLifecycleChanged(long id, int lifecycle, DownloadTask task) {}
}