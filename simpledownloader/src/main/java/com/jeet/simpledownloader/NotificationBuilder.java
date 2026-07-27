package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet Jati / Jeetarc.
*
* This source code is part of SimpleDownloader and licenced under SimpleDownloader Licence - v1.0.
*/


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import java.util.List;
import com.jeet.simpledownloader.util.Formator;

final class NotificationBuilder {
	static final String GROUP_KEY = "SimpleDownloader_download_group";
	private static final int PROGRESS_ID_BASE = 100000;
	private static final int FINISHED_ID_BASE = 300000;
	private final Context context;
	private final NotificationManager notificationManager;
	private final DownloadNotification config;
	private Notification.InboxStyle groupStyle = new Notification.InboxStyle();
	
	NotificationBuilder(Context context, DownloadNotification config) {
		this.context = context.getApplicationContext();
		this.config = config == null ? new DownloadNotification() : config;
		this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);  
	}  
	
	DownloadNotification getConfig() {
		return config;  
	}  
	
	void createChannel() {
		if (notificationManager == null) return;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
		NotificationChannel channel = new NotificationChannel(config.channelId, config.channelName, config.channelImportance);
		if (config.channelDescription != null) channel.setDescription(config.channelDescription);
		
		if (!config.soundEnabled) {
			channel.setSound(null, null);
		} else if (config.soundUri != null) {
			channel.setSound(config.soundUri, null);
		}  
		
		channel.enableVibration(config.vibrationEnabled);
		if (config.vibrationEnabled && config.vibrationPattern != null) {
			channel.setVibrationPattern(config.vibrationPattern);
		}  
		
		notificationManager.createNotificationChannel(channel);
	}  
	
	Notification buildSummary(int count, boolean foregroundMode) {  
		String title = count <= 1 ? "1 Download" : count + " Downloads";
		
		Notification.Builder b = newBuilder();
		applyCommon(b);
		b.setSmallIcon(config.smallIcon)
		.setOngoing(foregroundMode)
		.setOnlyAlertOnce(true)
		.setShowWhen(false);
        
		if (groupAllowed()) {  
			groupStyle.setSummaryText(title);
			b.setStyle(groupStyle)
			.setContentTitle(title)
			.setGroup(GROUP_KEY)
			.setGroupSummary(true);
		} else {  
			b.setContentTitle(title + " Running");
		}  
		if (foregroundMode) b.setCategory(Notification.CATEGORY_SERVICE);
		return b.build();
	}  
	
	Notification buildTask(DownloadTask task, Bitmap thumb, String text, String subText, int progress, boolean indeterminate, boolean paused) {
		Notification.Builder b = newBuilder();
		applyCommon(b);
		
		b.setContentTitle(task.getFileName())
		.setContentText(text)
		.setStyle(new Notification.BigTextStyle().bigText(text)) 
		.setCategory(Notification.CATEGORY_PROGRESS)
        .setSmallIcon(config.smallIcon)
		.setOnlyAlertOnce(true) 
		.setOngoing(true)
		.setShowWhen(true)
		.setWhen(task.mCreatedAt)
		.setProgress(100, Math.max(0, Math.min(100, progress)), indeterminate);
		
		if (groupAllowed()) b.setGroup(GROUP_KEY);
		if (subText != null) b.setSubText(subText);
		if (thumb != null) b.setLargeIcon(thumb);
		
		if (config.showPauseAction) {  
			if (paused) {  
				b.addAction(android.R.drawable.ic_media_play, "Resume", serviceAction(DownloadService.ACTION_RESUME, task.mId));
			} else {  
				b.addAction(android.R.drawable.ic_media_pause, "Pause", serviceAction(DownloadService.ACTION_PAUSE, task.mId));
			}  
		}  
		
		if (config.showCancelAction) b.addAction(android.R.drawable.ic_delete, "Cancel", serviceAction(DownloadService.ACTION_CANCEL, task.mId));
		
		// Dismiss pauses the task, stops foreground and clear the notification.  
		b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", serviceAction(DownloadService.ACTION_DISMISS, task.mId));
		return b.build();
	}  
	
	Notification buildComplete(DownloadTask task, Uri fileUri, Bitmap thumb) {
		Notification.Builder b = newBuilder();
		applyCommon(b);
		String mime = task.mMimeType;
		PendingIntent openPi = null;
		PendingIntent sharePi = null;
		
		if (fileUri != null) {  
			Intent openIntent = new Intent(Intent.ACTION_VIEW);
			openIntent.setDataAndType(fileUri, mime);
			openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);  
			openPi = PendingIntent.getActivity(context, progressNotificationId(task.mId), openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);  
			
			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			shareIntent.setType(mime);
			shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
			shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);  
			sharePi = PendingIntent.getActivity(context, finishedNotificationId(task.mId), Intent.createChooser(shareIntent, "Share file"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);  
		}  
		
		long size = task.mTotalBytes > 0 ? task.mTotalBytes : task.mBytesDownloaded;  
		String text = "Download complete (" + Formator.formatBytes(size) + ")";  
		
		b.setContentTitle(task.getFileName())  
		.setContentText(text)
		.setStyle(new Notification.BigTextStyle().bigText(text))
        .setCategory(Notification.CATEGORY_PROGRESS)
		.setSmallIcon(config.completeIcon)
		.setAutoCancel(true)
		.setOngoing(false)
		.setShowWhen(true);
		
		if (openPi != null) {  
			b.setContentIntent(openPi);  
			b.addAction(android.R.drawable.ic_menu_view, "Open", openPi);  
		}  
		
		if (sharePi != null) b.addAction(android.R.drawable.ic_menu_share, "Share", sharePi);  
		if (thumb != null) b.setLargeIcon(thumb);  
		return b.build();  
	}  
	
	Notification buildError(DownloadTask task, Throwable error, Bitmap thumb) {  
		Notification.Builder b = newBuilder();  
		applyCommon(b);  
		String message = error != null && error.getMessage() != null ? error.getMessage() : "Unknown error";  
		String text = "Download failed: " + message;  
		
		b.setContentTitle(task.getFileName())  
		.setContentText(text)  
		.setStyle(new Notification.BigTextStyle().bigText(text))  
		.setCategory(Notification.CATEGORY_ERROR)
        .setSmallIcon(config.errorIcon)  
		.setAutoCancel(true)  
		.setOngoing(false)
		.setShowWhen(true);  
		
		if (config.showRetryAction) b.addAction(android.R.drawable.ic_popup_sync, "Retry", serviceAction(DownloadService.ACTION_RETRY, task.mId));  
		if (thumb != null) b.setLargeIcon(thumb);  
		return b.build();  
	}  
	
	int summaryNotificationId() {  
		return config.foregroundNotificationId;  
	}  
	
	int progressNotificationId(long taskId) {  
		return safeNotificationId(PROGRESS_ID_BASE, taskId);  
	}  
	
	int finishedNotificationId(long taskId) {  
		return safeNotificationId(FINISHED_ID_BASE, taskId);  
	}  
	
	private int safeNotificationId(int base, long taskId) {  
		long value = base + Math.abs(taskId % 100000L);  
		if (value > Integer.MAX_VALUE) return base;  
		return (int) value;  
	}  
	
	private PendingIntent serviceAction(String action, long taskId) {  
		Intent intent = new Intent(context, DownloadService.class);  
		intent.setAction(action);  
		intent.putExtra(DownloadService.EXTRA_TASK_ID, taskId);  
		return PendingIntent.getService(context, (int) (taskId + action.hashCode()), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);  
	}  
	
	private Notification.Builder newBuilder() {  
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new Notification.Builder(context, config.channelId);  
		return new Notification.Builder(context);  
	}  
	
	private void applyCommon(Notification.Builder builder) {
		builder.setOnlyAlertOnce(true);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) builder.setVisibility(config.lockscreenVisibility);
		if (!config.soundEnabled) builder.setSound(null);
		if (!config.vibrationEnabled) builder.setVibrate(null);
		
		if (config.colorAccentEnabled) {
			int color = config.resolveColorAccent(context);
			builder.setColor(color);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setColorized(config.colorized);
		}
	}
	
	boolean groupAllowed() {  
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;  
	}
	
	static String shortName(String name, int trimFrom) {  
		if (name == null) return "";  
		if (name.length() > 30) return name.substring(0, Math.min(trimFrom, name.length())) + "...";  
		return name;  
	}
}
