package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.app.ActivityManager;
import android.net.Uri;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import java.util.Comparator;
import android.os.Looper;
import com.jeet.simpledownloader.thumbnail.ThumbLoader;
import androidx.annotation.Nullable;

/**
* Main entry point for starting, restoring, and managing downloads.
*
* <p>Use {@code with(context)} to create and configure download requests.</p>
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
	
	private static volatile SimpleDownloader sDefault;
	private static volatile Context sAppContext;
	private static final Object S_LOCK = new Object();
	static final int AUTO_MIN_SLOT = 1;
	static final int AUTO_MAX_SLOT = 10;
	private static volatile boolean sShuttingDown = false;
	static final AutoConcurrencyController autoConcurrencyController = new AutoConcurrencyController();
	
	public static SimpleDownloader with(Context context) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		if (sShuttingDown) throw new IllegalStateException("SimpleDownloader is shutting down. Call with(context) again after shutdown completes.");
		initialize(context.getApplicationContext());
		return new SimpleDownloader(context);
	}
	
	public SimpleDownloader setId(long id) {
		mRequestBuilder.putId(id);
		return this;
	}
	
	public SimpleDownloader setOutput(Uri folderUri, String fileName) {
		mRequestBuilder.putOutput(folderUri, fileName);
		return this;
	}
	
	public SimpleDownloader setOutput(Uri folderUri, FileName fileName) {
		mRequestBuilder.putOutput(folderUri, fileName);
		return this;
	}
	
	public SimpleDownloader setOutput(String folderPath, String fileName) {
		mRequestBuilder.putOutput(folderPath, fileName);
		return this;
	}
	
	public SimpleDownloader setOutput(String folderPath, FileName fileName) {
		mRequestBuilder.putOutput(folderPath, fileName);
		return this;
	}
	
	public SimpleDownloader overwrite(Uri fileUri) {
		mRequestBuilder.putOverwrite(fileUri);
		return this;
	}
	
	public SimpleDownloader overwrite(String outputPath) {
		mRequestBuilder.putOverwrite(outputPath);
		return this;
	}
	
	public SimpleDownloader setSubFolder(String subFolder) {
		mRequestBuilder.putSubFolder(subFolder);
		return this;
	}
	
	public SimpleDownloader setFileUrl(String fileUrl) {
		mRequestBuilder.putFileUrl(fileUrl);
		return this;
	}
    
    public SimpleDownloader setMimeType(String mimeType) {
        mRequestBuilder.putMimeType(mimeType);
        return this;
    }
    
    public SimpleDownloader setMimeType(MimeType mimeType) {
        mRequestBuilder.putMimeType(mimeType);
        return this;
    }
	
	public SimpleDownloader setUserAgent(String userAgent) {
		mRequestBuilder.putUserAgent(userAgent);
		return this;
	}
	
	public SimpleDownloader setHeader(String key, String value) {
		mRequestBuilder.putHeader(key, value);
		return this;
	}
	
	public SimpleDownloader setHeaders(Map<String, String> headers) {
		mRequestBuilder.putHeaders(headers);
		return this;
	}
	
	public SimpleDownloader setCookies(String cookies) {
		mRequestBuilder.putCookies(cookies);
		return this;
	}
	
	public SimpleDownloader setChecksum(String algorithm, String checksum) {
		mRequestBuilder.putChecksum(algorithm, checksum);
		return this;
	}
	
	public SimpleDownloader setRetryCount(int count) {
		mRetryPolicy = RetryPolicy.ofAttempts(Math.max(0, count));
		return this;
	}
	
	public SimpleDownloader setRetryPolicy(RetryPolicy retryPolicy) {
		if (retryPolicy == null) throw new IllegalArgumentException("RetryPolicy cannot be null.");
		mRetryPolicy = retryPolicy;
		return this;
	}
	
	public SimpleDownloader setConnectTimeout(int ms) {
		if (ms < 0) throw new IllegalArgumentException("Connect timeout cannot be negative.");
		mConnectTimeout = ms;
		return this;
	}
	
	public SimpleDownloader setReadTimeout(int ms) {
		if (ms < 0) throw new IllegalArgumentException("Read timeout cannot be negative.");
		mReadTimeout = ms;
		return this;
	}
	
	public SimpleDownloader setProgressInterval(long ms) {
		if (ms < 0) throw new IllegalArgumentException("Progress interval cannot be negative.");
		mProgressInterval = ms;
		return this;
	}
	
	public SimpleDownloader setBufferSize(int bytes) {
		mRequestBuilder.putBufferSize(bytes);
		return this;
	}
	
	public SimpleDownloader setPriority(Priority priority) {
		mRequestBuilder.putPriority(priority);
		return this;
	}
	
	public SimpleDownloader setWifiOnly(boolean wifiOnly) {
		mRequestBuilder.putWifiOnly(wifiOnly);
		return this;
	}
	
	public SimpleDownloader setLockedInQueue(boolean enable) {
		mRequestBuilder.putLockedInQueue(enable);
		return this;
	}
	
	public SimpleDownloader setDeleteOnRemoval(boolean enable) {
		mRequestBuilder.putDeleteOnRemoval(enable);
		return this;
	}
	
	public SimpleDownloader enableNotifications(boolean enable) {
		synchronized (mLock) {
			mNotificationsEnabled = enable;
		}
		return this;
	}
	
	public SimpleDownloader enableForeground(boolean enable) {
		synchronized (mLock) {
			mForegroundEnabled = enable;
			if (mForegroundEnabled) mNotificationsEnabled = true;
		}
		return this;
	}
	
	public SimpleDownloader setNotification(DownloadNotification notification) {
		mRequestBuilder.putNotification(notification);
		return this;
	}
	
	public SimpleDownloader setHttpClient(OkHttpClient client) {
		if (client == null) throw new IllegalArgumentException("OkHttpClient cannot be null.");
		
		synchronized (mLock) {
			for (DownloadTask task : taskManager.snapshot()) {
				if (task != null && task.cannotBeReplaced()) {
					throw new IllegalStateException("Cannot change the HTTP client while a download worker is running or scheduled.");
				}
			}
			httpEngine.setClient(client);
		}
		
		return this;
	}
	
	public SimpleDownloader setTaskComparator(Comparator<DownloadTask> comparator) {
		taskManager.setTaskComparator(comparator);
		return this;
	}
	
	public SimpleDownloader enableResumeOnNetworkGain(boolean enable) {
		networkManager.setRetryOnNetworkGain(enable);
		return this;
	}
	
	public SimpleDownloader enableHistory(boolean enable) {
		mEnableHistory = enable;
		return this;
	}
	
	public SimpleDownloader enableSorting(boolean enable) {
		taskManager.setSortingEnabled(enable);
		return this;
	}
	
	public SimpleDownloader setMaxConcurrent(int max) {
		synchronized (mLock) {
			mMaxConcurrent = max;
			if (isAutoConcurrentLocked()) {
				autoConcurrencyController.resetLocked();
				autoConcurrencyController.ensureInitializedLocked();
			} else {
				setEffectiveMaxConcurrentLocked(Math.max(1, max));
			}
			
			slotManager.ensureExecutorLocked();
			slotManager.submitReadyHeldTasksLocked();
		}
		return this;
	}
	
	public SimpleDownloader setDownloadOnSlotFree(boolean enable) {
		synchronized (mLock) {
			mDownloadOnSlotFree = enable;
			if (enable) slotManager.submitReadyHeldTasksLocked();
		}
		return this;
	}
	
	public SimpleDownloader addListener(DownloadListener listener) {
		mRequestBuilder.putListener(listener);
		return this;
	}
	
	public SimpleDownloader addObserver(TaskListObserver observer) {
		if (observer != null) taskManager.addObserver(mListenerOwnerKey, observer);
		return this;
	}
	
	public SimpleDownloader setAutoRestore(boolean enable) {
		synchronized (mLock) {
			mAutoRestore = enable;
			
			if (enable && !sAutoRestoreDone) {
				taskManager.restoreTasks(this, taskDatabase.loadAllTaskStates(), true);
				sAutoRestoreDone = true;
			}
		}
		return this;
	}
	
	public DownloadTask startDownload() {
		final DownloadRequest request;
		final DownloadTask task;
		
		synchronized (mLock) {
			if (sShuttingDown) throw new IllegalStateException("SimpleDownloader is shutting down.");
			validateNotificationConfigLocked();
			long generatedId = mRequestBuilder.hasCustomId() ? -1L : taskManager.nextId();
			request = mRequestBuilder.build(generatedId);
			
			DownloadTask oldTask = taskManager.getTask(request.id);
			if (oldTask != null) {
				if (oldTask.cannotBeReplaced()) {
					throw new IllegalStateException("An active task with ID " + request.id + " already exists and cannot be replaced.");
				}
				
				System.out.println("SimpleDownloader: Replacing existing inactive task with ID: " + request.id);
				oldTask.remove();
			}
			
			String overwriteKey = request.getOverwriteKey();
			if (taskManager.hasBusyOverwriteTarget(overwriteKey, request.id)) {
				throw new IllegalStateException("Output target is already used by another active task: " + overwriteKey);
			}
			
			task = request.createTask(this);
			taskManager.trackListenerOwnerLocked(mListenerOwnerKey, request.id);
			taskManager.putTaskLocked(task);
			taskManager.addTaskLocked(task);
			
			if (taskDatabase != null) taskDatabase.saveTask(task, Status.QUEUED);
			if (isAutoConcurrentLocked()) autoConcurrencyController.ensureInitializedLocked();
			slotManager.enqueueOrSubmitLocked(task, false);
			mRequestBuilder.customId = null;
		}
		
		return task;
	}
	
	public List<DownloadTask> restoreTasks() {
		return taskManager.restoreTasks(this, taskDatabase.loadAllTaskStates());
	}
	
	public <T> List<DownloadTask> restoreTasks(TaskField<T> field, T value) {
		return taskManager.restoreTasks(this, taskDatabase.loadTaskStates(field, value));
	}
	
    @Nullable
	public <T> DownloadTask restoreTask(TaskField<T> field, T value) {
		return taskManager.restoreTask(this, taskDatabase.loadLatestTaskState(field, value));
	}
	
	private static void initialize(Context context) {
		synchronized (S_LOCK) {
			if (sShuttingDown) throw new IllegalStateException("SimpleDownloader is shutting down.");
			if (sDefault != null) return;
			
			sAppContext = context.getApplicationContext();
			sShuttingDown = false;
			SimpleDownloader owner = new SimpleDownloader(sAppContext);
			sDefault = owner;
			
			thumbLoader = new ThumbLoader(sAppContext);
			taskManager = new TaskManager(owner);
			slotManager = new SlotManager(owner);
			networkManager = new NetworkManager(owner);
			httpEngine = new HttpEngine();
			taskDatabase = new TaskDatabase(sAppContext);
			autoConcurrencyController.resetLocked();
			networkManager.register(sAppContext);
		}
	}
	
	private static SimpleDownloader getDefaultDownloader() {
		SimpleDownloader downloader = sDefault;
		if (downloader == null) throw new IllegalStateException("SimpleDownloader is not initialized. Call SimpleDownloader.with(context) first.");
		return downloader;
	}
	
	
	static SimpleDownloader defaultDownloaderOrNull() {
		return sDefault;
	}
	
	static Context appContextOrNull() {
		return sAppContext;
	}
	
	void validateNotificationConfigLocked() {
		if (mForegroundEnabled && !mNotificationsEnabled) throw new IllegalStateException("cannot run foreground without notifications");
	}
	
	public static void releaseCallbacks(Object owner) {
		getDefaultDownloader();
		taskManager.releaseCallbacks(owner);
	}
	
	public static void releaseObserver(TaskListObserver observer) {
		TaskManager manager = taskManager;
		if (manager != null && observer != null) manager.removeObserver(observer);
	}
	
	public static void shutdown() {
		final DownloadExecutor executorToWait;
		
		synchronized (S_LOCK) {
			if (sDefault == null || sShuttingDown) return;
			sShuttingDown = true;
			DownloadService.shutdownServiceIfRunning();
			
			if (taskManager != null) {
				for (DownloadTask task : taskManager.snapshot()) {
					if (task != null) task.stopForShutdown();
				}
			}
			
			if (networkManager != null) networkManager.shutdownLocked();
			executorToWait = slotManager != null ? slotManager.shutdownLocked() : null;
		}
		
		Runnable cleanup = new Runnable() {
			@Override
			public void run() {
				finishShutdown(executorToWait);
			}
		};
		
		if (Looper.myLooper() == Looper.getMainLooper()) new Thread(cleanup, "SimpleDownloader-Shutdown").start();
        else cleanup.run();
	}
	
	private static void finishShutdown(DownloadExecutor executorToWait) {
		boolean fullyStopped = executorToWait == null || executorToWait.awaitTerminationQuietly(15_000L);
		TaskDatabase databaseToClose = null;
		
		synchronized (S_LOCK) {
			if (!fullyStopped) {
				System.err.println("SimpleDownloader: shutdown() timed out while waiting for workers. Keeping database and managers alive to avoid race-condition crashes.");
				sShuttingDown = false;
				return;
			}
			
			if (httpEngine != null) httpEngine.shutdown();
			if (taskManager != null) taskManager.shutdownLocked();
			if (thumbLoader != null) thumbLoader.shutdown();
			databaseToClose = taskDatabase;
			
			taskDatabase = null;
			taskManager = null;
			slotManager = null;
			networkManager = null;
			httpEngine = null;
			thumbLoader = null;
			sDefault = null;
			sAppContext = null;
			mMaxConcurrent = 0;
			sEffectiveMaxConcurrent = AUTO_MIN_SLOT;
			mDownloadOnSlotFree = true;
			mEnableHistory = false;
			mAutoRestore = false;
			sAutoRestoreDone = false;
			autoConcurrencyController.resetLocked();
			sShuttingDown = false;
		}
		
		if (databaseToClose != null) databaseToClose.close();
	}
	
	public static void forceDownload(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.forceDownload();
	}
	
	public static void pauseAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot()) t.pause();
	}
	
	public static void pause(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.pause();
	}
	
	public static void pause(Priority priority) {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot())
		if (t != null && t.mPriority == priority) t.pause();
	}
	
	public static void resumeAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot()) t.resume();
	}
	
	public static void resume(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.resume();
	}
	
	public static void resumeAll(Priority priority) {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot())
		if (t != null && t.mPriority == priority) t.resume();
	}
	
	public static void cancelAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot()) t.cancel();
	}
	
	public static void cancel(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.cancel();
	}
	
	public static void requeueAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot())
		t.requeue();
	}
	
	public static void requeue(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.requeue();
	}
	
	public static void removeAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot()) t.remove();
	}
	
	public static void remove(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.remove();
	}
	
	public static void remove(Status status) {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot())
		if (t != null && t.status == status) t.remove();
	}
	
	public static void remove(Priority priority) {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot())
		if (t != null && t.mPriority == priority) t.remove();
	}
	
	public static void retryAll() {
		getDefaultDownloader();
		for (DownloadTask t : taskManager.snapshot()) t.retry();
	}
	
	public static void retry(long id) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.retry();
	}
	
	public static void setLockedInQueue(long id, boolean enable) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.setLockedInQueue(enable);
	}
	
	public static void setDeleteOnRemoval(long id, boolean enable) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.setDeleteOnRemoval(enable);
	}
	
	public static void setWifiOnly(long id, boolean enable) {
		getDefaultDownloader();
		DownloadTask t = taskManager.getTask(id);
		if (t != null) t.setWifiOnly(enable);
	}
	
	public static void setPriority(long id, Priority priority) {
		getDefaultDownloader();
		synchronized (S_LOCK) {
			DownloadTask t = taskManager.getTask(id);
			if (t != null) t.setPriority(priority);
		}
	}
	
	public static DownloadTask getTask(long id) {
		getDefaultDownloader();
		return taskManager.getTask(id);
	}
	
	public static List<DownloadTask> getTasks() {
		getDefaultDownloader();
		return taskManager.getTasks();
	}
    
    @Nullable
	public static <T> DownloadTask getTask(TaskField<T> field, T value) {
		getDefaultDownloader();
		return taskManager.getTask(field, value);
	}
	
	public static <T> List<DownloadTask> getTasks(TaskField<T> field, T value) {
		getDefaultDownloader();
		return taskManager.getTasks(field, value);
	}
	
	public static int getTotalCount() {
		getDefaultDownloader();
		return taskManager.getTotalCount();
	}
	
	public static int getQueuedCount() {
		getDefaultDownloader();
		return slotManager.getQueuedCount();
	}
	
	public static int getOccupiedCount() {
		getDefaultDownloader();
		return slotManager.getOccupiedCount();
	}
	
	public static int getActiveCount() {
		getDefaultDownloader();
		return taskManager.getActiveCount();
	}
	
	public static int getNetworkType() {
		getDefaultDownloader();
		return networkManager != null ? networkManager.getNetworkType() : NETWORK_TYPE_NONE;
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
	
	public static int getEffectiveMaxConcurrent() {
		getDefaultDownloader();
		synchronized (S_LOCK) {
			return getEffectiveMaxConcurrentLocked();
		}
	}
	
	public static boolean isDownloading(long id) {
		getDefaultDownloader();
		return taskManager.isDownloading(id);
	}
	
	public static boolean isDownloading() {
		getDefaultDownloader();
		return taskManager.isDownloading();
	}
	
	public static boolean hasTask(long id) {
		getDefaultDownloader();
		return taskManager.hasTask(id);
	}
	
	public static boolean hasTask(String fileUrl) {
		getDefaultDownloader();
		return taskManager.hasTask(fileUrl);
	}
	
	public static boolean isNetworkAvailable() {
		getDefaultDownloader();
		return networkManager != null && networkManager.isNetworkAvailable();
	}
	
	public boolean areNotificationsEnabled() {
		return mNotificationsEnabled;
	}
	
	public boolean isForegroundEnabled() {
		return mForegroundEnabled;
	}
	
	static boolean isAutoConcurrentLocked() {
		return mMaxConcurrent <= 0;
	}
	
	static boolean isShuttingDown() {
		return sShuttingDown;
	}
	
	static int getEffectiveMaxConcurrentLocked() {
		if (mMaxConcurrent > 0) return Math.max(1, mMaxConcurrent);
		return Math.max(AUTO_MIN_SLOT, Math.min(AUTO_MAX_SLOT, sEffectiveMaxConcurrent));
	}
	
	static void setEffectiveMaxConcurrentLocked(int value) {
		sEffectiveMaxConcurrent = Math.max(AUTO_MIN_SLOT, Math.min(AUTO_MAX_SLOT, value));
	}
	
	static boolean isLowRamDevice() {
		if (sAppContext == null) return false;
		ActivityManager am = (ActivityManager) sAppContext.getSystemService(Context.ACTIVITY_SERVICE);
		return am != null && am.isLowRamDevice();
	}
	
	final Object mLock = S_LOCK;
	Object mListenerOwnerKey;
	final Context mContext;
	static TaskManager taskManager;
	static SlotManager slotManager;
	static NetworkManager networkManager;
	static HttpEngine httpEngine;
	static TaskDatabase taskDatabase;
	final DownloadRequest.Builder mRequestBuilder = new DownloadRequest.Builder();
	static ThumbLoader thumbLoader;
	static int mMaxConcurrent = 0;
	static int sEffectiveMaxConcurrent = AUTO_MIN_SLOT;
	static boolean mDownloadOnSlotFree = true;
	static boolean mEnableHistory = false;
	private boolean mNotificationsEnabled;
	private boolean mForegroundEnabled;
	volatile int mConnectTimeout;
	volatile int mReadTimeout;
	volatile long mProgressInterval = 300L;
	volatile RetryPolicy mRetryPolicy = RetryPolicy.builder().build();
	static boolean mAutoRestore = false;
	private static boolean sAutoRestoreDone = false;
	
	private SimpleDownloader(Context context) {
		mContext = context.getApplicationContext();
		mListenerOwnerKey = context;
	}
}
