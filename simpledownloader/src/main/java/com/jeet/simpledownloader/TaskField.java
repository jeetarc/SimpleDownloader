package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;

/**
* Use this to restore specific tasks (e.g., by ID, status, or MIME type).
* 
* <p>Predefined fields are ready to use: 
* {@link TaskField#ID}, {@link TaskField#STATUS}, {@link TaskField#MIME_TYPE}</p>
*/
public final class TaskField<T> {
	final String column;
	final Class<T> type;
	final ValueType valueType;
	
	enum ValueType {
		NORMAL, BOOLEAN, STATUS, PRIORITY
	}
	
	public static final TaskField<Long> ID = new TaskField<Long>("id", Long.class, ValueType.NORMAL);
	public static final TaskField<String> OWNER_ID = new TaskField<String>("owner_id", String.class, ValueType.NORMAL);
	public static final TaskField<String> FILE_URL = new TaskField<String>("file_url", String.class, ValueType.NORMAL);
	public static final TaskField<Status> STATUS = new TaskField<Status>("status", Status.class, ValueType.STATUS);
	public static final TaskField<Priority> PRIORITY = new TaskField<Priority>("priority", Priority.class, ValueType.PRIORITY);
	public static final TaskField<String> MIME_TYPE = new TaskField<String>("mime_type", String.class, ValueType.NORMAL);
	public static final TaskField<String> FILE_NAME = new TaskField<String>("output_file_name", String.class, ValueType.NORMAL);
	public static final TaskField<Long> CREATED_AT = new TaskField<Long>("created_at", Long.class, ValueType.NORMAL);
	public static final TaskField<Boolean> WIFI_ONLY = new TaskField<Boolean>("wifi_only", Boolean.class, ValueType.BOOLEAN);
	public static final TaskField<Integer> PROGRESS = new TaskField<Integer>("progress", Integer.class, ValueType.NORMAL);
	public static final TaskField<Long> BYTES_DOWNLOADED = new TaskField<Long>("bytes_downloaded", Long.class, ValueType.NORMAL);
	public static final TaskField<Long> TOTAL_BYTES = new TaskField<Long>("total_bytes", Long.class, ValueType.NORMAL);
	public static final TaskField<Uri> OUTPUT_URI = new TaskField<Uri>("output_uri", Uri.class, ValueType.NORMAL);
	public static final TaskField<String> OUTPUT_PATH = new TaskField<String>("output_path", String.class, ValueType.NORMAL);
	public static final TaskField<Uri> OVERWRITE_URI = new TaskField<Uri>("overwrite_uri", Uri.class, ValueType.NORMAL);
	public static final TaskField<String> OVERWRITE_PATH = new TaskField<String>("overwrite_path", String.class, ValueType.NORMAL);
	public static final TaskField<Uri> OUTPUT_FOLDER_URI = new TaskField<Uri>("tree_uri", Uri.class, ValueType.NORMAL);
	public static final TaskField<String> OUTPUT_FOLDER_PATH = new TaskField<String>("output_folder_path", String.class, ValueType.NORMAL);
	public static final TaskField<String> SUB_FOLDER_PATH = new TaskField<String>("sub_folder_path", String.class, ValueType.NORMAL);
	public static final TaskField<Boolean> DELETE_ON_REMOVAL = new TaskField<Boolean>("delete_on_removal", Boolean.class, ValueType.BOOLEAN);
	public static final TaskField<Boolean> LOCKED_IN_QUEUE = new TaskField<Boolean>("locked_in_queue", Boolean.class, ValueType.BOOLEAN);
	
	TaskField(String col, Class<T> type, ValueType val) {
		if (col == null || col.isEmpty()) throw new IllegalArgumentException("TaskField column cannot be null or empty.");
		if (type == null) throw new IllegalArgumentException("TaskField valueClass cannot be null.");
		if (val == null) throw new IllegalArgumentException("TaskField valueType cannot be null.");
		
		this.column = col;
		this.type = type;
		this.valueType = val;
	}
	
	String getColumn() {
		return column;
	}
	
	Class<T> getValueClass() {
		return type;
	}
	
	ValueType getValueType() {
		return valueType;
	}
	
	void validateValue(T value) {
		if (value == null) throw new IllegalArgumentException("Value cannot be null for TaskField: " + column);
		if (!type.isInstance(value)) throw new IllegalArgumentException("Invalid value type for TaskField " + column + ". Expected " + type.getSimpleName() + " but got " + value.getClass().getSimpleName());
	}
	
	boolean matches(DownloadTask task, T expected) {
		Object actual;
		
		switch (column) {
			case "id": actual = task.mId;
			break;
			
			case "owner_id": actual = task.mOwnerId;
			break;
			
			case "file_url": actual = task.mFileUrl;
			break;
			
			case "status": actual = task.status;
			break;
			
			case "priority": actual = task.mPriority;
			break;
			
			case "mime_type": actual = task.mMimeType;
			break;
			
			case "output_file_name": actual = task.getFileName();
			break;
			
			case "created_at": actual = task.mCreatedAt;
			break;
			
			case "wifi_only": actual = task.mWifiOnly;
			break;
			
			case "progress": actual = task.mProgress;
			break;
			
			case "bytes_downloaded": actual = task.mBytesDownloaded;
			break;
			
			case "total_bytes": actual = task.mTotalBytes;
			break;
			
			case "output_uri": actual = task.mOutputUri;
			break;
			
			case "output_path": actual = task.mOutputPath;
			break;
			
			case "overwrite_uri": actual = task.mOverwriteUri;
			break;
			
			case "overwrite_path": actual = task.mOverwritePath;
			break;
			
			case "tree_uri": actual = task.mTreeUri;
			break;
			
			case "output_folder_path": actual = task.mOutputFolderPath;
			break;
			
			case "sub_folder_path": actual = task.mSubFolderPath;
			break;
			
			case "delete_on_removal": actual = task.mDeleteOnRemoval;
			break;
			
			case "locked_in_queue": actual = task.mLockedInQueue;
			break;
			
			default: throw new IllegalArgumentException("Unsupported TaskField: " + column);
		}
		
		return actual == expected || (actual != null && actual.equals(expected));
	}
}
