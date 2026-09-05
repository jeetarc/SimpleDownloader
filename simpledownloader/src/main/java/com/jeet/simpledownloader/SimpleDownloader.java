package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.app.ActivityManager;
import android.content.Context;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.jeet.simpledownloader.thumbnail.ThumbLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import com.jeet.simpledownloader.util.Logs;

/**
* Main entry point for starting, restoring, and managing downloads.
*
* <p>Each instance is independent. Runtime task state, observers, listeners,
* session/slot state, network state and adaptive mode belong to the instance.
* The task database is shared by all instances.</p>
*/
public class SimpleDownloader {
	public static final int NETWORK_TYPE_NONE = NetworkManager.NETWORK_TYPE_NONE;
	public static final int NETWORK_TYPE_UNKNOWN = NetworkManager.NETWORK_TYPE_UNKNOWN;
	public static final int NETWORK_TYPE_WIFI = NetworkManager.NETWORK_TYPE_WIFI;
	public static final int NETWORK_TYPE_CELLULAR = NetworkManager.NETWORK_TYPE_CELLULAR;
	public static final int NETWORK_TYPE_ETHERNET = NetworkManager.NETWORK_TYPE_ETHERNET;
	public static final int NETWORK_TYPE_BLUETOOTH = NetworkManager.NETWORK_TYPE_BLUETOOTH;
	public static final int NETWORK_TYPE_VPN = NetworkManager.NETWORK_TYPE_VPN;
	public static final int NETWORK_TYPE_USB = NetworkManager.NETWORK_TYPE_USB;
	public static final int NETWORK_TYPE_ROAMING = NetworkManager.NETWORK_TYPE_ROAMING;
	
	static final int AUTO_MIN_SLOT = 1;
	static final int AUTO_MAX_SLOT = 10;
	
	static final Object GLOBAL_LOCK = new Object();
	static final String DEFAULT_OWNER_ID = "default_owner";
	private static final Set<SimpleDownloader> INSTANCES = new HashSet<SimpleDownloader>();
	private static final AtomicLong NEXT_TASK_ID = new AtomicLong(System.currentTimeMillis());
	private static final ExecutorService ADAPTIVE_DISPATCH_EXECUTOR = Executors.newSingleThreadExecutor();
	private static final AtomicInteger GLOBAL_MANUAL_OCCUPIED = new AtomicInteger();
	private static final AtomicInteger GLOBAL_AUTO_OCCUPIED = new AtomicInteger();
	private static final AutoConcurrencyController AUTO_CONCURRENCY_CONTROLLER = new AutoConcurrencyController();
	static TaskDatabase SHARED_DATABASE;
	private static int DATABASE_USERS;
	private static volatile SimpleDownloader sDefault;
	public static volatile boolean loggingEnabled = true;
	
	final Object mLock = new Object();
	final Context mContext;
	private final String mOwnerId;
	final TaskManager taskManager;
	final SlotManager slotManager;
	final NetworkManager networkManager;
	final HttpEngine httpEngine;
	final TaskDatabase taskDatabase;
	final ThumbLoader thumbLoader;
	private final CopyOnWriteArrayList<Listener> mDefaultListeners = new CopyOnWriteArrayList<Listener>();
	
	/** Receives updates from every task owned by this downloader. */
	public interface Listener {
		default void onStart(long id, DownloadTask task) {}
		default void onQueued(long id, int position, DownloadTask task) {}
		default void onProgress(long id, int progress, long speed, long etaMs, DownloadTask task) {}
		default void onPaused(long id, DownloadTask task) {}
		default void onResumed(long id, DownloadTask task) {}
		default void onCancelled(long id, DownloadTask task) {}
		default void onComplete(long id, android.net.Uri outputUri, DownloadTask task) {}
		default void onError(long id, android.net.Uri outputUri, Exception error, DownloadTask task) {}
		default void onRemoved(long id, boolean outputDeleted, DownloadTask task) {}
		default void onRetry(long id, int attempt, DownloadTask task) {}
		default void onWaitingForNetwork(long id, int networkType, DownloadTask task) {}
		default void onStatusChanged(long id, Status status, DownloadTask task) {}
		default void onActiveChanged(long id, boolean isActive, DownloadTask task) {}
		default void onLifecycleChanged(long id, int lifecycle, DownloadTask task) {}
	}
	
	volatile DownloadNotification mNotification = new DownloadNotification();
	private volatile int mMaxConcurrent = 0;
	private volatile int mEffectiveMaxConcurrent = AUTO_MIN_SLOT;
	volatile boolean mDownloadOnSlotFree = true;
	volatile boolean mEnableHistory = false;
	private volatile boolean mNotificationsEnabled;
	private volatile boolean mForegroundEnabled;
	private volatile boolean mAutoRestore;
	private volatile boolean mAutoRestoreDone;
	private volatile boolean mResumeOnNetworkGain;
	volatile int mConnectTimeout = 15_000;
	volatile int mReadTimeout = 30_000;
	volatile long mProgressInterval = 300L;
	volatile RetryPolicy mRetryPolicy = RetryPolicy.builder().build();
	private volatile Comparator<DownloadTask> mTaskComparator;
	volatile boolean mShutdown;
	private volatile int mBufferSize = 16 * 1024;
	private volatile String mSubFolderPath;
	private volatile String mUserAgent = System.getProperty("http.agent");
	private volatile Map<String,String> mHeaders = Collections.emptyMap();
	private volatile String mCookies;
	private volatile boolean mWifiOnlyDefault;
	private volatile boolean mDeleteOnRemovalDefault;
	private static volatile int sGlobalConcurrent = 0;
	
	private SimpleDownloader(Context context, Config config) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		mContext = context.getApplicationContext();
		mOwnerId = normalizeOwnerId(config.ownerId);
		mAutoRestore = config.autoRestore;
		mTaskComparator = config.taskComparator;
		mNotification = new DownloadNotification();
		
		synchronized (GLOBAL_LOCK) {
			taskDatabase = acquireDatabaseLocked(mContext);
			taskManager = new TaskManager(this);
			slotManager = new SlotManager(this);
			networkManager = new NetworkManager(this);
			httpEngine = new HttpEngine();
			thumbLoader = new ThumbLoader(mContext);
			mShutdown = false;
			INSTANCES.add(this);
			if (DEFAULT_OWNER_ID.equals(mOwnerId) && sDefault == null) sDefault = this;
			networkManager.register(mContext);
			networkManager.setRetryOnNetworkGain(false);
			
			if (config.httpClient != null) httpEngine.setClient(config.httpClient);
			else applyTimeoutConfigurationLocked();
			
			taskManager.setSortingEnabled(config.sortingEnabled);
			if (config.taskComparator != null) taskManager.setTaskComparator(config.taskComparator);
			configureEffectiveConcurrencyLocked();
		}
	}
	
	/** Returns the application's default-owner downloader. */
	public static SimpleDownloader getInstance(Context context) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		synchronized (GLOBAL_LOCK) {
			if (sDefault != null && !sDefault.mShutdown) return sDefault;
		}
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
		private boolean autoRestore;
		private boolean restoreAll;
		private boolean restoreFiltered;
		private TaskField<?> restoreField;
		private Object restoreValue;
		private boolean sortingEnabled = true;
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
		private boolean resumeOnNetworkGain;
		private SimpleDownloader builtDownloader;
		
		public Builder(Context context) {
			if (context == null) throw new IllegalArgumentException("Context cannot be null.");
			this.context = context.getApplicationContext();
		}
		
		/** Sets a persistent downloader/profile identity. Null or blank uses the built-in default owner. */
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
			return this;
		}
		
		public Builder restoreTasks(TaskField<?> field, Object value) {
			if (field == null) throw new IllegalArgumentException("TaskField cannot be null.");
			restoreFiltered = true;
			restoreField = field;
			restoreValue = value;
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
			
			Config config = new Config();
			config.ownerId = ownerId;
			config.httpClient = httpClient;
			config.autoRestore = autoRestore;
			config.restoreAll = restoreAll;
			config.restoreFiltered = restoreFiltered;
			config.restoreField = restoreField;
			config.restoreValue = restoreValue;
			config.sortingEnabled = sortingEnabled;
			config.taskComparator = taskComparator;
			
			SimpleDownloader downloader = new SimpleDownloader(context, config);
			downloader.setMaxConcurrent(maxConcurrent);
			downloader.enableHistory(historyEnabled);
			downloader.enableNotifications(notificationsEnabled || foregroundEnabled);
			downloader.enableForeground(foregroundEnabled);
			downloader.setNotification(notification);
			downloader.setProgressInterval(progressInterval);
			downloader.setConnectTimeout(connectTimeout);
			downloader.setReadTimeout(readTimeout);
			downloader.setBufferSize(bufferSize);
			downloader.setRetryPolicy(retryPolicy);
			downloader.setDownloadOnSlotFree(downloadOnSlotFree);
			downloader.enableResumeOnNetworkGain(resumeOnNetworkGain);
			
			if (autoRestore) downloader.restoreTasksInternal(true, null, null);
			else if (restoreAll) downloader.restoreTasksInternal(false, null, null);
			else if (restoreFiltered) downloader.restoreTasksInternal(false, restoreField, restoreValue);
			
			builtDownloader = downloader;
			return downloader;
		}
	}
	
	private static final class Config {
		String ownerId;
		OkHttpClient httpClient;
		boolean autoRestore;
		boolean restoreAll;
		boolean restoreFiltered;
		TaskField<?> restoreField;
		Object restoreValue;
		boolean sortingEnabled = true;
		Comparator<DownloadTask> taskComparator;
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
	
	private static TaskDatabase acquireDatabaseLocked(Context context) {
		if (SHARED_DATABASE == null) SHARED_DATABASE = new TaskDatabase(context);
		DATABASE_USERS++;
		return SHARED_DATABASE;
	}
	
	private static void releaseDatabaseLocked(TaskDatabase database) {
		if (database != SHARED_DATABASE) return;
		DATABASE_USERS--;
		if (DATABASE_USERS <= 0 && INSTANCES.isEmpty()) {
			DATABASE_USERS = 0;
			SHARED_DATABASE = null;
			database.close();
		}
	}
	
	static ArrayList<SimpleDownloader> snapshotInstancesLockedForTaskRestore() {
		return new ArrayList<SimpleDownloader>(INSTANCES);
	}
	
	private static ArrayList<SimpleDownloader> snapshotInstancesLocked() {
		return snapshotInstancesLockedForTaskRestore();
	}
	
	private static void ensureUniqueTaskIdLocked(long id, SimpleDownloader requester) {
		for (SimpleDownloader downloader : INSTANCES) {
			if (downloader == null || downloader == requester || downloader.mShutdown) continue;
			if (downloader.taskManager.hasTask(id)) throw new IllegalStateException("Task ID " + id + " is already used by another SimpleDownloader instance.");
		}
	}
	
	private static boolean hasBusyOverwriteTargetLocked(String overwriteKey, long ignoreTaskId, SimpleDownloader requester) {
		if (overwriteKey == null || overwriteKey.isEmpty()) return false;
		
		for (SimpleDownloader downloader : INSTANCES) {
			for (DownloadTask task : downloader.taskManager.snapshot()) {
				if (task == null) continue;
				if (task.mId == ignoreTaskId && downloader == requester) continue;
				if (task.isFinished()) continue;
				if (overwriteKey.equals(task.getOverwriteKey())) return true;
			}
		}
		return false;
	}
	
	static SimpleDownloader findTaskOwner(long id) {
		synchronized (GLOBAL_LOCK) {
			SimpleDownloader found = null;
			
			for (SimpleDownloader downloader : INSTANCES) {
				if (downloader == null || downloader.mShutdown) continue;
				if (downloader.taskManager.getTask(id) == null) continue;
				if (found != null) return null;
				found = downloader;
			}
			
			return found;
		}
	}
	
	static DownloadTask findTaskAcrossInstances(long id) {
		SimpleDownloader owner = findTaskOwner(id);
		return owner == null ? null : owner.taskManager.getTask(id);
	}
	
	static Context appContextOrNull() {
		synchronized (GLOBAL_LOCK) {
			if (sDefault != null && !sDefault.mShutdown) return sDefault.mContext;
			for (SimpleDownloader downloader : INSTANCES) {
				if (downloader != null && !downloader.mShutdown) return downloader.mContext;
			}
			return null;
		}
	}
	
	static SimpleDownloader defaultDownloaderOrNull() {
		synchronized (GLOBAL_LOCK) {
			return sDefault;
		}
	}
	
	long nextTaskId() {
		return NEXT_TASK_ID.incrementAndGet();
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
	
	@Nullable
	public <T> DownloadTask getTask(TaskField<T> field, T value) {
		return taskManager.getTask(field, value);
	}
	
	public <T> List<DownloadTask> getTasks(TaskField<T> field, T value) {
		return taskManager.getTasks(field, value);
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
				if (task != null && !task.cannotBeReplaced()) task.mMaxRetryCount = retryPolicy.getMaxRetryCount();
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
	
	List<Listener> getListenersSnapshot() {
		return new ArrayList<Listener>(mDefaultListeners);
	}
	
	public SimpleDownloader addListener(Listener listener) {
		if (listener != null && !mDefaultListeners.contains(listener)) mDefaultListeners.add(listener);
		return this;
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
				if (hasBusyOverwriteTargetLocked(overwriteKey, id, this)) throw new IllegalStateException("Output target is already used by another unfinished task: " + overwriteKey);
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
		
		synchronized (GLOBAL_LOCK) {
			if (request.hasId && oldTask == null) {
				ensureUniqueTaskIdLocked(resolved.id, this);
				if (taskDatabase.hasUnfinishedTask(resolved.id)) throw new IllegalStateException("Task ID " + resolved.id + " is already used by a persisted unfinished task.");
			}
			
			if (hasBusyOverwriteTargetLocked(overwriteKey, resolved.id, this)) throw new IllegalStateException("Output target is already used by another unfinished task: " + overwriteKey);
			
			taskManager.putTaskLocked(task);
			taskManager.addTaskLocked(task);
		}
		
		taskDatabase.saveTask(task);
		ensureAdaptiveInitializedLocked();
		slotManager.enqueueOrSubmitLocked(task, false);
		return task;
	}
	
	private List<DownloadTask> restoreTasksInternal(boolean autoRestore, TaskField<?> field, Object value) {
		
		synchronized (mLock) {
			ensureNotShutdownLocked();
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
	public SimpleDownloader setSubFolder(String value) { synchronized (mLock) { ensureNotShutdownLocked(); mSubFolderPath = value == null ? null : value.trim(); } return this; }
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
		if (max < 0) throw new IllegalArgumentException("globalConcurrent cannot be negative. Use 0 to disable the global cap.");
		sGlobalConcurrent = max;
		onAdaptiveStateChangedLocked();
	}
	
	/** Shutdown for the default instance. */
	public static void shutdownDefault() {
		SimpleDownloader downloader = defaultDownloaderOrNull();
		if (downloader != null) downloader.shutdown();
	}
	
	public static void enableLogging(boolean enable)  {
		loggingEnabled = enable;
	}
	
	static boolean hasGlobalCapacityLocked() { return sGlobalConcurrent <= 0 || getGlobalManualOccupiedLocked() + getGlobalAutoOccupiedLocked() < sGlobalConcurrent; }
	
	void validateNotificationConfigLocked() {
		if (mForegroundEnabled && !mNotificationsEnabled) throw new IllegalStateException("Cannot run foreground without notifications.");
	}
	
	boolean isAutoConcurrentLocked() {
		return mMaxConcurrent <= 0;
	}
	
	int getEffectiveMaxConcurrentLocked() {
		if (mMaxConcurrent > 0) return Math.max(1, mMaxConcurrent);
		return Math.max(AUTO_MIN_SLOT, Math.min(AUTO_MAX_SLOT, mEffectiveMaxConcurrent));
	}
	
	void setEffectiveMaxConcurrentLocked(int value) {
		mEffectiveMaxConcurrent = Math.max(AUTO_MIN_SLOT, Math.min(AUTO_MAX_SLOT, value));
	}
	
	private void configureEffectiveConcurrencyLocked() {
		if (isAutoConcurrentLocked()) {
			ensureAdaptiveInitializedLocked();
		} else {
			setEffectiveMaxConcurrentLocked(mMaxConcurrent);
		}
	}
	
	private void ensureAdaptiveInitializedLocked() {
		AUTO_CONCURRENCY_CONTROLLER.ensureInitializedLocked();
	}
	
	private void applyTimeoutConfigurationLocked() {
		// Request-level timeout clients are created lazily by HttpEngine.
	}
	
	void ensureNotShutdownLocked() {
		if (mShutdown) throw new IllegalStateException("SimpleDownloader is shut down.");
	}
	
	static int getGlobalAutoActiveCountLocked() {
		int count = 0;
		for (SimpleDownloader downloader : INSTANCES) {
			if (downloader == null || downloader.mShutdown) continue;
			if (!downloader.isAutoConcurrentLocked()) continue;
			count += downloader.taskManager.getCachedActiveSpeedTaskCountLocked();
		}
		return count;
	}
	
	static long getGlobalAutoSpeedLocked() {
		long total = 0;
		for (SimpleDownloader downloader : INSTANCES) {
			if (downloader == null || downloader.mShutdown) continue;
			if (!downloader.isAutoConcurrentLocked()) continue;
			total += downloader.taskManager.getCachedTotalActiveSpeedLocked();
		}
		return total;
	}
	
	static int getGlobalManualOccupiedLocked() {
		return Math.max(0, GLOBAL_MANUAL_OCCUPIED.get());
	}
	
	static int getGlobalAutoOccupiedLocked() {
		return Math.max(0, GLOBAL_AUTO_OCCUPIED.get());
	}
	
	static void onSlotOccupiedLocked(SimpleDownloader downloader) {
		if (downloader == null) return;
		if (downloader.isAutoConcurrentLocked()) GLOBAL_AUTO_OCCUPIED.incrementAndGet();
		else GLOBAL_MANUAL_OCCUPIED.incrementAndGet();
	}
	
	static void onSlotReleasedLocked(SimpleDownloader downloader) {
		if (downloader == null) return;
		if (downloader.isAutoConcurrentLocked()) decrement(GLOBAL_AUTO_OCCUPIED); else decrement(GLOBAL_MANUAL_OCCUPIED);
	}
	
	static void rebalanceOccupiedCountersLocked(int count, boolean oldAuto, boolean newAuto) {
		if (count <= 0 || oldAuto == newAuto) return;
		AtomicInteger oldCounter = oldAuto ? GLOBAL_AUTO_OCCUPIED : GLOBAL_MANUAL_OCCUPIED;
		AtomicInteger newCounter = newAuto ? GLOBAL_AUTO_OCCUPIED : GLOBAL_MANUAL_OCCUPIED;
		for (int i = 0; i < count; i++) decrement(oldCounter);
		newCounter.addAndGet(count);
	}
	
	private static void decrement(AtomicInteger value) {
		while (true) {
			int current = value.get();
			if (current <= 0) return;
			if (value.compareAndSet(current, current - 1)) return;
		}
	}
	
	static int getGlobalAdaptiveTargetLocked() {
		return AUTO_CONCURRENCY_CONTROLLER.getTargetSlotsLocked();
	}
	
	static void onAdaptiveStateChangedLocked() {
		ADAPTIVE_DISPATCH_EXECUTOR.execute(new Runnable() {
			@Override
			public void run() {
				ArrayList<SimpleDownloader> instances;
				synchronized (GLOBAL_LOCK) {
					instances = snapshotInstancesLocked();
				}
				
				for (SimpleDownloader downloader : instances) {
					if (downloader == null || downloader.mShutdown) continue;
					
					synchronized (downloader.mLock) {
						if (!downloader.isAutoConcurrentLocked()) continue;
						downloader.slotManager.ensureExecutorLocked();
						downloader.slotManager.submitReadyHeldTasksLocked();
					}
				}
			}
		});
	}
	
	static AutoConcurrencyController autoConcurrencyController() {
		return AUTO_CONCURRENCY_CONTROLLER;
	}
	
	static boolean isLowRamDevice() {
		Context context = appContextOrNull();
		if (context == null) return false;
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		return am != null && am.isLowRamDevice();
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
				
				synchronized (GLOBAL_LOCK) {
					INSTANCES.remove(SimpleDownloader.this);
					if (sDefault == SimpleDownloader.this) sDefault = null;
					releaseDatabaseLocked(databaseToRelease);
					AUTO_CONCURRENCY_CONTROLLER.onInstanceRemovedLocked();
					
					if (sDefault == null) {
						for (SimpleDownloader downloader : INSTANCES) {
							if (downloader != null && !downloader.mShutdown && DEFAULT_OWNER_ID.equals(downloader.mOwnerId)) {
								sDefault = downloader;
								break;
							}
						}
					}
				}
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
	public void remove(Status status) { for (DownloadTask task : taskManager.snapshot()) if (task != null && task.status == status) task.remove(); }
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
		
		/**
        * Deletes the persisted database data for a specific task.
        *
        * <p>This does not delete the downloaded file or remove the task
        * from an active SimpleDownloader instance.</p>
        *
        * @param taskId the task ID
        */		
		public void deleteForTask(long taskId) {
			TaskDatabase database = SHARED_DATABASE;
			if (database != null) database.deleteForTask(taskId);
		}
		
		/**
        * Deletes all persisted database data belonging to the specified owner.
        *
        * <p>This does not delete downloaded files or remove tasks from active
        * SimpleDownloader instances.</p>
        *
        * @param ownerId the owner ID
        */		
		public void deleteForOwner(String ownerId) {
			TaskDatabase database = SHARED_DATABASE;
			if (database != null) database.deleteForOwner(ownerId);
		}
		
		/**
        * Deletes all persisted database data belonging to the default owner.
        *
        * <p>This does not delete downloaded files or remove tasks from active
        * SimpleDownloader instances.</p>
        */		
		public void deleteForDefaultOwner() {
			TaskDatabase database = SHARED_DATABASE;
			if (database != null) database.deleteForDefaultOwner();
		}
		
		/**
        * Deletes all persisted task data from the database.
        *
        * <p>This does not delete downloaded files or remove tasks from active
        * SimpleDownloader instances.</p>
        */		
		public void deleteForAll() {
			TaskDatabase database = SHARED_DATABASE;
			if (database != null) database.deleteForAll();
		}
		
		/**
        * Drops and recreates the tasks table and its indexes.
        *
        * <p>This removes all persisted task data. It does not delete downloaded
        * files or remove tasks from active SimpleDownloader instances.</p>
        *
        * <p>Do not use this while downloads are running unless you intentionally
        * want to discard their persisted database state.</p>
        */		
		public void resetTasksTable() {
			TaskDatabase database = SHARED_DATABASE;
			if (database != null) database.resetTasksTable();
		}
	}
}
