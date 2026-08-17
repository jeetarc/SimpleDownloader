package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;
import com.jeet.simpledownloader.util.TypeResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import android.provider.MediaStore;

final class DownloadRequest {
	final long id;
	final Uri treeUri;
	final Uri overwriteUri;
	final String outputFolderPath;
	final String overwritePath;
	final Uri mediaStoreUri;
	final String subFolderPath;
	final String fileName;
	final String mimeType;
	final FileName fileNameMode;
	final MimeType mimeTypeMode;
	final String fileUrl;
	final String userAgent;
	final Map<String, String> headers;
	final String cookies;
	final String checksumAlgorithm;
	final String checksumValue;
	final int bufferSize;
	final Priority priority;
	final boolean wifiOnly;
	final boolean deleteOnRemoval;
	final boolean lockedInQueue;
	final DownloadNotification notification;
	final DownloadListener listener;
	
	private DownloadRequest(Builder builder, long id, ResolvedName resolved) {
		this.id = id;
		this.treeUri = builder.treeUri;
		this.overwriteUri = builder.overwriteUri;
		this.outputFolderPath = builder.outputFolderPath;
		this.overwritePath = builder.overwritePath;
		this.mediaStoreUri = builder.mediaStoreUri;
		this.subFolderPath = builder.subFolderPath;
		this.fileName = resolved.fileName;
		this.mimeType = resolved.mimeType;
		this.fileNameMode = resolved.fileNameMode;
		this.mimeTypeMode = resolved.mimeTypeMode;
		this.fileUrl = builder.fileUrl;
		this.userAgent = builder.userAgent;
		
		if (builder.headers.isEmpty()) {
			this.headers = Collections.emptyMap();
		} else {
			this.headers = Collections.unmodifiableMap(new HashMap<String, String>(builder.headers));
		}
		
		this.cookies = builder.cookies;
		this.checksumAlgorithm = builder.checksumAlgorithm;
		this.checksumValue = builder.checksumValue;
		this.bufferSize = builder.bufferSize;
		this.priority = builder.priority == null ? Priority.NORMAL : builder.priority;
		this.wifiOnly = builder.wifiOnly;
		this.deleteOnRemoval = builder.deleteOnRemoval;
		this.lockedInQueue = builder.lockedInQueue;
		this.notification = new DownloadNotification(builder.notification);
		this.listener = builder.listener;
	}
	
	DownloadTask createTask(SimpleDownloader downloader) {
		return new DownloadTask(downloader, this);
	}
	
	static final class Builder {
		Uri treeUri;
		Uri overwriteUri;
		String outputFolderPath;
		String overwritePath;
		Uri mediaStoreUri;
		String subFolderPath;
		String fileName;
		String mimeType;
		FileName fileNameMode = null;
		MimeType mimeTypeMode = null;
		String fileUrl;
		String userAgent = System.getProperty("http.agent");
		final Map<String, String> headers = new HashMap<String, String>();
		String cookies = null;
		String checksumAlgorithm = null;
		String checksumValue = null;
		Long customId = null;
		int bufferSize = 16384;
		Priority priority = Priority.NORMAL;
		boolean wifiOnly = false;
		boolean deleteOnRemoval = false;
		boolean lockedInQueue = false;
		DownloadNotification notification = new DownloadNotification();
		DownloadListener listener;
		
		Builder putId(long id) {
			customId = id;
			return this;
		}
		
		Builder putFileUrl(String fileUrl) {
			if (fileUrl == null || fileUrl.trim().isEmpty()) throw new IllegalArgumentException("fileUrl cannot be null or empty. use valid file URL to start download.");
			this.fileUrl = fileUrl.trim();
			return this;
		}
		
		Builder putOutput(Uri folderUri, String fileName) {
			if (folderUri == null) throw new IllegalArgumentException("setOutput(Uri, String): folderUri cannot be null");
			if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, String): fileName cannot be null or empty. Use a file name such as 'video.mp4'.");
			clearOutput();
			
			if (isMediaStoreCollectionUri(folderUri)) this.mediaStoreUri = folderUri; else this.treeUri = folderUri;
			this.fileName = fileName.trim();
			return this;
		}
		
		Builder putOutput(Uri folderUri, FileName fileName) {
			if (folderUri == null) throw new IllegalArgumentException("setOutput(Uri, FileName): folderUri cannot be null.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(Uri, FileName): fileName cannot be null.");
			clearOutput();
			
			if (isMediaStoreCollectionUri(folderUri)) this.mediaStoreUri = folderUri; else this.treeUri = folderUri;
			this.fileNameMode = fileName;
			return this;
		}
		
		Builder putOutput(String folderPath, String fileName) {
			if (folderPath == null || folderPath.trim().isEmpty()) throw new IllegalArgumentException("setOutput(String, String): folderPath cannot be null or empty.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(String, String): fileName cannot be null or empty.");
			clearOutput();
			
			outputFolderPath = folderPath.trim();
			this.fileName = fileName.trim();
			return this;
		}
		
		Builder putOutput(String folderPath, FileName fileName) {
			if (folderPath == null || folderPath.trim().isEmpty()) throw new IllegalArgumentException("setOutput(String, FileName): folderPath cannot be null or empty.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(String, FileName): fileName cannot be null.");
			clearOutput();
			
			outputFolderPath = folderPath.trim();
			this.fileNameMode = fileName;
			return this;
		}
		
		Builder putOverwrite(String outputPath) {
			if (outputPath == null || outputPath.trim().isEmpty()) throw new IllegalArgumentException("overwrite(String): outputPath cannot be null or empty.");
			
			clearOutput();
			overwritePath = outputPath.trim();
			return this;
		}
		
		Builder putOverwrite(Uri fileUri) {
			if (fileUri == null) throw new IllegalArgumentException("overwrite(Uri): Uri fileUri cannot be null. use a valid document URI for the file.");
			
			clearOutput();
			overwriteUri = fileUri;
			return this;
		}
		
		Builder putMimeType(String mimeType) {
			this.mimeType = mimeType == null ? null : mimeType.trim();
			return this;
		}
		
		Builder putMimeType(MimeType mimeType)  {
			this.mimeTypeMode = mimeType;
			return this;
		}
		
		Builder putSubFolder(String subFolder) {
			this.subFolderPath = subFolder == null ? null : subFolder.trim();
			return this;
		}
		
		Builder putUserAgent(String userAgent) {
			this.userAgent = userAgent;
			return this;
		}
		
		Builder putHeader(String key, String value) {
			headers.put(key, value);
			return this;
		}
		
		Builder putHeaders(Map<String, String> headers) {
			if (headers != null) this.headers.putAll(headers);
			return this;
		}
		
		Builder putCookies(String cookies) {
			this.cookies = cookies;
			return this;
		}
		
		Builder putChecksum(String algorithm, String checksum) {
			if (algorithm == null || algorithm.trim().isEmpty()) throw new IllegalArgumentException("setChecksum(String, String): algorithm cannot be null or empty. Use SHA-256, SHA-1 or MD5.");
			if (checksum == null || checksum.trim().isEmpty()) throw new IllegalArgumentException("setChecksum(String, String): checksum cannot be null or empty.");
			checksumAlgorithm = algorithm.trim();
			checksumValue = checksum.trim().toLowerCase(java.util.Locale.US);
			return this;
		}
		
		Builder putBufferSize(int bytes) {
			bufferSize = bytes;
			return this;
		}
		
		Builder putPriority(Priority priority) {
			this.priority = priority;
			return this;
		}
		
		Builder putWifiOnly(boolean wifiOnly) {
			this.wifiOnly = wifiOnly;
			return this;
		}
		
		Builder putLockedInQueue(boolean enable) {
			lockedInQueue = enable;
			return this;
		}
		
		Builder putDeleteOnRemoval(boolean enable) {
			deleteOnRemoval = enable;
			return this;
		}
		
		Builder putNotification(DownloadNotification notification) {
			this.notification = notification == null ? new DownloadNotification() : notification;
			return this;
		}
		
		Builder putListener(DownloadListener listener) {
			this.listener = listener;
			return this;
		}
        
        DownloadRequest build(long generatedId) {
			if (treeUri == null && mediaStoreUri == null && overwriteUri == null && outputFolderPath == null && overwritePath == null) {
				throw new IllegalStateException("Call setOutput(...) or overwrite(...) before starting a download.");
			}
			
			if (fileUrl == null) throw new IllegalStateException("Call setFileUrl(...) before starting a download.");
			if ((mimeType == null || mimeType.trim().isEmpty()) && mimeTypeMode == null) mimeTypeMode = MimeType.AUTO;
			ResolvedName resolved = resolveFileNameAndMimeType();
			
			if (overwriteUri == null && overwritePath == null) {
				if (resolved.fileName == null || resolved.fileName.isEmpty()) throw new IllegalStateException("fileName could not be resolved.");
				if (resolved.mimeType == null || resolved.mimeType.isEmpty()) throw new IllegalStateException("mimeType could not be resolved.");
			}
			
			long id = customId != null ? customId : generatedId;
			return new DownloadRequest(this, id, resolved);
		}
		
		boolean hasCustomId() {
			return customId != null;
		}
		
		private boolean isMediaStoreCollectionUri(Uri uri) {
			if (uri == null) return false;
			if (!"media".equals(uri.getAuthority())) return false;
			String value = uri.toString();
			
			return value.equals(MediaStore.Downloads.EXTERNAL_CONTENT_URI.toString())
			|| value.equals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
			|| value.equals(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())
			|| value.equals(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString());
		}
		
		private void clearOutput() {
			treeUri = null;
			overwriteUri = null;
			outputFolderPath = null;
			overwritePath = null;
			mediaStoreUri = null;
			fileName = null;
			mimeType = null;
			fileNameMode = null;
			mimeTypeMode = null;
		}
		
		private ResolvedName resolveFileNameAndMimeType() {
			String finalFileName = fileName;
			String finalMimeType = mimeType;
			FileName finalFileNameMode = fileNameMode;
			MimeType finalMimeTypeMode = mimeTypeMode;
			if (finalFileNameMode == null && finalMimeTypeMode == null) return new ResolvedName(finalFileName, finalMimeType, null, null);
			
			String extension = TypeResolver.getExtensionFromUrl(fileUrl);
			String suffix = extension.isEmpty() ? "" : "." + extension;
			
			if (finalFileNameMode == FileName.AUTO) {
				String segment = Uri.parse(fileUrl).getLastPathSegment();
				if (segment != null && !segment.trim().isEmpty()) finalFileName = segment;
				else finalFileName = createTimeBasedName() + suffix;
				
			} else if (finalFileNameMode == FileName.TIME_BASED) {
				finalFileName = createTimeBasedName() + suffix;
				if (!suffix.isEmpty()) finalFileNameMode = null;
			}
			
			if (finalMimeTypeMode == MimeType.AUTO) {
				String resolvedMime = TypeResolver.getMimeFromName(finalFileName);
				if (resolvedMime.isEmpty()) resolvedMime = TypeResolver.getMimeFromUrl(fileUrl);
				finalMimeType = resolvedMime.isEmpty() ? "application/octet-stream" : resolvedMime;
				if (finalFileNameMode == null && !resolvedMime.isEmpty()) finalMimeTypeMode = null;
				
			} else if (finalMimeTypeMode == MimeType.FROM_NAME) {
				String resolvedMime = TypeResolver.getMimeFromName(finalFileName);
				finalMimeType = resolvedMime.isEmpty() ? "application/octet-stream" : resolvedMime;
				if (finalFileNameMode == null) finalMimeTypeMode = null;
			}
			
			return new ResolvedName(finalFileName, finalMimeType, finalFileNameMode, finalMimeTypeMode);
		}
	}
	
	private static String createTimeBasedName() {
		java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US);
		return format.format(new java.util.Date());
	}
	
	String getOverwriteKey() {
		if (overwritePath != null && !overwritePath.trim().isEmpty()) return "path:" + overwritePath;
		if (overwriteUri != null) return "uri:" + overwriteUri.toString();
		return null;
	}
	
	private static final class ResolvedName {
		final String fileName;
		final String mimeType;
		final FileName fileNameMode;
		final MimeType mimeTypeMode;
		
		ResolvedName(String fileName, String mimeType, FileName fileNameMode, MimeType mimeTypeMode) {
			this.fileName = fileName;
			this.mimeType = mimeType;
			this.fileNameMode = fileNameMode;
			this.mimeTypeMode = mimeTypeMode;
		}
	}
}
