package com.jeetarc.simpledownloader;

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

/**
 * Configures the notification channel, appearance, actions, and update
 * behavior used by download notifications.
 */
public final class DownloadNotification {
    final String channelId;
    final String channelName;
    final String channelDescription;
    final int channelImportance;
    final int foregroundNotificationId;
    final boolean soundEnabled;
    final Uri soundUri;
    final boolean vibrationEnabled;
    final long[] vibrationPattern;

    int smallIcon = android.R.drawable.stat_sys_download;
    int completeIcon = android.R.drawable.stat_sys_download_done;
    int errorIcon = android.R.drawable.stat_notify_error;
    int lockscreenVisibility = Notification.VISIBILITY_PUBLIC;
    boolean colorAccentEnabled = false;
    int colorAccent = 0;
    boolean colorized = false;
    Bitmap thumbnail;
    boolean showThumbnail = true;
    boolean showPauseAction = true;
    boolean showCancelAction = true;
    boolean showRetryAction = true;
    boolean showCompleteNotification = true;
    boolean showErrorNotification = true;
    long notificationUpdateIntervalMs = 1000L;

    private static final DownloadNotification DEFAULT_INSTANCE = new DownloadNotification.Builder().build();

    private DownloadNotification(Builder builder) {
        this.channelId = builder.channelId;
        this.channelName = builder.channelName;
        this.channelDescription = builder.channelDescription;
        this.channelImportance = builder.channelImportance;
        this.foregroundNotificationId = builder.foregroundNotificationId;
        this.soundEnabled = builder.soundEnabled;
        this.soundUri = builder.soundUri;
        this.vibrationEnabled = builder.vibrationEnabled;
        this.vibrationPattern = builder.vibrationPattern == null ? null : builder.vibrationPattern.clone();
    }

    DownloadNotification(DownloadNotification source) {
        if (source == null) throw new IllegalArgumentException("Notification source cannot be null.");

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
        colorized = source.colorized;
        soundEnabled = source.soundEnabled;
        soundUri = source.soundUri;
        vibrationEnabled = source.vibrationEnabled;
        vibrationPattern = source.vibrationPattern == null ? null : source.vibrationPattern.clone();
        showThumbnail = source.showThumbnail;
        thumbnail = source.thumbnail;
        showPauseAction = source.showPauseAction;
        showCancelAction = source.showCancelAction;
        showRetryAction = source.showRetryAction;
        showCompleteNotification = source.showCompleteNotification;
        showErrorNotification = source.showErrorNotification;
        notificationUpdateIntervalMs = source.notificationUpdateIntervalMs;
    }

    public static final class Builder {
        private String channelId = "SimpleDownloader_downloads";
        private String channelName = "Downloads";
        private String channelDescription = null;
        private int channelImportance = NotificationManager.IMPORTANCE_DEFAULT;
        private int foregroundNotificationId = 7001;
        private boolean soundEnabled = false;
        private Uri soundUri = null;
        private boolean vibrationEnabled = false;
        private long[] vibrationPattern = null;

        public Builder() {
        }

        public Builder setChannelId(String channelId) {
            if (channelId != null && channelId.trim().length() > 0) this.channelId = channelId.trim();
            return this;
        }

        public Builder setChannelName(String channelName) {
            if (channelName != null && channelName.trim().length() > 0) this.channelName = channelName.trim();
            return this;
        }

        public Builder setChannelDescription(String channelDescription) {
            this.channelDescription = channelDescription;
            return this;
        }

        public Builder setChannelImportance(int channelImportance) {
            this.channelImportance = channelImportance;
            return this;
        }

        public Builder setForegroundNotificationId(int foregroundNotificationId) {
            if (foregroundNotificationId > 0) this.foregroundNotificationId = foregroundNotificationId;
            return this;
        }

        public Builder setSoundEnabled(boolean soundEnabled) {
            this.soundEnabled = soundEnabled;
            if (!soundEnabled) this.soundUri = null;
            return this;
        }

        public Builder setSound(Uri soundUri) {
            this.soundUri = soundUri;
            this.soundEnabled = soundUri != null;
            return this;
        }

        public Builder setVibrationEnabled(boolean vibrationEnabled) {
            this.vibrationEnabled = vibrationEnabled;
            if (!vibrationEnabled) this.vibrationPattern = null;
            return this;
        }

        public Builder setVibrationPattern(long[] vibrationPattern) {
            this.vibrationPattern = vibrationPattern;
            this.vibrationEnabled = vibrationPattern != null && vibrationPattern.length > 0;
            return this;
        }

        public DownloadNotification build() {
            return new DownloadNotification(this);
        }
    }

    public static DownloadNotification getInstance() {
        return new DownloadNotification(DEFAULT_INSTANCE);
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

    /** Pass a 32-bit ARGB color value (int), (Example: 0xFF2196F3 or Color.BLUE). */
    public DownloadNotification setColorAccent(int colorAccent) {
        this.colorAccent = colorAccent;
        this.colorAccentEnabled = true;
        return this;
    }

    public DownloadNotification clearColorAccent() {
        this.colorAccent = 0;
        this.colorAccentEnabled = false;
        this.colorized = false;
        return this;
    }

    public DownloadNotification setColorized(boolean colorized) {
        this.colorized = colorized;
        return this;
    }

    public DownloadNotification setLockscreenVisibility(int visibility) {
        this.lockscreenVisibility = visibility;
        return this;
    }

    public DownloadNotification setThumbnail(Bitmap bitmap) {
        this.thumbnail = bitmap;
        return this;
    }

    public DownloadNotification clearThumbnail() {
        this.thumbnail = null;
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
}
