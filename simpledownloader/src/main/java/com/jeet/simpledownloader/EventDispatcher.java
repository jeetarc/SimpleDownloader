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
                        l.onStart();
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
                dispatchQueued(dl, task.mId, pos, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onQueued(pos);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onProgress(final DownloadTask task) {
        final int p = task.mProgress;
        final long s = task.mSpeed;
        final long eta = task.mEta;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                dispatchProgress(dl, task.mId, p, s, eta, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onProgress(p, s, eta);
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
                        l.onPaused();
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
                        l.onResumed();
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
                        l.onCancelled();
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
        final Uri out = task.mOutputUri;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskComplete(task);
                dispatchComplete(dl, task.mId, out, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onComplete(out);
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
        final Uri out = task.mOutputUri;
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskError(task, error);
                dispatchError(dl, task.mId, out, error, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onError(out, error);
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
                dispatchRemoved(dl, task.mId, deleted, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onRemoved(deleted);
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
                dispatchRetry(dl, task.mId, attempt, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onRetry(attempt);
                    } catch (Throwable e) {
                        log(e);
                    }
                }
            }
        });
    }

    static void onWaitingForNetwork(final DownloadTask task) {
        final int n = task.mDownloader.networkManager.getNetworkType();
        final List<DownloadTask.Listener> tl = taskSnapshot(task);
        final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);

        task.postToMain(new Runnable() {
            @Override
            public void run() {
                DownloadService.onTaskWaitingForNetwork(task);
                dispatchWaiting(dl, task.mId, n, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onWaitingForNetwork(n);
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
                dispatchActive(dl, task.mId, active, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onActiveChanged(active);
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
                    dispatchActive(dl, task.mId, active, task);

                    for (DownloadTask.Listener l : safe(tl)) {
                        try {
                            l.onActiveChanged(active);
                        } catch (Throwable e) {
                            log(e);
                        }
                    }
                }

                dispatchStatus(dl, task.mId, status, task);

                for (DownloadTask.Listener l : safe(tl)) {
                    try {
                        l.onStatusChanged(status);
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
        dispatchLifecycle(dl, task.mId, DownloadTask.LIFECYCLE_STARTED, task);

        for (DownloadTask.Listener l : safe(tl)) {
            try {
                l.onLifecycleChanged(DownloadTask.LIFECYCLE_STARTED);
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
        dispatchLifecycle(dl, task.mId, DownloadTask.LIFECYCLE_ENDED, task);

        for (DownloadTask.Listener l : safe(tl)) {
            try {
                l.onLifecycleChanged(DownloadTask.LIFECYCLE_ENDED);
            } catch (Throwable e) {
                log(e);
            }
        }
    }
    
    private static void dispatchStart(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onStart(t.mId, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchQueued(List<SimpleDownloader.Listener> l, long id, int pos, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onQueued(id, pos, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchProgress(List<SimpleDownloader.Listener> l, long id, int p, long s, long eta, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onProgress(id, p, s, eta, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchPaused(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onPaused(t.mId, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchResumed(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onResumed(t.mId, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchCancelled(List<SimpleDownloader.Listener> l, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onCancelled(t.mId, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchComplete(List<SimpleDownloader.Listener> l, long id, Uri u, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onComplete(id, u, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchError(List<SimpleDownloader.Listener> l, long id, Uri u, Exception err, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onError(id, u, err, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchRemoved(List<SimpleDownloader.Listener> l, long id, boolean d, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onRemoved(id, d, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchRetry(List<SimpleDownloader.Listener> l, long id, int a, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onRetry(id, a, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchWaiting(List<SimpleDownloader.Listener> l, long id, int n, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onWaitingForNetwork(id, n, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchActive(List<SimpleDownloader.Listener> l, long id, boolean a, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onActiveChanged(id, a, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchStatus(List<SimpleDownloader.Listener> l, long id, Status s, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onStatusChanged(id, s, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }

    private static void dispatchLifecycle(List<SimpleDownloader.Listener> l, long id, int lc, DownloadTask t) {
        if (l == null) return;
        for (SimpleDownloader.Listener x : l) {
            try {
                x.onLifecycleChanged(id, lc, t);
            } catch (Throwable e) {
                log(e);
            }
        }
    }
}