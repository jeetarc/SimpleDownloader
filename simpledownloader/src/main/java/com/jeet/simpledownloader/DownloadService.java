package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.jeet.simpledownloader.util.Formator;
import com.jeet.simpledownloader.thumbnail.ThumbLoader;
import okhttp3.Call;

/**
* DownloadService is used internally for notifications and foreground execution.
*
* <p>Applications should not start or control this service directly.</p>
*/
public final class DownloadService extends Service {
	private static final String TAG = "SimpleDownloader";
	static final String ACTION_ATTACH_LIFECYCLE = "com.jeet.simpledownloader.action.ATTACH_LIFECYCLE";
	static final String ACTION_ATTACH_ACTIVE = "com.jeet.simpledownloader.action.ATTACH_ACTIVE";
	static final String ACTION_PAUSE = "com.jeet.simpledownloader.action.PAUSE";
	static final String ACTION_RESUME = "com.jeet.simpledownloader.action.RESUME";
	static final String ACTION_CANCEL = "com.jeet.simpledownloader.action.CANCEL";
	static final String ACTION_RETRY = "com.jeet.simpledownloader.action.RETRY";
	static final String ACTION_DISMISS = "com.jeet.simpledownloader.action.DISMISS";
	static final String EXTRA_TASK_ID = "SimpleDownloader_task_id";
	private static volatile DownloadService runningService;
	private NotificationManager notificationManager;
	private NotificationBuilder notificationBuilder;
	private boolean foregroundStarted;
	
	private final Set<Long> groupTasks = Collections.synchronizedSet(new HashSet<Long>());
	private final Set<Long> foregroundTasks = Collections.synchronizedSet(new HashSet<Long>());
	private final Map<Long, Long> lastNotifyTime = new LinkedHashMap<Long, Long>();
	private final Map<Long, Bitmap> thumbnails = new LinkedHashMap<Long, Bitmap>();
	private final Map<Long, NotificationBuilder> taskNotificationBuilders = new LinkedHashMap<Long, NotificationBuilder>();
	
	static void onTaskLifecycleStarted(DownloadTask task) {
		if (task == null || task.mContext == null || !task.mDownloader.areNotificationsEnabled()) return;
		task.mNotificationDismissed = false;
		DownloadService service = runningService;
		
		if (service != null) {
			service.handleLifecycleStarted(task);
		} else {
			startServiceForTask(task, ACTION_ATTACH_LIFECYCLE);
		}
	}
	
	static void onTaskBecameActive(DownloadTask task) {
		if (task == null || task.mContext == null || !task.mDownloader.areNotificationsEnabled()) return;
		if (!task.isActive() || task.status == Status.QUEUED) return;
		task.mNotificationDismissed = false;
		DownloadService service = runningService;
		
		if (service != null) {
			service.handleBecameActive(task);
		} else {
			startServiceForTask(task, ACTION_ATTACH_ACTIVE);
		}
	}
	
	static void onTaskProgress(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handleProgress(task);
	}
	
	static void onTaskPaused(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handlePaused(task);
	}
	
	static void onTaskResumed(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handleResumed(task);
	}
	
	static void onTaskWaitingForNetwork(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handleWaitingForNetwork(task);
	}
	
	static void onTaskRetry(DownloadTask task, int attempt) {
		if (task == null) return;
		
		if (task.mContext != null) {
			try {
				Context app = task.mContext.getApplicationContext();
				NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
				
				if (manager != null) {
					DownloadNotification notification = task.mNotification == null ? new DownloadNotification() : task.mNotification;
					NotificationBuilder builder = new NotificationBuilder(app, notification);
					manager.cancel(builder.finishedNotificationId(task.mId));
				}
			} catch (Throwable ignored) {}
		}
		
		DownloadService service = runningService;
		if (service != null) service.handleRetry(task, attempt);
	}
	
	static void onTaskComplete(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handleComplete(task);
	}
	
	static void onTaskError(DownloadTask task, Throwable error) {
		DownloadService service = runningService;
		if (service != null) service.handleError(task, error);
	}
	
	static void onTaskCancelled(DownloadTask task) {
		if (task != null) task.cancelThumbnailRequest();
		DownloadService service = runningService;
		if (service != null) service.handleFinalRemoval(task);
	}
	
	static void onTaskRemoved(DownloadTask task) {
		if (task != null) task.cancelThumbnailRequest();
		DownloadService service = runningService;
		if (service != null) service.handleFinalRemoval(task);
	}
	
	static void onTaskLifecycleEnded(DownloadTask task) {
		DownloadService service = runningService;
		if (service != null) service.handleLifecycleEnded(task);
	}
	
	static void onThumbnailReady(DownloadTask task, Bitmap bitmap) {
		if (task == null || bitmap == null) return;
		DownloadService service = runningService;
		
		if (service != null) {
			service.handleThumbnailReady(task, bitmap);
			return;
		}
		
		if (task.mNotificationDismissed || (task.status != Status.COMPLETED && task.status != Status.FAILED)) {
			if (!bitmap.isRecycled()) bitmap.recycle();
			return;
		}
		
		postFinishedThumbnail(task, bitmap);
	}
	
	static void shutdownServiceIfRunning() {
		DownloadService service = runningService;
		if (service != null) {
			service.tryStopForeground(true);
			service.stopSelf();
		}
	}
	
	private static void startServiceForTask(DownloadTask task, String action) {
		if (task == null || task.mContext == null || !task.mDownloader.areNotificationsEnabled()) return;
		Context app = task.mContext.getApplicationContext();
		Intent intent = new Intent(app, DownloadService.class);
		intent.setAction(action);
		intent.putExtra(EXTRA_TASK_ID, task.mId);
		boolean foreground =
		task.mDownloader.isForegroundEnabled()
		&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
		
		try {
			if (foreground) {
				app.startForegroundService(intent);
			} else {
				app.startService(intent);
			}
		} catch (RuntimeException error) {
			Log.e(TAG, "Unable to start DownloadService: " + error.toString());
		}
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		runningService = this;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) return START_NOT_STICKY;
		String action = intent.getAction();
		long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L);
		
		if (ACTION_PAUSE.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) task.pause();
			return START_NOT_STICKY;
		}
		
		if (ACTION_RESUME.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) {
				task.mNotificationDismissed = false;
				task.resume();
			}
			return START_NOT_STICKY;
		}
		
		if (ACTION_CANCEL.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) task.cancel();
			return START_NOT_STICKY;
		}
		
		if (ACTION_RETRY.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) {
				task.mNotificationDismissed = false;
				task.retry();
			}
			return START_NOT_STICKY;
		}
		
		if (ACTION_DISMISS.equals(action)) {
			handleDismiss(taskId);
			return START_NOT_STICKY;
		}
		
		if (ACTION_ATTACH_LIFECYCLE.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) handleLifecycleStarted(task);
			return START_NOT_STICKY;
		}
		
		if (ACTION_ATTACH_ACTIVE.equals(action)) {
			DownloadTask task = findTask(taskId);
			if (task != null) handleBecameActive(task);
			return START_NOT_STICKY;
		}
		
		return START_NOT_STICKY;
	}
	
	@Override
	public void onTimeout(int startId, int fgsType) {
		handleForegroundTimeout(startId);
	}
	
	@Override
	public void onDestroy() {
		runningService = null;
		foregroundStarted = false;
		notificationBuilder = null;
		groupTasks.clear();
		foregroundTasks.clear();
		
		synchronized (lastNotifyTime) {
			lastNotifyTime.clear();
		}
		
		synchronized (thumbnails) {
			thumbnails.clear();
		}
		
		synchronized (taskNotificationBuilders) {
			taskNotificationBuilders.clear();
		}
		
		super.onDestroy();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private DownloadTask findTask(long taskId) {
		if (taskId < 0) return null;
		SimpleDownloader downloader = SimpleDownloader.defaultDownloaderOrNull();
		if (downloader == null || SimpleDownloader.taskManager == null) return null;
		synchronized (downloader.mLock) {
			return SimpleDownloader.taskManager.getTask(taskId);
		}
	}
	
	private NotificationBuilder createBuilder(DownloadTask task) {
		DownloadNotification notification = new DownloadNotification();
		if (task != null && task.mNotification != null) notification = task.mNotification;
		NotificationBuilder builder = new NotificationBuilder(this, notification);
		builder.createChannel();
		return builder;
	}
	
	private NotificationBuilder taskBuilder(DownloadTask task) {
		if (task == null) return null;
		
		synchronized (taskNotificationBuilders) {
			NotificationBuilder builder = taskNotificationBuilders.get(task.mId);
			
			if (builder == null) {
				builder = createBuilder(task);
				taskNotificationBuilders.put(task.mId, builder);
			}
			
			return builder;
		}
	}
	
	private NotificationBuilder idBuilder() {
		NotificationBuilder builder = notificationBuilder;
		return builder != null ? builder : new NotificationBuilder(this, new DownloadNotification());
	}
	
	private void clearTaskNotificationBuilder(long taskId) {
		synchronized (taskNotificationBuilders) {
			taskNotificationBuilders.remove(taskId);
		}
	}
	
	private void handleLifecycleStarted(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		task.mNotificationDismissed = false;
		addToGroup(task);
		prepareConfiguredThumbnail(task);
		postProgressNotification(task, "Download starting...", null, task.mProgress, true, false, true);
	}
	
	private void prepareConfiguredThumbnail(final DownloadTask task) {
		if (task == null || task.mNotification == null) return;
		final DownloadNotification notification = task.mNotification;
		if (!notification.showThumbnail || notification.thumbnail != null) return;
		if (notification.thumbnailUrl == null || notification.thumbnailUrl.trim().isEmpty()) return;
		
		final ThumbLoader loader = task.mDownloader.thumbLoader;
		if (loader == null || loader.isShutdown()) return;
		final Call call;
		
		synchronized (task) {
			if (task.mThumbnailUrlAttempted || task.mThumbnailCall != null) return;
			task.mThumbnailUrlAttempted = true;
			
			try {
				call = task.mDownloader.httpEngine.newThumbnailCall(task, notification.thumbnailUrl, notification.thumbnailHeaders);
				task.mThumbnailCall = call;
			} catch (Throwable ignored) {
				return;
			}
		}
		
		try {
			loader.loadUrl(task.mId, call, 92, 92, new ThumbLoader.Callback() {
				@Override
				public void onThumbnailReady(long id, Bitmap bitmap) {
					boolean accepted;
					
					synchronized (task) {
						accepted = task.mThumbnailCall == call;
						if (accepted) task.mThumbnailCall = null;
					}
					
					if (!accepted) {
						if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
						return;
					}
					
					DownloadService.onThumbnailReady(task, bitmap);
				}
				
				@Override
				public void onThumbnailUnavailable(long id) {
					synchronized (task) {
						if (task.mThumbnailCall == call) task.mThumbnailCall = null;
					}
				}
			});
			
		} catch (Throwable ignored) {
			synchronized (task) {
				if (task.mThumbnailCall == call) task.mThumbnailCall = null;
			}
			
			loader.cancelUrl(call);
		}
	}
	
	private void handleBecameActive(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		if (task.status == Status.QUEUED || !task.isActive()) return;
		task.mNotificationDismissed = false;
	}
	
	private void handleProgress(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		if (task.mNotificationDismissed) return;
		
		if (!groupTasks.contains(task.mId)) {
			if (!task.isActive()) return;
			addToGroup(task);
		}
		
		if (shouldUpdateNotification(task)) {
			postProgressNotification(task, resolveProgressText(task), speedSubText(task), task.mProgress, false, false, true);
		}
	}
	
	private void handlePaused(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		if (task.mNotificationDismissed) return;
		if (!groupTasks.contains(task.mId)) return;
		String text = "Paused • " + formatBytesRatio(task.mBytesDownloaded, task.mTotalBytes);
		postProgressNotification(task, text, null, task.mProgress, false, true, true);
	}
	
	private void handleResumed(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		task.mNotificationDismissed = false;
		if (!groupTasks.contains(task.mId)) addToGroup(task);
		postProgressNotification(task, resolveProgressText(task), speedSubText(task), task.mProgress, false, false, true);
	}
	
	private void handleWaitingForNetwork(DownloadTask task) {
		if (!isNotificationAllowed(task)) return;
		if (task.mNotificationDismissed) return;
		
		if (!groupTasks.contains(task.mId)) addToGroup(task);
		String text = "Waiting for network • " + formatBytesRatio(task.mBytesDownloaded, task.mTotalBytes);
		postProgressNotification(task, text, null, task.mProgress, false, false, true);
	}
	
	private void handleRetry(DownloadTask task, int attempt) {
		if (!isNotificationAllowed(task)) return;
		cancelFinished(task.mId);
		if (task.mNotificationDismissed) return;
		
		if (!groupTasks.contains(task.mId)) {
			addToGroup(task);
		}
		
		String text = "Retrying..";
		int maxRetry = task.mMaxRetryCount;
		if (attempt > 0 && maxRetry > 0) text = "Retrying.. (" + attempt + " / " + maxRetry + ")";
		postProgressNotification(task, text, null, task.mProgress, false, false, true);
	}
	
	private void handleComplete(DownloadTask task) {
		if (task == null) return;
		NotificationBuilder builder = taskBuilder(task);
		Bitmap thumb = getThumbnail(task);
		cancelProgress(task.mId);
		
		if (task.mDownloader.areNotificationsEnabled() && builder != null && builder.getConfig().showCompleteNotification) {
			Notification notification = builder.buildComplete(task, task.mOutputUri, thumb);
			postNotification(builder.finishedNotificationId(task.mId), notification);
		}
		
		clearThumbnail(task.mId);
		clearTaskNotificationBuilder(task.mId);
		removeFromGroup(task.mId);
	}
	
	private void handleError(DownloadTask task, Throwable error) {
		if (task == null) return;
		NotificationBuilder builder = taskBuilder(task);
		Bitmap thumb = getThumbnail(task);
		cancelProgress(task.mId);
		
		if (task.mDownloader.areNotificationsEnabled() && builder != null && builder.getConfig().showErrorNotification) {
			Notification notification = builder.buildError(task, error, thumb);
			postNotification(builder.finishedNotificationId(task.mId), notification);
		}
		
		clearThumbnail(task.mId);
		clearTaskNotificationBuilder(task.mId);
		removeFromGroup(task.mId);
	}
	
	private void handleFinalRemoval(DownloadTask task) {
		if (task == null) return;
		cancelProgress(task.mId);
		removeFromGroup(task.mId);
		clearThumbnail(task.mId);
		clearTaskNotificationBuilder(task.mId);
	}
	
	private void handleLifecycleEnded(DownloadTask task) {
		if (task == null) return;
		task.mNotificationDismissed = false;
		cancelProgress(task.mId);
		removeFromGroup(task.mId);
		clearThumbnail(task.mId);
		clearTaskNotificationBuilder(task.mId);
	}
	
	private void handleDismiss(long taskId) {
		DownloadTask task = findTask(taskId);
		
		if (task != null) {
			task.cancelThumbnailRequest();
			task.mNotificationDismissed = true;
			task.pause();
		}
		
		cancelProgress(taskId);
		removeFromGroup(taskId);
		clearThumbnail(taskId);
		clearTaskNotificationBuilder(taskId);
	}
	
	private void handleThumbnailReady(DownloadTask task, Bitmap bitmap) {
		if (task == null || bitmap == null) return;
		
		if (task.status == Status.CANCELLED) {
			if (!bitmap.isRecycled()) bitmap.recycle();
			return;
		}
		
		synchronized (thumbnails) {
			thumbnails.put(task.mId, bitmap);
		}
		
		NotificationBuilder builder = taskBuilder(task);
		
		if (task.status == Status.COMPLETED) {
			if (task.mDownloader.areNotificationsEnabled() && builder != null && builder.getConfig().showCompleteNotification) {
				Notification notification = builder.buildComplete(task, task.mOutputUri, bitmap);
				postNotification(builder.finishedNotificationId(task.mId), notification);
			}
			
			clearThumbnail(task.mId);
			clearTaskNotificationBuilder(task.mId);
			
		} else if (task.status == Status.FAILED) {
			if (task.mDownloader.areNotificationsEnabled() && builder != null && builder.getConfig().showErrorNotification) {
				Notification notification = builder.buildError(task, task.mLastError, bitmap);
				postNotification(builder.finishedNotificationId(task.mId), notification);
			}
			
			clearThumbnail(task.mId);
			clearTaskNotificationBuilder(task.mId);
			
		} else if (groupTasks.contains(task.mId)) {
			postProgressNotification(task, resolveProgressText(task), speedSubText(task), task.mProgress, false, task.status == Status.PAUSED, true);
		}
	}
	
	private void handleForegroundTimeout(int startId) {
		ArrayList<Long> ids = new ArrayList<Long>();
		synchronized (groupTasks) {
			ids.addAll(groupTasks);
		}
		
		for (Long taskIdObj : ids) {
			if (taskIdObj == null) continue;
			handleDismiss(taskIdObj.longValue());
		}
		
		stopSelf(startId);
	}
	
	private boolean isNotificationAllowed(DownloadTask task) {
		return task != null && task.mDownloader.areNotificationsEnabled();
	}
	
	private String resolveProgressText(DownloadTask task) {
		if (task == null) return "Downloading..";
		if (task.status == Status.WAITING_FOR_NETWORK) return "Waiting for network • " + formatBytesRatio(task.mBytesDownloaded, task.mTotalBytes);
		if (task.status == Status.RETRYING) return "Retrying..";
		if (task.status == Status.PAUSED) return "Paused • " + formatBytesRatio(task.mBytesDownloaded, task.mTotalBytes);
		return Formator.formatEta(task.mEta) + " left • " + formatBytesRatio(task.mBytesDownloaded, task.mTotalBytes);
	}
	
	private String speedSubText(DownloadTask task) {
		if (task == null || task.status != Status.DOWNLOADING) return null;
		return Formator.formatSpeed(task.mSpeed);
	}
	
	private void postProgressNotification(DownloadTask task, String text, String subText, int progress, boolean indeterminate, boolean paused, boolean allowPost) {
		if (!allowPost || task == null) return;
		if (task.mNotificationDismissed) return;
		if (!groupTasks.contains(task.mId)) return;
		NotificationBuilder builder = taskBuilder(task);
		if (builder == null) return;
		Notification notification = builder.buildTask(task, getThumbnail(task), text, subText, progress, indeterminate, paused);
		postNotification(builder.progressNotificationId(task.mId), notification);
	}
	
	private static void postFinishedThumbnail(DownloadTask task, Bitmap bitmap) {
		try {
			Context app = task.mContext.getApplicationContext();
			NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
			
			if (manager == null) return;
			DownloadNotification config = task.mNotification == null ? new DownloadNotification() : task.mNotification;
			NotificationBuilder builder = new NotificationBuilder(app, config);
			builder.createChannel();
			Notification notification;
			
			if (task.status == Status.COMPLETED) {
				if (!builder.getConfig().showCompleteNotification) return;
				notification = builder.buildComplete(task, task.mOutputUri, bitmap);
				
			} else {
				if (!builder.getConfig().showErrorNotification) return;
				notification = builder.buildError(task, task.mLastError, bitmap);
			}
			
			manager.notify(builder.finishedNotificationId(task.mId), notification);
		} catch (Throwable ignored) {}
	}
	
	private Bitmap getThumbnail(DownloadTask task) {
		if (task == null) return null;
		Bitmap bitmap;
		
		synchronized (thumbnails) {
			bitmap = thumbnails.get(task.mId);
		}
		
		if (bitmap != null) return bitmap;
		DownloadNotification notification = task.mNotification;
		if (notification == null || !notification.showThumbnail) return null;
		return notification.thumbnail;
	}
	
	private void clearThumbnail(long taskId) {
		synchronized (thumbnails) {
			thumbnails.remove(taskId);
		}
	}
	
	private boolean shouldUpdateNotification(DownloadTask task) {
		if (task == null) return false;
		DownloadNotification config = task.mNotification == null ? new DownloadNotification() : task.mNotification;
		long interval = config.notificationUpdateIntervalMs;
		if (interval <= 0) return true;
		long now = System.currentTimeMillis();
		
		synchronized (lastNotifyTime) {
			Long last = lastNotifyTime.get(task.mId);
			if (last != null && now - last < interval) return false;
			lastNotifyTime.put(task.mId, now);
			return true;
		}
	}
	
	private void addToGroup(DownloadTask task) {
		if (task == null || !task.mDownloader.areNotificationsEnabled()) return;
		if (!groupTasks.add(task.mId)) return;
		if (task.mDownloader.isForegroundEnabled()) foregroundTasks.add(task.mId);
		if (notificationBuilder == null) notificationBuilder = createBuilder(task);
		refreshSummary();
	}
	
	private void removeFromGroup(long taskId) {
		boolean removed = groupTasks.remove(taskId);
		foregroundTasks.remove(taskId);
		
		synchronized (lastNotifyTime) {
			lastNotifyTime.remove(taskId);
		}
		
		if (removed) refreshSummary();
	}
	
	private void refreshSummary() {
		int count = groupTasks.size();
		
		if (count <= 0) {
			cancelSummary();
			tryStopForeground(true);
			notificationBuilder = null;
			stopSelf();
			return;
		}
		
		if (notificationBuilder == null) notificationBuilder = createBuilder(null);
		boolean foregroundMode = !foregroundTasks.isEmpty();
		Notification summary = notificationBuilder.buildSummary(count, foregroundMode);
		
		if (foregroundMode) {
			tryStartForeground(summary);
		} else {
			tryStopForeground(true);
			if (notificationBuilder.groupAllowed()) {
				postNotification(notificationBuilder.summaryNotificationId(), summary);
			}
		}
	}
	
	private void tryStartForeground(Notification summary) {
		if (summary == null || notificationBuilder == null) return;
		if (foregroundStarted) {
			postNotification(notificationBuilder.summaryNotificationId(), summary);
			return;
		}
		
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				startForeground(notificationBuilder.summaryNotificationId(), summary, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
			} else {
				startForeground(notificationBuilder.summaryNotificationId(), summary);
			}
			foregroundStarted = true;
		} catch (RuntimeException error) {
			foregroundStarted = false;
			Log.e(TAG, "Unable to start foreground service: " + error.toString());
			stopSelf();
		}
	}
	
	private void tryStopForeground(boolean removeNotification) {
		if (!foregroundStarted) return;
		try {
			stopForeground(removeNotification);
		} catch (Throwable ignored) {}
		foregroundStarted = false;
	}
	
	private void cancelSummary() {
		if (notificationManager == null) return;
		NotificationBuilder builder = idBuilder();
		notificationManager.cancel(builder.summaryNotificationId());
	}
	
	private void cancelProgress(long taskId) {
		if (notificationManager == null) return;
		NotificationBuilder builder = idBuilder();
		notificationManager.cancel(builder.progressNotificationId(taskId));
	}
	
	private void cancelFinished(long taskId) {
		if (notificationManager == null) return;
		NotificationBuilder builder = idBuilder();
		notificationManager.cancel(builder.finishedNotificationId(taskId));
	}
	
	private void postNotification(int id, Notification notification) {
		if (notificationManager == null || notification == null) return;
		
		try {
			notificationManager.notify(id, notification);
		} catch (RuntimeException error) {
			Log.e(TAG, "Unable to post notification: " + error.toString());
		}
	}
	
	static String formatBytesRatio(long downloaded, long total) {
		return Formator.formatRatio(Formator.formatBytes(downloaded), Formator.formatBytes(total), " / ");
	}
}
