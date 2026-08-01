package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;


class TaskDatabase extends SQLiteOpenHelper {
	private volatile boolean mClosed = false;
	private static final String DB_NAME = "SimpleDownloader.db";
	private static final int VERSION = 1;
	private static final String TABLE_TASKS = "tasks";
	
	private static final String ID = "id";
	private static final String FILE_URL = "file_url";
	private static final String OUTPUT_URI = "output_uri";
	private static final String TREE_URI = "tree_uri";
	private static final String OVERWRITE_URI = "overwrite_uri";
	private static final String OUTPUT_FOLDER_PATH = "output_folder_path";
	private static final String OVERWRITE_PATH = "overwrite_path";
	private static final String OUTPUT_PATH = "output_path";
	private static final String OUTPUT_FILE_NAME = "output_file_name";
	private static final String FILE_NAME = "file_name";
	private static final String FILE_NAME_MODE = "file_name_mode";
	private static final String MIME_TYPE_MODE = "mime_type_mode";
	private static final String MIME_TYPE = "mime_type";
	private static final String USER_AGENT = "user_agent";
	private static final String HEADERS = "headers";
	private static final String COOKIES = "cookies";
	private static final String CHECKSUM_ALGORITHM = "checksum_algorithm";
	private static final String CHECKSUM_VALUE = "checksum_value";
	private static final String BUFFER_SIZE = "buffer_size";
	private static final String PRIORITY = "priority";
	private static final String WIFI_ONLY = "wifi_only";
	private static final String STATUS = "status";
	private static final String PROGRESS = "progress";
	private static final String CREATED_AT = "created_at";
	private static final String UPDATED_AT = "updated_at";
	private static final String BYTES_DOWNLOADED = "bytes_downloaded";
	private static final String TOTAL_BYTES = "total_bytes";
	private static final String ETAG = "etag";
	private static final String LAST_MODIFIED = "last_modified";
	private static final String DELETE_ON_REMOVAL = "delete_on_removal";
	private static final String LOCKED_IN_QUEUE = "locked_in_queue";
	private static final String CHECKSUM_FAILED = "checksum_failed";
	
	TaskDatabase(Context context) {
		super(context, DB_NAME, null, VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		createTasksTable(db);
		createIndexes(db);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		createTasksTable(db);
		ensureColumn(db, TABLE_TASKS, UPDATED_AT, "INTEGER DEFAULT 0");
		createIndexes(db);
	}
	
	@Override
	public synchronized void close() {
		mClosed = true;
		super.close();
	}
	
	private void createTasksTable(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " (" +
		ID + " INTEGER PRIMARY KEY," +
		FILE_URL + " TEXT NOT NULL," +
		OUTPUT_URI + " TEXT," +
		TREE_URI + " TEXT," +
		OVERWRITE_URI + " TEXT," +
		OUTPUT_FOLDER_PATH + " TEXT," +
		OVERWRITE_PATH + " TEXT," +
		OUTPUT_PATH + " TEXT," +
		OUTPUT_FILE_NAME + " TEXT," +
		FILE_NAME + " TEXT," +
		FILE_NAME_MODE + " TEXT," +
		MIME_TYPE_MODE + " TEXT," +
		MIME_TYPE + " TEXT," +
		USER_AGENT + " TEXT," +
		HEADERS + " TEXT," +
		COOKIES + " TEXT," +
		CHECKSUM_ALGORITHM + " TEXT," +
		CHECKSUM_VALUE + " TEXT," +
		BUFFER_SIZE + " INTEGER DEFAULT 16384," +
		PRIORITY + " INTEGER DEFAULT 2," +
		WIFI_ONLY + " INTEGER DEFAULT 0," +
		STATUS + " INTEGER NOT NULL," +
		PROGRESS + " INTEGER DEFAULT 0," +
		BYTES_DOWNLOADED + " INTEGER DEFAULT 0," +
		TOTAL_BYTES + " INTEGER DEFAULT -1," +
		ETAG + " TEXT," +
		LAST_MODIFIED + " TEXT," +
		CREATED_AT + " INTEGER NOT NULL," +
		UPDATED_AT + " INTEGER NOT NULL," +
		DELETE_ON_REMOVAL + " INTEGER DEFAULT 0," +
		LOCKED_IN_QUEUE + " INTEGER DEFAULT 0," +
		CHECKSUM_FAILED + " INTEGER DEFAULT 0" +
		")");
	}
	
	private void createIndexes(SQLiteDatabase db) {
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_status ON " + TABLE_TASKS + "(" + STATUS + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_priority ON " + TABLE_TASKS + "(" + PRIORITY + ")");
		db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_mime_type ON " + TABLE_TASKS + "(" + MIME_TYPE + ")");
	}
	
	private void ensureColumn(SQLiteDatabase db, String table, String column, String type) {
		Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
		try {
			while (c.moveToNext()) {
				if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) return;
			}
		} finally {
			c.close();
		}
		db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
	}
	
	void saveTask(DownloadTask task, Status status) {
		if (task == null || mClosed) return;
		getWritableDatabase().insertWithOnConflict(TABLE_TASKS, null, toValues(task, status), SQLiteDatabase.CONFLICT_REPLACE);
	}
	
	void updateTaskData(long id, ContentValues values) {
		if (values == null || mClosed) return;
		if (!values.containsKey(UPDATED_AT)) values.put(UPDATED_AT, System.currentTimeMillis());
		getWritableDatabase().update(TABLE_TASKS, values, ID + "=?", new String[]{String.valueOf(id)});
	}
	
	void updateStatus(long id, Status status, long bytesDownloaded, int progress) {
		ContentValues values = new ContentValues();
		values.put(STATUS, status == null ? Status.PAUSED.getCode() : status.getCode());
		values.put(PROGRESS, progress);
		values.put(BYTES_DOWNLOADED, bytesDownloaded);
		values.put(UPDATED_AT, System.currentTimeMillis());
		updateTaskData(id, values);
	}
	
	void updateResumeData(long id, long bytesDownloaded, long totalBytes, int progress, String eTag, String lastModified) {
		ContentValues values = new ContentValues();
		values.put(BYTES_DOWNLOADED, bytesDownloaded);
		values.put(TOTAL_BYTES, totalBytes);
		values.put(PROGRESS, progress);
		values.put(ETAG, eTag);
		values.put(LAST_MODIFIED, lastModified);
		values.put(UPDATED_AT, System.currentTimeMillis());
		updateTaskData(id, values);
	}
	
	void updateOutputUri(long id, Uri outputUri) {
		ContentValues values = new ContentValues();
		values.put(OUTPUT_URI, outputUri != null ? outputUri.toString() : null);
		values.put(UPDATED_AT, System.currentTimeMillis());
		updateTaskData(id, values);
	}
	
	void updateOutputFileName(long id, String outputName) {
		ContentValues values = new ContentValues();
		values.put(OUTPUT_FILE_NAME, outputName);
		values.put(UPDATED_AT, System.currentTimeMillis());
		updateTaskData(id, values);
	}
	
	void updateOutputData(DownloadTask task) {
		if (task == null) return;
		ContentValues values = new ContentValues();
		values.put(OUTPUT_URI, task.mOutputUri != null ? task.mOutputUri.toString() : null);
		values.put(OUTPUT_FILE_NAME, task.mOutputName != null ? task.mOutputName : task.mFileName);
		values.put(OUTPUT_PATH, task.mOutputPath);
		values.put(UPDATED_AT, System.currentTimeMillis());
		updateTaskData(task.mId, values);
	}
	
	void updateResolvedMetadata(DownloadTask task) {
		if (task == null) return;
		ContentValues values = new ContentValues();
		values.put(FILE_NAME, task.mFileName);
		values.put(FILE_NAME_MODE, task.mFileNameMode != null ? task.mFileNameMode.name() : null);
		values.put(MIME_TYPE, task.mMimeType);
		values.put(MIME_TYPE_MODE, task.mMimeTypeMode != null ? task.mMimeTypeMode.name() : null);
		updateTaskData(task.mId, values);
	}
	
	void updateWifiOnly(long id, boolean enable) {
		ContentValues values = new ContentValues();
		values.put(WIFI_ONLY, enable ? 1 : 0);
		updateTaskData(id, values);
	}
	
	void updatePriority(long id, Priority priority) {
		ContentValues values = new ContentValues();
		values.put(PRIORITY, priority == null ? Priority.NORMAL.getWeight() : priority.getWeight());
		updateTaskData(id, values);
	}
	
	void updateLockedInQueue(long id, boolean enable) {
		ContentValues values = new ContentValues();
		values.put(LOCKED_IN_QUEUE, enable ? 1 : 0);
		updateTaskData(id, values);
	}
	
	void updateDeleteOnRemoval(long id, boolean enable) {
		ContentValues values = new ContentValues();
		values.put(DELETE_ON_REMOVAL, enable ? 1 : 0);
		updateTaskData(id, values);
	}
	
	void updateChecksumFailed(long id, boolean failed) {
		ContentValues values = new ContentValues();
		values.put(CHECKSUM_FAILED, failed ? 1 : 0);
		updateTaskData(id, values);
	}
	
	void removeTask(long id) {
		if (mClosed) return;
		getWritableDatabase().delete(TABLE_TASKS, ID + "=?", new String[]{String.valueOf(id)});
	}
	
	void removeAllTasks() {
		if (mClosed) return;
		getWritableDatabase().delete(TABLE_TASKS, null, null);
	}
    
    void clearFinishedInternalData(long id) {
		if (mClosed) return;
		ContentValues values = new ContentValues();
		values.putNull(ETAG);
		values.putNull(LAST_MODIFIED);
		values.putNull(CHECKSUM_ALGORITHM);
		values.putNull(CHECKSUM_VALUE);
		values.putNull(FILE_NAME_MODE);
		values.putNull(MIME_TYPE_MODE);
		values.put(CHECKSUM_FAILED, 0);
		updateTaskData(id, values);
	}
	
	private ContentValues toValues(DownloadTask task, Status saveStatus) {
		long now = System.currentTimeMillis();
		ContentValues values = new ContentValues();
		values.put(ID, task.mId);
		values.put(FILE_URL, task.mFileUrl);
		values.put(OUTPUT_URI, task.mOutputUri != null ? task.mOutputUri.toString() : null);
		values.put(TREE_URI, task.mTreeUri != null ? task.mTreeUri.toString() : null);
		values.put(OVERWRITE_URI, task.mOverwriteUri != null ? task.mOverwriteUri.toString() : null);
		values.put(OUTPUT_FOLDER_PATH, task.mOutputFolderPath);
		values.put(OVERWRITE_PATH, task.mOverwritePath);
		values.put(OUTPUT_PATH, task.mOutputPath);
		values.put(OUTPUT_FILE_NAME, task.mOutputName != null ? task.mOutputName : task.mFileName);
		values.put(FILE_NAME, task.mFileName);
		values.put(FILE_NAME_MODE, task.mFileNameMode != null ? task.mFileNameMode.name() : null);
		values.put(MIME_TYPE_MODE, task.mMimeTypeMode != null ? task.mMimeTypeMode.name() : null);
		values.put(MIME_TYPE, task.mMimeType);
		values.put(USER_AGENT, task.mUserAgent);
		values.put(HEADERS, headersToJson(task.mHeaders));
		values.put(COOKIES, task.mCookies);
		values.put(CHECKSUM_ALGORITHM, task.mChecksumAlgorithm);
		values.put(CHECKSUM_VALUE, task.mChecksumValue);
		values.put(BUFFER_SIZE, task.mBufferSize);
		values.put(PRIORITY, task.mPriority.getWeight());
		values.put(WIFI_ONLY, task.mWifiOnly ? 1 : 0);
		values.put(STATUS, saveStatus == null ? task.status.getCode() : saveStatus.getCode());
		values.put(PROGRESS, task.mProgress);
		values.put(BYTES_DOWNLOADED, task.mBytesDownloaded);
		values.put(TOTAL_BYTES, task.mTotalBytes);
		values.put(ETAG, task.mETag);
		values.put(LAST_MODIFIED, task.mLastModified);
		values.put(CREATED_AT, task.mCreatedAt > 0 ? task.mCreatedAt : now);
		values.put(UPDATED_AT, now);
		values.put(DELETE_ON_REMOVAL, task.mDeleteOnRemoval ? 1 : 0);
		values.put(LOCKED_IN_QUEUE, task.mLockedInQueue ? 1 : 0);
		values.put(CHECKSUM_FAILED, task.mChecksumFailed ? 1 : 0);
		return values;
	}
	
	private TaskState fromCursor(Cursor c) {
		TaskState state = new TaskState();
		state.id = getLong(c, ID, -1);
		state.fileUrl = getString(c, FILE_URL);
		state.outputUri = getString(c, OUTPUT_URI);
		state.treeUri = getString(c, TREE_URI);
		state.overwriteUri = getString(c, OVERWRITE_URI);
		state.outputFolderPath = getString(c, OUTPUT_FOLDER_PATH);
		state.overwritePath = getString(c, OVERWRITE_PATH);
		state.outputPath = getString(c, OUTPUT_PATH);
		state.outputName = getString(c, OUTPUT_FILE_NAME);
		state.fileName = getString(c, FILE_NAME);
		state.fileNameMode = FileName.fromDatabaseValue(getString(c, FILE_NAME_MODE));
		state.mimeTypeMode = MimeType.fromDatabaseValue(getString(c, MIME_TYPE_MODE));
		state.mimeType = getString(c, MIME_TYPE);
		state.userAgent = getString(c, USER_AGENT);
		state.headers = headersFromJson(getString(c, HEADERS));
		state.cookies = getString(c, COOKIES);
		state.checksumAlgorithm = getString(c, CHECKSUM_ALGORITHM);
		state.checksumValue = getString(c, CHECKSUM_VALUE);
		state.bufferSize = getInt(c, BUFFER_SIZE, 16384);
		state.priority = Priority.fromDatabaseValue(getString(c, PRIORITY));
		state.wifiOnly = getBoolean(c, WIFI_ONLY, false);
		state.status = Status.fromDatabaseValue(getString(c, STATUS));
		state.progress = getInt(c, PROGRESS, 0);
		state.createdAt = getLong(c, CREATED_AT, 0);
		state.updatedAt = getLong(c, UPDATED_AT, 0);
		state.bytesDownloaded = getLong(c, BYTES_DOWNLOADED, 0);
		state.totalBytes = getLong(c, TOTAL_BYTES, -1);
		state.eTag = getString(c, ETAG);
		state.lastModified = getString(c, LAST_MODIFIED);
		state.deleteOnRemoval = getBoolean(c, DELETE_ON_REMOVAL, false);
		state.lockedInQueue = getBoolean(c, LOCKED_IN_QUEUE, false);
		state.checksumFailed = getBoolean(c, CHECKSUM_FAILED, false);
		return state;
	}
	
	<T> List<TaskState> loadTaskStates(TaskField<T> field, T value) {
		if (mClosed) return new ArrayList<TaskState>();
		FieldQuery query = createFieldQuery(field, value);
		return queryTaskStates(query.selection, query.args, CREATED_AT + " ASC", null);
	}
	
	<T> TaskState loadLatestTaskState(TaskField<T> field, T value) {
		if (mClosed) return null;
		FieldQuery query = createFieldQuery(field, value);
		List<TaskState> states = queryTaskStates(query.selection, query.args, CREATED_AT + " DESC", "1");
		return states.isEmpty() ? null : states.get(0);
	}
	
	List<TaskState> loadAllTaskStates() {
		if (mClosed) return new ArrayList<TaskState>();
		return queryTaskStates(null, null, CREATED_AT + " ASC", null);
	}
	
	private List<TaskState> queryTaskStates(String selection, String[] args, String orderBy, String limit) {
		List<TaskState> tasks = new ArrayList<TaskState>();
		if (mClosed) return tasks;
		Cursor cursor = getReadableDatabase().query(TABLE_TASKS, null, selection, args, null, null, orderBy, limit);
		
		try {
			while (cursor.moveToNext()) {
				tasks.add(fromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		
		return tasks;
	}
	
	private static <T> FieldQuery createFieldQuery(TaskField<T> field, T value) {
		if (field == null) throw new IllegalArgumentException("TaskField cannot be null.");
		if (value == null) return new FieldQuery(field.column + " IS NULL", null);
		
		if (!field.type.isInstance(value)) {
			throw new IllegalArgumentException("Expected " + field.type.getSimpleName() + " for field " + field.column + ", but received " + value.getClass().getSimpleName() + ".");
		}
		
		switch (field.valueType) {
			case BOOLEAN:
			return new FieldQuery(field.column + "=?",
			new String[] {
				((Boolean) value) ? "1" : "0"
			});
			
			case STATUS:
			Status status = (Status) value;
			return new FieldQuery("(" + field.column + "=? OR " + field.column + "=?)",
			new String[] {
				String.valueOf(status.getCode()),
				status.name()
			});
			
			case PRIORITY:
			Priority priority = (Priority) value;
			return new FieldQuery("(" + field.column + "=? OR " + field.column + "=?)",
			new String[] {
				String.valueOf(priority.getWeight()),
				priority.name()
			});
			
			case NORMAL:
			default:
			return new FieldQuery(field.column + "=?",
			new String[] {
				String.valueOf(value)
			});
		}
	}
	
	static String headersToJson(Map<String, String> headers) {
		try {
			JSONObject obj = new JSONObject();
			if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) obj.put(e.getKey(), e.getValue());
			return obj.toString();
			
		} catch (Exception e) { return "{}"; }
	}
	
	static Map<String, String> headersFromJson(String json) {
		Map<String, String> map = new HashMap<String, String>();
		try {
			if (json == null || json.isEmpty()) return map;
			JSONObject obj = new JSONObject(json);
			Iterator<String> keys = obj.keys();
			
			while (keys.hasNext()) {
				String key = keys.next();
				map.put(key, obj.getString(key));
			}
			
		} catch (Exception ignored) {}
		return map;
	}
	
	private static String getString(Cursor c, String column) {
		int index = c.getColumnIndex(column);
		if (index < 0 || c.isNull(index)) return null;
		return c.getString(index);
	}
	
	private static int getInt(Cursor c, String column, int fallback) {
		int index = c.getColumnIndex(column);
		if (index < 0 || c.isNull(index)) return fallback;
		return c.getInt(index);
	}
	
	private static long getLong(Cursor c, String column, long fallback) {
		int index = c.getColumnIndex(column);
		if (index < 0 || c.isNull(index)) return fallback;
		return c.getLong(index);
	}
	
	private static double getDouble(Cursor c, String column, double fallback) {
		int index = c.getColumnIndex(column);
		if (index < 0 || c.isNull(index)) return fallback;
		return c.getDouble(index);
	}
	
	private static boolean getBoolean(Cursor c, String column, boolean fallback) {
		int index = c.getColumnIndex(column);
		if (index < 0 || c.isNull(index)) return fallback;
		return c.getInt(index) != 0;
	}
	
	private static final class FieldQuery {
		final String selection;
		final String[] args;
		
		FieldQuery(String selection, String[] args) {
			this.selection = selection;
			this.args = args;
		}
	}
}
