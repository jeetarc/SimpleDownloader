package com.jeet.simpledownloader.thumbnail;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.jeet.simpledownloader.util.Logs;

/**
* Thumbnail loader (used internally).
* <p>It can decode local media on download.
*/
public final class ThumbLoader {
	private static final long ATTEMPT_TIMEOUT_MS = 60_000L;
	private static final int DEFAULT_WIDTH = 256;
	private static final int DEFAULT_HEIGHT = 256;
	
	private final Context context;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private final ExecutorService decoderExecutor;
	private final ScheduledExecutorService timeoutExecutor;
	private final Set<ThumbRequest> requests = Collections.newSetFromMap(new ConcurrentHashMap<ThumbRequest, Boolean>());
	private volatile boolean shutdown;
	
	public interface Callback {
		void onThumbnailReady(long id, Bitmap bitmap);
		void onThumbnailUnavailable(long id);
	}
	
	public ThumbLoader(Context context) {
		if (context == null) throw new NullPointerException("context == null");
		Context applicationContext = context.getApplicationContext();
		this.context = applicationContext != null ? applicationContext : context;
		decoderExecutor = Executors.newSingleThreadExecutor(new BackgroundThreadFactory("SimpleDownloader-ThumbDecoder"));
		timeoutExecutor = Executors.newSingleThreadScheduledExecutor(new BackgroundThreadFactory("SimpleDownloader-ThumbTimeout"));
	}
	
	public ThumbRequest createRequest(long id, File sourceFile, String mimeType, int width, int height, Callback callback) {
		return createRequest(id, sourceFile, null, mimeType, width, height, callback);
	}
	
	public ThumbRequest createRequest(long id, Uri sourceUri, String mimeType, int width, int height, Callback callback) {
		return createRequest(id, null, sourceUri, mimeType, width, height, callback);
	}
	
	public ThumbRequest createRequest(long id, File sourceFile, Uri sourceUri, String mimeType, int width, int height, Callback callback) {
		if (shutdown) throw new IllegalStateException("ThumbLoader is shut down.");
		if (sourceFile == null && sourceUri == null) throw new IllegalArgumentException("A local thumbnail source is required.");
		if (callback == null) throw new NullPointerException("callback == null");
        int safeWidth = width > 0 ? width : DEFAULT_WIDTH;
		int safeHeight = height > 0 ? height : DEFAULT_HEIGHT;
		ThumbRequest request = new ThumbRequest(id, sourceFile, sourceUri, mimeType, safeWidth, safeHeight, callback);
		requests.add(request);
		return request;
	}
	
	/** Called after the output buffer has been flushed at a progress milestone. */
	public void onBytesAvailable(ThumbRequest request, long downloadedBytes) {
		if (request == null || shutdown) return;
		submit(request, request.onBytesAvailable(Math.max(0L, downloadedBytes)));
	}
	
	/** Allows one final extraction attempt for the completed file. */
	public void onCompleted(ThumbRequest request, long downloadedBytes) {
		if (request == null || shutdown) return;
		submit(request, request.onCompleted(Math.max(0L, downloadedBytes)));
	}
	
	public void cancel(ThumbRequest request) {
		if (request == null) return;
		requests.remove(request);
		request.cancel();
	}
	
	public void shutdown() {
		if (shutdown) return;
		shutdown = true;
		
		for (ThumbRequest request : requests) {
			if (request != null) request.cancel();
		}
		
		requests.clear();
		decoderExecutor.shutdownNow();
		timeoutExecutor.shutdownNow();
	}
	
	public boolean isShutdown() {
		return shutdown;
	}
	
	public boolean hasRunningRequests() {
		return !requests.isEmpty();
	}
	
	private void submit(final ThumbRequest request, final ThumbRequest.Attempt attempt) {
		if (attempt == null || shutdown) return;
		
		try {
			Future<?> decodeFuture = decoderExecutor.submit(new Runnable() {
				@Override
				public void run() {
					decode(request, attempt);
				}
			});
			
			ScheduledFuture<?> timeoutFuture = timeoutExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					timeout(request, attempt);
				}
				
			}, ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			
			request.attachFutures(attempt, decodeFuture, timeoutFuture);
		} catch (RejectedExecutionException e) {
            Logs.err("Thumbnail request execution rejected.", e);
			cancel(request);
		}
	}
	
	private void decode(final ThumbRequest request, final ThumbRequest.Attempt attempt) {
		Bitmap bitmap = null;
		
		try {
			bitmap = ThumbDecoder.decode(context, request);
		} catch (Exception ignored) {
			// Partial files are expected to fail until enough metadata is available.
		}
		
		boolean success = bitmap != null;
		ThumbRequest.Transition transition = request.finishAttempt(attempt.generation, success, false);
		cancelTransitionFutures(transition);
		
		if (!transition.accepted) {
			recycle(bitmap);
			return;
		}
		
		if (transition.success) {
			postReady(request, attempt.generation, bitmap);
			return;
		}
		
		if (transition.terminalFailure) {
			postUnavailable(request, attempt.generation);
			return;
		}
		
		submit(request, transition.nextAttempt);
	}
	
	private void timeout(ThumbRequest request, ThumbRequest.Attempt attempt) {
		ThumbRequest.Transition transition = request.finishAttempt(attempt.generation, false, true);
		cancelTransitionFutures(transition);
		if (!transition.accepted) return;
		
		if (transition.terminalFailure) {
			postUnavailable(request, attempt.generation);
			return;
		}
		
		submit(request, transition.nextAttempt);
	}
	
	private static void cancelTransitionFutures(ThumbRequest.Transition transition) {
		if (transition.decodeToCancel != null) transition.decodeToCancel.cancel(true);
		if (transition.timeoutToCancel != null) transition.timeoutToCancel.cancel(false);
	}
	
	private void postReady(final ThumbRequest request, final long generation, final Bitmap bitmap) {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				requests.remove(request);
				
				if (shutdown || !request.canDeliver(generation)) {
					recycle(bitmap);
					return;
				}
				
				request.callback.onThumbnailReady(request.id, bitmap);
			}
		});
	}
	
	private void postUnavailable(final ThumbRequest request, final long generation) {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				requests.remove(request);
				if (shutdown || !request.canReportUnavailable(generation)) return;
				request.callback.onThumbnailUnavailable(request.id);
			}
		});
	}
	
	private static void recycle(Bitmap bitmap) {
		if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
	}
	
	private static final class BackgroundThreadFactory implements ThreadFactory {
		private final String name;
		private final AtomicInteger count = new AtomicInteger();
		
		BackgroundThreadFactory(String name) {
			this.name = name;
		}
		
		@Override
		public Thread newThread(final Runnable runnable) {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
					runnable.run();
				}
			}, name + "-" + count.incrementAndGet());
			
			thread.setDaemon(true);
			return thread;
		}
	}
}
