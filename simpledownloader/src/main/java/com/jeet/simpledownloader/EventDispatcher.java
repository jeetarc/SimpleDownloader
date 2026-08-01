package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.ArrayList;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import java.util.Collections;

/** Dispatches events, Listeners */
final class EventDispatcher {
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
	private EventDispatcher() {}
	
	private static List<DownloadListener> snapshot(DownloadTask task) {
		if (task == null || task.mListeners == null || task.mListeners.isEmpty()) return null;
		return new ArrayList<DownloadListener>(task.mListeners);
	}
	
	private static void clearAfterFinal(DownloadTask task) {
		if (task != null) task.mListeners.clear();
	}
	
	static void onTasksChanged(final TaskManager manager) {
		if (manager == null) return;
		
		MAIN_HANDLER.post(new Runnable() {
			@Override
			public void run() {
				final List<DownloadTask> tasks = Collections.unmodifiableList(manager.consumeTasksChangedSnapshot());
				final List<TaskListObserver> observers = manager.snapshotObservers();
				
				for (TaskListObserver observer : observers) {
					try {
						observer.onTasksChanged(tasks);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: TaskManager listener failed: " + error.toString());
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
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: TaskManager listener failed for task " + task.mId + ": " + error.toString());
					}
				}
			}
		});
	}
	
	static void onStart(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadListener listener : listeners) {
					
					try {
						listener.onStart(task.mId, task);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: " + error.toString());
					}
				}
			}
		});
	}
	
	static void onQueued(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		if (listeners == null) return;
		SlotManager slotManager = task.mDownloader.slotManager;
		final int position = slotManager != null ? slotManager.getQueuePosition(task) : 0;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadListener listener : listeners) {
					
					try {
						listener.onQueued(task.mId, position, task);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: " + error.toString());
					}
					
				}
			}
		});
	}
	
	static void onProgress(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		final int progress = task.mProgress;
		final long speed = task.mSpeed;
		final long eta = task.mEta;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onProgress(task.mId, progress, speed, eta, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
        
        onTaskUpdated(task.mDownloader.taskManager, task);
	}
	
	static void onPaused(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskPaused(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onPaused(task.mId, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
	}
	
	static void onResumed(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskResumed(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onResumed(task.mId, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
	}
	
	static void onCancelled(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskCancelled(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onCancelled(task.mId, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
				
				dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onComplete(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		final android.net.Uri outputUri = task.mOutputUri;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskComplete(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onComplete(task.mId, outputUri, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
				
				dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onError(final DownloadTask task, final Exception error) {
		final List<DownloadListener> listeners = snapshot(task);
		final android.net.Uri outputUri = task.mOutputUri;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskError(task, error);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onError(task.mId, outputUri, error, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
				
				dispatchLifecycleEnded(task, listeners);
			}
		});
	}
	
	static void onRemoved(final DownloadTask task, final boolean outputDeleted) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskRemoved(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onRemoved(task.mId, outputDeleted, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
				
				dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onRetry(final DownloadTask task, final int attempt) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskRetry(task, attempt);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onRetry(task.mId, attempt, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
	}
	
	static void onWaitingForNetwork(final DownloadTask task) {
		final List<DownloadListener> listeners = snapshot(task);
		final int networkType = task.mDownloader.networkManager.getNetworkType();
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				DownloadService.onTaskWaitingForNetwork(task);
				
				if (listeners != null) {
					for (DownloadListener listener : listeners) {
						
						try {
							listener.onWaitingForNetwork(task.mId, networkType, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
	}
	
	static void onActiveChanged(final DownloadTask task, final boolean isActive) {
		final List<DownloadListener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (DownloadListener listener : listeners) {
					
					try {
						listener.onActiveChanged(task.mId, isActive, task);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: " + error.toString());
					}
				}
			}
		});
	}
	
	static void onLifecycleChanged(final DownloadTask task, final int lifecycle) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				if (lifecycle == DownloadTask.LIFECYCLE_STARTED) {
					dispatchLifecycleStarted(task, listeners);
				} else if (lifecycle == DownloadTask.LIFECYCLE_ENDED) {
					dispatchLifecycleEnded(task, listeners);
				}
			}
		});
	}
	
	static void onStatusFlow(final DownloadTask task, final Status status, final boolean activeChanged, final boolean isActive) {
		final List<DownloadListener> listeners = snapshot(task);
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				if (isActive) {
					boolean lifecycleStartedNow = dispatchLifecycleStarted(task, listeners);
					if (!lifecycleStartedNow) DownloadService.onTaskBecameActive(task);
				}
				
				if (listeners != null) {
					if (activeChanged) {
						for (DownloadListener listener : listeners) {
							
							try {
								listener.onActiveChanged(task.mId, isActive, task);
							} catch (Throwable error) {
								System.err.println("SimpleDownloader: " + error.toString());
							}
						}
					}
					
					for (DownloadListener listener : listeners) {
						try {
							listener.onStatusChanged(task.mId, status, task);
						} catch (Throwable error) {
							System.err.println("SimpleDownloader: " + error.toString());
						}
					}
				}
			}
		});
	}
	
	private static boolean dispatchLifecycleStarted(DownloadTask task, List<DownloadListener> listeners) {
		if (task == null) return false;
		
		if (!task.mLifecycleStarted && !task.mLifecycleEnded) {
			task.mLifecycleStarted = true;
			task.mNotificationDismissed = false;
			DownloadService.onTaskLifecycleStarted(task);
			
			if (listeners != null) {
				for (DownloadListener listener : listeners) {
					
					try {
						listener.onLifecycleChanged(task.mId, DownloadTask.LIFECYCLE_STARTED, task);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: " + error.toString());
					}
				}
			}
			return true;
		}
		return false;
	}
	
	private static void dispatchLifecycleEnded(DownloadTask task, List<DownloadListener> listeners) {
		if (task == null) return;
		
		if (!task.mLifecycleEnded) {
			task.mLifecycleEnded = true;
			task.mNotificationDismissed = false;
			DownloadService.onTaskLifecycleEnded(task);
			
			if (listeners != null) {
				for (DownloadListener listener : listeners) {
					
					try {
						listener.onLifecycleChanged(task.mId, DownloadTask.LIFECYCLE_ENDED, task);
					} catch (Throwable error) {
						System.err.println("SimpleDownloader: " + error.toString());
					}
				}
			}
		}
	}
}
