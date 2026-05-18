package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
	
	public interface Listener {
		default void onStart(long id, DownloadTask task) {}
		default void onQueued(long id, int queuePosition, boolean lockedInQueue, DownloadTask task) {}
		default void onProgress(long id, int progress, long speedPerSec, long etaMs, DownloadTask task) {}
		default void onPaused(long id, DownloadTask task) {}
		default void onResumed(long id, DownloadTask task) {}
		default void onCancelled(long id, DownloadTask task) {}
		default void onComplete(long id, Uri outputFileUri, DownloadTask task) {}
		default void onError(long id, Uri outputFileUri, Exception error, DownloadTask task) {}
		default void onRemoved(long id, boolean deleteOnRemoval, DownloadTask task) {}
		default void onRetrying(long id, int attempt, int maxAttempts, DownloadTask task) {}    
		default void onWaitingForNetwork(long id, int networkType, DownloadTask task) {}
		default void onLoadDatabase(long id, int progress, DownloadTask task) {}
		default void onStatusChanged(long id, Status status, DownloadTask task) {}
		default void onActiveChanged(long id, boolean isActive, DownloadTask task) {}
		default void onLifecycleChanged(long id, int lifecycle, DownloadTask task) {}    
	}
	
	static int sMaxConcurrent = 0;
	static boolean sDownloadOnSlotFree = true;
	static Boolean sEnableHistory = null;
	private static boolean sEnableSorting = true;
	static Context sAppContext;
	static OkHttpClient sHttpClient;
	static final NetworkManager networkManager = new NetworkManager();
	static TaskDatabase sDatabase;
	static final ConcurrentHashMap<Long, DownloadTask> sRegistry = new ConcurrentHashMap<>();
	static final List<DownloadTask> sTaskList = new ArrayList<>();
	static final Map<Integer, List<Long>> sContextTaskMap = new HashMap<>();
	static final AtomicLong sIdGenerator = new AtomicLong(System.currentTimeMillis());
	
	public static SimpleDownloader with(Context context) {
		if (context == null) throw new IllegalArgumentException("Context cannot be null.");
		Context appContext = context.getApplicationContext();
		int ownerKey = System.identityHashCode(context);
		
		synchronized (SimpleDownloader.class) {
			if (sAppContext == null) {
				sAppContext = appContext;
				sDatabase = new TaskDatabase(appContext);
				networkManager.register(appContext);
			}
		}
		
		return new SimpleDownloader(ownerKey, appContext);
	}
	
	public static void releaseCallbacks(Context context) {
		if (context == null) return;
		int key = System.identityHashCode(context);
		
		synchronized (SimpleDownloader.class) {
			List<Long> ids = sContextTaskMap.get(key);
			if (ids != null) {
				for (Long id : ids) {
					DownloadTask t = sRegistry.get(id);
					if (t != null) t.releaseCallbacks();
				}
				sContextTaskMap.remove(key);
			}
			if (sDatabase != null) sDatabase.mActiveListener = null;
		}
		networkManager.release();
	}
	
	public static void forceDownload(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.forceDownload();
	}
	
	public static void pause() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values())) t.pause();
	}
	
	public static void pause(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.pause();
	}
	
	public static void pause(Priority priority) {
		for (DownloadTask t : new ArrayList<>(sRegistry.values()))
		if (t.mPriority == priority) t.pause();
	}
	
	public static void resume(Priority priority) {
		for (DownloadTask t : new ArrayList<>(sRegistry.values()))
		if (t.mPriority == priority) t.resume();
	}
	
	public static void resume() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values())) t.resume();
	}
	
	public static void resume(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.resume();
	}
	
	public static void cancel() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values())) t.cancel();
	}
	
	public static void cancel(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.cancel();
	}
	
	public static void requeue(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.requeue();
	}
	
	public static void requeue() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values()))
		t.requeue();
	}
	
	public static void remove(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.remove();
	}
	
	public static void remove(Status status) {
		for (DownloadTask t : new ArrayList<>(sRegistry.values()))
		if (t.status == status) t.remove();
	}
	
	public static void remove(Priority priority) {
		for (DownloadTask t : new ArrayList<>(sRegistry.values()))
		if (t.mPriority == priority) t.remove();
	}
	
	public static void remove() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values())) t.remove();
	}
	
	public static void retry(long id) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.retry();
	}
	
	public static void retry() {
		for (DownloadTask t : new ArrayList<>(sRegistry.values())) t.retry();
	}
	
	public static void setLockedInQueue(long id, boolean enable) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.setLockedInQueue(enable);
	}
	
	public static void setDeleteOnRemoval(long id, boolean enable) {
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.setDeleteOnRemoval(enable);
	}
	
	public static void wifiOnly(long id, boolean enable){
		DownloadTask t = sRegistry.get(id);
		if (t != null) t.wifiOnly(enable);
	}
	
	public static void setPriority(long id, Priority priority) {
		synchronized (SimpleDownloader.class) {
			DownloadTask t = sRegistry.get(id);
			if (t != null) t.setPriority(priority);
		}
	}
	
	public static DownloadTask getTask(long id) {
		return sRegistry.get(id);
	}
	
	public static ArrayList<DownloadTask> getTasks() {
		synchronized (SimpleDownloader.class) {
			return new ArrayList<>(sTaskList);
		}
	}
	
	public static ArrayList<DownloadTask> getTasks(Status status) {
		ArrayList<DownloadTask> result = new ArrayList<>();
		synchronized (SimpleDownloader.class) {
			for (DownloadTask task : sTaskList) {
				if (task != null && task.getStatus() == status) result.add(task);
			}
		}
		return result;
	}
	
	public static ArrayList<DownloadTask> getTasks(String mimeType) {
		ArrayList<DownloadTask> result = new ArrayList<>();
		synchronized (SimpleDownloader.class) {
			for (DownloadTask task : sTaskList) {
				if (task != null && mimeType != null && mimeType.equals(task.getMimeType())) result.add(task);
			}
		}
		return result;
	}
	
	public static ArrayList<DownloadTask> getTasks(Priority priority) {
		ArrayList<DownloadTask> result = new ArrayList<>();
		synchronized (SimpleDownloader.class) {
			for (DownloadTask task : sTaskList) {
				if (task != null && task.getPriority() == priority) result.add(task);
			}
		}
		return result;
	}
	
	public static int getTotalCount() {
		synchronized (SimpleDownloader.class) { return sRegistry.size(); }
	}
	
	public static int getQueuedCount() {
		return SlotHandler.getQueuedCount();
	}
	
	public static int getOccupiedCount() {
		return SlotHandler.getOccupiedCount();
	}
	
	public static int getActiveCount() {
		int count = 0;
		for (DownloadTask t : sRegistry.values())
		if (t.isActive()) count++;
		return count;
	}
	
	public static boolean isDownloading(long id) {
		DownloadTask t = sRegistry.get(id);
		return t != null && t.getStatus() == Status.DOWNLOADING;
	}
	
	public static boolean isDownloading() {
		for (DownloadTask t : sRegistry.values())
		if (t.getStatus() == Status.DOWNLOADING) return true;
		return false;
	}
	
	public static boolean hasTask(long id) {
		synchronized (SimpleDownloader.class) {
			return sRegistry.containsKey(id);
		}
	}
	
	public static boolean hasTask(String fileUrl) {
		synchronized (SimpleDownloader.class) {
			for (DownloadTask task : sRegistry.values()) {
				if (task != null && fileUrl != null && fileUrl.equals(task.mFileUrl)) return true;
			}
			return false;
		}
	}
	
	public static boolean isNetworkAvailable() {
		return networkManager != null && networkManager.isNetworkAvailable();
	}
	
	public static int getNetworkType() {
		return networkManager != null ? networkManager.getNetworkType() : NETWORK_TYPE_NONE;
	}
	
	static OkHttpClient getHttpClient(int connectTimeout, int readTimeout) {
		synchronized (SimpleDownloader.class) {
			if (sHttpClient == null) {
				sHttpClient = new OkHttpClient.Builder()
				.connectTimeout(60, TimeUnit.SECONDS)
				.readTimeout(60, TimeUnit.SECONDS)
				.build();
			}
		}
		
		if (connectTimeout <= 0 && readTimeout <= 0) return sHttpClient;
		OkHttpClient.Builder builder = sHttpClient.newBuilder();
		if (connectTimeout > 0) builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
		if (readTimeout > 0) builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS);
		return builder.build();
	}
	
	final int mListenerOwnerKey;
	final Context mContext;
	Uri mTreeUri;
	Uri mOverwriteUri;
	String mFileName;
	String mMimeType;
	FileName mFileNameMode = null;
	MimeType mMimeTypeMode = null;
	String mFileUrl;
	String mUserAgent = "Mozilla/5.0";
	final Map<String, String> mHeaders = new HashMap<>();
	String mCookies = null;
	long mId = -1;
	Long mCustomId = null;
	int mRetryCount = 0;
	RetryPolicy mRetryPolicy = null;
	int mConnectTimeout = 0;
	int mReadTimeout = 0;
	int mProgress = 0;
	long mProgressInterval = 300;
	int mBufferSize = 16384;
	Priority mPriority = Priority.NORMAL;
	boolean mWifiOnly = false;
	boolean mDeleteOnRemoval = false;
	Listener mListener;
	DownloadTask mTask;
	boolean mLockedInQueue = false;
	public final TaskDatabase TaskDatabase;
	
	private SimpleDownloader(int listenerOwnerKey, Context appContext) {
		mListenerOwnerKey = listenerOwnerKey;
		mContext = appContext;
		TaskDatabase = sDatabase;
	}
	
	public SimpleDownloader withConfig(Context context) {
		SimpleDownloader instance = SimpleDownloader.with(context);
		
		instance.mTreeUri = mTreeUri;
		instance.mOverwriteUri = mOverwriteUri;
		instance.mFileName = mFileName;
		instance.mMimeType = mMimeType;
		instance.mFileNameMode = mFileNameMode;
		instance.mMimeTypeMode = mMimeTypeMode;
		instance.mFileUrl = mFileUrl;
		instance.mUserAgent = mUserAgent;
		instance.mHeaders.putAll(mHeaders);
		instance.mCookies = mCookies;
		instance.mRetryCount = mRetryCount;
		instance.mRetryPolicy = mRetryPolicy;
		instance.mConnectTimeout = mConnectTimeout;
		instance.mReadTimeout = mReadTimeout;
		instance.mProgressInterval = mProgressInterval;
		instance.mBufferSize = mBufferSize;
		instance.mPriority = mPriority;
		instance.mWifiOnly = mWifiOnly;
		instance.mDeleteOnRemoval = mDeleteOnRemoval;
		instance.mLockedInQueue = mLockedInQueue;
		instance.mListener = mListener;
		
		instance.mId = -1;
		instance.mCustomId = null;
		instance.mTask = null;
		
		return instance;
	}
	
	public SimpleDownloader enableRetryOnNetworkGain(boolean enable) {
		networkManager.setRetryOnNetworkGain(enable);
		return this;
	}
	
	public SimpleDownloader enableHistory(boolean enable) {
		sEnableHistory = enable;
		return this;
	}
	
	public SimpleDownloader enableSorting(boolean enable) {
		synchronized (SimpleDownloader.class) {
			sEnableSorting = enable;
			if (enable) sortTaskListLocked();
		}
		return this;
	}
	
	public SimpleDownloader setMaxConcurrent(int max) {
		if (max < 0) throw new IllegalArgumentException("maxConcurrent cannot be negative.");
		synchronized (SimpleDownloader.class) {
			sMaxConcurrent = max;
			SlotHandler.ensureExecutorLocked();
		}
		return this;
	}
	
	public SimpleDownloader setDownloadOnSlotFree(boolean enable) {
		synchronized (SimpleDownloader.class) {
			sDownloadOnSlotFree = enable;
			if (enable) SlotHandler.submitReadyHeldTasksLocked();
		}
		return this;
	}
	
	public SimpleDownloader setOutput(Uri treeUri, String fileName, String mimeType) {
		if (treeUri == null) throw new IllegalArgumentException("setOutput(Uri, String, String): Uri cannot be null. Use a valid folder Uri from ACTION_OPEN_DOCUMENT_TREE.");
		if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, String, String): String fileName cannot be null or empty. Use a file name such as 'video.mp4'.");
		if (mimeType == null || mimeType.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, String, String): String mimeType cannot be null or empty. Use a MIME type such as 'video/mp4'.");
		
		mTreeUri = treeUri;
		mOverwriteUri = null;
		mFileName = fileName.trim();
		mMimeType = mimeType.trim();
		mFileNameMode = null;
		mMimeTypeMode = null;
		
		return this;
	}
	
	public SimpleDownloader setOutput(Uri treeUri, FileName fileNameMode, MimeType mimeTypeMode) {
		if (treeUri == null) throw new IllegalArgumentException("setOutput(Uri, FileName, MimeType): Uri cannot be null. Use a valid folder Uri from ACTION_OPEN_DOCUMENT_TREE.");
		if (fileNameMode == null) throw new IllegalArgumentException("setOutput(Uri, FileName, MimeType): FileName cannot be null. Use FileName.AUTO or another supported FileName mode.");
		if (mimeTypeMode == null) throw new IllegalArgumentException("setOutput(Uri, FileName, MimeType): MimeType cannot be null. Use MimeType.AUTO or another supported MimeType mode.");
		
		mTreeUri = treeUri;
		mOverwriteUri = null;
		mFileNameMode = fileNameMode;
		mMimeTypeMode = mimeTypeMode;
		mFileName = null;
		mMimeType = null;
		
		return this;
	}
	
	public SimpleDownloader setOutput(Uri treeUri, String fileName, MimeType mimeTypeMode) {
		if (treeUri == null) throw new IllegalArgumentException("setOutput(Uri, String, MimeType): Uri cannot be null. Use a valid folder Uri from ACTION_OPEN_DOCUMENT_TREE.");
		if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, String, MimeType): String fileName cannot be null or empty. Use a file name such as 'video.mp4'.");
		if (mimeTypeMode == null) throw new IllegalArgumentException("setOutput(Uri, String, MimeType): MimeType cannot be null. Use MimeType.AUTO or another supported MimeType mode.");
		
		mTreeUri = treeUri;
		mOverwriteUri = null;
		mFileName = fileName.trim();
		mMimeTypeMode = mimeTypeMode;
		mFileNameMode = null;
		mMimeType = null;
		
		return this;
	}
	
	public SimpleDownloader setOutput(Uri treeUri, FileName fileNameMode, String mimeType) {
		if (treeUri == null) throw new IllegalArgumentException("setOutput(Uri, FileName, String): Uri cannot be null. Use a valid folder Uri from ACTION_OPEN_DOCUMENT_TREE.");
		if (fileNameMode == null) throw new IllegalArgumentException("setOutput(Uri, FileName, String): FileName cannot be null. Use FileName.AUTO or another supported FileName mode.");
		if (mimeType == null || mimeType.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, FileName, String): String mimeType cannot be null or empty. Use a MIME type such as 'video/mp4'.");
		
		mTreeUri = treeUri;
		mOverwriteUri = null;
		mFileNameMode = fileNameMode;
		mMimeType = mimeType.trim();
		mFileName = null;
		mMimeTypeMode = null;
		
		return this;
	}
	
	public SimpleDownloader overwrite(Uri fileUri) {
		if (fileUri == null) throw new IllegalArgumentException("overwrite(Uri): Uri fileUri cannot be null. use a valid document URI for the file.");
		
		mOverwriteUri = fileUri;
		mTreeUri = null;
		mFileName = null;
		mMimeType = null;
		mFileNameMode = null;
		mMimeTypeMode = null;
		
		return this;
	}
	
	public SimpleDownloader setFileUrl(String fileUrl) {
		if (fileUrl == null || fileUrl.isEmpty()) throw new IllegalArgumentException("fileUrl cannot be null or empty. use valid file URL to start download.");
		mFileUrl = fileUrl;
		return this;
	}
	
	public SimpleDownloader setUserAgent(String userAgent) {
		mUserAgent = userAgent;
		return this;
	}
	
	public SimpleDownloader setHeader(String key, String value) {
		mHeaders.put(key, value);
		return this;
	}
	
	public SimpleDownloader setHeaders(Map<String, String> headers) {
		if (headers != null) mHeaders.putAll(headers);
		return this;
	}
	
	public SimpleDownloader setCookies(String cookies) {
		mCookies = cookies;
		return this;
	}
	
	public SimpleDownloader setId(long id) {
		mCustomId = id;
		return this;
	}
	
	public SimpleDownloader setRetryCount(int count) {
		mRetryCount = Math.max(0, count);
		mRetryPolicy = RetryPolicy.ofAttempts(mRetryCount);
		return this;
	}
	
	public SimpleDownloader setRetryPolicy(RetryPolicy retryPolicy) {
		mRetryPolicy = retryPolicy;
		if (retryPolicy != null) mRetryCount = retryPolicy.getMaxRetries();
		return this;
	}
	
	public SimpleDownloader setConnectTimeout(int ms) {
		mConnectTimeout = ms;
		return this;
	}
	
	public SimpleDownloader setReadTimeout(int ms) {
		mReadTimeout = ms;
		return this;
	}
	
	public SimpleDownloader setProgressInterval(long ms) {
		mProgressInterval = ms;
		return this;
	}
	
	public SimpleDownloader setBufferSize(int bytes) {
		mBufferSize = bytes;
		return this;
	}
	
	public SimpleDownloader setPriority(Priority priority) {
		mPriority = priority;
		return this;
	}
	
	public SimpleDownloader wifiOnly(boolean wifiOnly) {
		mWifiOnly = wifiOnly;
		return this;
	}
	
	public SimpleDownloader setLockedInQueue(boolean enable) {
		mLockedInQueue = enable;
		return this;
	}
	
	public SimpleDownloader setDeleteOnRemoval(boolean enable) {
		mDeleteOnRemoval = enable;
		return this;
	}
	
	public SimpleDownloader addListener(Listener listener) {
		mListener = listener;
		if (TaskDatabase != null) TaskDatabase.mActiveListener = listener;
		return this;
	}
	
	public DownloadTask startDownload() {
		if (mTreeUri == null && mOverwriteUri == null) throw new IllegalStateException("Call setOutput(...) or overwrite(...) before starting a download.");
		if (mFileUrl == null) throw new IllegalStateException("Call setFileUrl(...) before starting a download.");
		if (mFileNameMode != null || mMimeTypeMode != null) resolveFileNameAndMimeType();
		
		if (mOverwriteUri == null) {
			if (mFileName == null || mFileName.isEmpty()) throw new IllegalStateException("fileName could not be resolved.");
			if (mMimeType == null || mMimeType.isEmpty()) throw new IllegalStateException("mimeType could not be resolved.");
		}
		
		mId = mCustomId != null ? mCustomId : sIdGenerator.incrementAndGet();
		final DownloadTask task = new DownloadTask(
		mContext, mTreeUri, mFileName, mMimeType, mFileUrl, mUserAgent, new HashMap<>(mHeaders),
		mCookies, mId, mRetryCount, mConnectTimeout, mReadTimeout, mOverwriteUri, mProgressInterval, mBufferSize,
		mPriority, mWifiOnly, mDeleteOnRemoval, mLockedInQueue, mListener, mRetryPolicy != null ? mRetryPolicy : RetryPolicy.ofAttempts(mRetryCount));
		
		synchronized (SimpleDownloader.class) {
			List<Long> ids = sContextTaskMap.get(mListenerOwnerKey);
			if (ids == null) {
				ids = new ArrayList<>();
				sContextTaskMap.put(mListenerOwnerKey, ids);
			}
			ids.add(mId);
			
			if (sDatabase != null) {
				String outputUriStr = mOverwriteUri != null ? mOverwriteUri.toString() : null;
				String treeUriStr = mTreeUri != null ? mTreeUri.toString() : null;
				String overwriteStr = mOverwriteUri != null ? mOverwriteUri.toString() : null;
				sDatabase.saveTask(mId, mFileUrl, outputUriStr, treeUriStr, overwriteStr, mFileName, mMimeType, mUserAgent,
				mHeaders, mCookies, mRetryCount, mConnectTimeout, mReadTimeout, mProgressInterval, mBufferSize, mPriority.name(),
				mWifiOnly, Status.QUEUED.name(), mProgress, task.mCreatedAt, 0, mDeleteOnRemoval, mLockedInQueue);
			}
			
			DownloadTask oldTask = sRegistry.get(mId);
			if (oldTask != null && oldTask != task) oldTask.remove();
			sRegistry.put(mId, task);
			addTaskToListLocked(task);
			SlotHandler.enqueueOrSubmitLocked(task, false);
		}
		
		if (mFileNameMode != null || mMimeTypeMode != null) {
			mFileName = null;
			mMimeType = null;
		}
		
		mTask = task;
		return task;
	}
	
	private void resolveFileNameAndMimeType() {
		String ext = MimeTypeMap.getFileExtensionFromUrl(mFileUrl);
		String suffix = (ext != null && !ext.isEmpty()) ? "." + ext : "";
		
		if (mMimeTypeMode == MimeType.AUTO) {
			if (ext != null && !ext.isEmpty()) {
				String resolved = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
				mMimeType = (resolved != null && !resolved.isEmpty()) ? resolved : "application/octet-stream";
			} else mMimeType = "application/octet-stream";
		}
		
		if (mFileNameMode == FileName.AUTO) {
			String segment = Uri.parse(mFileUrl).getLastPathSegment();
			if (segment != null && !segment.isEmpty()){
				mFileName = segment;
			} else {
				java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
				mFileName = sdf.format(new java.util.Date()) + suffix;
			}
			
		} else if (mFileNameMode == FileName.TIME_MILLIS) {
			java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
			mFileName = sdf.format(new java.util.Date()) + suffix;
		}
	}
	
	static boolean isCurrentTaskLocked(DownloadTask task) {
		return task != null && sRegistry.get(task.mId) == task;
	}
	
	static void addTaskToListLocked(DownloadTask task) {
		if (task == null) return;
		if (!sTaskList.contains(task)) sTaskList.add(task);
		sortTaskListLocked();
	}
	
	static void removeTaskFromListLocked(DownloadTask task) {
		if (task == null) return;
		sTaskList.remove(task);
		sortTaskListLocked();
	}
	
	static void sortTaskList() {
		synchronized (SimpleDownloader.class) {
			sortTaskListLocked();
		}
	}
	
	static void sortTaskListLocked() {
		if (!sEnableSorting || sTaskList.size() <= 1) return;
		Collections.sort(sTaskList, new Comparator<DownloadTask>() {
			@Override
			public int compare(DownloadTask a, DownloadTask b) {
				int groupA = getTaskSortGroup(a);
				int groupB = getTaskSortGroup(b);
				if (groupA != groupB) return groupA - groupB;
				int priorityCompare = b.getPriority().getWeight() - a.getPriority().getWeight();
				if (priorityCompare != 0) return priorityCompare;
				return Long.compare(b.getCreatedAt(), a.getCreatedAt());
			}
		});
	}
	
	private static int getTaskSortGroup(DownloadTask task) {
		if (task == null) return 99;
		Status status = task.getStatus();
		if (status == Status.DOWNLOADING || status == Status.CONNECTING || status == Status.RETRYING || status == Status.PAUSED || status == Status.WAITING_FOR_NETWORK) return 1;
		if (status == Status.QUEUED) return 2;
		if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED) return 3;
		return 4;
	}
}
