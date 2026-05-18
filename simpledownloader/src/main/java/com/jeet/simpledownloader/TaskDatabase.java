package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
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
import static com.jeet.simpledownloader.SimpleDownloader.networkManager;

public class TaskDatabase extends SQLiteOpenHelper {
	private static final String DB_NAME = "simple_downloader.db";
	private static final int VERSION = 5;
	private static final String TABLE = "tasks";
	public volatile SimpleDownloader.Listener mActiveListener;
	
	static class TaskRecord {
		public final long id;
		public final String fileUrl;
		public final String outputUri;
		public final String treeUri;
		public final String overwriteUri;
		public final String fileName;
		public final String mimeType;
		public final String userAgent;
		public final String headers;
		public final String cookies;
		public final int retryCount;
		public final int connectTimeout;
		public final int readTimeout;
		public final long progressInterval;
		public final int bufferSize;
		public final String priority;
		public final boolean wifiOnly;
		public final String status;
		public final long bytesDownloaded;
		public final long totalBytes;
		public final String eTag;
		public final String lastModified;
		public final int progress;
		public final long createdAt;
		public final boolean lockedInQueue;
		public final boolean deleteOnRemoval;
		
		TaskRecord(long id, String fileUrl, String outputUri, String treeUri, String overwriteUri,
		String fileName, String mimeType, String userAgent, String headers, String cookies,
		int retryCount, int connectTimeout, int readTimeout, long progressInterval, int bufferSize, String priority, 
		boolean wifiOnly, String status, int progress, long createdAt, long bytesDownloaded, long totalBytes, String eTag, String lastModified, boolean deleteOnRemoval, boolean lockedInQueue) {
			this.id = id;
			this.fileUrl = fileUrl;
			this.outputUri = outputUri;
			this.treeUri = treeUri;
			this.overwriteUri = overwriteUri;
			this.fileName = fileName;
			this.mimeType = mimeType;
			this.userAgent = userAgent;
			this.headers = headers;
			this.cookies = cookies;
			this.retryCount = retryCount;
			this.connectTimeout = connectTimeout;
			this.readTimeout = readTimeout;
			this.progressInterval = progressInterval;
			this.bufferSize = bufferSize;
			this.priority = priority;
			this.wifiOnly = wifiOnly;
			this.status = status;
			this.bytesDownloaded = bytesDownloaded;
			this.totalBytes = totalBytes;
			this.eTag = eTag;
			this.lastModified = lastModified;
			this.progress = progress;
			this.createdAt = createdAt;
			this.lockedInQueue = lockedInQueue;
			this.deleteOnRemoval = deleteOnRemoval;
		}
	}
	
	TaskDatabase(Context context) {
		super(context, DB_NAME, null, VERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE + " ("
		+ "id                INTEGER PRIMARY KEY,"
		+ "file_url          TEXT,"
		+ "output_uri        TEXT,"
		+ "tree_uri          TEXT,"
		+ "overwrite_uri     TEXT,"
		+ "file_name         TEXT,"
		+ "mime_type         TEXT,"
		+ "user_agent        TEXT,"
		+ "headers           TEXT,"
		+ "cookies           TEXT,"
		+ "retry_count       INTEGER,"
		+ "connect_timeout   INTEGER,"
		+ "read_timeout      INTEGER,"
		+ "progress_interval INTEGER,"
		+ "buffer_size       INTEGER,"
		+ "priority          TEXT,"
		+ "wifi_only         INTEGER,"
		+ "status            TEXT,"
		+ "progress          INTEGER,"
		+ "bytes_downloaded  INTEGER,"
		+ "total_bytes       INTEGER,"
		+ "etag              TEXT,"
		+ "last_modified     TEXT,"
		+ "created_at        INTEGER,"
		+ "delete_on_removal INTEGER,"
		+ "locked_in_queue   INTEGER"
		+ ")");
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 4) addColumn(db, "created_at INTEGER DEFAULT 0");
		if (oldVersion < 5) {
			addColumn(db, "total_bytes INTEGER DEFAULT -1");
			addColumn(db, "etag TEXT");
			addColumn(db, "last_modified TEXT");
		}
	}
	
	private void addColumn(SQLiteDatabase db, String column) {
		try {
			db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column);
		} catch (Exception ignored) {}
	}
	
	static String headersToJson(Map<String, String> headers) {
		try {
			JSONObject obj = new JSONObject();
			for (Map.Entry<String, String> e : headers.entrySet())
			obj.put(e.getKey(), e.getValue());
			return obj.toString();
		} catch (Exception e) { return "{}"; }
	}
	
	static Map<String, String> headersFromJson(String json) {
		Map<String, String> map = new HashMap<>();
		try {
			if (json == null || json.isEmpty()) return map;
			JSONObject obj = new JSONObject(json);
			Iterator<String> keys = obj.keys();
			while (keys.hasNext()) {
				String key = keys.next();
				map.put(key, obj.getString(key));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
	}
	
	void saveTask(long id, String fileUrl, String outputUri, String treeUri, String overwriteUri,
	String fileName, String mimeType, String userAgent, Map<String, String> headers, String cookies, int retryCount, int connectTimeout,
	int readTimeout, long progressInterval, int bufferSize, String priority, boolean wifiOnly,
	String status, int progress, long createdAt, long bytesDownloaded, boolean deleteOnRemoval, boolean lockedInQueue) {
		ContentValues cv = new ContentValues();
		cv.put("id", id);
		cv.put("file_url", fileUrl);
		cv.put("output_uri", outputUri);
		cv.put("tree_uri", treeUri);
		cv.put("overwrite_uri", overwriteUri);
		cv.put("file_name", fileName);
		cv.put("mime_type", mimeType);
		cv.put("user_agent", userAgent);
		cv.put("headers", headersToJson(headers));
		cv.put("cookies", cookies);
		cv.put("retry_count", retryCount);
		cv.put("connect_timeout", connectTimeout);
		cv.put("read_timeout", readTimeout);
		cv.put("progress_interval", progressInterval);
		cv.put("buffer_size", bufferSize);
		cv.put("priority", priority);
		cv.put("wifi_only", wifiOnly ? 1 : 0);
		cv.put("status", status);
		cv.put("progress", progress);
		cv.put("created_at", createdAt);
		cv.put("bytes_downloaded", bytesDownloaded);
		cv.put("total_bytes", -1);
		cv.put("etag", (String) null);
		cv.put("last_modified", (String) null);
		cv.put("delete_on_removal", deleteOnRemoval ? 1 : 0);
		cv.put("locked_in_queue", lockedInQueue ? 1 : 0);
		getWritableDatabase().insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
	}
	
	void updateStatus(long id, String status, long bytesDownloaded, int progress) {
		ContentValues cv = new ContentValues();
		cv.put("status", status);
		cv.put("progress", progress);
		cv.put("bytes_downloaded", bytesDownloaded);
		getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
	}
	
	void updateResumeData(long id, long bytesDownloaded, long totalBytes, String eTag, String lastModified) {
		ContentValues cv = new ContentValues();
		cv.put("bytes_downloaded", bytesDownloaded);
		cv.put("total_bytes", totalBytes);
		cv.put("etag", eTag);
		cv.put("last_modified", lastModified);
		getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
	}
	
	void updateOutputUri(long id, Uri outputUri) {
		ContentValues cv = new ContentValues();
		cv.put("output_uri", outputUri != null ? outputUri.toString() : null);
		getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
	}
	
	private DownloadTask buildAndRegister(TaskRecord r) {
		if (r == null) return null;
		if (SimpleDownloader.sRegistry.containsKey(r.id)) return SimpleDownloader.sRegistry.get(r.id);
		
		Uri treeUri = r.treeUri != null ? Uri.parse(r.treeUri) : null;
		Uri overwriteUri = r.overwriteUri != null ? Uri.parse(r.overwriteUri) : null;
		Uri outputUri = r.outputUri != null ? Uri.parse(r.outputUri) : null;
		Priority priority = Priority.NORMAL;
		try { priority = Priority.valueOf(r.priority); } catch (Exception e) { e.printStackTrace(); }
		
		final DownloadTask task = new DownloadTask(
		SimpleDownloader.sAppContext, treeUri, r.fileName, r.mimeType, r.fileUrl,
		r.userAgent, headersFromJson(r.headers), r.cookies,
		r.id, r.retryCount, r.connectTimeout, r.readTimeout,
		overwriteUri, r.progressInterval, r.bufferSize,
		priority, r.wifiOnly, r.deleteOnRemoval, r.lockedInQueue, mActiveListener);
		
		task.mBytesDownloaded = r.bytesDownloaded;
		task.mTotalBytes = r.totalBytes;
		task.mProgress = r.progress;
		task.mCreatedAt = r.createdAt > 0 ? r.createdAt : System.currentTimeMillis();
		task.mOutputFileUri = outputUri != null ? outputUri : overwriteUri;
		task.mETag = r.eTag;
		task.mLastModified = r.lastModified;
		
		Status restored;
		try {
			restored = Status.valueOf(r.status);
		} catch (Exception e) {
			restored = Status.PAUSED;
		}
		
		boolean wasActiveBeforeClose = restored == Status.DOWNLOADING || restored == Status.CONNECTING || restored == Status.RETRYING;
		boolean wasWaitingForNetwork = restored == Status.WAITING_FOR_NETWORK;
		boolean noPreferredNetwork = networkManager.isNetworkAvailable() && task.mWifiOnly && networkManager.getNetworkType() != SimpleDownloader.NETWORK_TYPE_WIFI;
		final Status finalStatus;
		
		synchronized (SimpleDownloader.class) {
			SimpleDownloader.sRegistry.put(r.id, task);
			SimpleDownloader.addTaskToListLocked(task);
			
			if (wasActiveBeforeClose || restored == Status.PAUSED || restored == Status.QUEUED) {
				if (!SlotHandler.pauseRestoredTaskLocked(task)) SlotHandler.restoreQueuedTaskLocked(task);
				
			} else if (wasWaitingForNetwork) {
				if (noPreferredNetwork) {
					task.setStatus(Status.WAITING_FOR_NETWORK);
					if (!networkManager.getWaitingForPreferredNetwork().contains(task)) networkManager.getWaitingForPreferredNetwork().add(task);
					
				} else if (!SlotHandler.pauseRestoredTaskLocked(task)) SlotHandler.restoreQueuedTaskLocked(task);
				
			} else task.setStatus(restored);
		}
		finalStatus = task.status;
		
		ListenerDispatcher.onLoadDatabase(task);
		if (finalStatus == Status.PAUSED) {
			ListenerDispatcher.onPaused(task);
			
		} else if (finalStatus == Status.QUEUED) {
			ListenerDispatcher.onQueued(task);
			
		} else if (finalStatus == Status.WAITING_FOR_NETWORK) {
			ListenerDispatcher.onWaitingForNetwork(task);
		}
		
		return task;
	}
	
	private TaskRecord loadTaskRecord(long id) {
		Cursor c = getReadableDatabase().query(TABLE, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
		TaskRecord r = c.moveToFirst() ? fromCursor(c) : null;
		c.close(); return r;
	}
	
	private List<TaskRecord> loadTasksByPriority(String priority) {
		List<TaskRecord> list = new ArrayList<>();
		Cursor c = getReadableDatabase().query(TABLE, null, "priority=?", new String[]{priority}, null, null, null);
		try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
		return list;
	}
	
	private List<TaskRecord> loadTasksByStatus(String status) {
		List<TaskRecord> list = new ArrayList<>();
		Cursor c = getReadableDatabase().query(TABLE, null, "status=?", new String[]{status}, null, null, null);
		try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
		return list;
	}
	
	private List<TaskRecord> loadTasksByMimeType(String mimeType) {
		List<TaskRecord> list = new ArrayList<>();
		Cursor c = getReadableDatabase().query(TABLE, null, "mime_type=?", new String[]{mimeType}, null, null, null);
		try { while (c.moveToNext()) list.add(fromCursor(c)); } finally { c.close(); }
		return list;
	}
	
	private List<TaskRecord> loadAllTasks() {
		List<TaskRecord> tasks = new ArrayList<>();
		Cursor c = getReadableDatabase().query(TABLE, null, null, null, null, null, null);
		try { while (c.moveToNext()) tasks.add(fromCursor(c)); } finally { c.close(); }
		return tasks;
	}
	
	private long getLongSafe(Cursor c, String column, long fallback) {
		int index = c.getColumnIndex(column);
		return index >= 0 && !c.isNull(index) ? c.getLong(index) : fallback;
	}
	
	private String getStringSafe(Cursor c, String column) {
		int index = c.getColumnIndex(column);
		return index >= 0 && !c.isNull(index) ? c.getString(index) : null;
	}
	
	private TaskRecord fromCursor(Cursor c) {
		return new TaskRecord(
		c.getLong  (c.getColumnIndexOrThrow("id")),
		c.getString(c.getColumnIndexOrThrow("file_url")),
		c.getString(c.getColumnIndexOrThrow("output_uri")),
		c.getString(c.getColumnIndexOrThrow("tree_uri")),
		c.getString(c.getColumnIndexOrThrow("overwrite_uri")),
		c.getString(c.getColumnIndexOrThrow("file_name")),
		c.getString(c.getColumnIndexOrThrow("mime_type")),
		c.getString(c.getColumnIndexOrThrow("user_agent")),
		c.getString(c.getColumnIndexOrThrow("headers")),
		c.getString(c.getColumnIndexOrThrow("cookies")),
		c.getInt   (c.getColumnIndexOrThrow("retry_count")),
		c.getInt   (c.getColumnIndexOrThrow("connect_timeout")),
		c.getInt   (c.getColumnIndexOrThrow("read_timeout")),
		c.getLong  (c.getColumnIndexOrThrow("progress_interval")),
		c.getInt   (c.getColumnIndexOrThrow("buffer_size")),
		c.getString(c.getColumnIndexOrThrow("priority")),
		c.getInt   (c.getColumnIndexOrThrow("wifi_only")) == 1,
		c.getString(c.getColumnIndexOrThrow("status")),
		c.getInt   (c.getColumnIndexOrThrow("progress")),
		c.getLong  (c.getColumnIndexOrThrow("created_at")),
		c.getLong  (c.getColumnIndexOrThrow("bytes_downloaded")),
		getLongSafe(c, "total_bytes", -1),
		getStringSafe(c, "etag"),
		getStringSafe(c, "last_modified"),
		c.getInt   (c.getColumnIndexOrThrow("delete_on_removal")) == 1,
		c.getInt   (c.getColumnIndexOrThrow("locked_in_queue")) == 1
		);
	}
	
	public void loadTaskData(long id) {
		buildAndRegister(loadTaskRecord(id));
	}
	
	public void loadTasksData(Status status) {
		for (TaskRecord r : loadTasksByStatus(status.name()))
		buildAndRegister(r);
	}
	
	public void loadTasksData(String mimeType) {
		for (TaskRecord r : loadTasksByMimeType(mimeType))
		buildAndRegister(r);
	}
	
	public void loadTasksData(Priority priority) {
		for (TaskRecord r : loadTasksByPriority(priority.name()))
		buildAndRegister(r);
	}
	
	public void loadTasksData() {
		for (TaskRecord r : loadAllTasks())
		buildAndRegister(r);
	}
	
	public static void removeTasksData() {
		if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeAllTasks();
	}
	
	public static void removeTaskData(long id) {
		if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.removeTask(id);
	}
	
	void updateTaskData(long id, ContentValues cv) {
		getWritableDatabase().update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
	}
	
	void removeTask(long id) {
		getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
	}
	
	void removeAllTasks() {
		getWritableDatabase().delete(TABLE, null, null);
	}
}
