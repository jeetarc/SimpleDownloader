package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;
import android.provider.MediaStore;
import com.jeet.simpledownloader.util.TypeResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
* An immutable description of a download to start with {@link SimpleDownloader}.
*
* <p>Use {@link Builder} to create a request. Request-only settings describe the
* download itself; shared settings override the corresponding downloader defaults
* only when they are explicitly set on the request.</p>
*/
public final class DownloadRequest {
	final long id;
	final boolean hasId;
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
	final Priority priority;
	final boolean wifiOnly;
	final boolean deleteOnRemoval;
	final boolean lockedInQueue;
	final boolean hasSubFolder;
	final boolean hasMimeType;
	final boolean hasUserAgent;
	final boolean hasHeaders;
	final boolean hasCookies;
	final boolean hasPriority;
	final boolean hasWifiOnly;
	final boolean hasDeleteOnRemoval;
	final boolean hasLockedInQueue;
	
	private DownloadRequest(Builder builder, long id, ResolvedName resolved) {
		this.id = id;
		this.hasId = builder.hasId;
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
		this.headers = builder.headers.isEmpty() ? Collections.<String, String>emptyMap()
		: Collections.unmodifiableMap(new HashMap<String, String>(builder.headers));
		this.cookies = builder.cookies;
		this.checksumAlgorithm = builder.checksumAlgorithm;
		this.checksumValue = builder.checksumValue;
		this.priority = builder.priority == null ? Priority.NORMAL : builder.priority;
		this.wifiOnly = builder.wifiOnly;
		this.deleteOnRemoval = builder.deleteOnRemoval;
		this.lockedInQueue = builder.lockedInQueue;
		this.hasSubFolder = builder.hasSubFolder;
		this.hasMimeType = builder.hasMimeType;
		this.hasUserAgent = builder.hasUserAgent;
		this.hasHeaders = builder.hasHeaders;
		this.hasCookies = builder.hasCookies;
		this.hasPriority = builder.hasPriority;
		this.hasWifiOnly = builder.hasWifiOnly;
		this.hasDeleteOnRemoval = builder.hasDeleteOnRemoval;
		this.hasLockedInQueue = builder.hasLockedInQueue;
	}
	
	DownloadRequest resolve(long resolvedId) {
		if (hasId && id != resolvedId) throw new IllegalArgumentException("Cannot replace an explicit task ID with another ID.");
		Builder copy = new Builder();
		copy.copyFromRequest(this);
		return copy.buildResolved(hasId ? id : resolvedId);
	}
	
	DownloadTask createTask(SimpleDownloader downloader) {
		return new DownloadTask(downloader, this);
	}
	
	/** Builder for immutable {@link DownloadRequest} values. */
	public static final class Builder {
		Uri treeUri;
		Uri overwriteUri;
		String outputFolderPath;
		String overwritePath;
		Uri mediaStoreUri;
		String subFolderPath;
		String fileName;
		String mimeType;
		FileName fileNameMode;
		MimeType mimeTypeMode;
		String fileUrl;
		String userAgent = System.getProperty("http.agent");
		final Map<String, String> headers = new HashMap<String, String>();
		String cookies;
		String checksumAlgorithm;
		String checksumValue;
		long id;
		Priority priority = Priority.NORMAL;
		boolean wifiOnly;
		boolean deleteOnRemoval;
		boolean lockedInQueue;
		boolean hasId;
		boolean hasSubFolder;
		boolean hasMimeType;
		boolean hasUserAgent;
		boolean hasHeaders;
		boolean hasCookies;
		boolean hasPriority;
		boolean hasWifiOnly;
		boolean hasDeleteOnRemoval;
		boolean hasLockedInQueue;
		
		public Builder() {}
		
		/** Sets the task ID. */
		public Builder setId(long id) {
			this.id = id;
			hasId = true;
			return this;
		}
		
		/** Sets the source URL. */
		public Builder setFileUrl(String fileUrl) {
			if (fileUrl == null || fileUrl.trim().isEmpty()) throw new IllegalArgumentException("fileUrl cannot be null or empty. use valid file URL to start download.");
			this.fileUrl = fileUrl.trim();
			return this;
		}
		
		public Builder setOutput(Uri folderUri, String fileName) {
			if (folderUri == null) throw new IllegalArgumentException("setOutput(Uri, String): folderUri cannot be null");
			if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("setOutput(Uri, String): fileName cannot be null or empty. Use a file name such as 'video.mp4'.");
			clearOutput();
			if (isMediaStoreCollectionUri(folderUri)) mediaStoreUri = folderUri; else treeUri = folderUri;
			this.fileName = fileName.trim();
			return this;
		}
		
		public Builder setOutput(Uri folderUri, FileName fileName) {
			if (folderUri == null) throw new IllegalArgumentException("setOutput(Uri, FileName): folderUri cannot be null.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(Uri, FileName): fileName cannot be null.");
			clearOutput();
			if (isMediaStoreCollectionUri(folderUri)) mediaStoreUri = folderUri; else treeUri = folderUri;
			fileNameMode = fileName;
			return this;
		}
		
		public Builder setOutput(String folderPath, String fileName) {
			if (folderPath == null || folderPath.trim().isEmpty()) throw new IllegalArgumentException("setOutput(String, String): folderPath cannot be null or empty.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(String, String): fileName cannot be null or empty.");
			clearOutput();
			outputFolderPath = folderPath.trim();
			this.fileName = fileName.trim();
			return this;
		}
		
		public Builder setOutput(String folderPath, FileName fileName) {
			if (folderPath == null || folderPath.trim().isEmpty()) throw new IllegalArgumentException("setOutput(String, FileName): folderPath cannot be null or empty.");
			if (fileName == null) throw new IllegalArgumentException("setOutput(String, FileName): fileName cannot be null.");
			clearOutput();
			outputFolderPath = folderPath.trim();
			fileNameMode = fileName;
			return this;
		}
		
		public Builder overwrite(String outputPath) {
			if (outputPath == null || outputPath.trim().isEmpty()) throw new IllegalArgumentException("overwrite(String): outputPath cannot be null or empty.");
			clearOutput();
			overwritePath = outputPath.trim();
			return this;
		}
		
		public Builder overwrite(Uri fileUri) {
			if (fileUri == null) throw new IllegalArgumentException("overwrite(Uri): Uri fileUri cannot be null. use a valid document URI for the file.");
			clearOutput();
			overwriteUri = fileUri;
			return this;
		}
		
		public Builder setSubFolder(String subFolder) {
			subFolderPath = subFolder == null ? null : subFolder.trim();
			hasSubFolder = true;
			return this;
		}
		
		public Builder setMimeType(String mimeType) {
			this.mimeType = mimeType == null ? null : mimeType.trim();
			hasMimeType = true;
			return this;
		}
		
		public Builder setMimeType(MimeType mimeType) {
			mimeTypeMode = mimeType;
			hasMimeType = true;
			return this;
		}
		
		public Builder setUserAgent(String userAgent) {
			this.userAgent = userAgent;
			hasUserAgent = true;
			return this;
		}
		
		public Builder addHeader(String key, String value) {
			headers.put(key, value);
			hasHeaders = true;
			return this;
		}
		
		public Builder setHeaders(Map<String, String> headers) {
			if (headers != null) this.headers.putAll(headers);
			hasHeaders = true;
			return this;
		}
		
		public Builder setCookies(String cookies) {
			this.cookies = cookies;
			hasCookies = true;
			return this;
		}
		
		public Builder setChecksum(String algorithm, String checksum) {
			if (algorithm == null || algorithm.trim().isEmpty()) throw new IllegalArgumentException("setChecksum(String, String): algorithm cannot be null or empty. Use SHA-256, SHA-1 or MD5.");
			if (checksum == null || checksum.trim().isEmpty()) throw new IllegalArgumentException("setChecksum(String, String): checksum cannot be null or empty.");
			checksumAlgorithm = algorithm.trim();
			checksumValue = checksum.trim().toLowerCase(java.util.Locale.US);
			return this;
		}
		
		public Builder setPriority(Priority priority) {
			this.priority = priority;
			hasPriority = true;
			return this;
		}
		
		public Builder setWifiOnly(boolean wifiOnly) {
			this.wifiOnly = wifiOnly;
			hasWifiOnly = true;
			return this;
		}
		
		public Builder setLockedInQueue(boolean enable) {
			lockedInQueue = enable;
			hasLockedInQueue = true;
			return this;
		}
		
		public Builder setDeleteOnRemoval(boolean enable) {
			deleteOnRemoval = enable;
			hasDeleteOnRemoval = true;
			return this;
		}
		
		/** Builds an immutable request. The downloader assigns an ID when none is set. */
		public DownloadRequest build() {
			validateBase();
			return new DownloadRequest(this, hasId ? id : 0L, new ResolvedName(fileName, mimeType, fileNameMode, mimeTypeMode));
		}
		
		private void copyFromRequest(DownloadRequest request) {
			treeUri = request.treeUri;
			overwriteUri = request.overwriteUri;
			outputFolderPath = request.outputFolderPath;
			overwritePath = request.overwritePath;
			mediaStoreUri = request.mediaStoreUri;
			subFolderPath = request.subFolderPath;
			fileName = request.fileName;
			fileNameMode = request.fileNameMode;
			mimeType = request.mimeType;
			mimeTypeMode = request.mimeTypeMode;
			fileUrl = request.fileUrl;
			userAgent = request.userAgent;
			
			headers.clear();
			headers.putAll(request.headers);
			
			cookies = request.cookies;
			checksumAlgorithm = request.checksumAlgorithm;
			checksumValue = request.checksumValue;
			id = request.id;
			priority = request.priority;
			wifiOnly = request.wifiOnly;
			deleteOnRemoval = request.deleteOnRemoval;
			lockedInQueue = request.lockedInQueue;
			
			hasId = request.hasId;
			hasSubFolder = request.hasSubFolder;
			hasMimeType = request.hasMimeType;
			hasUserAgent = request.hasUserAgent;
			hasHeaders = request.hasHeaders;
			hasCookies = request.hasCookies;
			hasPriority = request.hasPriority;
			hasWifiOnly = request.hasWifiOnly;
			hasDeleteOnRemoval = request.hasDeleteOnRemoval;
			hasLockedInQueue = request.hasLockedInQueue;
		}
		
		private DownloadRequest buildResolved(long resolvedId) {
			validateBase();
			if ((mimeType == null || mimeType.trim().isEmpty()) && mimeTypeMode == null) mimeTypeMode = MimeType.AUTO;
			ResolvedName resolved = resolveFileNameAndMimeType();
			if (overwriteUri == null && overwritePath == null) {
				if (resolved.fileName == null || resolved.fileName.isEmpty()) throw new IllegalStateException("fileName could not be resolved.");
				if (resolved.mimeType == null || resolved.mimeType.isEmpty()) throw new IllegalStateException("mimeType could not be resolved.");
			}
			return new DownloadRequest(this, resolvedId, resolved);
		}
		
		private void validateBase() {
			if (treeUri == null && mediaStoreUri == null && overwriteUri == null && outputFolderPath == null && overwritePath == null) {
				throw new IllegalStateException("Call setOutput(...) or overwrite(...) before starting a download.");
			}
			if (fileUrl == null) throw new IllegalStateException("Call setFileUrl(...) before starting a download.");
		}
		
		private static boolean isMediaStoreCollectionUri(Uri uri) {
			if (uri == null || !"media".equals(uri.getAuthority())) return false;
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
			hasMimeType = false;
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
	
	/** Creates a new request using a SAF (document-tree) / MediaStore folder URI and a custom file name. */
	public static DownloadRequest from(Uri folderUri, String fileName, String fileUrl) {
		return builder().setFileUrl(fileUrl).setOutput(folderUri, fileName).build();
	}
	
	/** Creates a new request using a SAF (document-tree) / MediaStore folder URI and a FileName (file name mode). */
	public static DownloadRequest from(Uri folderUri, FileName fileName, String fileUrl) {
		return builder().setFileUrl(fileUrl).setOutput(folderUri, fileName).build();
	}
	
	/** Creates a new request using a filesystem folder path and a custom file name. */
	public static DownloadRequest from(String folderPath, String fileName, String fileUrl) {
		return builder().setFileUrl(fileUrl).setOutput(folderPath, fileName).build();
	}
	
	/** Creates a new request using a filesystem folder path and a FileName (file name mode). */
	public static DownloadRequest from(String folderPath, FileName fileName, String fileUrl) {
		return builder().setFileUrl(fileUrl).setOutput(folderPath, fileName).build();
	}
    
    /** Creates a new request using a file URI and writes directly to it. */
    public static DownloadRequest fromOverwrite(Uri fileUri, String fileUrl) {
        return builder().overwrite(fileUri).setFileUrl(fileUrl).build();
    }
    
    /** Creates a new request using a file path and writes directly to it. */
    public static DownloadRequest fromOverwrite(String filePath, String fileUrl) {
        return builder().overwrite(filePath).setFileUrl(fileUrl).build();
    }
	
	/** Creates a new request builder. */
	public static Builder builder() {
		return new Builder();
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
