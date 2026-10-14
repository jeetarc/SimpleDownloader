package com.jeetarc.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import com.jeetarc.simpledownloader.util.Logs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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
		if (task == null || task.mDownloader.mDefaultListeners.isEmpty()) return null;
		return new ArrayList<>(task.mDownloader.mDefaultListeners);
	}
	
	private static <T> List<T> safe(List<T> list) {
		return list == null ? Collections.<T>emptyList() : list;
	}
	
	private static boolean noListeners(List<DownloadTask.Listener> tl, List<SimpleDownloader.Listener> dl) {
		return tl == null && dl == null;
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
	
	static void onTaskUpdated(final DownloadTask task) {
		if (task.mTaskUpdatePending) return;
		task.mTaskUpdatePending = true;
		task.postToMain(task.mTaskUpdateRunnable);
	}
	
	static void onStart(final DownloadTask task) {
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onStart(task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		DownloadService.onTaskQueued(task);
		final int pos = task.mDownloader.slotManager.getQueuePosition(task);
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onQueued(pos, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		if (task.mProgressPending) return;
		task.mProgressPending = true;
		task.mTaskUpdatePending = true;
		task.postToMain(task.mProgressRunnable);
	}
	
	static void onPaused(final DownloadTask task) {
		DownloadService.onTaskPaused(task);
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onPaused(task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		DownloadService.onTaskResumed(task);
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onResumed(task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		DownloadService.onTaskCancelled(task);
		onLifecycleChanged(task, DownloadTask.LIFECYCLE_ENDED);
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onCancelled(task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onCancelled(task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				task.mListeners.clear();
			}
		});
	}
	
	static void onComplete(final DownloadTask task) {
		DownloadService.onTaskComplete(task);
		onLifecycleChanged(task, DownloadTask.LIFECYCLE_ENDED);
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onComplete(task.mOutputUri, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onComplete(task.mOutputUri, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				task.mListeners.clear();
			}
		});
	}
	
	static void onError(final DownloadTask task, final Exception error) {
		DownloadService.onTaskError(task, error);
		onLifecycleChanged(task, DownloadTask.LIFECYCLE_ENDED);
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onError(task.mOutputUri, error, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onError(task.mOutputUri, error, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				// Not clearing listeners here because of retry.
			}
		});
	}
	
	static void onRemoved(final DownloadTask task, final boolean deleted) {
		DownloadService.onTaskRemoved(task);
		onLifecycleChanged(task, DownloadTask.LIFECYCLE_ENDED);
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onRemoved(deleted, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onRemoved(deleted, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				task.mListeners.clear();
			}
		});
	}
	
	static void onRetry(final DownloadTask task, final int attempt) {
		DownloadService.onTaskRetry(task, attempt);
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onRetry(attempt, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		DownloadService.onTaskWaitingForNetwork(task);
		final int networkType = task.mDownloader.networkManager.getNetworkType();
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onWaitingForNetwork(networkType, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onActiveChanged(active, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
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
		if (lifecycle == DownloadTask.LIFECYCLE_STARTED) {
			if (task.mLifecycleStarted || task.mLifecycleEnded) return;
			task.mLifecycleStarted = true;
			task.mNotificationDismissed = false;
			DownloadService.onTaskLifecycleStarted(task);
		} else if (lifecycle == DownloadTask.LIFECYCLE_ENDED) {
			if (task.mLifecycleEnded) return;
			task.mLifecycleEnded = true;
			DownloadService.onTaskLifecycleEnded(task);
		}
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onLifecycleChanged(lifecycle, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onLifecycleChanged(lifecycle, task);
					} catch (Throwable e) {
						log(e);
					}
				}
			}
		});
	}
	
	static void onStatusFlow(final DownloadTask task, final Status status, final boolean activeChanged, final boolean active) {
		if (active) onLifecycleChanged(task, DownloadTask.LIFECYCLE_STARTED);
		
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				if (activeChanged) {
					for (DownloadTask.Listener l : safe(tl)) {
						try {
							l.onActiveChanged(active, task);
						} catch (Throwable e) {
							log(e);
						}
					}
					
					for (SimpleDownloader.Listener l : safe(dl)) {
						try {
							l.onActiveChanged(active, task);
						} catch (Throwable e) {
							log(e);
						}
					}
				}
				
				for (DownloadTask.Listener l : safe(tl)) {
					try {
						l.onStatusChanged(status, task);
					} catch (Throwable e) {
						log(e);
					}
				}
				
				for (SimpleDownloader.Listener l : safe(dl)) {
					try {
						l.onStatusChanged(status, task);
					} catch (Throwable e) {
						log(e);
					}
				}
			}
		});
	}
	
	static void dispatchTaskUpdated(TaskManager manager, DownloadTask task) {
		if (manager == null || task == null) return;
		final List<TaskListObserver> observers = manager.snapshotObservers();
		if (observers.isEmpty()) return;
		
		for (TaskListObserver observer : observers) {
			if (!manager.hasObserver(observer)) continue;
			try {
				observer.onTaskUpdated(task.mId, task);
			} catch (Throwable e) {
				log(e);
			}
		}
	}
	
	static void dispatchProgress(int progress, long speed, long eta, DownloadTask task) {
		if (task == null) return;
		final List<DownloadTask.Listener> tl = taskSnapshot(task);
		final List<SimpleDownloader.Listener> dl = downloaderSnapshot(task);
		if (noListeners(tl, dl)) return;
		
		for (DownloadTask.Listener l : safe(tl)) {
			try {
				l.onProgress(progress, speed, eta, task);
			} catch (Throwable e) {
				log(e);
			}
		}
		
		for (SimpleDownloader.Listener l : safe(dl)) {
			try {
				l.onProgress(progress, speed, eta, task);
			} catch (Throwable e) {
				log(e);
			}
		}
	}
}
