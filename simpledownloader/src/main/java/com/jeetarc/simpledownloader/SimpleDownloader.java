package com.jeetarc.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.jeetarc.simpledownloader.thumbnail.ThumbLoader;
import com.jeetarc.simpledownloader.util.Logs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.OkHttpClient;


/**
* Main entry point for starting, restoring, and managing downloads.
*
* Each downloader instance has its own tasks, listeners, queue, and network state. 
* The DownloaderRegistry handles the rules that apply across instances, along with global slot limits, adaptive concurrency, and the shared database.
*/
public class SimpleDownloader {
	public static final String VERSION = "1.0.2";
	
	public static final int NETWORK_TYPE_NONE = NetworkManager.NETWORK_TYPE_NONE;
	public static final int NETWORK_TYPE_UNKNOWN = NetworkManager.NETWORK_TYPE_UNKNOWN;
	public static final int NETWORK_TYPE_WIFI = NetworkManager.NETWORK_TYPE_WIFI;
	public static final int NETWORK_TYPE_CELLULAR = NetworkManager.NETWORK_TYPE_CELLULAR;
	public static final int NETWORK_TYPE_ETHERNET = NetworkManager.NETWORK_TYPE_ETHERNET;
	public static final int NETWORK_TYPE_BLUETOOTH = NetworkManager.NETWORK_TYPE_BLUETOOTH;
	public static final int NETWORK_TYPE_VPN = NetworkManager.NETWORK_TYPE_VPN;
	public static final int NETWORK_TYPE_USB = NetworkManager.NETWORK_TYPE_USB;
	public static final int NETWORK_TYPE_ROAMING = NetworkManager.NETWORK_TYPE_ROAMING;
	
	static final String DEFAULT_OWNER_ID = "default_owner";
	public static volatile boolean loggingEnabled = false;
	
	final Object mLock = new Object();
	final Context mContext;
	private final String mOwnerId;
	final TaskManager taskManager;
	final SlotManager slotManager;
	final NetworkManager networkManager;
	final HttpEngine httpEngine;
	final TaskDatabase taskDatabase;
	final ThumbLoader thumbLoader;
	
	/** Receives updates from every task owned by this downloader. */
	public interface Listener {
		default void onStart(DownloadTask task) {}
		default void onQueued(int position, DownloadTask task) {}
		default void onProgress(int progress, long speed, long etaMs, DownloadTask task) {}
		default void onPaused(DownloadTask task) {}
		default void onResumed(DownloadTask task) {}
		default void onCancelled(DownloadTask task) {}
		default void onComplete(Uri outputUri, DownloadTask task) {}
		default void onError(Uri outputUri, Exception error, DownloadTask task) {}
		default void onRemoved(boolean outputDeleted, DownloadTask task) {}
		default void onRetry(int attempt, DownloadTask task) {}
		default void onWaitingForNetwork(int networkType, DownloadTask task) {}
		default void onStatusChanged(Status status, DownloadTask task) {}
		default void onActiveChanged(boolean isActive, DownloadTask task) {}
		default void onLifecycleChanged(int lifecycle, DownloadTask task) {}
	}
	
	final CopyOnWriteArrayList<Listener> mDefaultListeners = new CopyOnWriteArrayList<Listener>();
	volatile DownloadNotification mNotification = new DownloadNotification();
	private volatile int mMaxConcurrent = 0;
	private volatile int mEffectiveMaxConcurrent = DownloaderRegistry.AUTO_MIN_SLOT;
	volatile boolean mDownloadOnSlotFree = true;
	volatile boolean mHoldSlotOnPause = false;
	volatile boolean mEnableHistory = false;
	private volatile boolean mNotificationsEnabled;
	private volatile boolean mForegroundEnabled;
	private volatile boolean mAutoRestore = true;
	private volatile boolean mAutoRestoreDone;
	volatile boolean mRestoreAutoDispatchBlocked;
	private volatile boolean mResumeOnNetworkGain = true;
	volatile int mConnectTimeout = 15_000;
	volatile int mReadTimeout = 30_000;
	volatile long mProgressInterval = 300L;
	volatile RetryPolicy mRetryPolicy = RetryPolicy.builder().build();
	private volatile Comparator<DownloadTask> mTaskComparator;
	volatile boolean mShutdown;
	private volatile int mBufferSize = 16 * 1024;
	private volatile String mSubFolderPath = "";
	private volatile String mUserAgent = System.getProperty("http.agent");
	private volatile Map<String,String> mHeaders = Collections.emptyMap();
	private volatile String mCookies;
	private volatile boolean mWifiOnlyDefault;
	private volatile boolean mDeleteOnRemovalDefault;
	
	private SimpleDownloader(Context context, Config config) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		mContext = context.getApplicationContext();
		mOwnerId = normalizeOwnerId(config.ownerId);
		mAutoRestore = config.autoRestore;
		mTaskComparator = config.taskComparator;
		mNotification = config.notification;
		mMaxConcurrent = config.maxConcurrent;
		mEnableHistory = config.historyEnabled;
		mNotificationsEnabled = config.notificationsEnabled || config.foregroundEnabled;
		mForegroundEnabled = config.foregroundEnabled;
		mProgressInterval = config.progressInterval;
		mConnectTimeout = config.connectTimeout;
		mReadTimeout = config.readTimeout;
		mBufferSize = config.bufferSize;
		mRetryPolicy = config.retryPolicy;
		mDownloadOnSlotFree = config.downloadOnSlotFree;
		mHoldSlotOnPause = config.holdSlotOnPause;
		mResumeOnNetworkGain = config.resumeOnNetworkGain;
		
		synchronized (DownloaderRegistry.LOCK) {
			taskDatabase = DownloaderRegistry.INSTANCE.acquireDatabaseLocked(mContext);
			taskManager = new TaskManager(this);
			slotManager = new SlotManager(this);
			networkManager = new NetworkManager(this);
			httpEngine = new HttpEngine();
			thumbLoader = new ThumbLoader(mContext);
			mShutdown = false;
			DownloaderRegistry.INSTANCE.registerLocked(this);
			networkManager.register(mContext);
			networkManager.setRetryOnNetworkGain(mResumeOnNetworkGain);
			
			if (config.httpClient != null) httpEngine.setClient(config.httpClient);
			taskManager.setSortingEnabled(config.sortingEnabled);
			if (config.taskComparator != null) taskManager.setTaskComparator(config.taskComparator);
			configureEffectiveConcurrencyLocked();
		}
	}
	
	/** Returns the application's default-owner downloader. */
	public static SimpleDownloader getInstance(Context context) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		SimpleDownloader current = DownloaderRegistry.INSTANCE.defaultDownloaderOrNull();
		if (current != null && !current.mShutdown) return current;
		return new Builder(context).build();
	}
	
	/** Creates a new independent downloader builder. */
	public static Builder builder(Context context) {
		return new Builder(context);
	}
	
	/** Builder for an independent {@link SimpleDownloader} instance. */
	public static final class Builder {
		private final Context context;
		private String ownerId;
		private OkHttpClient httpClient;
		private boolean autoRestore = true;
		private boolean restoreAll;
		private boolean restoreFiltered;
		private TaskField<?> restoreField;
		private Object restoreValue;
		private boolean sortingEnabled;
		private Comparator<DownloadTask> taskComparator;
		private int maxConcurrent = 0;
		private boolean historyEnabled;
		private boolean foregroundEnabled;
		private boolean notificationsEnabled;
		private DownloadNotification notification;
		private long progressInterval = 300L;
		private int connectTimeout = 15_000;
		private int readTimeout = 30_000;
		private int bufferSize = 16 * 1024;
		private RetryPolicy retryPolicy = RetryPolicy.builder().build();
		private boolean downloadOnSlotFree = true;
		private boolean holdSlotOnPause = false;
		private boolean resumeOnNetworkGain = true;
		private SimpleDownloader builtDownloader;
		
		public Builder(Context context) {
			if (context == null) throw new IllegalArgumentException("Context cannot be null.");
			this.context = context.getApplicationContext();
		}
		
		/** Sets a downloader/profile identity. Null or blank uses the built-in default owner. */
		public Builder setOwnerId(String ownerId) {
			String value = ownerId == null ? null : ownerId.trim();
			this.ownerId = value == null || value.length() == 0 ? DEFAULT_OWNER_ID : value;
			return this;
		}
		
		public Builder setHttpClient(OkHttpClient client) {
			if (client == null) throw new IllegalArgumentException("OkHttpClient cannot be null.");
			httpClient = client;
			return this;
		}
		
		public Builder setMaxConcurrent(int max) {
			if (max < 0) throw new IllegalArgumentException("maxConcurrent cannot be negative. Use 0 for adaptive mode.");
			maxConcurrent = max;
			return this;
		}
		
		public Builder enableHistory(boolean enable) {
			historyEnabled = enable;
			return this;
		}
		
		public Builder enableForeground(boolean enable) {
			foregroundEnabled = enable;
			if (enable) notificationsEnabled = true;
			return this;
		}
		
		public Builder enableNotifications(boolean enable) {
			notificationsEnabled = enable;
			return this;
		}
		
		public Builder setNotification(DownloadNotification notification) {
			this.notification = notification == null ? null : new DownloadNotification(notification);
			return this;
		}
		
		public Builder setHoldSlotOnPause(boolean enable) {
			holdSlotOnPause = enable;
			return this;
		}
		
		public Builder setProgressInterval(long ms) {
			progressInterval = ms;
			return this;
		}
		
		public Builder setConnectTimeout(int ms) {
			connectTimeout = ms;
			return this;
		}
		
		public Builder setReadTimeout(int ms) {
			readTimeout = ms;
			return this;
		}
		
		public Builder setBufferSize(int bytes) {
			if (bytes <= 0) throw new IllegalArgumentException("Buffer size must be greater than zero.");
			bufferSize = bytes;
			return this;
		}
		
		public Builder setRetryCount(int count) {
			retryPolicy = RetryPolicy.ofAttempts(Math.max(0, count));
			return this;
		}
		
		public Builder setRetryPolicy(RetryPolicy retryPolicy) {
			if (retryPolicy == null) throw new IllegalArgumentException("RetryPolicy cannot be null.");
			this.retryPolicy = retryPolicy;
			return this;
		}
		
		public Builder enableResumeOnNetworkGain(boolean enable) {
			resumeOnNetworkGain = enable;
			return this;
		}
		
		public Builder setAutoRestore(boolean enable) {
			autoRestore = enable;
			return this;
		}
		
		public Builder restoreTasks() {
			restoreAll = true;
			autoRestore = false;
			return this;
		}
		
		public Builder restoreTasks(TaskField<?> field, Object value) {
			if (field == null) throw new IllegalArgumentException("TaskField cannot be null.");
			restoreFiltered = true;
			restoreField = field;
			restoreValue = value;
			autoRestore = false;
			return this;
		}
		
		public Builder enableSorting(boolean enable) {
			sortingEnabled = enable;
			return this;
		}
		
		public Builder setTaskComparator(Comparator<DownloadTask> comparator) {
			taskComparator = comparator;
			return this;
		}
		
		public SimpleDownloader build() {
			if (builtDownloader != null) {
				if (builtDownloader.mShutdown) throw new IllegalStateException("Builder's downloader has already been shut down.");
				return builtDownloader;
			}
			
			int restoreSelections = 0;
			if (autoRestore) restoreSelections++;
			if (restoreAll) restoreSelections++;
			if (restoreFiltered) restoreSelections++;
			if (restoreSelections > 1) throw new IllegalStateException("Only one restore method can be configured: setAutoRestore(true), restoreTasks(), or restoreTasks(field, value).");
			
			Config config = new Config(this);
			SimpleDownloader downloader = new SimpleDownloader(context, config);
			
			if (autoRestore) downloader.restoreTasksInternal(true, null, null);
			else if (restoreAll) downloader.restoreTasksInternal(false, null, null);
			else if (restoreFiltered) downloader.restoreTasksInternal(false, restoreField, restoreValue);
			
			builtDownloader = downloader;
			return downloader;
		}
	}
	
	/** Immutable snapshot of every construction-time option. */
	private static final class Config {
		final String ownerId;
		final OkHttpClient httpClient;
		final boolean autoRestore;
		final boolean restoreAll;
		final boolean restoreFiltered;
		final TaskField<?> restoreField;
		final Object restoreValue;
		final boolean sortingEnabled;
		final Comparator<DownloadTask> taskComparator;
		final int maxConcurrent;
		final boolean historyEnabled;
		final boolean foregroundEnabled;
		final boolean notificationsEnabled;
		final DownloadNotification notification;
		final long progressInterval;
		final int connectTimeout;
		final int readTimeout;
		final int bufferSize;
		final RetryPolicy retryPolicy;
		final boolean downloadOnSlotFree;
		final boolean holdSlotOnPause;
		final boolean resumeOnNetworkGain;
		
		Config(Builder builder) {
			this.ownerId = builder.ownerId;
			this.httpClient = builder.httpClient;
			this.autoRestore = builder.autoRestore;
			this.restoreAll = builder.restoreAll;
			this.restoreFiltered = builder.restoreFiltered;
			this.restoreField = builder.restoreField;
			this.restoreValue = builder.restoreValue;
			this.sortingEnabled = builder.sortingEnabled;
			this.taskComparator = builder.taskComparator;
			this.maxConcurrent = builder.maxConcurrent;
			this.historyEnabled = builder.historyEnabled;
			this.foregroundEnabled = builder.foregroundEnabled;
			this.notificationsEnabled = builder.notificationsEnabled;
			this.notification = builder.notification == null ? new DownloadNotification() : new DownloadNotification(builder.notification);
			this.progressInterval = validateNonNegative(builder.progressInterval, "Progress interval");
			this.connectTimeout = validateTimeout(builder.connectTimeout, "Connect timeout");
			this.readTimeout = validateTimeout(builder.readTimeout, "Read timeout");
			this.bufferSize = builder.bufferSize;
			this.retryPolicy = builder.retryPolicy;
			this.downloadOnSlotFree = builder.downloadOnSlotFree;
			this.holdSlotOnPause = builder.holdSlotOnPause;
			this.resumeOnNetworkGain = builder.resumeOnNetworkGain;
		}
	}
	
	private static String normalizeOwnerId(String ownerId) {
		if (ownerId == null) return DEFAULT_OWNER_ID;
		String value = ownerId.trim();
		return value.length() == 0 ? DEFAULT_OWNER_ID : value;
	}
	
	private static int validateTimeout(int value, String name) {
		if (value < 0) throw new IllegalArgumentException(name + " cannot be negative.");
		return value;
	}
	
	private static long validateNonNegative(long value, String name) {
		if (value < 0) throw new IllegalArgumentException(name + " cannot be negative.");
		return value;
	}
	
	long nextTaskId() {
		return DownloaderRegistry.INSTANCE.nextTaskId();
	}
	
	public String getOwnerId() {
		return mOwnerId;
	}
	
	public RetryPolicy getRetryPolicy() {
		return mRetryPolicy;
	}
	
	public int getConnectTimeout() {
		return mConnectTimeout;
	}
	
	public int getReadTimeout() {
		return mReadTimeout;
	}
	
	public long getProgressInterval() {
		return mProgressInterval;
	}
	
	public int getMaxConcurrent() {
		return mMaxConcurrent;
	}
	
	public int getEffectiveMaxConcurrent() {
		synchronized (mLock) {
			return getEffectiveMaxConcurrentLocked();
		}
	}
	
	public boolean areNotificationsEnabled() {
		return mNotificationsEnabled;
	}
	
	public boolean isForegroundEnabled() {
		return mForegroundEnabled;
	}
	
	public boolean isAdaptiveConcurrencyEnabled() {
		return isAutoConcurrentLocked();
	}
	
	public boolean isNetworkAvailable() {
		return networkManager != null && networkManager.isNetworkAvailable();
	}
	
	public int getNetworkType() {
		return networkManager == null ? NETWORK_TYPE_NONE : networkManager.getNetworkType();
	}
	
	public int getTotalCount() {
		return taskManager.getTotalCount();
	}
	
	public int getQueuedCount() {
		return slotManager.getQueuedCount();
	}
	
	public int getOccupiedCount() {
		return slotManager.getOccupiedCount();
	}
	
	public int getActiveCount() {
		return taskManager.getActiveCount();
	}
	
	public boolean isDownloading(long id) {
		DownloadTask task = taskManager.getTask(id);
		return task != null && task.getStatus() == Status.DOWNLOADING;
	}
	
	public boolean isDownloading() {
		return taskManager.isDownloading();
	}
	
	public boolean hasTask(long id) {
		return taskManager.hasTask(id);
	}
	
	public boolean hasTask(String fileUrl) {
		return taskManager.hasTask(fileUrl);
	}
	
	public DownloadTask getTask(long id) {
		return taskManager.getTask(id);
	}
	
	public List<DownloadTask> getTasks() {
		return taskManager.getTasks();
	}
	
	public <T> List<DownloadTask> getTasks(TaskField<T> field, T value) {
		return taskManager.getTasks(field, value);
	}
	
	@Nullable
	public <T> DownloadTask getTask(TaskField<T> field, T value) {
		return taskManager.getTask(field, value);
	}
	
	public SimpleDownloader setMaxConcurrent(int max) {
		if (max < 0) throw new IllegalArgumentException("maxConcurrent cannot be negative. Use 0 for adaptive mode.");
		
		synchronized (mLock) {
			ensureNotShutdownLocked();
			boolean oldAuto = isAutoConcurrentLocked();
			mMaxConcurrent = max;
			boolean newAuto = isAutoConcurrentLocked();
			if (oldAuto != newAuto) slotManager.onConcurrencyModeChangedLocked(oldAuto, newAuto);
			configureEffectiveConcurrencyLocked();
			slotManager.ensureExecutorLocked();
			slotManager.submitReadyHeldTasksLocked();
		}
		
		return this;
	}
	
	public SimpleDownloader setRetryCount(int count) {
		return setRetryPolicy(RetryPolicy.ofAttempts(Math.max(0, count)));
	}
	
	public SimpleDownloader setRetryPolicy(RetryPolicy retryPolicy) {
		if (retryPolicy == null) throw new IllegalArgumentException("RetryPolicy cannot be null.");
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mRetryPolicy = retryPolicy;
			for (DownloadTask task : taskManager.snapshot()) {
				if (task != null && !task.cannotBeReplaced()) task.mMaxRetryCount = retryPolicy.getRetryCount();
			}
		}
		return this;
	}
	
	public SimpleDownloader setConnectTimeout(int ms) {
		validateTimeout(ms, "Connect timeout");
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mConnectTimeout = ms;
			httpEngine.clearTimeoutClients();
		}
		return this;
	}
	
	public SimpleDownloader setReadTimeout(int ms) {
		validateTimeout(ms, "Read timeout");
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mReadTimeout = ms;
			httpEngine.clearTimeoutClients();
		}
		return this;
	}
	
	public SimpleDownloader setProgressInterval(long ms) {
		validateNonNegative(ms, "Progress interval");
		mProgressInterval = ms;
		return this;
	}
	
	public SimpleDownloader enableNotifications(boolean enable) {
		synchronized (mLock) {
			ensureNotShutdownLocked();
			if (!enable && mForegroundEnabled) throw new IllegalStateException("Cannot disable notifications while foreground mode is enabled.");
			mNotificationsEnabled = enable;
		}
		return this;
	}
	
	public SimpleDownloader enableForeground(boolean enable) {
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mForegroundEnabled = enable;
			if (enable) mNotificationsEnabled = true;
		}
		return this;
	}
	
	public SimpleDownloader enableHistory(boolean enable) {
		synchronized (mLock) { ensureNotShutdownLocked(); mEnableHistory = enable; }
		return this;
	}
	
	public SimpleDownloader setNotification(DownloadNotification notification) {
		mNotification = notification == null ? new DownloadNotification() : new DownloadNotification(notification);
		return this;
	}
	
	public SimpleDownloader setTaskComparator(Comparator<DownloadTask> comparator) {
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mTaskComparator = comparator;
			taskManager.setTaskComparator(comparator);
		}
		return this;
	}
	
	public SimpleDownloader enableResumeOnNetworkGain(boolean enable) {
		mResumeOnNetworkGain = enable;
		networkManager.setRetryOnNetworkGain(enable);
		return this;
	}
	
	public SimpleDownloader setDownloadOnSlotFree(boolean enable) {
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mDownloadOnSlotFree = enable;
			if (enable) slotManager.submitReadyHeldTasksLocked();
		}
		return this;
	}
	
	public SimpleDownloader setHoldSlotOnPause(boolean enable) {
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mHoldSlotOnPause = enable;
		}
		return this;
	}
	
	public SimpleDownloader addListener(Listener listener) {
		if (listener != null && !mDefaultListeners.contains(listener)) mDefaultListeners.add(listener);
		return this;
	}
	
	public boolean hasListeners() {
		return !mDefaultListeners.isEmpty();
	}
	
	public boolean hasListener(Listener listener) {
		return listener != null && mDefaultListeners.contains(listener);
	}
	
	public SimpleDownloader removeListener(Listener listener) {
		if (listener != null) mDefaultListeners.remove(listener);
		return this;
	}
	
	public SimpleDownloader removeAllListeners() {
		mDefaultListeners.clear();
		return this;
	}
	
	public SimpleDownloader addObserver(TaskListObserver observer) {
		if (observer != null) taskManager.addObserver(observer);
		return this;
	}
	
	public boolean hasObservers() {
		return taskManager.hasObservers();
	}
	
	public boolean hasObserver(TaskListObserver observer) {
		return taskManager.hasObserver(observer);
	}
	
	public SimpleDownloader removeObserver(TaskListObserver observer) {
		taskManager.removeObserver(observer);
		return this;
	}
	
	public void releaseAllCallbacks() {
		mDefaultListeners.clear();
		taskManager.releaseAllCallbacks();
	}
	
	public DownloadTask startDownload(DownloadRequest request) {
		if (request == null) throw new IllegalArgumentException("DownloadRequest cannot be null.");
		synchronized (mLock) {
			ensureNotShutdownLocked();
			validateNotificationConfigLocked();
			return startDownloadLocked(request);
		}
	}
	
	public List<DownloadTask> startDownloads(List<DownloadRequest> requests) {
		if (requests == null) throw new IllegalArgumentException("DownloadRequests cannot be null.");
		ArrayList<DownloadTask> tasks = new ArrayList<DownloadTask>();
		
		synchronized (mLock) {
			ensureNotShutdownLocked();
			validateNotificationConfigLocked();
			HashSet<Long> ids = new HashSet<Long>();
			HashSet<String> overwriteTargets = new HashSet<String>();
			ArrayList<Long> resolvedIds = new ArrayList<Long>(requests.size());
			
			for (DownloadRequest request : requests) {
				if (request == null) throw new IllegalArgumentException("DownloadRequest cannot be null.");
				long id = request.hasId ? request.id : nextTaskId();
				resolvedIds.add(Long.valueOf(id));
				if (!ids.add(id)) throw new IllegalStateException("Duplicate task ID in download batch: " + id);
				String overwriteKey = request.getOverwriteKey();
				if (overwriteKey != null && !overwriteTargets.add(overwriteKey)) throw new IllegalStateException("Duplicate output target in download batch: " + overwriteKey);
				DownloadTask oldTask = taskManager.getTask(id);
				if (oldTask != null && oldTask.cannotBeReplaced()) throw new IllegalStateException("An unfinished task with ID " + id + " already exists and cannot be replaced.");
				if (DownloaderRegistry.INSTANCE.hasBusyOverwriteTargetLocked(overwriteKey, id, this)) throw new IllegalStateException("Output target is already used by another unfinished task: " + overwriteKey);
			}
			
			for (int i = 0; i < requests.size(); i++) {
				tasks.add(startDownloadLocked(requests.get(i), resolvedIds.get(i).longValue()));
			}
		}
		
		return tasks;
	}
	
	private DownloadTask startDownloadLocked(DownloadRequest request) {
		return startDownloadLocked(request, request.hasId ? request.id : nextTaskId());
	}
	
	private DownloadTask startDownloadLocked(DownloadRequest request, long id) {
		DownloadRequest resolved = request.resolve(id);
		DownloadTask oldTask = taskManager.getTask(resolved.id);
		
		if (oldTask != null) {
			if (oldTask.cannotBeReplaced()) throw new IllegalStateException("An unfinished task with ID " + resolved.id + " already exists and cannot be replaced.");
			oldTask.removeForReplacement();
			Logs.info("A task with the same ID: " + resolved.id + " already exists. Replacing the old task with the new one.");
		}
		
		String overwriteKey = resolved.getOverwriteKey();
		DownloadTask task = resolved.createTask(this);
		
		synchronized (DownloaderRegistry.LOCK) {
			if (request.hasId && oldTask == null) {
				DownloaderRegistry.INSTANCE.ensureUniqueTaskIdLocked(resolved.id, this);
				if (taskDatabase.hasUnfinishedTask(resolved.id)) throw new IllegalStateException("Task ID " + resolved.id + " is already used by a persisted unfinished task.");
			}
			
			if (DownloaderRegistry.INSTANCE.hasBusyOverwriteTargetLocked(overwriteKey, resolved.id, this)) throw new IllegalStateException("Output target is already used by another unfinished task: " + overwriteKey);
			
			taskManager.putTaskLocked(task);
			taskManager.addTaskLocked(task);
		}
		
		taskDatabase.saveTask(task);
		ensureAdaptiveInitializedLocked();
		mRestoreAutoDispatchBlocked = false;
		slotManager.enqueueOrSubmitLocked(task, false);
		return task;
	}
	
	private List<DownloadTask> restoreTasksInternal(boolean autoRestore, TaskField<?> field, Object value) {
		
		synchronized (mLock) {
			ensureNotShutdownLocked();
			mRestoreAutoDispatchBlocked = !autoRestore;
			List<TaskState> states;
			if (field == null) states = taskDatabase.loadTaskStatesForOwner(mOwnerId);
			else states = loadTaskStatesForField(field, value);
			List<DownloadTask> restored = taskManager.restoreTasks(this, states, autoRestore);
			
			if (autoRestore) mAutoRestoreDone = true;
			return restored;
		}
	}
	
	@SuppressWarnings({"rawtypes", "unchecked"})
	private List<TaskState> loadTaskStatesForField(TaskField<?> field, Object value) {
		return taskDatabase.loadTaskStatesForOwner(mOwnerId, (TaskField) field, value);
	}
	
	public int getBufferSize() { return mBufferSize; }
	
	public SimpleDownloader setBufferSize(int bytes) {
		if (bytes <= 0) throw new IllegalArgumentException("Buffer size must be greater than zero.");
		synchronized (mLock) { ensureNotShutdownLocked(); mBufferSize = bytes; }
		return this;
	}
	
	public String getSubFolder() { return mSubFolderPath; }
	public Map<String,String> getHeaders() { return mHeaders; }
	public String getUserAgent() { return mUserAgent; }
	public String getCookies() { return mCookies; }
	public boolean isWifiOnlyDefault() { return mWifiOnlyDefault; }
	public boolean isDeleteOnRemovalDefault() { return mDeleteOnRemovalDefault; }
	public SimpleDownloader setSubFolder(String value) { synchronized (mLock) { ensureNotShutdownLocked(); mSubFolderPath = value == null ? "" : value.trim(); } return this; }
	public SimpleDownloader setHeaders(Map<String,String> headers) { synchronized (mLock) { ensureNotShutdownLocked(); mHeaders = headers == null || headers.isEmpty() ? Collections.<String,String>emptyMap() : Collections.unmodifiableMap(new HashMap<String,String>(headers)); } return this; }
	
	public SimpleDownloader addHeader(String key, String value) {
		if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("Header key cannot be null or empty.");
		if (value == null) throw new IllegalArgumentException("Header value cannot be null.");
		synchronized (mLock) { ensureNotShutdownLocked(); HashMap<String,String> copy = new HashMap<String,String>(mHeaders); copy.put(key, value); mHeaders = Collections.unmodifiableMap(copy); }
		return this;
	}
	
	public SimpleDownloader setUserAgent(String value) { synchronized (mLock) { ensureNotShutdownLocked(); mUserAgent = value; } return this; }
	public SimpleDownloader setCookies(String value) { synchronized (mLock) { ensureNotShutdownLocked(); mCookies = value; } return this; }
	public SimpleDownloader setWifiOnly(boolean enable) { synchronized (mLock) { ensureNotShutdownLocked(); mWifiOnlyDefault = enable; } return this; }
	public SimpleDownloader setDeleteOnRemoval(boolean enable) { synchronized (mLock) { ensureNotShutdownLocked(); mDeleteOnRemovalDefault = enable; } return this; }
	
	public static void setGlobalConcurrent(int max) {
		DownloaderRegistry.INSTANCE.setGlobalConcurrent(max);
	}
	
	/** Shutdown for the default instance. */
	public static void shutdownDefault() {
		SimpleDownloader downloader = DownloaderRegistry.INSTANCE.defaultDownloaderOrNull();
		if (downloader != null) downloader.shutdown();
	}
	
	public static void enableLogging(boolean enable)  {
		loggingEnabled = enable;
	}
	
	void validateNotificationConfigLocked() {
		if (mForegroundEnabled && !mNotificationsEnabled) {
			throw new IllegalStateException("Cannot run foreground without notifications.");
		}
	}
	
	boolean isAutoConcurrentLocked() {
		return mMaxConcurrent <= 0;
	}
	
	int getEffectiveMaxConcurrentLocked() {
		if (mMaxConcurrent > 0) return Math.max(1, mMaxConcurrent);
		return Math.max(DownloaderRegistry.AUTO_MIN_SLOT, Math.min(DownloaderRegistry.AUTO_MAX_SLOT, mEffectiveMaxConcurrent));
	}
	
	void setEffectiveMaxConcurrentLocked(int value) {
		mEffectiveMaxConcurrent = Math.max(DownloaderRegistry.AUTO_MIN_SLOT, Math.min(DownloaderRegistry.AUTO_MAX_SLOT, value));
	}
	
	private void configureEffectiveConcurrencyLocked() {
		if (isAutoConcurrentLocked()) ensureAdaptiveInitializedLocked();
		else setEffectiveMaxConcurrentLocked(mMaxConcurrent);
	}
	
	private void ensureAdaptiveInitializedLocked() {
		DownloaderRegistry.INSTANCE.autoConcurrencyController().ensureInitializedLocked();
	}
	
	void ensureNotShutdownLocked() {
		if (mShutdown) throw new IllegalStateException("SimpleDownloader is shut down.");
	}
	
	/** Stops this downloader instance and releases its runtime resources. */
	public void shutdown() {
		final DownloadExecutor executorToWait;
		final TaskDatabase databaseToRelease;
		
		synchronized (mLock) {
			if (mShutdown) return;
			DownloadService.onDownloaderShutdown(this);
			mShutdown = true;
			
			for (DownloadTask task : taskManager.snapshot()) {
				if (task != null) task.stopForShutdown();
			}
			
			networkManager.shutdownLocked();
			executorToWait = slotManager.shutdownLocked();
			databaseToRelease = taskDatabase;
		}
		
		Runnable cleanup = new Runnable() {
			@Override
			public void run() {
				if (executorToWait != null) executorToWait.awaitTerminationQuietly(15_000L);
				taskManager.shutdownLocked();
				httpEngine.shutdown();
				thumbLoader.shutdown();
				
				DownloaderRegistry.INSTANCE.unregister(SimpleDownloader.this, databaseToRelease);
			}
		};
		
		if (Looper.myLooper() == Looper.getMainLooper()) new Thread(cleanup, "SimpleDownloader-Shutdown").start();
		else cleanup.run();
	}
	
	public void forceDownload(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.forceDownload(); }
	public void pauseAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.pause(); }
	public void pause(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.pause(); }
	public void pause(Priority priority) { for (DownloadTask task : taskManager.snapshot()) if (task != null && task.mPriority == priority) task.pause(); }
	public void resumeAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.resume(); }
	public void resume(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.resume(); }
	public void resumeAll(Priority priority) { for (DownloadTask task : taskManager.snapshot()) if (task != null && task.mPriority == priority) task.resume(); }
	public void cancelAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.cancel(); }
	public void cancel(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.cancel(); }
	public void requeueAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.requeue(); }
	public void requeue(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.requeue(); }
	public void removeAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.remove(); }
	public void remove(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.remove(); }
	public void remove(Status status) { for (DownloadTask task : taskManager.snapshot()) if (task != null && task.mStatus == status) task.remove(); }
	public void remove(Priority priority) { for (DownloadTask task : taskManager.snapshot()) if (task != null && task.mPriority == priority) task.remove(); }
	public void retryAll() { for (DownloadTask task : taskManager.snapshot()) if (task != null) task.retry(); }
	public void retry(long id) { DownloadTask task = taskManager.getTask(id); if (task != null) task.retry(); }
	public void setLockedInQueue(long id, boolean enable) { DownloadTask task = taskManager.getTask(id); if (task != null) task.setLockedInQueue(enable); }
	public void setDeleteOnRemoval(long id, boolean enable) { DownloadTask task = taskManager.getTask(id); if (task != null) task.setDeleteOnRemoval(enable); }
	public void setWifiOnly(long id, boolean enable) { DownloadTask task = taskManager.getTask(id); if (task != null) task.setWifiOnly(enable); }
	
	@Override
	public String toString() {
		return "SimpleDownloader{ownerId='" + mOwnerId + "'}";
	}
	
	private static final Database DATABASE_API = new Database();
	public static Database database() { return DATABASE_API;}
	public static final class Database {
		private Database() {}
		
		/** Deletes the persisted database data for a specific task. This does not delete the downloaded file or remove the task from an active SimpleDownloader instance.*/		
		public void deleteForTask(long taskId) {
			TaskDatabase database = DownloaderRegistry.INSTANCE.sharedDatabase();
			if (database != null) database.deleteForTask(taskId);
		}
		
		/** Deletes all persisted database data belonging to the specified owner. This does not delete the downloaded file or remove the task from an active SimpleDownloader instance.*/		
		public void deleteForOwner(String ownerId) {
			TaskDatabase database = DownloaderRegistry.INSTANCE.sharedDatabase();
			if (database != null) database.deleteForOwner(ownerId);
		}
		
		/** Deletes all persisted database data belonging to the default owner. This does not delete the downloaded file or remove the task from an active SimpleDownloader instance.*/		
		public void deleteForDefaultOwner() {
			TaskDatabase database = DownloaderRegistry.INSTANCE.sharedDatabase();
			if (database != null) database.deleteForDefaultOwner();
		}
		
		/** Deletes all persisted task data from the database. This does not delete the downloaded file or remove the task from an active SimpleDownloader instance.*/		
		public void deleteForAll() {
			TaskDatabase database = DownloaderRegistry.INSTANCE.sharedDatabase();
			if (database != null) database.deleteForAll();
		}
		
		/** Drops and recreates the tasks table and its indexes. This removes all persisted task data. It does not delete downloaded files or remove tasks from active SimpleDownloader instances.*/		
		public void resetTasksTable() {
			TaskDatabase database = DownloaderRegistry.INSTANCE.sharedDatabase();
			if (database != null) database.resetTasksTable();
		}
	}
}
