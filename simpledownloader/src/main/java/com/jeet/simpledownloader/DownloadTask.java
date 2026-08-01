package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.util.Collections;
import okhttp3.Call;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

import com.jeet.simpledownloader.thumbnail.ThumbRequest;
import com.jeet.simpledownloader.thumbnail.ThumbLoader;

/**
* {@code DownloadTask} is a live task object and provides access to it's state, output,
* configuration, listeners, controls, etc.
*
* <p>Task values may continue changing while the download is running.</p>
*/
public class DownloadTask {
	public static final int LIFECYCLE_ENDED = 0;
	public static final int LIFECYCLE_STARTED = 1;
	
	final SimpleDownloader mDownloader;
	final long mId;
	final Context mContext;
	final Uri mTreeUri;
	final String mOutputFolderPath;
	final String mOverwritePath;
	String mOutputPath;
	File mOutputFile;
	final Uri mOverwriteUri;
	volatile String mFileName;
	volatile String mMimeType;
	volatile FileName mFileNameMode;
	volatile MimeType mMimeTypeMode;
	DocumentFile mOutputDocFile;
	volatile Uri mOutputUri;
	volatile String mOutputName;
	final String mFileUrl;
	final String mUserAgent;
	final Map<String,String> mHeaders;
	final String mCookies;
	final String mChecksumAlgorithm;
	final String mChecksumValue;
	final int mBufferSize;
	int mMaxRetryCount;
	volatile Priority mPriority;
	volatile boolean mWifiOnly;
	volatile long mCreatedAt = System.currentTimeMillis();
	volatile Status status = Status.STARTING;
	volatile long mBytesDownloaded = 0;
	volatile long mTotalBytes = -1;
	volatile long mSpeed = 0;
	volatile int mProgress = 0;
	volatile long mEta = -1;
	volatile String mETag;
	volatile String mLastModified;
	volatile Exception mLastError;
	final List<DownloadListener> mListeners = new CopyOnWriteArrayList<>();
	volatile boolean mLockedInQueue = false;
	volatile boolean mDeleteOnRemoval = false;
	volatile boolean mLifecycleStarted = false;
	volatile boolean mLifecycleEnded = false;
	volatile boolean mIgnoredRange = false;
	volatile boolean mPauseRequested = false;
	volatile boolean mRemoveRequested = false;
	volatile boolean mCancelRequested = false;
	volatile boolean mRefreshRequested = false;
	volatile boolean mRequeueRequested = false;
    volatile boolean mNetworkPaused = false;
    volatile boolean mForceDownload = false;
	volatile boolean mManualRetryPending = false;
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
	private volatile Future<?> mFuture;
	volatile boolean mWorkerRunning = false;
	volatile Call mCurrentCall;
	volatile ThumbRequest mThumbRequest;
	long mLastSyncTime = 0;
	long mLastSyncBytes = 0;
	long mLastSpeedForAutoConcurrency;
	final DownloadNotification mNotification;
	volatile boolean mNotificationDismissed = false;
	volatile boolean mChecksumFailed = false;
	volatile Call mThumbnailCall;
	volatile boolean mThumbnailUrlAttempted;
	
	DownloadTask(SimpleDownloader downloader, DownloadRequest request) {
		// used for new tasks
		
		mDownloader = downloader;
		mContext = downloader.mContext;
		mNotification = request.notification;
		mTreeUri = request.treeUri;
		mOutputFolderPath = request.outputFolderPath;
		mOverwritePath = request.overwritePath;
		mOverwriteUri = request.overwriteUri;
		mOutputUri = request.overwriteUri;
		mFileName = request.fileName;
		mFileNameMode = request.fileNameMode;
		mMimeTypeMode = request.mimeTypeMode;
		mMimeType = request.mimeType;
		mFileUrl = request.fileUrl;
		mUserAgent = request.userAgent;
		mHeaders = request.headers;
		mCookies = request.cookies;
		mChecksumAlgorithm = request.checksumAlgorithm;
		mChecksumValue = request.checksumValue;
		mId = request.id;
		mBufferSize = request.bufferSize <= 0 ? 16384 : request.bufferSize;
		mPriority = request.priority == null ? Priority.NORMAL : request.priority;
		mWifiOnly = request.wifiOnly;
		mDeleteOnRemoval = request.deleteOnRemoval;
		mLockedInQueue = request.lockedInQueue;
		mMaxRetryCount = downloader.mRetryPolicy.getMaxRetryCount();
		if (request.listener != null) mListeners.add(request.listener);
	}
	
	private DownloadTask(SimpleDownloader downloader, TaskState state) {
		// used for restored tasks
		
		mDownloader = downloader;
		mContext = downloader.mContext;
		mNotification = new DownloadNotification(downloader.mRequestBuilder.notification);
		mTreeUri = parseUri(state.treeUri);
		mOutputFolderPath = state.outputFolderPath;
		mOverwritePath = state.overwritePath;
		mOutputPath = state.outputPath;
		if (mOutputPath != null && mOutputPath.length() > 0) mOutputFile = new File(mOutputPath);
		mOverwriteUri = parseUri(state.overwriteUri);
		mOutputUri = parseUri(state.outputUri);
		if (mOutputUri == null && mOverwriteUri != null) mOutputUri = mOverwriteUri;
		
		if (mOutputPath == null && mOutputUri != null && "content".equals(mOutputUri.getScheme())) {
			mOutputDocFile = DocumentFile.fromSingleUri(mContext, mOutputUri);
		}
		
		mOutputName = state.outputName;
		mFileName = state.fileName;
		mMimeType = state.mimeType;
		mMimeTypeMode = state.mimeTypeMode;
		mFileNameMode = state.fileNameMode;
		mFileUrl = state.fileUrl;
		mUserAgent = state.userAgent;
		
		if (state.headers == null || state.headers.isEmpty()) {
			mHeaders = Collections.emptyMap();
		} else {
			mHeaders = Collections.unmodifiableMap(new HashMap<String, String>(state.headers));
		}
		
		mCookies = state.cookies;
		mChecksumAlgorithm = state.checksumAlgorithm;
		mChecksumValue = state.checksumValue;
		mId = state.id;
		mMaxRetryCount = downloader.mRetryPolicy.getMaxRetryCount();
		mBufferSize = state.bufferSize <= 0 ? 16384 : state.bufferSize;
		mPriority = state.priority == null ? Priority.NORMAL : state.priority;
		mWifiOnly = state.wifiOnly;
		mDeleteOnRemoval = state.deleteOnRemoval;
		mLockedInQueue = state.lockedInQueue;
		if (downloader.mRequestBuilder.listener != null) mListeners.add(downloader.mRequestBuilder.listener);
		mBytesDownloaded = state.bytesDownloaded;
		mTotalBytes = state.totalBytes;
		mProgress = state.progress;
		mCreatedAt = state.createdAt > 0 ? state.createdAt : System.currentTimeMillis();
		mETag = state.eTag;
		mLastModified = state.lastModified;
		status = state.status == null ? Status.PAUSED : state.status;
		mLastSyncBytes = state.bytesDownloaded;
		mLastSyncTime = System.currentTimeMillis();
		mChecksumFailed = state.checksumFailed;
	}
	
	public void pause() {
		if (status == Status.DOWNLOADING || status == Status.CONNECTING || status == Status.RETRYING) {
			mPauseRequested = true;
			cancelRunningCall();
			return;
		}
		
		if (status == Status.QUEUED || status == Status.WAITING_FOR_NETWORK) {
			synchronized (mDownloader.mLock) {
				mDownloader.slotManager.removeQueuedTask(this);
				setStatus(Status.PAUSED);
			}
			
			EventDispatcher.onPaused(this);
			return;
		}
	}
	
	public void resume() {
		if (status != Status.PAUSED) return;
		mNotificationDismissed = false;
		resetStopFlags();
		mDownloader.slotManager.resumeOccupiedTask(this);
	}
	
	public void cancel() {
		if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED) return;
		
		if (status == Status.QUEUED || status == Status.PAUSED || status == Status.WAITING_FOR_NETWORK) {
			mDownloader.slotManager.removeQueuedTask(this);
			setStatus(Status.CANCELLED);
			deleteOutput();
			EventDispatcher.onCancelled(this);
			mDownloader.slotManager.finishTask(this, true, true);
			return;
		}
		
		if (isActive()) {
			mCancelRequested = true;
			cancelRunningCall();
		}
	}
	
	public void remove() {
		if (isActive()) {
			mRemoveRequested = true;
			cancelRunningCall();
			return;
		}
		
		boolean deleted = false;
		if (mDeleteOnRemoval) deleted = deleteOutput();
		
		synchronized (mDownloader.mLock) {
			SimpleDownloader.taskManager.clearAutoSpeedLocked(this);
			mDownloader.slotManager.removeQueuedTask(this);
			mDownloader.networkManager.getWaitingForPreferredNetwork().remove(this);
			mDownloader.taskManager.removeTaskCompletelyLocked(this);
			if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.removeTask(mId);
		}
		
		EventDispatcher.onRemoved(this, deleted);
		mDownloader.slotManager.finishTask(this, false, true);
	}
	
	public void retry() {
		if (status != Status.FAILED) return;
		cancelThumbnailRequest();
		mThumbnailUrlAttempted = false;
		
		synchronized (mDownloader.mLock) {
			mNotificationDismissed = false;
			mSpeed = 0;
			mEta = -1;
			mLastError = null;
			resetStopFlags();
			mManualRetryPending = true;
			mDownloader.slotManager.enqueueOrSubmitLocked(this, false);
		}
	}
	
	public void requeue() {
		if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED || status == Status.QUEUED) return;
		if (isActive()) {
			mRequeueRequested = true;
			cancelRunningCall();
			return;
		}
		
		if (status == Status.PAUSED || status == Status.WAITING_FOR_NETWORK) {
			synchronized (mDownloader.mLock) {
				mDownloader.networkManager.getWaitingForPreferredNetwork().remove(this);
				mDownloader.slotManager.finishTask(this, false, true);
				mDownloader.slotManager.enqueueOrSubmitLocked(this, false);
			}
		}
	}
	
	public void setPriority(Priority priority) {
		mPriority = priority == null ? Priority.NORMAL : priority;
		mDownloader.slotManager.reorderQueuedTask(this);
		mDownloader.taskManager.sortTasks();
		if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updatePriority(mId, mPriority);
	}
	
	public DownloadTask setWifiOnly(boolean enable) {
		mWifiOnly = enable;
		if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateWifiOnly(mId, enable);
		
		if (!enable && status == Status.WAITING_FOR_NETWORK) {
			synchronized (mDownloader.mLock) {
				mDownloader.networkManager.getWaitingForPreferredNetwork().remove(this);
				resetStopFlags();
				mDownloader.slotManager.enqueueOrSubmitLocked(this, false);
			}
			return this;
		}
		
		if (enable && isActive() && mDownloader.networkManager.isNetworkAvailable() && mDownloader.networkManager.getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI) {
			mNetworkPaused = true;
			cancelRunningCall();
		}
		return this;
	}
	
	public DownloadTask setDeleteOnRemoval(boolean enable) {
		mDeleteOnRemoval = enable;
		if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateDeleteOnRemoval(mId, enable);
		return this;
	}
	
	public DownloadTask setLockedInQueue(boolean enable) {
		if (mLockedInQueue == enable) return this;
		mLockedInQueue = enable;
		mDownloader.taskManager.sortTasks();
		if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateLockedInQueue(mId, enable);
		if (status == Status.QUEUED) mDownloader.slotManager.onLockedStateChanged(this);
		return this;
	}
	
	public void forceDownload() {
		if (!isQueued()) return;
		mForceDownload = true;
		resetStopFlags();
		mDownloader.slotManager.submitTask(this, true);
	}
	
	public DownloadTask addListener(DownloadListener listener) {
		if (listener != null && !mListeners.contains(listener)) mListeners.add(listener);
		return this;
	}
	
	public DownloadTask removeListener(DownloadListener listener) {
		if (listener != null) mListeners.remove(listener);
		return this;
	}
	
	public void releaseCallbacks() {
		mListeners.clear();
	}
	
	public long getId() { return mId; }
	public String getFileUrl() { return mFileUrl; }
	public String getFileName() { return mOutputName == null || mOutputName.isEmpty() ? mFileName : mOutputName; }
	public String getMimeType() { return mMimeType; }
	public Uri getOutputUri() { return mOutputUri; }
	public File getOutputFile() { return mOutputFile; }
	public String getOutputFolderPath() { return mOutputFolderPath; }
	public String getOutputPath() { return mOutputPath; }
	public DocumentFile getOutputDocumentFile() { return mOutputDocFile; }
	public long getTotalBytes() { return mTotalBytes; }
	public long getDownloadedBytes() { return mBytesDownloaded; }
	public long getSpeed() { return mSpeed; }
	public int getProgress() { return mProgress; }
	public long getEtaMs() { return mEta; }
	public long getCreatedAt() { return mCreatedAt; }
	public String getUserAgent() { return mUserAgent; }
	public Map<String,String> getHeaders() { return mHeaders; }
	public String getCookies() { return mCookies; }
	public boolean isWifiOnly() { return mWifiOnly; }
	public int getBufferSize() { return mBufferSize; }
	public Priority getPriority() { return mPriority; }
	public Status getStatus() { return status; }
	public Uri getTreeUri() { return mTreeUri; }
	public Uri getOverwriteUri() { return mOverwriteUri; }
	public Exception getError() { return mLastError; }
	public SimpleDownloader getDownloader() { return mDownloader; }
	public int getMaxRetryCount() { return mMaxRetryCount; }
	public boolean canPause() { return isActive() || status == Status.QUEUED || status == Status.WAITING_FOR_NETWORK; }
	public boolean canResume() { return status == Status.PAUSED; }
	public boolean canRetry() { return status == Status.FAILED; }    
	public boolean isWaitingForNetwork() { return status == Status.WAITING_FOR_NETWORK; }
	public boolean isFinished() { return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED; }    
	public boolean isQueued() { return status == Status.QUEUED; }
	public boolean isPaused() { return status == Status.PAUSED; }
	public boolean isActive() { return status == Status.DOWNLOADING || status == Status.CONNECTING || status == Status.RETRYING; }
	public boolean isOccupiedSlot() { return mDownloader.slotManager.isOccupiedSlot(this); }
	public boolean isDeleteOnRemoval() { return mDeleteOnRemoval; }
	public boolean isLockedInQueue() { return mLockedInQueue; }
	
	static DownloadTask restore(SimpleDownloader downloader, TaskState state) {
		return state == null ? null : new DownloadTask(downloader, state);
	}
	
	private static Uri parseUri(String value) {
		return value == null || value.length() == 0 ? null : Uri.parse(value);
	}
	
	DownloadTask clearListeners() {
		mListeners.clear();
		return this;
	}
	
	void setStatus(final Status newStatus) {
		final Status oldStatus = status;
		if (oldStatus == newStatus) return;
		final boolean wasActive = isStartStatus(oldStatus);
		final boolean isNowActive = isStartStatus(newStatus);
		final boolean activeChanged = wasActive != isNowActive;
		status = newStatus;
		
		if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateStatus(mId, newStatus, mBytesDownloaded, mProgress);
		mDownloader.taskManager.sortTasks();
		EventDispatcher.onTaskUpdated(mDownloader.taskManager, this);
		EventDispatcher.onStatusFlow(this, newStatus, activeChanged, isNowActive);
	}
	
	void setStatusRestored(Status newStatus) {
		Status oldStatus = status;
		if (oldStatus == newStatus) return;
		boolean wasActive = isStartStatus(oldStatus);
		boolean isNowActive = isStartStatus(newStatus);
		boolean activeChanged = wasActive != isNowActive;
		status = newStatus;
		EventDispatcher.onStatusFlow(this, newStatus, activeChanged, isNowActive);
	}
	
	private boolean isStartStatus(Status s) {
		return s == Status.CONNECTING || s == Status.DOWNLOADING || s == Status.RETRYING;
	}
	
	void postToMain(Runnable r) {
		if (r != null) MAIN_HANDLER.post(r);
	}
	
	void setFuture(Future<?> future) {
		mFuture = future;
	}
	
	void clearFuture() {
		mFuture = null;
	}
	
	private void cancelFuture() {
		Future<?> future = mFuture;
		if (future != null) future.cancel(true);
	}
	
	void cancelRunningCall() {
		Call call = mCurrentCall;
		
		if (call != null) {
			call.cancel();
		} else if (stopRequested()) {
			cancelFuture();
		}
	}
	
	void stopForShutdown() {
		mPauseRequested = true;
		mCancelRequested = false;
		mRemoveRequested = false;
		mRequeueRequested = false;
		mNetworkPaused = false;
		mRefreshRequested = false;
		
		cancelRunningCall();
		cancelFuture();
		clearFuture();
		
		if (status == Status.CONNECTING || status == Status.DOWNLOADING || status == Status.RETRYING || status == Status.WAITING_FOR_NETWORK) {
			status = Status.PAUSED;
			mSpeed = 0;
			mEta = -1;
			if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateStatus(mId, Status.PAUSED, mBytesDownloaded, mProgress);
			
		} else if (status == Status.QUEUED) {
			if (mDownloader.taskDatabase != null) mDownloader.taskDatabase.updateStatus(mId, Status.QUEUED, mBytesDownloaded, mProgress);
		}
	}
	
	boolean deleteOutput() {
		try {
			
			if (mOutputFile != null) return !mOutputFile.exists() || mOutputFile.delete();
			if (mOutputPath != null && !mOutputPath.isEmpty()) {
				File file = new File(mOutputPath);
				return !file.exists() || file.delete();
			}
			
			if (mOutputDocFile != null) return !mOutputDocFile.exists() || mOutputDocFile.delete();
			return true;
			
		} catch (Exception error) {
			System.err.println("SimpleDownloader: " + error.toString());
			return false;
		}
	}
	
	boolean stopRequested() {
		return mPauseRequested || mCancelRequested || mRemoveRequested || mNetworkPaused || mRefreshRequested || mRequeueRequested;
	}
	
	void resetStopFlags() {
		mPauseRequested = false;
		mCancelRequested = false;
		mNetworkPaused = false;
		mRemoveRequested = false;
		mRefreshRequested = false;
		mRequeueRequested = false;
	}
	
	void clearFinishedRuntimeData() {
		mCurrentCall = null;
		resetStopFlags();
		mIgnoredRange = false;
		mLastSyncTime = 0L;
		mLastSyncBytes = 0L;
		mLastSpeedForAutoConcurrency = 0L;
		
		if (status != Status.FAILED) {
			// Completed and cancelled tasks cannot be retry.
			mETag = null;
			mLastModified = null;
			mChecksumFailed = false;
			mManualRetryPending = false;
		}
	}
	
	void cancelThumbnailRequest() {
		ThumbRequest request;
		Call call;
		
		synchronized (this) {
			request = mThumbRequest;
			call = mThumbnailCall;
			mThumbRequest = null;
			mThumbnailCall = null;
		}
		
		ThumbLoader loader = mDownloader.thumbLoader;
		if (request != null && loader != null) loader.cancel(request);
		
		if (call != null) {
			if (loader != null) {
				loader.cancelUrl(call);
			} else {
				call.cancel();
			}
		}
	}
	
	boolean cannotBeReplaced() {
		Future<?> future = mFuture;
		return isActive() || mWorkerRunning || (future != null && !future.isDone());
	}
	
	String getOverwriteKey() {
		if (mOverwritePath != null && !mOverwritePath.trim().isEmpty()) return "path:" + mOverwritePath;
		if (mOverwriteUri != null) return "uri:" + mOverwriteUri.toString();
		return null;
	}
}
