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
	public static final TaskField<String> FILE_URL = new TaskField<String>("file_url", String.class, ValueType.NORMAL);
    public static final TaskField<Status> STATUS = new TaskField<Status>("status", Status.class, ValueType.STATUS);
    public static final TaskField<Priority> PRIORITY = new TaskField<Priority>("priority", Priority.class, ValueType.PRIORITY);
    public static final TaskField<String> MIME_TYPE = new TaskField<String>("mime_type", String.class, ValueType.NORMAL);
	public static final TaskField<String> FILE_NAME = new TaskField<String>("output_file_name", String.class, ValueType.NORMAL);
    public static final TaskField<Long> CREATED_AT = new TaskField<Long>("created_at", Long.class, ValueType.NORMAL);
    public static final TaskField<Boolean> WIFI_ONLY = new TaskField<Boolean>("wifi_only", Boolean.class, ValueType.BOOLEAN);
    public static final TaskField<Integer> BUFFER_SIZE = new TaskField<Integer>("buffer_size", Integer.class, ValueType.NORMAL);
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
}
