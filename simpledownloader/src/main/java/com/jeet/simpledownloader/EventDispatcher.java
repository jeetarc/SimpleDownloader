package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.jeet.simpledownloader.util.Logs;

/**
 * Dispatches downloader-wide and task-specific callbacks.
 * All callbacks are delivered on the main thread.
 */
final class EventDispatcher {
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private EventDispatcher() {}

    private static List<DownloadTask.Listener> taskSnapshot(DownloadTask task) {
        if (task == null || task.mListeners.isEmpty()) return null;
        return new ArrayList<>(task.mListeners);
    }

    private static List<SimpleDownloader.Listener> downloaderSnapshot(DownloadTask task) {
        return task == null ? null : task.mDownloader.getListenersSnapshot();
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }

    private static void log(Throwable e) {
        Logs.warn("Something unexpected happened while posting callbacks.", e);
    }

    static void onTasksChanged(final TaskManager manager) {
        if (manager == null) return;

        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                final List<DownloadTask> tasks = Collections.unmodifiableList(manager.consumeTasksChangedSnapshot());
                final int size = tasks.size();

                for (TaskListObserver observer : manager.snapshotObservers()) {
                    try {
                        observer.onTasksChanged(size, tasks);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onTaskUpdated(final TaskManager manager, final DownloadTask task) {
        if (manager == null || task == null) return;
        final List<TaskListObserver> observers = manager.snapshotObservers();
        if (observers.isEmpty()) return;

        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                for (TaskListObserver observer : observers) {
                    if (!manager.hasObserver(observer)) continue;

                    try {
                        observer.onTaskUpdated(task.mId, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onStart(final DownloadTask task) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                dispatchStart(dl, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onStart(task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onQueued(final DownloadTask task) {
        final int pos = task.mDownloader.slotManager.getQueuePosition(task);
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                dispatchQueued(dl, pos, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onQueued(pos, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onProgress(final DownloadTask task) {
        final int progress = task.mProgress;
        final long speed = task.mSpeed;
        final long eta = task.mEta;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                dispatchProgress(dl, progress, speed, eta, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onProgress(progress, speed, eta, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                onTaskUpdated(task.mDownloader.taskManager, task);
            }
        });
    }

    static void onPaused(final DownloadTask task) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskPaused(task);
                dispatchPaused(dl, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onPaused(task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onResumed(final DownloadTask task) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskResumed(task);
                dispatchResumed(dl, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onResumed(task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onCancelled(final DownloadTask task) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskCancelled(task);
                dispatchCancelled(dl, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onCancelled(task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                dispatchLifecycleEnded(task, tl, dl);
                task.mListeners.clear();
            }
        });
    }

    static void onComplete(final DownloadTask task) {
        final Uri outputUri = task.mOutputUri;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskComplete(task);
                dispatchComplete(dl, outputUri, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onComplete(outputUri, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                dispatchLifecycleEnded(task, tl, dl);
                task.mListeners.clear();
            }
        });
    }

    static void onError(final DownloadTask task, final Exception error) {
        final Uri outputUri = task.mOutputUri;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskError(task, error);
                dispatchError(dl, outputUri, error, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onError(outputUri, error, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                dispatchLifecycleEnded(task, tl, dl);
            }
        });
    }

    static void onRemoved(final DownloadTask task, final boolean deleted) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskRemoved(task);
                dispatchRemoved(dl, deleted, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onRemoved(deleted, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }

                dispatchLifecycleEnded(task, tl, dl);
                task.mListeners.clear();
            }
        });
    }

    static void onRetry(final DownloadTask task, final int attempt) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskRetry(task, attempt);
                dispatchRetry(dl, attempt, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onRetry(attempt, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onWaitingForNetwork(final DownloadTask task) {
        final int networkType = task.mDownloader.networkManager.getNetworkType();
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskWaitingForNetwork(task);
                dispatchWaiting(dl, networkType, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onWaitingForNetwork(networkType, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onActiveChanged(final DownloadTask task, final boolean active) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                dispatchActive(dl, active, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onActiveChanged(active, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onLifecycleChanged(final DownloadTask task, final int lifecycle) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                if (lifecycle == DownloadTask.LIFECYCLE_STARTED) dispatchLifecycleStarted(task, tl, dl);
                else if (lifecycle == DownloadTask.LIFECYCLE_ENDED) dispatchLifecycleEnded(task, tl, dl);
            }
        });
    }

    static void onStatusFlow(final DownloadTask task, final Status status, final boolean activeChanged, final boolean active) {
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                if (active) {
                    boolean started = dispatchLifecycleStarted(task, tl, dl);
                    if (!started) DownloadService.onTaskBecameActive(task);
                }

                if (activeChanged) {
                    dispatchActive(dl, active, task);

                    for (DownloadTask.Listener l : safe(tl)) {
                        try {
                            l.onActiveChanged(active, task);
                        } catch (Throwable e) {
                            log(e);
                        }
                    }
                }

                dispatchStatus(dl, status, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onStatusChanged(status, task);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    private static boolean dispatchLifecycleStarted(DownloadTask task, List<DownloadTask.Listener> tl, List<SimpleDownloader.Listener> dl) {
        if (task == null || task.mLifecycleStarted || task.mLifecycleEnded) return false;
        task.mLifecycleStarted = true;
        task.mNotificationDismissed = false;
        DownloadService.onTaskLifecycleStarted(task);
        dispatchLifecycle(dl, DownloadTask.LIFECYCLE_STARTED, task);

        for (DownloadTask.Listener l : safe(tl)) {
            try {
                l.onLifecycleChanged(DownloadTask.LIFECYCLE_STARTED, task);
            } catch (Throwable e) {
                log(e);
            }
        }
        return true;
    }

    private static void dispatchLifecycleEnded(DownloadTask task, List<DownloadTask.Listener> tl, List<SimpleDownloader.Listener> dl) {
        if (task == null || task.mLifecycleEnded) return;
        task.mLifecycleEnded = true;
        task.mNotificationDismissed = false;
        DownloadService.onTaskLifecycleEnded(task);
        dispatchLifecycle(dl, DownloadTask.LIFECYCLE_ENDED, task);

        for (DownloadTask.Listener l : safe(tl)) {
            try {
                l.onLifecycleChanged(DownloadTask.LIFECYCLE_ENDED, task);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchStart(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onStart(t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchQueued(List<SimpleDownloader.Listener> l, int pos, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onQueued(pos, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchProgress(List<SimpleDownloader.Listener> l, int progress, long speed, long eta, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onProgress(progress, speed, eta, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchPaused(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onPaused(t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchResumed(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onResumed(t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchCancelled(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onCancelled(t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchComplete(List<SimpleDownloader.Listener> l, Uri uri, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onComplete(uri, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchError(List<SimpleDownloader.Listener> l, Uri uri, Exception err, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onError(uri, err, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchRemoved(List<SimpleDownloader.Listener> l, boolean deleted, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onRemoved(deleted, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchRetry(List<SimpleDownloader.Listener> l, int attempt, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onRetry(attempt, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchWaiting(List<SimpleDownloader.Listener> l, int nt, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onWaitingForNetwork(nt, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchActive(List<SimpleDownloader.Listener> l, boolean active, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onActiveChanged(active, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchStatus(List<SimpleDownloader.Listener> l, Status s, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onStatusChanged(s, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchLifecycle(List<SimpleDownloader.Listener> l, int lc, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onLifecycleChanged(lc, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }
}
