package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import static com.jeet.simpledownloader.SimpleDownloader.networkManager;
import android.util.Log;

final class DownloadWorker {
	private final DownloadTask task;
	private final SpeedHelper speedHelper = new SpeedHelper();
	private final EtaHelper etaHelper = new EtaHelper();
	private boolean stopHandled = false;
	
	DownloadWorker(DownloadTask t) {
		this.task = t;
	}
	
	void run() {
		task.mPauseRequested = false;
		task.mCancelRequested = false;
		task.mRequeueRequested = false;
        task.mLastError = null;
		networkManager.register(SimpleDownloader.sAppContext);
		if (!task.mLifecycleStarted && !task.mLifecycleEnded) ListenerDispatcher.onLifecycleChanged(task, DownloadTask.LIFECYCLE_STARTED);
		
		if (shouldWaitForWifi()) {
			networkManager.moveToWaitingForNetwork(task);
			return;
		}
        
		ListenerDispatcher.onStart(task);
		executeWithRetry(task.mBytesDownloaded);
	}
	
	private void executeWithRetry(long resumeFrom) {
		int attempt = 0;
		while (true) {
			try {
				doDownload(resumeFrom);
				return;
				
			} catch (RefreshRequestException refresh) {
				resumeFrom = refresh.downloadedTotal;
				task.mBytesDownloaded = resumeFrom;
				continue;
				
			} catch (Exception error) {
				
				if (shouldStop()) {
					try {
						if (!stopHandled) executeStopRequest(task.mBytesDownloaded);
					} catch (RefreshRequestException refresh) {
						resumeFrom = refresh.downloadedTotal;
						continue;
					} catch (Exception ignored) {}
					return;
					
				} else if (stopHandled) return;
				
				long delay = task.mRetryPolicy.getDelayMs(attempt + 1);
				if (delay < 0) delay = 100;
				try {
					Thread.sleep(delay);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
				
				if (!networkManager.isNetworkAvailable()) {
					networkManager.moveToWaitingForNetwork(task);
					return;
				}
				
				Exception mapped = mapException(error);
				if (!task.mRetryPolicy.shouldRetry(mapped) || attempt >= task.mRetryPolicy.getMaxRetries()) {
					task.mLastError = mapped;
                    task.setStatus(Status.FAILED);
					ListenerDispatcher.onError(task, mapped);
					SlotHandler.finishTask(task, true, true);
					return;
				}
				
				attempt++;
				resumeFrom = task.mBytesDownloaded;
				task.setStatus(Status.RETRYING);
				ListenerDispatcher.onRetrying(task, attempt);
			}
		}
	}
	
	private void executeStopRequest(long downloadedTotal) throws Exception {
		if (stopHandled) return;
		stopHandled = true;
		boolean shouldSync = true;
		task.mBytesDownloaded = downloadedTotal;
		task.mSpeed = 0;
		task.mEta = -1;
		speedHelper.reset(downloadedTotal);
		etaHelper.reset();
		
		if (task.mPauseRequested) {
			task.setStatus(Status.PAUSED);
			ListenerDispatcher.onPaused(task);
			task.mPauseRequested = false;
			SlotHandler.finishTask(task, false, false);
			
		} else if (task.mRemoveRequested) {
			synchronized (SimpleDownloader.class) {
				SlotHandler.removeQueuedTask(task);
				networkManager.getWaitingForPreferredNetwork().remove(task);
				SimpleDownloader.sRegistry.remove(task.mId);
				SimpleDownloader.removeTaskFromListLocked(task);
				if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(task.mId);
				if (task.mDeleteOnRemoval && task.mOutputFile != null) task.mOutputFile.delete();
			}
			
			ListenerDispatcher.onActiveChanged(task, false);
			ListenerDispatcher.onRemoved(task);
			shouldSync = false;
			task.mRemoveRequested = false;
			SlotHandler.finishTask(task, false, true);
			
		} else if (task.mCancelRequested) {
			task.setStatus(Status.CANCELLED);
			if (task.mOutputFile != null) task.mOutputFile.delete();
			synchronized (SimpleDownloader.class) {
				if (SimpleDownloader.sEnableHistory == null || !SimpleDownloader.sEnableHistory) {
					SimpleDownloader.sRegistry.remove(task.mId);
					SimpleDownloader.removeTaskFromListLocked(task);
					if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(task.mId);
				}
			}
			
			ListenerDispatcher.onCancelled(task);
			shouldSync = false;
			task.mCancelRequested = false;
			SlotHandler.finishTask(task, true, true);
			
		} else if (task.mNetworkPaused) {
			task.mNetworkPaused = false;
			networkManager.moveToWaitingForNetwork(task);
			
		} else if (task.mRefreshRequested) {
			task.mRefreshRequested = false;
			syncNow(downloadedTotal, task.mTotalBytes);
			throw new RefreshRequestException(downloadedTotal);
			
		} else if (task.mRequeueRequested) {
			task.mRequeueRequested = false;
			SlotHandler.finishTask(task, false, true);
			SlotHandler.enqueueOrSubmit(task, false);
		}
		
		if (shouldSync) syncNow(downloadedTotal, task.mTotalBytes);
	}
	
	private void doDownload(long resumeFrom) throws Exception {
		speedHelper.reset(resumeFrom);
		etaHelper.reset();
		stopHandled = false;
		
		task.setStatus(Status.CONNECTING);
		OutputResolver.OutputState output = OutputResolver.resolve(task);
		long existingFileSize = output.length;
		
		if (shouldStop()) {
			executeStopRequest(existingFileSize);
			return;
		}
		
		HttpEngine.HttpConnection connection = null;
		OutputStream out = null;
		long finalTotal = -1;
		
		try {
			connection = HttpEngine.open(task, existingFileSize);
			long resumeBase = Math.max(0, connection.resumeBase);
			long totalBytes = connection.totalBytes;
			if (connection.alreadyComplete) {
				executeComplete(resumeBase, totalBytes);
				return;
			}
			
			task.mTotalBytes = totalBytes;
			syncNow(resumeBase, totalBytes);
			finalTotal = totalBytes;
			out = OutputResolver.openOutput(task.mContext, task.mOutputFile, !connection.restartFromZero && resumeBase > 0);
			if (shouldStop()) {
				executeStopRequest(resumeBase);
				return;
			}
			
			task.setStatus(Status.DOWNLOADING);
			byte[] buffer = new byte[task.mBufferSize];
			long total = resumeBase;
			long lastBytesReported = -1;
			long lastUpdateTime = 0;
			int len;
			
			while ((len = connection.input.read(buffer)) != -1) {
				if (shouldStop()) {
					executeStopRequest(Math.max(0, total));
					return;
				}
				
				out.write(buffer, 0, len);
				total += len;
				task.mBytesDownloaded = total;
				syncIfRequired(total, totalBytes);
				long now = System.currentTimeMillis();
				long speed = speedHelper.update(total);
				task.mSpeed = speed;
				
				if (total != lastBytesReported && (task.mProgressInterval == 0 || now - lastUpdateTime >= task.mProgressInterval)) {
					lastUpdateTime = now;
					lastBytesReported = total;
					task.mProgress = totalBytes > 0 ? (int) Math.min(100, (total * 100) / totalBytes) : 0;
					task.mEta = etaHelper.update(speed, total, totalBytes);
					ListenerDispatcher.onProgress(task);
				}
			}
			
			out.flush();
			if (totalBytes > 0 && total < totalBytes) throw DownloadException.emptyResponse("Download ended before expected size. Expected " + totalBytes + ", got " + total + ".");
			executeComplete(total, finalTotal);
			
		} finally {
			closeQuietly(out);
			if (connection != null) connection.close();
			task.mCurrentCall = null;
		}
	}
	
	private void executeComplete(long total, long totalBytes) {
		task.mBytesDownloaded = total;
		task.mTotalBytes = totalBytes;
		task.mProgress = 100;
		task.mEta = 0;
		task.mSpeed = 0;
		syncNow(total, totalBytes);
		ListenerDispatcher.onProgress(task);
		task.setStatus(Status.COMPLETED);
		ListenerDispatcher.onComplete(task);
		SlotHandler.finishTask(task, true, true);
	}
	
	boolean shouldStop() {
		return task.mPauseRequested || task.mCancelRequested || task.mRemoveRequested || task.mNetworkPaused || task.mRefreshRequested || task.mRequeueRequested;
	}
	
	private boolean shouldWaitForWifi() {
		return task.mWifiOnly && networkManager.isNetworkAvailable() && networkManager.getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI;
	}
	
	private void syncIfRequired(long bytesDownloaded, long totalBytes) {
		long now = System.currentTimeMillis();
		if (now - task.mLastSyncTime < 2000 && bytesDownloaded - task.mLastSyncBytes < 65536) return;
		syncNow(bytesDownloaded, totalBytes);
	}
	
	private void syncNow(long bytesDownloaded, long totalBytes) {
		task.mLastSyncTime = System.currentTimeMillis();
		task.mLastSyncBytes = bytesDownloaded;
		if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.updateResumeData(task.mId, bytesDownloaded, totalBytes, task.mETag, task.mLastModified);
	}
	
	private void closeQuietly(OutputStream out) {
		if (out == null) return;
		try {
			out.flush();
		} catch (Throwable ignored) {}
		try {
			out.close();
		} catch (Throwable ignored) {}
	}
	
	private Exception mapException(Exception error) {
		if (error instanceof DownloadException) return error;
		String message = error.getMessage();
		if (message != null && (message.contains("ENOSPC") || message.contains("No space left on device"))) return DownloadException.enospc(error);
		if (error instanceof SocketTimeoutException) return DownloadException.timeout(error);
		if (error instanceof UnknownHostException) return DownloadException.dnsError(error);
		if (error instanceof SSLException) return DownloadException.sslError(error);
		if (error instanceof IOException && !networkManager.isNetworkAvailable()) return DownloadException.networkLost(error);
		return new DownloadException(DownloadException.Type.UNKNOWN, message == null || message.length() == 0 ? "Unknown download error." : message, -1, true, error);
	}    
	
	static class SpeedHelper {
		private static final double ALPHA = 0.30;
		private static final long MIN_INTERVAL = 1000;
		private long speedTimer = 0;
		private long speedBase = 0;
		private double emaSpeed = 0;
		private boolean initialized = false;
		
		synchronized long update(long totalBytesDownloaded) {
			long now = System.currentTimeMillis();
			if (speedTimer <= 0) {
				speedTimer = now;
				speedBase = totalBytesDownloaded;
				emaSpeed = 0;
				initialized = false;
				return 0;
			}
			
			long elapsed = now - speedTimer;
			if (elapsed < MIN_INTERVAL) return (long) emaSpeed;
			long downloaded = Math.max(0, totalBytesDownloaded - speedBase);
			long instantSpeed = (downloaded * 1000L) / elapsed;
			
			if (!initialized) {
				emaSpeed = instantSpeed;
				initialized = true;
			} else emaSpeed = (ALPHA * instantSpeed) + ((1 - ALPHA) * emaSpeed);
			
			speedTimer = now;
			speedBase = totalBytesDownloaded;
			return (long) emaSpeed;
		}
		
		synchronized void reset(long currentBytes) {
			speedTimer = System.currentTimeMillis();
			speedBase = currentBytes;
			emaSpeed = 0;
			initialized = false;
		}
	}
	
	static class EtaHelper {
		private static final double ALPHA = 0.2;
		private static final int MIN_SAMPLES = 3;
		private static final long STALL_TIMEOUT = 3000;
		private double emaSpeed = 0;
		private int sampleCount = 0;
		private long lastProgress = 0;
		private boolean initialized = false;
		
		synchronized long update(long speed, long bytesDownloaded, long totalBytes) {
			if (totalBytes <= 0 || speed < 0) return -1;
			if (bytesDownloaded >= totalBytes) return 0;
			
			if (speed > 0) {
				lastProgress = System.currentTimeMillis();
				if (!initialized) {
					emaSpeed = speed;
					initialized = true;
					
				} else emaSpeed = ALPHA * speed + (1 - ALPHA) * emaSpeed;
				sampleCount++;
				
			} else if (lastProgress > 0 && System.currentTimeMillis() - lastProgress > STALL_TIMEOUT) return -2;
			
			if (sampleCount < MIN_SAMPLES || emaSpeed <= 0) return -1;
			long remaining = totalBytes - bytesDownloaded;
			return (long) Math.ceil((remaining / emaSpeed) * 1000.0);
		}
		
		synchronized void reset() {
			emaSpeed = 0;
			sampleCount = 0;
			lastProgress = 0;
			initialized = false;
		}
	}
}
