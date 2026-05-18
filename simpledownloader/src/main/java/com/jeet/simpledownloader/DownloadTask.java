package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.documentfile.provider.DocumentFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import javax.net.ssl.SSLException;
import okhttp3.Call;
import static com.jeet.simpledownloader.SimpleDownloader.networkManager;

public class DownloadTask {
	public static final int LIFECYCLE_ENDED = 0;
	public static final int LIFECYCLE_STARTED = 1;
	
	final long mId;
	final Context mContext;
	final Uri mTreeUri;
	final Uri mOverwriteUri;
	final String mFileName;
	final String mMimeType;
	DocumentFile mOutputFile;
	volatile Uri mOutputFileUri;
	volatile String mOutputFileName;
	final String mFileUrl;
	final String mUserAgent;
	final Map<String,String> mHeaders;
	final String mCookies;
	final int mMaxRetries;
	final int mConnectTimeout;
	final int mReadTimeout;
	final long mProgressInterval;
	final int mBufferSize;
	final RetryPolicy mRetryPolicy;
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
	final List<SimpleDownloader.Listener> mListeners = new CopyOnWriteArrayList<>();
	volatile boolean mLockedInQueue = false;
	volatile boolean mDeleteOnRemoval = false;
	volatile boolean mLifecycleStarted = false;
	volatile boolean mLifecycleEnded = false;
	volatile boolean mIgnoredRange = false;
	volatile boolean mPauseRequested = false;
	volatile boolean mRemoveRequested = false;
	volatile boolean mCancelRequested = false;
	volatile boolean mNetworkPaused = false;
	volatile boolean mRefreshRequested = false;
	volatile boolean mRequeueRequested = false;
	private final Handler mMainHandler = new Handler(Looper.getMainLooper());
	private volatile Future<?> mFuture;
	volatile Call mCurrentCall;
	long mLastSyncTime = 0;
	long mLastSyncBytes = 0;
	
	DownloadTask(Context context, Uri treeUri, String fileName, String mimeType,
	String fileUrl, String userAgent, Map<String,String> headers, String cookies,
	long id, int maxRetries, int connectTimeout, int readTimeout,
	Uri overwriteUri, long progressInterval, int bufferSize,
	Priority priority, boolean wifiOnly, boolean deleteFileOnRemoval, boolean lockedInQueue, SimpleDownloader.Listener listener) {
		this(context, treeUri, fileName, mimeType, fileUrl, userAgent, headers, cookies, id, maxRetries, connectTimeout, readTimeout, overwriteUri, 
		progressInterval, bufferSize, priority, wifiOnly, deleteFileOnRemoval, lockedInQueue, listener, RetryPolicy.ofAttempts(maxRetries));
	}
	
	DownloadTask(Context context, Uri treeUri, String fileName, String mimeType,
	String fileUrl, String userAgent, Map<String,String> headers, String cookies,
	long id, int maxRetries, int connectTimeout, int readTimeout,
	Uri overwriteUri, long progressInterval, int bufferSize,
	Priority priority, boolean wifiOnly, boolean deleteFileOnRemoval, boolean lockedInQueue, SimpleDownloader.Listener listener, RetryPolicy retryPolicy) {
		mContext = context;
		mTreeUri = treeUri;
		mOverwriteUri = overwriteUri;
		mOutputFileUri = overwriteUri;
		mFileName = fileName;
		mMimeType = mimeType;
		mFileUrl = fileUrl;
		mUserAgent = userAgent;
		mHeaders = headers == null ? Collections.<String,String>emptyMap() : headers;
		mCookies = cookies;
		mId = id;
		mMaxRetries = maxRetries;
		mConnectTimeout = connectTimeout;
		mReadTimeout = readTimeout;
		mProgressInterval = progressInterval;
		mBufferSize = bufferSize <= 0 ? 16384 : bufferSize;
		mPriority = priority == null ? Priority.NORMAL : priority;
		mWifiOnly = wifiOnly;
		mDeleteOnRemoval = deleteFileOnRemoval;
		mLockedInQueue = lockedInQueue;
		mRetryPolicy = retryPolicy == null ? RetryPolicy.ofAttempts(maxRetries) : retryPolicy;
		if (listener != null) mListeners.add(listener);
	}
	
	public DownloadTask addListener(SimpleDownloader.Listener listener) {
		if (listener != null && !mListeners.contains(listener)) mListeners.add(listener);
		return this;
	}
	
	public DownloadTask removeListener(SimpleDownloader.Listener listener) {
		if (listener != null) mListeners.remove(listener);
		return this;
	}
	
	DownloadTask clearListeners() {
		mListeners.clear();
		return this;
	}
	
	public void releaseCallbacks() {
		mListeners.clear();
	}
	
	void setStatus(final Status newStatus) {
		final Status oldStatus = status;
		if (oldStatus == newStatus) return;
		final boolean wasActive = isStartStatus(oldStatus);
		final boolean isNowActive = isStartStatus(newStatus);
		final boolean activeChanged = wasActive != isNowActive;
		status = newStatus;
		if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.updateStatus(mId, newStatus.name(), mBytesDownloaded, mProgress);
		SimpleDownloader.sortTaskList();
		ListenerDispatcher.onStatusFlow(this, newStatus, activeChanged, isNowActive);
	}
	
	private boolean isStartStatus(Status s) {
		return s == Status.CONNECTING || s == Status.DOWNLOADING || s == Status.RETRYING;
	}
	
	void postToMain(Runnable r) {
		mMainHandler.post(r);
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
		if (call != null) call.cancel();
	}
	
	public long getId() { return mId; }
	public String getFileUrl() { return mFileUrl; }
	public String getFileName() { return mOutputFileName == null || mOutputFileName.isEmpty() ? mFileName : mOutputFileName; }
	public String getMimeType() { return mMimeType; }
	public Uri getOutputFileUri() { return mOutputFileUri; }
	public DocumentFile getOutputFile() { return mOutputFile; }
	public long getTotalBytes() { return mTotalBytes; }
	public long getDownloadedBytes() { return mBytesDownloaded; }
	public long getSpeed() { return mSpeed; }
	public int getProgress() { return mProgress; }
	public long getEtaMs() { return mEta; }
	public long getCreatedAt() { return mCreatedAt; }
	public String getUserAgent() { return mUserAgent; }
	public Map<String,String> getHeaders() { return Collections.unmodifiableMap(mHeaders); }
	public String getCookies() { return mCookies; }
	public boolean isWifiOnly() { return mWifiOnly; }
	public int getBufferSize() { return mBufferSize; }
	public long getProgressInterval(){ return mProgressInterval; }
	public int getConnectTimeout() { return mConnectTimeout; }
	public int getReadTimeout() { return mReadTimeout; }
	public int getMaxRetries() { return mMaxRetries; }
	public Priority getPriority() { return mPriority; }
	public Status getStatus() { return status; }
	public Uri getTreeUri() { return mTreeUri; }
	public Uri getOverwriteUri() { return mOverwriteUri; }
	public Exception getError() { return mLastError; }
	public boolean isWaitingForNetwork() { return status == Status.WAITING_FOR_NETWORK; }
	public boolean isFinished() { return status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED; }    
	public boolean isQueued() { return status == Status.QUEUED; }
	public boolean isPaused() { return status == Status.PAUSED; }
	public boolean isActive() { return status == Status.DOWNLOADING || status == Status.CONNECTING || status == Status.RETRYING; }
	public boolean isDeleteOnRemoval() { return mDeleteOnRemoval; }
	public boolean isLockedInQueue() { return mLockedInQueue; }
	
	public void pause() {
		if (status == Status.DOWNLOADING || status == Status.CONNECTING || status == Status.RETRYING) {
			mPauseRequested = true;
			cancelRunningCall();
			return;
		}
		
		if (status == Status.WAITING_FOR_NETWORK) {
			synchronized (SimpleDownloader.class) {
				networkManager.getWaitingForPreferredNetwork().remove(this);
				setStatus(Status.PAUSED);
			}
			
			ListenerDispatcher.onPaused(this);
			SlotHandler.finishTask(this, false, false);
		}
	}
	
	public void resume() {
		if (status != Status.PAUSED) return;
		resetStopFlags();
		SlotHandler.resumeOccupiedTask(this);
		ListenerDispatcher.onResumed(this);
	}
	
	public DownloadTask wifiOnly(boolean enable) {
		mWifiOnly = enable;
		if (SimpleDownloader.sDatabase != null) {
			ContentValues cv = new ContentValues();
			cv.put("wifi_only", enable ? 1 : 0);
			SimpleDownloader.sDatabase.updateTaskData(mId, cv);
		}
		
		if (!enable && status == Status.WAITING_FOR_NETWORK) {
			synchronized (SimpleDownloader.class) {
				networkManager.getWaitingForPreferredNetwork().remove(this);
				resetStopFlags();
				SlotHandler.enqueueOrSubmitLocked(this, false);
			}
			return this;
		}
		
		if (enable && isActive() && networkManager.isNetworkAvailable() && networkManager.getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI) {
			mNetworkPaused = true;
			cancelRunningCall();
		}
		return this;
	}
	
	public void setPriority(Priority priority) {
		mPriority = priority == null ? Priority.NORMAL : priority;
		SlotHandler.reorderQueuedTask(this);
		SimpleDownloader.sortTaskList();
		if (SimpleDownloader.sDatabase != null) {
			ContentValues cv = new ContentValues();
			cv.put("priority", mPriority.name());
			SimpleDownloader.sDatabase.updateTaskData(mId, cv);
		}
	}
	
	public DownloadTask setLockedInQueue(boolean enable) {
		mLockedInQueue = enable;
		SimpleDownloader.sortTaskList();
		if (SimpleDownloader.sDatabase != null) {
			ContentValues cv = new ContentValues();
			cv.put("locked_in_queue", enable ? 1 : 0);
			SimpleDownloader.sDatabase.updateTaskData(mId, cv);
		}
		
		if (status == Status.QUEUED) SlotHandler.onLockedStateChanged(this);
		return this;
	}
	
	public void forceDownload() {
		if (status != Status.QUEUED) return;
		resetStopFlags();
		SlotHandler.submitTask(this, true);
	}
	
	public void cancel() {
		if (status == Status.COMPLETED || status == Status.FAILED || status == Status.CANCELLED) return;
		if (status == Status.QUEUED) {
			synchronized (SimpleDownloader.class) {
				SlotHandler.removeQueuedTask(this);
				if (SimpleDownloader.sEnableHistory == null || !SimpleDownloader.sEnableHistory) {
					SimpleDownloader.sRegistry.remove(mId);
					SimpleDownloader.removeTaskFromListLocked(this);
					if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(mId);
				}
			}
			
			setStatus(Status.CANCELLED);
			ListenerDispatcher.onCancelled(this);
			return;
		}
		
		if (status == Status.PAUSED || status == Status.WAITING_FOR_NETWORK) {
			setStatus(Status.CANCELLED);
			synchronized (SimpleDownloader.class) {
				networkManager.getWaitingForPreferredNetwork().remove(this);
				if (SimpleDownloader.sEnableHistory == null || !SimpleDownloader.sEnableHistory) {
					SimpleDownloader.sRegistry.remove(mId);
					SimpleDownloader.removeTaskFromListLocked(this);
					if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(mId);
				}
			}
			
			if (mOutputFile != null) mOutputFile.delete();
			ListenerDispatcher.onCancelled(this);
			SlotHandler.finishTask(this, false, true);
			return;
		}
		
		if (isActive()) {
			mCancelRequested = true;
			cancelRunningCall();
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
			synchronized (SimpleDownloader.class) {
				networkManager.getWaitingForPreferredNetwork().remove(this);
				SlotHandler.finishTask(this, false, true);
				SlotHandler.enqueueOrSubmitLocked(this, false);
			}
		}
	}
	
	public void remove() {
		if (isActive()) {
			mRemoveRequested = true;
			cancelRunningCall();
			return;
		}
		
		synchronized (SimpleDownloader.class) {
			SlotHandler.removeQueuedTask(this);
			networkManager.getWaitingForPreferredNetwork().remove(this);
			SimpleDownloader.sRegistry.remove(mId);
			SimpleDownloader.removeTaskFromListLocked(this);
			if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(mId);
			if (mDeleteOnRemoval && mOutputFile != null) mOutputFile.delete();
		}
		
		ListenerDispatcher.onRemoved(this);
		SlotHandler.finishTask(this, false, true);
	}
	
	void resumeOccupiedWaiting() {
		synchronized (SimpleDownloader.class) {
			if (status != Status.WAITING_FOR_NETWORK) return;
			resetStopFlags();
			SlotHandler.submitTask(this, true);
		}
	}
	
	public void retry() {
		synchronized (SimpleDownloader.class) {
			if (status != Status.FAILED) return;
			mSpeed = 0;
			mEta = -1;
			mLifecycleStarted = false;
			mLifecycleEnded = false;
            mLastError = null;
			resetStopFlags();
			if (mWifiOnly && networkManager.isNetworkAvailable() && networkManager.getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI) {
				setStatus(Status.WAITING_FOR_NETWORK);
				if (!networkManager.getWaitingForPreferredNetwork().contains(this)) networkManager.getWaitingForPreferredNetwork().add(this);
				SimpleDownloader.sortTaskListLocked();
				ListenerDispatcher.onWaitingForNetwork(this);
				return;
			}
			SlotHandler.enqueueOrSubmitLocked(this, false);
		}
	}
	
	public DownloadTask setDeleteOnRemoval(boolean enable) {
		mDeleteOnRemoval = enable;
		if (SimpleDownloader.sDatabase != null) {
			ContentValues cv = new ContentValues();
			cv.put("delete_on_removal", enable ? 1 : 0);
			SimpleDownloader.sDatabase.updateTaskData(mId, cv);
		}
		return this;
	}
	
	private void resetStopFlags() {
		mPauseRequested = false;
		mCancelRequested = false;
		mNetworkPaused = false;
		mRemoveRequested = false;
		mRefreshRequested = false;
		mRequeueRequested = false;
	}
}
