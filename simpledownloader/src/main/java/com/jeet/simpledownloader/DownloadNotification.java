package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader and licenced under SimpleDownloader Licence - v1.0.
*/

import android.app.NotificationManager;
import android.app.Notification;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.graphics.Bitmap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures the notification channel, appearance, actions, and update
 * behavior used by download notifications.
 */
public final class DownloadNotification {
	String channelId = "SimpleDownloader_downloads";
	String channelName = "Downloads";
	String channelDescription = null;
	int channelImportance = NotificationManager.IMPORTANCE_DEFAULT;
	int foregroundNotificationId = 7001;
	int smallIcon = android.R.drawable.stat_sys_download;
	int completeIcon = android.R.drawable.stat_sys_download_done;
	int errorIcon = android.R.drawable.stat_notify_error;
	int lockscreenVisibility = Notification.VISIBILITY_PUBLIC;
	boolean colorAccentEnabled = false;
	int colorAccent = 0;
	int colorAccentRes = 0;
	boolean colorized = false;
	boolean soundEnabled = false;
	Uri soundUri = null;
	Bitmap thumbnail;
	String thumbnailUrl;
	Map<String, String> thumbnailHeaders = Collections.emptyMap();
	boolean vibrationEnabled = false;
	long[] vibrationPattern = null;
	boolean showThumbnail = true;
	boolean showPauseAction = true;
	boolean showCancelAction = true;
	boolean showRetryAction = true;
	boolean showCompleteNotification = true;
	boolean showErrorNotification = true;
	long notificationUpdateIntervalMs = 1000L;
	
	public DownloadNotification() {}
	
	DownloadNotification(DownloadNotification source) {
		if (source == null) return;
		
		channelId = source.channelId;
		channelName = source.channelName;
		channelDescription = source.channelDescription;
		channelImportance = source.channelImportance;
		foregroundNotificationId = source.foregroundNotificationId;
		smallIcon = source.smallIcon;
		completeIcon = source.completeIcon;
		errorIcon = source.errorIcon;
		lockscreenVisibility = source.lockscreenVisibility;
		colorAccentEnabled = source.colorAccentEnabled;
		colorAccent = source.colorAccent;
		colorAccentRes = source.colorAccentRes;
		colorized = source.colorized;
		soundEnabled = source.soundEnabled;
		soundUri = source.soundUri;
		vibrationEnabled = source.vibrationEnabled;
		vibrationPattern = source.vibrationPattern == null ? null : source.vibrationPattern.clone();
		showThumbnail = source.showThumbnail;
		thumbnail = source.thumbnail;
		thumbnailUrl = source.thumbnailUrl;
		thumbnailHeaders = source.thumbnailHeaders == null || source.thumbnailHeaders.isEmpty() ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<String, String>(source.thumbnailHeaders));
		showPauseAction = source.showPauseAction;
		showCancelAction = source.showCancelAction;
		showRetryAction = source.showRetryAction;
		showCompleteNotification = source.showCompleteNotification;
		showErrorNotification = source.showErrorNotification;
		notificationUpdateIntervalMs = source.notificationUpdateIntervalMs;
	}
	
	public DownloadNotification setChannelId(String channelId) {
		if (channelId != null && channelId.trim().length() > 0) this.channelId = channelId.trim();
		return this;
	}
	
	public DownloadNotification setChannelName(String channelName) {
		if (channelName != null && channelName.trim().length() > 0) this.channelName = channelName.trim();
		return this;
	}
	
	public DownloadNotification setChannelDescription(String channelDescription) {
		this.channelDescription = channelDescription;
		return this;
	}
	
	public DownloadNotification setChannelImportance(int channelImportance) {
		this.channelImportance = channelImportance;
		return this;
	}
	
	public DownloadNotification setForegroundNotificationId(int foregroundNotificationId) {
		if (foregroundNotificationId > 0) this.foregroundNotificationId = foregroundNotificationId;
		return this;
	}
	
	public DownloadNotification setSmallIcon(int smallIcon) {
		if (smallIcon != 0) this.smallIcon = smallIcon;
		return this;
	}
	
	public DownloadNotification setCompleteIcon(int completeIcon) {
		if (completeIcon != 0) this.completeIcon = completeIcon;
		return this;
	}
	
	public DownloadNotification setErrorIcon(int errorIcon) {
		if (errorIcon != 0) this.errorIcon = errorIcon;
		return this;
	}
	
	// Pass a real color int, (Example: 0xFF0087E5).
	public DownloadNotification setColorAccent(int colorAccent) {
		this.colorAccent = colorAccent;
		this.colorAccentRes = 0;
		this.colorAccentEnabled = true;
		return this;
	}
	
	// Pass an Android color resource, (Example: R.color.blue).
	public DownloadNotification setColorAccentResource(int colorAccentRes) {
		if (colorAccentRes != 0) {
			this.colorAccentRes = colorAccentRes;
			this.colorAccent = 0;
			this.colorAccentEnabled = true;
		}
		return this;
	}
	
	public DownloadNotification clearColorAccent() {
		this.colorAccent = 0;
		this.colorAccentRes = 0;
		this.colorAccentEnabled = false;
		this.colorized = false;
		return this;
	}
	
	public DownloadNotification setColorized(boolean colorized) {
		this.colorized = colorized;
		return this;
	}
	
	public DownloadNotification setSoundEnabled(boolean soundEnabled) {
		this.soundEnabled = soundEnabled;
		if (!soundEnabled) this.soundUri = null;
		return this;
	}
	
	public DownloadNotification setSound(Uri soundUri) {
		this.soundUri = soundUri;
		this.soundEnabled = soundUri != null;
		return this;
	}
	
	public DownloadNotification setVibrationEnabled(boolean vibrationEnabled) {
		this.vibrationEnabled = vibrationEnabled;
		if (!vibrationEnabled) this.vibrationPattern = null;
		return this;
	}
	
	public DownloadNotification setVibrationPattern(long[] vibrationPattern) {
		this.vibrationPattern = vibrationPattern;
		this.vibrationEnabled = vibrationPattern != null && vibrationPattern.length > 0;
		return this;
	}
	
	public DownloadNotification setLockscreenVisibility(int visibility) {
		this.lockscreenVisibility = visibility;
		return this;
	}
	
	public DownloadNotification setThumbnail(Bitmap bitmap) {
		this.thumbnail = bitmap;
		this.thumbnailUrl = null;
		this.thumbnailHeaders = Collections.emptyMap();
		return this;
	}
	
	public DownloadNotification setThumbnailUrl(String url) {
		return setThumbnailUrl(url, null);
	}
	
	public DownloadNotification setThumbnailUrl(String url, Map<String, String> headers) {
		this.thumbnail = null;
		if (url == null || url.trim().isEmpty()) {
			this.thumbnailUrl = null;
			this.thumbnailHeaders = Collections.emptyMap();
			return this;
		}
		
		this.thumbnailUrl = url.trim();
		
		if (headers == null || headers.isEmpty()) {
			this.thumbnailHeaders = Collections.emptyMap();
			return this;
		}
		
		Map<String, String> copiedHeaders = new LinkedHashMap<String, String>();
		for (Map.Entry<String, String> header : headers.entrySet()) {
			if (header.getKey() == null || header.getValue() == null) continue;
			copiedHeaders.put(header.getKey(), header.getValue());
		}
		
		this.thumbnailHeaders = Collections.unmodifiableMap(copiedHeaders);
		return this;
	}
	
	public DownloadNotification clearThumbnail() {
		this.thumbnail = null;
		this.thumbnailUrl = null;
		this.thumbnailHeaders = Collections.emptyMap();
		return this;
	}
	
	public DownloadNotification setShowThumbnail(boolean showThumbnail) {
		this.showThumbnail = showThumbnail;
		return this;
	}
	
	public DownloadNotification setShowPauseAction(boolean showPauseAction) {
		this.showPauseAction = showPauseAction;
		return this;
	}
	
	public DownloadNotification setShowCancelAction(boolean showCancelAction) {
		this.showCancelAction = showCancelAction;
		return this;
	}
	
	public DownloadNotification setShowRetryAction(boolean showRetryAction) {
		this.showRetryAction = showRetryAction;
		return this;
	}
	
	public DownloadNotification setShowCompleteNotification(boolean showCompleteNotification) {
		this.showCompleteNotification = showCompleteNotification;
		return this;
	}
	
	public DownloadNotification setShowErrorNotification(boolean showErrorNotification) {
		this.showErrorNotification = showErrorNotification;
		return this;
	}
	
	public DownloadNotification setNotificationUpdateInterval(long millis) {
		if (millis > 0) this.notificationUpdateIntervalMs = millis;
		return this;
	}
	
	int resolveColorAccent(Context context) {
		if (!colorAccentEnabled) return 0;
		if (colorAccentRes != 0 && context != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return context.getColor(colorAccentRes);
			return context.getResources().getColor(colorAccentRes);
		}
		return colorAccent;
	}
}
