package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.Collections;
import java.util.Map;

final class TaskState {
	long id;
	String fileUrl;
	String outputUri;
	String treeUri;
	String overwriteUri;
    String mediaStoreUri;
    String subFolderPath;
	String outputFolderPath;
	String overwritePath;
	String outputPath;
	String outputName;
	String fileName;
	FileName fileNameMode;
	MimeType mimeTypeMode;
	String mimeType;
	String userAgent;
	Map<String, String> headers = Collections.emptyMap();
	String cookies;
	String checksumAlgorithm;
	String checksumValue;
	int maxRetryCount;
	int bufferSize;
	Priority priority = Priority.NORMAL;
	boolean wifiOnly;
	Status status = Status.PAUSED;
	int progress;
	long createdAt;
	long updatedAt;
	long bytesDownloaded;
	long totalBytes = -1;
	String eTag;
	String lastModified;
	boolean deleteOnRemoval;
	boolean lockedInQueue;
    boolean checksumFailed;
}
