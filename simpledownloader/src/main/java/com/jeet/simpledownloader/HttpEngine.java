package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class HttpEngine {
	private HttpEngine() {}
	
	static HttpConnection open(DownloadTask task, long existingFileSize) throws IOException, RefreshRequestException {
		OkHttpClient client = SimpleDownloader.getHttpClient(task.mConnectTimeout, task.mReadTimeout);
		Request.Builder requestBuilder = new Request.Builder()
		.url(task.mFileUrl)
		.header("User-Agent", task.mUserAgent == null ? "Mozilla/5.0" : task.mUserAgent);
		
		if (existingFileSize > 0) {
			requestBuilder.header("Range", "bytes=" + existingFileSize + "-");
			
            if (task.mETag != null && task.mETag.length() > 0) {
				requestBuilder.header("If-Range", task.mETag);
                
			} else if (task.mLastModified != null && task.mLastModified.length() > 0) {
				requestBuilder.header("If-Range", task.mLastModified);
			}
		}
        
		for (Map.Entry<String, String> header : task.mHeaders.entrySet()) {
			if (header.getKey() != null && header.getValue() != null) requestBuilder.header(header.getKey(), header.getValue());
		}
		
		if (task.mCookies != null && !task.mCookies.isEmpty()) requestBuilder.header("Cookie", task.mCookies);
		Call call = client.newCall(requestBuilder.build());
		task.mCurrentCall = call;
		Response response = call.execute();
		return validate(task, response, existingFileSize);
	}
	
	private static HttpConnection validate(DownloadTask task, Response response, long existingFileSize) throws IOException, RefreshRequestException {
		int code = response.code();
		String eTag = response.header("ETag");
		String lastModified = response.header("Last-Modified");
		task.mIgnoredRange = false;
		
		if (code == 416) {
			long remoteTotal = parseTotalFromContentRange(response.header("Content-Range"));
			if (remoteTotal > 0 && existingFileSize == remoteTotal) {
				response.close();
				return new HttpConnection(response, null, null, existingFileSize, remoteTotal, code, eTag, lastModified, false, true);
			}
			
			response.close();
			OutputResolver.clearOutput(task);
			throw new RefreshRequestException(0);
		}
		
		if (existingFileSize > 0 && isRemoteChanged(task, eTag, lastModified)) {
			response.close();
			OutputResolver.clearOutput(task);
			throw new RefreshRequestException(0);
		}
		
		if (code == 200 && existingFileSize > 0) {
			OutputResolver.clearOutput(task);
			existingFileSize = 0;
			task.mIgnoredRange = true;
            
		} else if (code == 206) {
			validatePartialResponse(response, existingFileSize);
            
		} else if (code < 200 || code >= 300) {
			response.close();
			throw DownloadException.http(code);
            
		} else if (code != 200 && code != 206) {
			response.close();
			throw DownloadException.http(code);
		}
		
		task.mETag = eTag != null ? eTag : task.mETag;
		task.mLastModified = lastModified != null ? lastModified : task.mLastModified;
		ResponseBody body = response.body();
		if (body == null) {
			response.close();
			throw DownloadException.emptyResponse("Empty response body.");
		}
		
		long contentLength = body.contentLength();
		long totalBytes = resolveTotalBytes(response, existingFileSize, contentLength);
		InputStream input = body.byteStream();
		return new HttpConnection(response, body, input, existingFileSize, totalBytes, code, task.mETag, task.mLastModified, task.mIgnoredRange, false);
	}
	
	private static boolean isRemoteChanged(DownloadTask task, String eTag, String lastModified) {
		if (task.mETag != null && eTag != null && !task.mETag.equals(eTag)) return true;
		if (task.mLastModified != null && lastModified != null && !task.mLastModified.equals(lastModified)) return true;
		return false;
	}
	
	private static void validatePartialResponse(Response response, long existingFileSize) throws IOException {
		if (existingFileSize <= 0) return;
		String contentRange = response.header("Content-Range");
		if (contentRange == null || contentRange.trim().isEmpty()) throw new DownloadException(DownloadException.Type.RANGE_NOT_SUPPORTED, "Missing Content-Range for partial response.", response.code(), false);
		long start = parseStartFromContentRange(contentRange);
		if (start != existingFileSize) throw new DownloadException(DownloadException.Type.RANGE_NOT_SUPPORTED, "Invalid Content-Range start: " + contentRange, response.code(), false);
	}
	
	private static long resolveTotalBytes(Response response, long base, long contentLength) {
		long fromRange = parseTotalFromContentRange(response.header("Content-Range"));
		if (fromRange > 0) return fromRange;
		return contentLength > 0 ? base + contentLength : -1;
	}
	
	private static long parseStartFromContentRange(String contentRange) {
		try {
			String value = contentRange.toLowerCase(Locale.US).trim();
			if (!value.startsWith("bytes")) return -1;
			int space = value.indexOf(' ');
			int dash = value.indexOf('-');
			if (space < 0 || dash < 0 || dash <= space) return -1;
			return Long.parseLong(value.substring(space + 1, dash).trim());
            
		} catch (Throwable ignored) {
			return -1;
		}
	}
	
	private static long parseTotalFromContentRange(String contentRange) {
		try {
			if (contentRange == null) return -1;
			int slash = contentRange.indexOf('/');
			if (slash < 0 || slash >= contentRange.length() - 1) return -1;
			String total = contentRange.substring(slash + 1).trim();
			if ("*".equals(total)) return -1;
			return Long.parseLong(total);
            
		} catch (Throwable ignored) {
			return -1;
		}
	}
	
	static final class HttpConnection implements Closeable {
		final Response response;
		final ResponseBody body;
		final InputStream input;
		final long resumeBase;
		final long totalBytes;
		final int code;
		final String eTag;
		final String lastModified;
		final boolean restartFromZero;
		final boolean alreadyComplete;
		
		HttpConnection(Response response, ResponseBody body, InputStream input, long resumeBase, long totalBytes, int code, String eTag, 
        String lastModified, boolean restartFromZero, boolean alreadyComplete) {
			this.response = response;
			this.body = body;
			this.input = input;
			this.resumeBase = resumeBase;
			this.totalBytes = totalBytes;
			this.code = code;
			this.eTag = eTag;
			this.lastModified = lastModified;
			this.restartFromZero = restartFromZero;
			this.alreadyComplete = alreadyComplete;
		}
		
		@Override
		public void close() {
			try {
				if (input != null) input.close();
			} catch (Exception ignored) {}
			
			try {
				if (body != null) body.close();
			} catch (Exception ignored) {}
			
			try {
				if (response != null) response.close();
			} catch (Exception ignored) {}
		}
	}
}
