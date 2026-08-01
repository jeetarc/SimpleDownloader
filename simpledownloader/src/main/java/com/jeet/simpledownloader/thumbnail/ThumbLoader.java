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
import okhttp3.Call;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
* Thumbnail loader (used internally).
* <p>It can decode local media on download and from thumbnail image URLs.
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
	private final Set<Call> networkCalls = Collections.newSetFromMap(new ConcurrentHashMap<Call, Boolean>());
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
		if (sourceFile == null && sourceUri == null) {
			throw new IllegalArgumentException("A local thumbnail source is required.");
		}
		
		if (callback == null) throw new NullPointerException("callback == null");
		int safeWidth = width > 0 ? width : DEFAULT_WIDTH;
		int safeHeight = height > 0 ? height : DEFAULT_HEIGHT;
		ThumbRequest request = new ThumbRequest(id, sourceFile, sourceUri, mimeType, safeWidth, safeHeight, callback);
		requests.add(request);
		return request;
	}
	
	public void loadUrl(final long id, final Call call, final int width, final int height, final Callback callback) {
		if (shutdown) throw new IllegalStateException("ThumbLoader is shut down.");
		if (call == null) throw new NullPointerException("call == null");
		if (callback == null) throw new NullPointerException("callback == null");
		
		final int safeWidth = width > 0 ? width : DEFAULT_WIDTH;
		final int safeHeight = height > 0 ? height : DEFAULT_HEIGHT;
		networkCalls.add(call);
		
		try {
			call.timeout().timeout(ATTEMPT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
			call.enqueue(new okhttp3.Callback() {
				@Override
				public void onFailure(Call failedCall, IOException error) {
					networkCalls.remove(failedCall);
					if (shutdown || failedCall.isCanceled()) return;
					postUrlUnavailable(id, failedCall, callback);
				}
				
				@Override
				public void onResponse(Call completedCall, Response response) {
					Bitmap bitmap = null;
					
					try {
						if (!response.isSuccessful()) {
							postUrlUnavailable(id, completedCall, callback);
							return;
						}
						
						ResponseBody body = response.body();
						if (body == null) {
							postUrlUnavailable(id, completedCall, callback);
							return;
						}
						
						bitmap = ThumbDecoder.decodeImage(body.byteStream(), safeWidth, safeHeight);
						if (bitmap == null) {
							postUrlUnavailable(id, completedCall, callback);
							return;
						}
						
						postUrlReady(id, completedCall, bitmap, callback);
						bitmap = null;
						
					} catch (Throwable ignored) {
						if (!completedCall.isCanceled()) postUrlUnavailable(id, completedCall, callback);
						
					} finally {
						networkCalls.remove(completedCall);
						response.close();
						recycle(bitmap);
					}
				}
			});
			
		} catch (Throwable error) {
			networkCalls.remove(call);
			if (!call.isCanceled()) postUrlUnavailable(id, null, callback);
			call.cancel();
		}
	}
	
	/** Called after the output buffer has been flushed at a progress milestone. */
	public void onBytesAvailable(ThumbRequest request, long downloadedBytes) {
		if (request == null || shutdown) return;
		submit(request, request.onBytesAvailable(Math.max(0L, downloadedBytes)));
	}
	
	/** Always allows one final extraction attempt against the completed file. */
	public void onCompleted(ThumbRequest request, long downloadedBytes) {
		if (request == null || shutdown) return;
		submit(request, request.onCompleted(Math.max(0L, downloadedBytes)));
	}
	
	public void cancel(ThumbRequest request) {
		if (request == null) return;
		requests.remove(request);
		request.cancel();
	}
	
	public void cancelUrl(Call call) {
		if (call == null) return;
		networkCalls.remove(call);
		call.cancel();
	}
	
	public void shutdown() {
		if (shutdown) return;
		shutdown = true;
		
		for (ThumbRequest request : requests) {
			if (request != null) request.cancel();
		}
		for (Call call : networkCalls) {
			if (call != null) call.cancel();
		}
		
		requests.clear();
		networkCalls.clear();
		decoderExecutor.shutdownNow();
		timeoutExecutor.shutdownNow();
	}
	
	public boolean isShutdown() {
		return shutdown;
	}
	
	public boolean hasRunningRequests() {
		return !requests.isEmpty() || !networkCalls.isEmpty();
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
		} catch (RejectedExecutionException ignored) {
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
	
	private void postUrlReady(final long id, final Call call, final Bitmap bitmap, final Callback callback) {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (shutdown || call.isCanceled()) {
					recycle(bitmap);
					return;
				}
				
				callback.onThumbnailReady(id, bitmap);
			}
		});
	}
	
	private void postUrlUnavailable(final long id, final Call call, final Callback callback) {
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (shutdown || (call != null && call.isCanceled())) return;
				callback.onThumbnailUnavailable(id);
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

