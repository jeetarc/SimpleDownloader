package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.util.ArrayList;
import java.util.List;

final class ListenerDispatcher {
	private ListenerDispatcher() {}
	
	private static List<SimpleDownloader.Listener> snapshot(DownloadTask task) {
		if (task == null || task.mListeners == null || task.mListeners.isEmpty()) return null;
		return new ArrayList<>(task.mListeners);
	}
	
	private static void clearAfterFinal(DownloadTask task) {
		if (task != null) {
			task.mListeners.clear();
		}
	}
	
	static void onStart(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onStart(task.mId, task);
				}
			}
		});
	}
	
	static void onQueued(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				int queuePosition = SlotHandler.getQueuePosition(task);
				
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onQueued(task.mId, queuePosition, task.mLockedInQueue, task);
				}
			}
		});
	}
	
	static void onProgress(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final int progress = task.mProgress;
		final long speed = task.mSpeed;
		final long eta = task.mEta;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onProgress(task.mId, progress, speed, eta, task);
				}
			}
		});
	}
	
	static void onPaused(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onPaused(task.mId, task);
				}
			}
		});
	}
	
	static void onResumed(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onResumed(task.mId, task);
				}
			}
		});
	}
	
	static void onCancelled(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onCancelled(task.mId, task);
				}
				
				dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onComplete(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final android.net.Uri outputUri = task.mOutputFileUri;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onComplete(task.mId, outputUri, task);
				}
				
				dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onError(final DownloadTask task, final Exception error) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final android.net.Uri outputUri = task.mOutputFileUri;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onError(task.mId, outputUri, error, task);
				}
				
				dispatchLifecycleEnded(task, listeners);
			}
		});
	}
	
	static void onRemoved(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final boolean deleteOnRemoval = task.mDeleteOnRemoval;
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onRemoved(task.mId, deleteOnRemoval, task);
				}
				
                dispatchLifecycleEnded(task, listeners);
				clearAfterFinal(task);
			}
		});
	}
	
	static void onRetrying(final DownloadTask task, final int attempt) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final int maxRetries = task.mMaxRetries;
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onRetrying(task.mId, attempt, maxRetries, task);
				}
			}
		});
	}
	
	static void onWaitingForNetwork(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final int networkType = SimpleDownloader.networkManager.getNetworkType();
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onWaitingForNetwork(task.mId, networkType, task);
				}
			}
		});
	}
	
	static void onActiveChanged(final DownloadTask task, final boolean isActive) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onActiveChanged(task.mId, isActive, task);
				}
			}
		});
	}
	
	static void onLifecycleChanged(final DownloadTask task, final int lifecycle) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
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
	
	static void onLoadDatabase(final DownloadTask task) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		final int progress = task.mProgress;
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onLoadDatabase(task.mId, progress, task);
				}
			}
		});
	}
	
	static void onStatusFlow(final DownloadTask task, final Status status, final boolean activeChanged, final boolean isActive) {
		final List<SimpleDownloader.Listener> listeners = snapshot(task);
		if (listeners == null) return;
		
		task.postToMain(new Runnable() {
			@Override
			public void run() {
				if (isActive) dispatchLifecycleStarted(task, listeners);
				
				if (activeChanged) {
					for (SimpleDownloader.Listener listener : listeners) {
						listener.onActiveChanged(task.mId, isActive, task);
					}
				}
				
				for (SimpleDownloader.Listener listener : listeners) {
					listener.onStatusChanged(task.mId, status, task);
				}
			}
		});
	}
	
	private static void dispatchLifecycleStarted(DownloadTask task, List<SimpleDownloader.Listener> listeners) {
		if (task == null || listeners == null) return;
		
		if (!task.mLifecycleStarted && !task.mLifecycleEnded) {
			task.mLifecycleStarted = true;
			for (SimpleDownloader.Listener listener : listeners) {
				listener.onLifecycleChanged(task.mId, DownloadTask.LIFECYCLE_STARTED, task);
			}
		}
	}
	
	private static void dispatchLifecycleEnded(DownloadTask task, List<SimpleDownloader.Listener> listeners) {
		if (task == null || listeners == null) return;
		
		if (!task.mLifecycleEnded) {
			task.mLifecycleEnded = true;
			for (SimpleDownloader.Listener listener : listeners) {
				listener.onLifecycleChanged(task.mId, DownloadTask.LIFECYCLE_ENDED, task);
			}
		}
	}
}