package com.jeet.simpledownloader;

/*

Copyright (c) 2026 Jeet / Jeetarc.

This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import android.content.ContentValues;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.os.Environment;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.os.Bundle;
import com.jeet.simpledownloader.util.Logs;

final class OutputResolver {
	private static final Map<String, Object> sFolderLocks = new HashMap<>();
	private OutputResolver() {}
	
	static OutputState resolve(DownloadTask task) throws IOException {  
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");  
		if (task.mMediaStoreUri == null && task.mOutputDocFile == null && task.mOutputUri != null && task.mOutputFile == null) task.mOutputDocFile = DocumentFile.fromSingleUri(task.mContext, task.mOutputUri);  
		
		if (task.mOutputFile != null && task.mOutputFile.exists()) {  
			task.mOutputPath = task.mOutputFile.getAbsolutePath();  
			task.mOutputUri = createFileProviderUri(task.mContext, task.mOutputFile);  
			task.mOutputName = task.mOutputFile.getName();  
			updateOutputData(task);  
			return new OutputState(null, task.mOutputFile, task.mOutputUri, safeLength(task.mOutputFile));  
		}  
		
		if (task.mOutputDocFile != null && task.mOutputDocFile.exists()) {  
			task.mOutputUri = task.mOutputDocFile.getUri();  
			task.mOutputName = task.mOutputDocFile.getName();  
			return new OutputState(task.mOutputDocFile, null, task.mOutputUri, safeLength(task.mOutputDocFile));  
		}  
		
		if (task.mOverwritePath != null && task.mOverwritePath.length() > 0) return resolveOverwritePath(task);  
		if (task.mOverwriteUri != null) {
			if (isMediaStoreItemUri(task.mOverwriteUri)) return resolveOverwriteMediaStore(task);
			DocumentFile file = DocumentFile.fromSingleUri(task.mContext, task.mOverwriteUri);
			if (file == null || !file.exists()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid or missing overwrite file Uri.");
			task.mOutputDocFile = file;
			task.mOutputUri = file.getUri();
			task.mOutputName = file.getName();
			updateOutputData(task);
			return new OutputState(file, null, file.getUri(), safeLength(file));
		}
		
		if (task.mOutputFolderPath != null && task.mOutputFolderPath.length() > 0) return resolvePathOutput(task);  
		if (task.mMediaStoreUri != null) return resolveMediaStoreOutput(task, true);  
		if (task.mTreeUri != null) return resolveDocFileOutput(task);  
		throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output target was not resolved.");  
	}  
	
	static OutputStream openOutput(DownloadTask task, boolean append) throws IOException {  
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");  
		if (task.mOutputFile != null) return new FileOutputStream(task.mOutputFile, append);  
		if (task.mContext == null || task.mOutputUri == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output file is invalid.");  
		
		OutputStream out = task.mContext.getContentResolver().openOutputStream(task.mOutputUri, append ? "wa" : "w");  
		if (out == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot open output stream.");  
		return out;  
	}  
	
	static OutputState resolveExisting(DownloadTask task) throws IOException {
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");
		
		// MediaStore
		if (task.mMediaStoreUri != null) {
			return resolveMediaStoreOutput(task, false);
		}
		
		// Normal file output
		if (task.mOutputFile != null && task.mOutputFile.exists()) {
			task.mOutputPath = task.mOutputFile.getAbsolutePath();
			task.mOutputUri = createFileProviderUri(task.mContext, task.mOutputFile);
			task.mOutputName = task.mOutputFile.getName();
			return new OutputState(null, task.mOutputFile, task.mOutputUri, safeLength(task.mOutputFile));
		}
		
		// SAF / DocumentFile output
		if (task.mOutputDocFile != null && task.mOutputDocFile.exists()) {
			task.mOutputUri = task.mOutputDocFile.getUri();
			task.mOutputName = task.mOutputDocFile.getName();
			return new OutputState(task.mOutputDocFile, null, task.mOutputUri, safeLength(task.mOutputDocFile));
		}
		
		// Overwrite path
		if (task.mOverwritePath != null && task.mOverwritePath.length() > 0) {
			File file = new File(task.mOverwritePath);
			if (file.exists()) {
				task.mOutputFile = file;
				task.mOutputPath = file.getAbsolutePath();
				task.mOutputUri = createFileProviderUri(task.mContext, file);
				task.mOutputName = file.getName();
				return new OutputState(null, file, task.mOutputUri, resolveOverwriteResumeLength(task, safeLength(file)));
			}
		}
		
		// Overwrite URI
		if (task.mOverwriteUri != null) {
			
			if (isMediaStoreItemUri(task.mOverwriteUri)) {
				long length = getMediaStoreLength(task, task.mOverwriteUri);
				if (length >= 0) {
					task.mOutputUri = task.mOverwriteUri;
					task.mOutputName = getMediaStoreDisplayName(task, task.mOverwriteUri);
					return new OutputState(null, null, task.mOverwriteUri, resolveOverwriteResumeLength(task, length));
				}
				return new OutputState(null, null, null, 0);
			}
			
			DocumentFile file = DocumentFile.fromSingleUri(task.mContext, task.mOverwriteUri);
			if (file != null && file.exists()) {
				task.mOutputDocFile = file;
				task.mOutputUri = file.getUri();
				task.mOutputName = file.getName();
				return new OutputState(file, null, file.getUri(), resolveOverwriteResumeLength(task, safeLength(file)));
			}
		}
		
		clearOutputReferences(task);
		return new OutputState(null, null, null, 0);
	}
	
	// SAF / DOCUMENT FILE OUTPUT //  
	
	private static OutputState resolveDocFileOutput(DownloadTask task) throws IOException {  
		DocumentFile folder = DocumentFile.fromTreeUri(task.mContext, task.mTreeUri);  
		folder = resolveDocFileSubFolder(folder, task.mSubFolderPath);  
		String validName = sanitizeFileName(task.mFileName);  
		if (validName == null) validName = "download_" + System.currentTimeMillis();  
		
		String lockKey = task.mTreeUri != null ? task.mTreeUri.toString() : folder.getUri().toString();  
		Object lock = getFolderLock(lockKey);  
		
		DocumentFile file;  
		synchronized (lock) {  
			file = folder.createFile(task.mMimeType, validName);  
		}  
		
		if (file == null || file.getUri() == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Failed to create output file.");  
		Uri fileUri = file.getUri();
		String fileName = file.getName();
		task.mOutputDocFile = file;  
		task.mOutputUri = fileUri;  
		task.mOutputName = fileName;  
		markFreshOutput(task);  
		updateOutputData(task);  
		updateFreshOutputState(task);  
		return new OutputState(file, null, fileUri, 0);  
	}  
	
	private static DocumentFile resolveDocFileSubFolder(DocumentFile baseFolder, String subFolder) throws IOException {  
		if (baseFolder == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output folder cannot be resolved.");  
		if (!baseFolder.exists() || !baseFolder.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid output folder.");  
		
		String normalized = normalizeSubFolder(subFolder);  
		if (subFolder != null && !subFolder.trim().isEmpty() && normalized == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid subfolder path.");  
		if (normalized == null) return baseFolder;  
		
		DocumentFile folder = baseFolder;  
		String[] parts = normalized.split("/");  
		
		for (String part : parts) {  
			if (part == null || part.isEmpty()) continue;  
			DocumentFile child = folder.findFile(part);  
			
			if (child == null) {
				child = folder.createDirectory(part);
				if (child == null || !child.exists() || !child.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot create subfolder: " + part);
			} else if (!child.isDirectory()) {
				throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Subfolder path collides with a file: " + part);
			}
			
			folder = child;  
		}  
		
		return folder;  
	}  
	
	// FILE PATH OUTPUT //  
	
	private static OutputState resolvePathOutput(DownloadTask task) throws IOException {  
		File folder = resolveFileSubFolder(new File(task.mOutputFolderPath), task.mSubFolderPath);  
		if (!folder.canWrite()) throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "No write permission for output directory.");  
		
		String validName = sanitizeFileName(task.mFileName);  
		if (validName == null) validName = "download_" + System.currentTimeMillis();  
		File file = createUniqueFile(folder, validName);  
		
		task.mOutputFile = file;  
		task.mOutputPath = file.getAbsolutePath();  
		task.mOutputUri = createFileProviderUri(task.mContext, file);  
		task.mOutputName = file.getName();  
		markFreshOutput(task);  
		updateOutputData(task);  
		updateFreshOutputState(task);  
		return new OutputState(null, file, task.mOutputUri, 0);  
	}  
	
	private static File createUniqueFile(File folder, String fileName) throws IOException {  
		String ext = com.jeet.simpledownloader.util.TypeResolver.getExtension(fileName);  
		String baseName = getBaseName(fileName);  
		String suffix = ext.isEmpty() ? "" : "." + ext;  
		File file = new File(folder, fileName);  
		
		try {  
			if (file.createNewFile()) return file;  
		} catch (IOException e) {  
			throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create output file.", -1, false, e);  
		}  
		
		for (int i = 1; i <= 999; i++) {  
			File candidate = new File(folder, baseName + " (" + i + ")" + suffix);  
			try {  
				if (candidate.createNewFile()) return candidate;  
			} catch (IOException e) {  
				throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create output file.", -1, false, e);  
			}  
		}  
		throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot create unique output file after 999 attempts.");  
	}  
	
	private static File resolveFileSubFolder(File baseFolder, String subFolder) throws IOException {  
		if (baseFolder == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID,"Output directory cannot be resolved.");  
		
		String normalized = normalizeSubFolder(subFolder);  
		if (subFolder != null && !subFolder.trim().isEmpty() && normalized == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid subfolder path.");  
		
		if (normalized == null) return baseFolder;  
		File folder = new File(baseFolder, normalized);  
		
		if (!folder.exists() && !folder.mkdirs()) throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create subfolder.");  
		if (!folder.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid subfolder path.");  
		return folder;  
	}  
	
	private static OutputState resolveOverwritePath(DownloadTask task) throws IOException {  
		File file = new File(task.mOverwritePath);  
		File parent = file.getParentFile();  
		if (parent == null || !parent.exists() || !parent.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid overwrite file path.");  
		if (!parent.canWrite()) throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "No write permission for overwrite path.");  
		
		if (!file.exists()) {  
			try {  
				if (!file.createNewFile()) throw new IOException("createNewFile returned false.");  
			} catch (IOException e) {  
				throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create overwrite file.", -1, false, e);  
			}  
		}  
		
		if (!file.canWrite()) throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "No write permission for overwrite file.");  
		task.mOutputFile = file;  
		task.mOutputPath = file.getAbsolutePath();  
		task.mOutputUri = createFileProviderUri(task.mContext, file);  
		task.mOutputName = file.getName();  
		updateOutputData(task);  
		return new OutputState(null, file, task.mOutputUri, safeLength(file));  
	}  
	
	// MEDIA STORE OUTPUT //  
	
	private static OutputState resolveMediaStoreOutput(DownloadTask task, boolean allowCreate) throws IOException {  
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "MediaStore output requires Android 10 (API 29) or newer.");  
		
		ContentResolver resolver = task.mContext.getContentResolver();
		
		if (task.mOutputUri != null) {  
			long probeLength = getMediaStoreLength(task, task.mOutputUri);  
			if (probeLength >= 0) {  
				long resumeLength = Math.max(Math.max(0L, task.mBytesDownloaded), probeLength);  
				return new OutputState(null, null, task.mOutputUri, resumeLength);  
			}  
			
			try {  
				resolver.delete(task.mOutputUri, null, null);  
			} catch (Throwable ignored) {}  
			
			task.mOutputUri = null;  
			task.mOutputName = null;  
		}  
		
		if (!allowCreate) return new OutputState(null, null, null, 0);  
		String fileName = sanitizeFileName(task.mFileName);  
		if (fileName == null) fileName = "download_" + System.currentTimeMillis();  
		String relativePath = resolveMediaStoreSubFolder(task.mMediaStoreUri, task.mSubFolderPath);  
		if (relativePath == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid subfolder path.");  
		
		Uri createdUri;  
		Object lock = getFolderLock(task.mMediaStoreUri.toString());  
		
		synchronized (lock) {  
			fileName = resolveUniqueMediaStoreName(task, fileName, relativePath);  
			ContentValues values = new ContentValues();  
			values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);  
			values.put(MediaStore.MediaColumns.MIME_TYPE, task.mMimeType);  
			values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);  
			values.put(MediaStore.MediaColumns.IS_PENDING, 1);  
			createdUri = resolver.insert(task.mMediaStoreUri, values);  
		}  
		
		if (createdUri == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Failed to create MediaStore output.");  
		
		task.mOutputUri = createdUri;  
		task.mOutputName = fileName;  
		markFreshOutput(task);  
		updateOutputData(task);  
		updateFreshOutputState(task);  
		return new OutputState(null, null, createdUri, 0);  
	}
	
	private static String resolveUniqueMediaStoreName(DownloadTask task, String baseFileName, String relativePath) {  
		ContentResolver resolver = task.mContext.getContentResolver();  
		String ext = com.jeet.simpledownloader.util.TypeResolver.getExtension(baseFileName);  
		String baseName = getBaseName(baseFileName);  
		String suffix = ext.isEmpty() ? "" : "." + ext;  
		String candidate = baseFileName;  
		int i = 0;  
		
		while (mediaStoreNameExists(resolver, task.mMediaStoreUri, relativePath, candidate)) {  
			i++;  
			candidate = baseName + " (" + i + ")" + suffix;  
			if (i > 999) break;  
		}  
		
		return candidate;  
	}  
	
	private static boolean mediaStoreNameExists(ContentResolver resolver, Uri collection, String relativePath, String displayName) {  
		String[] projection = { MediaStore.MediaColumns._ID };  
		String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?";  
		String[] args = { displayName, relativePath };  
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {  
			Bundle queryArgs = new Bundle();  
			queryArgs.putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE);  
			queryArgs.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection);  
			queryArgs.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args);  
			
			try (Cursor c = resolver.query(collection, projection, queryArgs, null)) {  
				return c != null && c.moveToFirst();  
			} catch (Throwable ignored) {  
				return false;  
			}  
		}  
		
		Uri includePending = MediaStore.setIncludePending(collection);  
		try (Cursor c = resolver.query(includePending, projection, selection, args, null)) {  
			return c != null && c.moveToFirst();  
		} catch (Throwable ignored) {  
			return false;  
		}  
	}  
	
	private static String resolveMediaStoreSubFolder(Uri collectionUri, String subFolder) {  
		String root = Environment.DIRECTORY_DOWNLOADS;  
		
		if (collectionUri != null) {  
			String value = collectionUri.toString();  
			if (value.contains("/video/")) root = Environment.DIRECTORY_MOVIES;  
			else if (value.contains("/images/")) root = Environment.DIRECTORY_PICTURES;  
			else if (value.contains("/audio/")) root = Environment.DIRECTORY_MUSIC;
		}  
		
		String normalized = normalizeSubFolder(subFolder);  
		if (subFolder != null && !subFolder.trim().isEmpty() && normalized == null) return null;  
		if (normalized == null) return root + "/";  
		return root + "/" + normalized + "/";  
	}
	
	private static OutputState resolveOverwriteMediaStore(DownloadTask task) throws IOException {
		Uri uri = task.mOverwriteUri;
		
		if (uri == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "MediaStore overwrite Uri cannot be null.");
		long length = getMediaStoreLength(task, uri);
		if (length < 0) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "MediaStore overwrite file does not exist or cannot be opened.");
		
		task.mOutputUri = uri;
		task.mOutputName = getMediaStoreDisplayName(task, uri);
		return new OutputState(null, null, uri, resolveOverwriteResumeLength(task, length));
	}
	
	private static String getMediaStoreDisplayName(DownloadTask task, Uri uri) {
		if (task == null || task.mContext == null || uri == null) return null;
		Cursor cursor = null;
		
		try {
			cursor = task.mContext.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
			if (cursor != null && cursor.moveToFirst()) {
				int index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
				if (index >= 0 && !cursor.isNull(index)) return cursor.getString(index);
			}
			
		} catch (Throwable thr) {
            Logs.err("Failed to get MediaStore display name.", thr);
		} finally {
			if (cursor != null) cursor.close();
		}
		
		return null;
	}
	
	// OTHERS //  
	
	private static String normalizeSubFolder(String raw) {  
		if (raw == null) return null;  
		
		String value = raw.trim().replace('\\', '/');  
		while (value.startsWith("/")) value = value.substring(1);  
		while (value.endsWith("/")) value = value.substring(0, value.length() - 1);  
		
		if (value.isEmpty()) return null;  
		StringBuilder out = new StringBuilder();  
		String[] parts = value.split("/+");  
		
		for (String part : parts) {  
			String segment = part.trim();  
			if (segment.isEmpty() || ".".equals(segment)) continue;  
			if ("..".equals(segment)) return null;  
			if (out.length() > 0) out.append('/');  
			out.append(segment);  
		}  
		
		return out.length() == 0 ? null : out.toString();  
	}  
	
	private static String sanitizeFileName(String name) {  
		if (name == null) return null;  
		name = name.trim();  
		name = name.replace("\\", "");  
		name = name.replace("/", "");  
		name = name.replace(":", "");  
		name = name.replace("*", "");  
		name = name.replace("?", "");  
		name = name.replace("\"", "");  
		name = name.replace("<", "");  
		name = name.replace(">", "");  
		name = name.replace("|", "");
		
		while (name.startsWith(".")) name = name.substring(1);  
		return name.length() == 0 ? null : name;  
	}  
	
	private static String getBaseName(String fileName) {  
		int dot = fileName.lastIndexOf('.');  
		if (dot <= 0) return fileName;  
		return fileName.substring(0, dot);  
	}  
	
	private static Uri createFileProviderUri(Context context, File file) {  
		try {  
			return FileProvider.getUriForFile(context, context.getPackageName() + ".simpledownloader.fileprovider", file);  
		} catch (Exception error) {  
			Logs.warn("FileProvider cannot be resolved. The output URI will be null for file path outputs.", error);
			return null;
		}  
	}
	
	private static boolean isMediaStoreItemUri(Uri uri) {
		if (uri == null) return false;
		if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return false;
		if (!MediaStore.AUTHORITY.equals(uri.getAuthority())) return false;
		
		try {
			return ContentUris.parseId(uri) > 0;
		} catch (Throwable ignored) {
			return false;
		}
	}
	
	private static Object getFolderLock(String key) {  
		if (key == null) key = "default";  
		
		synchronized (sFolderLocks) {  
			Object lock = sFolderLocks.get(key);  
			if (lock == null) {  
				lock = new Object();  
				sFolderLocks.put(key, lock);  
			}  
			return lock;  
		}  
	}
	
	private static void clearOutputReferences(DownloadTask task) {
		if (task == null) return;
		task.mOutputFile = null;
		task.mOutputDocFile = null;
		task.mOutputUri = null;
		task.mOutputName = null;
		task.mOutputPath = null;
	}
	
	private static void markFreshOutput(DownloadTask task) {  
		task.mBytesDownloaded = 0;  
		task.mProgress = 0;  
		task.mIgnoredRange = true;  
		task.mSpeed = 0;  
		task.mEta = -1;  
	}
	
	static void clearOutput(DownloadTask task) throws IOException {
		if (task == null) return;
		OutputStream clear = null;
		
		try {  
			if (task.mOutputFile != null) clear = new FileOutputStream(task.mOutputFile, false);  
			else if (task.mOutputUri != null) clear = task.mContext.getContentResolver().openOutputStream(task.mOutputUri, "w");  
			if (clear == null) return;  
			
		} finally {  
			closeQuietly(clear);
		}  
		
		markFreshOutput(task);
		if (task.mDownloader.taskDatabase != null) task.mDownloader.taskDatabase.updateResumeData(task.mId, 0, -1, 0, null, null);  
	}  
	
	static void deleteIfEmpty(DownloadTask task) {    
		if (task == null || task.mBytesDownloaded > 0) return;
		if (task.mOverwritePath != null || task.mOverwriteUri != null) return;
		
		try {
			OutputState output = resolveExisting(task);    
			boolean hasOutput = output.file != null || output.pathFile != null || output.uri != null;    
			if (!hasOutput || output.length > 0) return;    
			deleteOutput(task);    
			
		} catch (Throwable ignored) {}    
	}    
	
	private static void updateOutputData(DownloadTask task) {  
		if (task == null || task.mDownloader.taskDatabase == null) return;  
		task.mDownloader.taskDatabase.updateOutputData(task);  
	}  
	
	private static void updateFreshOutputState(DownloadTask task) {  
		if (task == null || task.mDownloader.taskDatabase == null) return;  
		task.mDownloader.taskDatabase.updateResumeData(task.mId, 0, -1, 0, null, null);  
	}  
	
	private static long safeLength(DocumentFile file) {  
		try {  
			if (file == null) return 0;  
			long length = file.length();  
			return Math.max(0, length);  
		} catch (Throwable thr) {
            Logs.err("Unable get file length for DocumentFile: " + file.getUri().toString(), thr);
			return 0;
		}
	}  
	
	private static long safeLength(File file) {  
		try {  
			if (file == null) return 0;  
			return Math.max(0, file.length());  
		} catch (Throwable thr) {
            Logs.err("Unable get file length for File: " + file.getAbsolutePath(), thr);
			return 0;  
		}
	}  
	
	private static long getMediaStoreLength(DownloadTask task, Uri uri) {
		if (task == null || task.mContext == null || uri == null) return -1;
		
		try (ParcelFileDescriptor pfd = task.mContext.getContentResolver().openFileDescriptor(uri, "r")) {
			if (pfd == null) return -1;
			long size = pfd.getStatSize();
			return size >= 0 ? size : -1;
			
		} catch (Throwable thr) {
            Logs.err("Unable get file length for MediaStore item: " + uri.toString(), thr);
			return -1;
		}
	}
	
	private static long resolveOverwriteResumeLength(DownloadTask task, long actualOnDiskLength) {
		return task.mBytesDownloaded > 0 ? actualOnDiskLength : 0;
	}
	
	private static void closeQuietly(OutputStream out) {  
		if (out == null) return;  
		try {  
			out.close();  
		} catch (Throwable ignored) {}  
	}  
	
	static boolean isOutputValid(DownloadTask task) {
		if (task == null || task.mContext == null) return false;
		boolean isValid = false;
		
		try {
			boolean isMediaStore = isMediaStoreItemUri(task.mOutputUri);
			
			if (task.mOutputFile != null) {
				isValid = task.mOutputFile.exists();
				
			} else if (task.mOutputDocFile != null) {
				isValid = task.mOutputDocFile.exists();
				
			} else if (isMediaStore) {
				try (ParcelFileDescriptor pfd = task.mContext.getContentResolver().openFileDescriptor(task.mOutputUri, "r")) {
					isValid = pfd != null;
				} catch (Throwable ignored) {
					isValid = false;
				}
				
			} 
			
			if (!isValid) {
				if (isMediaStore) {
					try {
						task.mContext.getContentResolver().delete(task.mOutputUri, null, null);
					} catch (Throwable ignored) {}
				}
				
				clearOutputReferences(task);
				updateOutputData(task);
			}
			
			return isValid;
		} catch (Throwable thr) {
			clearOutputReferences(task);
			updateOutputData(task);
            Logs.err("Unable to verify output file.", thr);
			return false;
		}
	}
	
	static void finishOutput(DownloadTask task) throws IOException {  
		if (task == null || task.mMediaStoreUri == null) return;  
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;  
		if (task.mOutputUri == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "MediaStore output URI cannot be resolved.");  
		
		ContentValues values = new ContentValues();  
		values.put(MediaStore.MediaColumns.IS_PENDING, 0);  
		
		int count = task.mContext.getContentResolver().update(task.mOutputUri, values, null, null);  
		if (count <= 0) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Failed to finalize MediaStore output.");  
	}
	
	static boolean deleteOutput(DownloadTask task) {  
		if (task == null) return true;  
		if (executeDeleteOutput(task)) {  
			clearOutputReferences(task);
			updateOutputData(task);
			return true;  
		}  
		return false;  
	}
	
	private static boolean executeDeleteOutput(DownloadTask task) {  
		try {  
			if (task.mOutputFile != null) {  
				return !task.mOutputFile.exists() || task.mOutputFile.delete();  
			}  
			
			if (task.mOutputPath != null && !task.mOutputPath.isEmpty()) {  
				File file = new File(task.mOutputPath);  
				return !file.exists() || file.delete();  
			}  
			
			if (task.mMediaStoreUri != null && task.mOutputUri != null) {  
				int count = task.mContext.getContentResolver().delete(task.mOutputUri, null, null);  
				return count >= 0;  
			}  
			
			if (task.mOutputDocFile != null) {  
				return !task.mOutputDocFile.exists() || task.mOutputDocFile.delete();  
			}  
			
			return true;  
			
		} catch (Throwable error) {  
			System.err.println("SimpleDownloader: " + error);  
			return false;  
		}  
	}  
	
	static final class OutputState {  
		final DocumentFile file;  
		final File pathFile;  
		final Uri uri;  
		final long length;  
		
		OutputState(DocumentFile file, File pathFile, Uri uri, long length) {  
			this.file = file;  
			this.pathFile = pathFile;  
			this.uri = uri;  
			this.length = Math.max(0, length);  
		}  
	}
	
}
