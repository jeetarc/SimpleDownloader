package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import javax.net.ssl.SSLException;
import android.graphics.Bitmap;
import com.jeet.simpledownloader.thumbnail.ThumbLoader;
import com.jeet.simpledownloader.util.SpeedHelper;
import com.jeet.simpledownloader.util.EtaHelper;
import com.jeet.simpledownloader.util.Logs;

final class DownloadWorker {
	private final DownloadTask task;
	private final SpeedHelper speedHelper = new SpeedHelper();
	private final EtaHelper etaHelper = new EtaHelper();
	private boolean stopHandled = false;
	
	private static final long OUTPUT_CHECK_INTERVAL = 8000;
	private static final long SYNC_INTERVAL_MS = 4000;
	private static final int MAX_REFRESH = 6;
	
	DownloadWorker(DownloadTask t) {
		this.task = t;
	}
	
	void run() {
		task.mLastError = null;
		if (task.mChecksumFailed) {
			try {
				resetChecksumOutputForRetry();
			} catch (Exception error) {
				executeFailed(mapException(error));
				return;
			}
		}
		
		task.mDownloader.networkManager.register(task.mDownloader.mContext);
		if (!task.mLifecycleStarted && !task.mLifecycleEnded) EventDispatcher.onLifecycleChanged(task, DownloadTask.LIFECYCLE_STARTED);
		
		if (!task.mDownloader.networkManager.canRunNow(task)) {
			task.mDownloader.networkManager.moveToWaitingForNetwork(task);
			return;
		}
		
		EventDispatcher.onStart(task);
		executeWithRetry(task.mBytesDownloaded);
	}
	
	private void executeWithRetry(long resumeFrom) {
		RetryPolicy retryPolicy = task.mDownloader.getRetryPolicy();
		int attempt = 0;
		int refreshCount = 0;
		
		while (true) {
			try {
				doDownload(resumeFrom);
				return;
				
			} catch (RefreshRequestException refresh) {
				if (refreshCount >= MAX_REFRESH) {
					executeFailed(new DownloadException(DownloadException.Type.UNKNOWN, "Too many refresh attempts.", -1, false));
					return;
				}
				
				refreshCount++;
				resumeFrom = refresh.downloadedTotal;
				task.mBytesDownloaded = resumeFrom;
				continue;
				
			} catch (Exception error) {
				if (task.stopRequested()) {
					try {
						if (!stopHandled) executeStopRequest(task.mBytesDownloaded);
					} catch (RefreshRequestException refresh) {
						resumeFrom = refresh.downloadedTotal;
						continue;
						
					} catch (Exception ignored) {}
					return;
				} else if (stopHandled) return;
				
				NetworkManager networkManager = task.mDownloader.networkManager;
				if (!networkManager.canRunNow(task)) {
					networkManager.moveToWaitingForNetwork(task);
					return;
				}
				
				Exception mappedError = mapException(error);
				
				if (!retryPolicy.shouldRetry(mappedError) || attempt >= retryPolicy.getMaxRetryCount()) {
					executeFailed(mappedError);
					return;
				}
				
				long delay = Math.max(100L, retryPolicy.getDelayMs(attempt + 1));
				
				try {
					Thread.sleep(delay);
				} catch (InterruptedException interrupted) {
					if (!task.stopRequested()) {
						Thread.currentThread().interrupt();
						return;
					}
				}
				
				if (task.stopRequested()) {
					Thread.interrupted();
					continue;
				}
				
				if (!networkManager.canRunNow(task)) {
					networkManager.moveToWaitingForNetwork(task);
					return;
				}
				
				attempt++;
				resumeFrom = task.mBytesDownloaded;
				task.setStatus(Status.RETRYING);
				EventDispatcher.onRetry(task, attempt);
			}
		}
	}
	
	private void executeStopRequest(long downloadedTotal) throws Exception {
		if (stopHandled) return;
		stopHandled = true;
		SimpleDownloader downloader = task.mDownloader;
		SlotManager slotManager = downloader.slotManager;
		NetworkManager networkManager = downloader.networkManager;
		TaskManager taskManager = downloader.taskManager;
		TaskDatabase taskDatabase = downloader.taskDatabase;
		boolean shouldSync = true;
		task.mBytesDownloaded = downloadedTotal;
		task.mSpeed = 0;
		task.mEta = -1;
		speedHelper.reset(downloadedTotal);
		etaHelper.reset();
		
		if (task.mShutdownRequested) {
			task.mShutdownRequested = false;
			
		} else if (task.mRemoveRequested) {
			boolean deleted = false;
			if (task.mDeleteOnRemoval) deleted = OutputResolver.deleteOutput(task);
			synchronized (downloader.mLock) {
				slotManager.removeQueuedTask(task);
				networkManager.getWaitingForPreferredNetwork().remove(task);
				synchronized (SimpleDownloader.GLOBAL_LOCK) {
					taskManager.removeTaskCompletelyLocked(task);
				}
				taskManager.clearAutoSpeedLocked(task);
				if (taskDatabase != null) taskDatabase.removeTask(task.mId);
			}
			
			EventDispatcher.onActiveChanged(task, false);
			EventDispatcher.onRemoved(task, deleted);
			shouldSync = false;
			task.mRemoveRequested = false;
			slotManager.finishTask(task, false, true);
			
		} else if (task.mCancelRequested) {
			task.setStatus(Status.CANCELLED);
			OutputResolver.deleteOutput(task);
			EventDispatcher.onCancelled(task);
			shouldSync = false;
			task.mCancelRequested = false;
			slotManager.finishTask(task, true, true);
			
		} else if (task.mPauseRequested) {
			task.setStatus(Status.PAUSED);
			EventDispatcher.onPaused(task);
			task.mPauseRequested = false;
			slotManager.finishTask(task, false, false);
			
		} else if (task.mNetworkPaused) {
			task.mNetworkPaused = false;
			networkManager.moveToWaitingForNetwork(task);
			
		} else if (task.mRefreshRequested) {
			task.mRefreshRequested = false;
			syncNow(downloadedTotal, task.mTotalBytes);
			throw new RefreshRequestException(downloadedTotal);
			
		} else if (task.mRequeueRequested) {
			task.mRequeueRequested = false;
			slotManager.finishTask(task, false, true);
			slotManager.enqueueOrSubmit(task, false);
		}
		
		if (shouldSync) syncNow(downloadedTotal, task.mTotalBytes);
	}
	
	private void doDownload(long resumeFrom) throws Exception {
		etaHelper.reset();
		stopHandled = false;
		
		if (task.stopRequested()) {
			executeStopRequest(resumeFrom);
			return;
		}
		
		task.setStatus(Status.CONNECTING);
		OutputResolver.OutputState existingOutput = OutputResolver.resolveExisting(task);
		long existingFileSize = Math.max(0L, existingOutput.length);
		task.mBytesDownloaded = existingFileSize;
		speedHelper.reset(existingFileSize);
		requestThumbnail(existingFileSize, false);
		
		if (task.stopRequested()) {
			executeStopRequest(resumeFrom);
			return;
		}
		
		HttpEngine.HttpConnection connection = null;
		OutputResolver.OutputState output = null;
		OutputStream out = null;
		long finalTotal = -1;
		
		try {
			connection = task.mDownloader.httpEngine.open(task, existingFileSize);
			output = OutputResolver.resolve(task);
			long resumeBase = Math.max(0, connection.resumeBase);
			long totalBytes = connection.totalBytes;
			if (connection.alreadyComplete) {
				executeComplete(resumeBase, totalBytes);
				return;
			}
			
			task.mTotalBytes = totalBytes;
			syncNow(resumeBase, totalBytes);
			finalTotal = totalBytes;
			out = OutputResolver.openOutput(task, !connection.restartFromZero && resumeBase > 0);
			if (task.stopRequested()) {
				executeStopRequest(resumeBase);
				return;
			}
			
			task.setStatus(Status.DOWNLOADING);
			byte[] buffer = new byte[task.mDownloader.getBufferSize()];
			long total = resumeBase;
			long lastProgressBytes = -1L;
			long lastProgressUpdateTime = 0L;
			long lastNotificationUpdateTime = 0L;
			long lastOutputCheckTime = 0L;
			long notificationUpdateInterval = task.mNotification != null ? task.mNotification.notificationUpdateIntervalMs : 1000L;
			int len;
			
			while ((len = connection.input.read(buffer)) != -1) {
				if (task.stopRequested()) {
					executeStopRequest(Math.max(0, total));
					return;
				}
				
				out.write(buffer, 0, len);
				total += len;
				task.mBytesDownloaded = total;
				long now = System.currentTimeMillis();
				boolean progressDue = total != lastProgressBytes && (task.mDownloader.mProgressInterval <= 0L || now - lastProgressUpdateTime >= task.mDownloader.mProgressInterval);
				boolean notificationDue = task.mNotification != null && task.mDownloader.areNotificationsEnabled() && !task.mNotificationDismissed && (notificationUpdateInterval <= 0L || now - lastNotificationUpdateTime >= notificationUpdateInterval);
				
				if (progressDue || notificationDue) {
					task.mProgress = totalBytes > 0L ? (int) Math.min(100L, (total * 100L) / totalBytes) : 0;
					long speed = speedHelper.update(total);
					task.mSpeed = speed;
					task.mEta = etaHelper.update(speed, total, totalBytes);
					
					if (notificationDue) {
						lastNotificationUpdateTime = now;
						requestThumbnail(total, false);
						DownloadService.onTaskProgress(task);
					}
					
					if (progressDue) {
						lastProgressUpdateTime = now;
						lastProgressBytes = total;
						EventDispatcher.onProgress(task);
						recordAutoSpeedSample(speed);
					}
				}
				
				if (now - lastOutputCheckTime >= OUTPUT_CHECK_INTERVAL) {
					lastOutputCheckTime = now;
					if (!OutputResolver.isOutputValid(task)) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output file was deleted, corrupted, or became invalid.", -1, false);
				}
				
				syncAfterInterval(total, totalBytes, now);
			}
			
			out.flush();
			out.close();
			out = null;
			if (connection != null) {
				connection.close();
				connection = null;
			}
			
			task.mCurrentCall = null;
			if (!OutputResolver.isOutputValid(task)) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output file was deleted, corrupted, or became invalid.", -1, false);
			if (totalBytes > 0 && total < totalBytes) throw DownloadException.emptyResponse("Download ended before expected size. Expected " + totalBytes + ", got " + total + ".");
			executeComplete(total, finalTotal);
			
		} finally {
			closeQuietly(out);
			if (connection != null) connection.close();
			task.mCurrentCall = null;
		}
	}
	
	private void executeComplete(long downloaded, long total) throws Exception {
		task.mBytesDownloaded = downloaded;
		task.mTotalBytes = total;
		task.mProgress = 100;
		task.mEta = 0;
		task.mSpeed = 0;
		EventDispatcher.onProgress(task);
		syncNow(downloaded, total);
		verifyChecksum();
		OutputResolver.finishOutput(task);
		requestThumbnail(downloaded, true);
		task.setStatus(Status.COMPLETED);
		EventDispatcher.onComplete(task);
		task.mDownloader.slotManager.finishTask(task, true, true);
	}
	
	private void executeFailed(Exception error) {
		task.mLastError = error;
		Throwable cause = error.getCause();
		if (cause != null) Logs.err("Download Failed for task " + task.mId, error);
		OutputResolver.deleteIfEmpty(task);
		task.setStatus(Status.FAILED);
		EventDispatcher.onError(task, error);
		task.mDownloader.slotManager.finishTask(task, true, true);
	}
	
	private void syncAfterInterval(long bytesDownloaded, long totalBytes, long now) {
		if (bytesDownloaded == task.mLastSyncBytes) return;
		if (now - task.mLastSyncTime >= SYNC_INTERVAL_MS) syncNow(bytesDownloaded, totalBytes);
	}
	
	private void syncNow(long bytesDownloaded, long totalBytes) {
		task.mLastSyncTime = System.currentTimeMillis();
		task.mLastSyncBytes = bytesDownloaded;
		int progress = totalBytes > 0 ? (int) Math.min(100, (bytesDownloaded * 100L) / totalBytes) : 0;
		if (task.mDownloader.taskDatabase != null) task.mDownloader.taskDatabase.updateResumeData(task.mId, bytesDownloaded, totalBytes, progress, task.mETag, task.mLastModified);
	}
	
	private void verifyChecksum() throws Exception {
		if (task.mChecksumAlgorithm == null || task.mChecksumAlgorithm.length() == 0) return;
		if (task.mChecksumValue == null || task.mChecksumValue.length() == 0) return;
		String actual = calculateChecksum(task.mChecksumAlgorithm);
		
		if (!actual.equalsIgnoreCase(task.mChecksumValue)) {
			task.mChecksumFailed = true;
			if (task.mDownloader.taskDatabase != null) task.mDownloader.taskDatabase.updateChecksumFailed(task.mId, true);
			throw new DownloadException(DownloadException.Type.CHECKSUM_FAILED, "Checksum verification failed. Expected " + task.mChecksumValue + ", got " + actual + ".", -1, false);
		}
	}
	
	private String calculateChecksum(String algorithm) throws Exception {
		MessageDigest digest = MessageDigest.getInstance(algorithm);
		InputStream in = null;
		
		try {
			if (task.mOutputFile != null) in = new FileInputStream(task.mOutputFile);
			else if (task.mOutputUri != null) in = task.mContext.getContentResolver().openInputStream(task.mOutputUri);
			
			if (in == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot open output for checksum verification.", -1, false);
			byte[] buffer = new byte[Math.max(4096, task.mDownloader.getBufferSize())];
			int len;
			while ((len = in.read(buffer)) != -1) digest.update(buffer, 0, len);
			return toHex(digest.digest());
			
		} finally {
			if (in != null) try { in.close(); } catch (Throwable ignored) {}
		}
	}
	
	private String toHex(byte[] bytes) {
		char[] hex = new char[bytes.length * 2];
		char[] table = "0123456789abcdef".toCharArray();
		for (int i = 0; i < bytes.length; i++) {
			int v = bytes[i] & 0xFF;
			hex[i * 2] = table[v >>> 4];
			hex[i * 2 + 1] = table[v & 0x0F];
		}
		
		return new String(hex);
	}
	
	private void resetChecksumOutputForRetry() throws Exception {
		boolean overwrite = task.mOverwriteUri != null || (task.mOverwritePath != null && !task.mOverwritePath.isEmpty());
		if (overwrite) OutputResolver.clearOutput(task);
		else if (OutputResolver.isOutputValid(task) && !OutputResolver.deleteOutput(task)) throw new DownloadException(DownloadException.Type.FILE_ERROR, "Cannot delete corrupted output before retry.", -1, false);
		
		task.mChecksumFailed = false;
		if (task.mDownloader.taskDatabase != null) task.mDownloader.taskDatabase.updateChecksumFailed(task.mId, false);
	}
	
	private Exception mapException(Exception error) {
		if (error instanceof DownloadException) return error;
		String message = error.getMessage();
		if (message != null && (message.contains("ENOSPC") || message.contains("No space left on device"))) return DownloadException.enospc(error);
		if (error instanceof SocketTimeoutException) return DownloadException.timeout(error);
		if (error instanceof UnknownHostException) return DownloadException.dnsError(error);
		if (error instanceof SSLException) return DownloadException.sslError(error);
		if (error instanceof IOException && !task.mDownloader.networkManager.isNetworkAvailable()) return DownloadException.networkLost(error);
		return new DownloadException(DownloadException.Type.UNKNOWN, message == null || message.length() == 0 ? "Unknown download error." : message, -1, true, error);
	}
	
	private void requestThumbnail(long availableBytes, boolean completed) {
		if (!completed && availableBytes < 80L * 1024L) return;
		if (!task.mDownloader.areNotificationsEnabled()) return;
		
		DownloadNotification notification = task.mNotification;
		if (notification != null) {
			if (!notification.showThumbnail) return;
			if (notification.thumbnail != null) return;
			if (notification.thumbnailUrl != null) return;
		}
		
		ThumbLoader loader = task.mDownloader.thumbLoader;
		if (loader == null || loader.isShutdown()) return;
		
		if (task.mThumbRequest == null) {
			if (task.mOutputFile == null && task.mOutputUri == null) return;
			
			try {
				task.mThumbRequest = loader.createRequest(task.mId, task.mOutputFile, task.mOutputUri, task.mMimeType, 92, 92, new ThumbLoader.Callback() {
					@Override
					public void onThumbnailReady(long id, Bitmap bitmap) {
						DownloadService.onThumbnailReady(task, bitmap);
					}
					
					@Override
					public void onThumbnailUnavailable(long id) {}
				});
				
			} catch (Exception e) {
				Logs.err("Failed create thumbnail request.", e);
				return;
			}
		}
		
		if (completed) loader.onCompleted(task.mThumbRequest, availableBytes); else loader.onBytesAvailable(task.mThumbRequest, availableBytes);
	}
	
	private void recordAutoSpeedSample(long speed) {
		synchronized (task.mDownloader.mLock) {
			task.mDownloader.taskManager.updateAutoSpeedLocked(task, speed);
			SimpleDownloader.autoConcurrencyController().recordSpeedSampleLocked();
		}
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
}

