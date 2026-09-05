package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.jeet.simpledownloader.util.TypeResolver;
import com.jeet.simpledownloader.util.Logs;

final class HttpEngine {
	private OkHttpClient baseClient;
	private boolean ownsBaseClient;
	private final Map<String, OkHttpClient> timeoutClients = new HashMap<String, OkHttpClient>();
	HttpEngine() {}
	
	synchronized void setClient(OkHttpClient client) {
		if (client == null) throw new IllegalArgumentException("OkHttpClient cannot be null.");
		if (baseClient == client) return;
		
		OkHttpClient previousClient = baseClient;
		boolean closePreviousClient = ownsBaseClient;
		
		baseClient = client;
		ownsBaseClient = false;
		timeoutClients.clear();
		
		if (closePreviousClient) shutdownClient(previousClient);
	}
	
	synchronized void clearTimeoutClients() {
		timeoutClients.clear();
	}

	synchronized OkHttpClient getClient(int connectTimeout, int readTimeout) {
		if (baseClient == null) {
			baseClient = new OkHttpClient.Builder()
			.connectTimeout(60, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.followRedirects(true)
			.followSslRedirects(true)
			.build();
			
			ownsBaseClient = true;
		}
		
		if (connectTimeout <= 0 && readTimeout <= 0) return baseClient;
		
		String key = connectTimeout + ":" + readTimeout;
		OkHttpClient cached = timeoutClients.get(key);
		if (cached != null) return cached;
		
		OkHttpClient.Builder builder = baseClient.newBuilder();
		if (connectTimeout > 0) builder.connectTimeout(connectTimeout, TimeUnit.MILLISECONDS);
		if (readTimeout > 0) builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS);
		
		OkHttpClient client = builder.build();
		timeoutClients.put(key, client);
		return client;
	}
	
	Request.Builder newRequestBuilder(DownloadTask task) {
		String userAgent = task.mUserAgent;
		if (userAgent == null || userAgent.trim().isEmpty()) userAgent = System.getProperty("http.agent");
		
		Request.Builder builder = new Request.Builder()
		.url(task.mFileUrl)
		.header("User-Agent", userAgent)
		.header("Accept-Encoding", "identity");
		
		if (task.mHeaders != null) {
			for (Map.Entry<String, String> header : task.mHeaders.entrySet()) {
				if (header.getKey() != null && header.getValue() != null) {
					builder.header(header.getKey(), header.getValue());
				}
			}
		}
		
		if (task.mCookies != null && !task.mCookies.isEmpty()) builder.header("Cookie", task.mCookies);
		return builder;
	}
	
	Call newCall(DownloadTask task, Request request) {
		OkHttpClient client = getClient(task.mDownloader.mConnectTimeout, task.mDownloader.mReadTimeout);
		return client.newCall(request);
	}
	
	Call newThumbnailCall(DownloadTask task, String thumbnailUrl, Map<String, String> headers) {
		if (task == null || thumbnailUrl == null || thumbnailUrl.trim().isEmpty()) throw new IllegalArgumentException("Thumbnail URL or DownloadTask cannot be resolved");
		String userAgent = task.mUserAgent;
		if (userAgent == null || userAgent.trim().isEmpty()) userAgent = System.getProperty("http.agent");
		
		Request.Builder builder = new Request.Builder()
		.url(thumbnailUrl.trim())
		.header("Accept", "image/*");
		
		if (userAgent != null && !userAgent.trim().isEmpty()) {
			builder.header("User-Agent", userAgent);
		}
		
		if (headers != null) {
			for (Map.Entry<String, String> header : headers.entrySet()) {
				if (header.getKey() == null || header.getValue() == null) continue;
				builder.header(header.getKey(), header.getValue());
			}
		}
		
		return newCall(task, builder.build());
	}
	
	HttpConnection open(DownloadTask task, long existingFileSize) throws IOException, RefreshRequestException {
		Request.Builder requestBuilder = newRequestBuilder(task);
		
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
		Call call = newCall(task, requestBuilder.build());
		task.mCurrentCall = call;
		Response response = call.execute();
		return validate(task, response, existingFileSize);
	}
	
	private HttpConnection validate(DownloadTask task, Response response, long existingFileSize) throws IOException, RefreshRequestException {
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
		
		if (existingFileSize <= 0) {
			task.mETag = eTag;
			task.mLastModified = lastModified;
		} else {
			if (eTag != null) task.mETag = eTag;
			if (lastModified != null) task.mLastModified = lastModified;
		}
		
		String contentDisposition = "";
		String contentType = "";
		String serverFileName = "";
		String responseMime = "";
		boolean updateDatabase = false;
		
		if (task.mFileNameMode != null || task.mMimeTypeMode != null) {
			contentDisposition = response.header("Content-Disposition");
			contentType = response.header("Content-Type");
			serverFileName = parseNameFromContentDisposition(contentDisposition);
			responseMime = parseMimeFromContentType(contentType);
		}
		
		if (task.mFileNameMode == FileName.AUTO) {
			if (serverFileName != null && !serverFileName.isEmpty()) {
				task.mFileName = serverFileName;
			}
			
			if (task.mFileName == null || task.mFileName.isEmpty()) {
				task.mFileName = "download";
			}
			
			if (TypeResolver.getExtension(task.mFileName).isEmpty()) {
				String extension = TypeResolver.getExtensionFromMime(responseMime);
				if (!extension.isEmpty()) task.mFileName += "." + extension;
			}
			
			task.mFileNameMode = null;
			updateDatabase = true;
			
		} else if (task.mFileNameMode == FileName.TIME_BASED) {
			String extension = TypeResolver.getExtension(serverFileName);
			if (extension.isEmpty()) extension = TypeResolver.getExtensionFromMime(responseMime);
			
			if (!extension.isEmpty() && TypeResolver.getExtension(task.mFileName).isEmpty()) {
				task.mFileName = task.mFileName + "." + extension;
			}
			
			task.mFileNameMode = null;
			updateDatabase = true;
		}
		
		if (task.mMimeTypeMode == MimeType.AUTO) {
			String resolvedMime = TypeResolver.getMimeFromName(task.mFileName);
			if (resolvedMime.isEmpty()) resolvedMime = responseMime;
			task.mMimeType = !resolvedMime.isEmpty() ? resolvedMime : "application/octet-stream";
			
			task.mMimeTypeMode = null;
			updateDatabase = true;
			
		} else if (task.mMimeTypeMode == MimeType.FROM_NAME) {
			String resolvedMime = TypeResolver.getMimeFromName(task.mFileName);
			task.mMimeType = !resolvedMime.isEmpty() ? resolvedMime : "application/octet-stream";
			
			task.mMimeTypeMode = null;
			updateDatabase = true;
		}
		
		if (updateDatabase && task.mDownloader.taskDatabase != null) {
			task.mDownloader.taskDatabase.updateResolvedMetadata(task);
		}
		
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
	
	private boolean isRemoteChanged(DownloadTask task, String eTag, String lastModified) {
		if (task.mETag != null && eTag != null && !task.mETag.equals(eTag)) return true;
		if (task.mLastModified != null && lastModified != null && !task.mLastModified.equals(lastModified)) return true;
		return false;
	}
	
	private void validatePartialResponse(Response response, long existingFileSize) throws IOException {
		if (existingFileSize <= 0) throw new DownloadException(DownloadException.Type.RANGE_NOT_SUPPORTED, "Unexpected 206 response for fresh request.", response.code(), false);
		String contentRange = response.header("Content-Range");
		if (contentRange == null || contentRange.trim().isEmpty()) throw new DownloadException(DownloadException.Type.RANGE_NOT_SUPPORTED, "Missing Content-Range for partial response.", response.code(), false);
		long start = parseStartFromContentRange(contentRange);
		if (start != existingFileSize) throw new DownloadException(DownloadException.Type.RANGE_NOT_SUPPORTED, "Invalid Content-Range start: " + contentRange, response.code(), false);
	}
	
	private long resolveTotalBytes(Response response, long base, long contentLength) {
		long fromRange = parseTotalFromContentRange(response.header("Content-Range"));
		if (fromRange > 0) return fromRange;
		return contentLength > 0 ? base + contentLength : -1;
	}
	
	private long parseStartFromContentRange(String contentRange) {
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
	
	private long parseTotalFromContentRange(String contentRange) {
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
	
	private String parseNameFromContentDisposition(String value) {
		if (value == null || value.trim().isEmpty()) return null;
		String fileNameStar = findDispositionValue(value, "filename*");
		
		if (fileNameStar != null) {
			String decoded = decodeFileNameStar(fileNameStar);
			if (decoded != null && decoded.trim().length() > 0) return decoded;
		}
		
		String fileName = findDispositionValue(value, "filename");
		if (fileName != null && fileName.trim().length() > 0) return fileName;
		
		return null;
	}
	
	private String findDispositionValue(String value, String key) {
		java.util.List<String> parts = new java.util.ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '"') {
				inQuotes = !inQuotes;
				current.append(c);
				
			} else if (c == ';' && !inQuotes) {
				parts.add(current.toString().trim());
				current.setLength(0);
				
			} else {
				current.append(c);
			}
		}
		
		if (current.length() > 0) parts.add(current.toString().trim());
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			int eq = part.indexOf('=');
			if (eq <= 0) continue;
			String name = part.substring(0, eq).trim();
			if (!key.equalsIgnoreCase(name)) continue;
			String result = part.substring(eq + 1).trim();
			if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) result = result.substring(1, result.length() - 1);
			return result;
		}
		
		return null;
	}
	
	private String decodeFileNameStar(String value) {
		try {
			int first = value.indexOf('\'');
			int second = value.indexOf('\'', first + 1);
			if (first >= 0 && second > first) {
				String charset = value.substring(0, first).trim();
				if (charset.isEmpty()) charset = "UTF-8";
				String encoded = value.substring(second + 1);
				return java.net.URLDecoder.decode(encoded, charset);
			}
			return java.net.URLDecoder.decode(value, "UTF-8");
		} catch (Throwable ignored) {
			return value;
		}
	}
	
	private static String parseMimeFromContentType(String contentType) {
		if (contentType == null) return null;
		
		int separator = contentType.indexOf(';');
		if (separator >= 0) contentType = contentType.substring(0, separator);
		String mime = contentType.trim().toLowerCase(java.util.Locale.US);
		
		if (mime.length() == 0 || isGenericMime(mime)) {
			return null;
		}
		
		return mime.contains("/") ? mime : null;
	}
	
	private static boolean isGenericMime(String mime) {
		return "application/octet-stream".equals(mime)
		|| "binary/octet-stream".equals(mime)
		|| "multipart/form-data".equals(mime)
		|| "application/force-download".equals(mime)
		|| "application/download".equals(mime)
		|| "application/x-download".equals(mime);
	}
	
	synchronized void shutdown() {
		if (ownsBaseClient) shutdownClient(baseClient);
		timeoutClients.clear();
		baseClient = null;
		ownsBaseClient = false;
	}
	
	private void shutdownClient(OkHttpClient client) {
		if (client == null) return;
		client.dispatcher().cancelAll();
		client.dispatcher().executorService().shutdownNow();
		client.connectionPool().evictAll();
		if (client.cache() != null) {
			try {
				client.cache().close();
			} catch (Throwable thr) {
                Logs.err("Failed to shutdown OkHttpClient.", thr);
            }
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
