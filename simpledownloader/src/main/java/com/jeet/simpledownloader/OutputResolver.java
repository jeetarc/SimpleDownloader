package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
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
import android.util.Log;

final class OutputResolver {
	private static final Map<String, Object> sFolderLocks = new HashMap<>();
	private OutputResolver() {}
	
	static OutputState resolve(DownloadTask task) throws IOException {
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");
		if (task.mOutputDocFile == null && task.mOutputUri != null && task.mOutputFile == null) task.mOutputDocFile = DocumentFile.fromSingleUri(task.mContext, task.mOutputUri);
		
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
			DocumentFile file = DocumentFile.fromSingleUri(task.mContext, task.mOverwriteUri);
			if (file == null || !file.exists()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid or missing overwrite file Uri.");
			task.mOutputDocFile = file;
			task.mOutputUri = file.getUri();
			task.mOutputName = file.getName();
			updateOutputData(task);
			return new OutputState(file, null, file.getUri(), safeLength(file));
		}
		
		if (task.mOutputFolderPath != null && task.mOutputFolderPath.length() > 0) return resolvePathOutput(task);
		if (task.mTreeUri != null) return resolveDocFileOutput(task);
		throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "No output target was configured.");
	}
	
	static OutputStream openOutput(DownloadTask task, boolean append) throws IOException {
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");
		if (task.mOutputFile != null) return new FileOutputStream(task.mOutputFile, append);
		if (task.mContext == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Context cannot be resolved.");
		if (task.mOutputDocFile == null || task.mOutputDocFile.getUri() == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Output file is invalid.");
		OutputStream out = task.mContext.getContentResolver().openOutputStream(task.mOutputDocFile.getUri(), append ? "wa" : "w");
		if (out == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot open output stream.");
		return out;
	}
	
	static OutputState resolveExisting(DownloadTask task) throws IOException {
		if (task == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Download task cannot be resolved.");
		if (task.mOutputFile == null && task.mOutputPath != null && task.mOutputPath.length() > 0) task.mOutputFile = new File(task.mOutputPath);
		
		if (task.mOutputFile != null && task.mOutputFile.exists()) {
			task.mOutputPath = task.mOutputFile.getAbsolutePath();
			task.mOutputUri = createFileProviderUri(task.mContext, task.mOutputFile);
			task.mOutputName = task.mOutputFile.getName();
			return new OutputState(null, task.mOutputFile, task.mOutputUri, safeLength(task.mOutputFile));
		}
		
		if (task.mOutputDocFile == null && task.mOutputUri != null) task.mOutputDocFile = DocumentFile.fromSingleUri(task.mContext, task.mOutputUri);
		if (task.mOutputDocFile != null && task.mOutputDocFile.exists()) {
			task.mOutputUri = task.mOutputDocFile.getUri();
			task.mOutputName = task.mOutputDocFile.getName();
			return new OutputState(task.mOutputDocFile, null, task.mOutputUri, safeLength(task.mOutputDocFile));
		}
		
		if (task.mOverwritePath != null && task.mOverwritePath.length() > 0) {
			File file = new File(task.mOverwritePath);
			if (file.exists()) {
				task.mOutputFile = file;
				task.mOutputPath = file.getAbsolutePath();
				task.mOutputUri = createFileProviderUri(task.mContext, file);
				task.mOutputName = file.getName();
				return new OutputState(null, file, task.mOutputUri, safeLength(file));
			}
		}
		
		if (task.mOverwriteUri != null) {
			DocumentFile file = DocumentFile.fromSingleUri(task.mContext, task.mOverwriteUri);
			if (file != null && file.exists()) {
				task.mOutputDocFile = file;
				task.mOutputUri = file.getUri();
				task.mOutputName = file.getName();
				return new OutputState(file, null, file.getUri(), safeLength(file));
			}
		}
		
		return new OutputState(null, null, null, 0);
	}
	
	private static OutputState resolveDocFileOutput(DownloadTask task) throws IOException {
		DocumentFile folder = DocumentFile.fromTreeUri(task.mContext, task.mTreeUri);
		if (folder == null || !folder.exists() || !folder.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid output folder.");
		
		String validName = sanitizeFileName(task.mFileName);
		if (validName == null) validName = "download_" + System.currentTimeMillis();
		
		String lockKey = task.mTreeUri != null ? task.mTreeUri.toString() : folder.getUri().toString();
		Object lock = getFolderLock(lockKey);
		
		DocumentFile file;
		synchronized (lock) {
			file = folder.createFile(task.mMimeType, validName);
		}
		
		if (file == null || file.getUri() == null) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Failed to create output file.");
		task.mOutputDocFile = file;
		task.mOutputUri = file.getUri();
		task.mOutputName = file.getName();
		markFreshOutput(task);
		updateOutputData(task);
		updateFreshOutputState(task);
		return new OutputState(file, null, task.mOutputUri, 0);
	}
	
	private static OutputState resolvePathOutput(DownloadTask task) throws IOException {
		File folder = new File(task.mOutputFolderPath);
		if (!folder.exists() && !folder.mkdirs()) throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create output directory.");
		if (!folder.exists() || !folder.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Invalid output directory.");
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
	
	private static File createUniqueFile(File folder, String fileName) throws IOException {
		String ext = com.jeet.simpledownloader.util.TypeResolver.getExtension(fileName);
		String baseName = getBaseName(fileName);
		File file = new File(folder, fileName);
		
		try {
			if (file.createNewFile()) return file;
		} catch (IOException e) {
			throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create output file.", -1, false, e);
		}
		
		for (int i = 1; i <= 999; i++) {
			String suffix = ext.isEmpty() ? "" : "." + ext;
			File candidate = new File(folder, baseName + " (" + i + ")" + suffix);
			try {
				if (candidate.createNewFile()) return candidate;
			} catch (IOException e) {
				throw new DownloadException(DownloadException.Type.STORAGE_PERMISSION_DENIED, "Cannot create output file.", -1, false, e);
			}
		}
		throw new DownloadException(DownloadException.Type.OUTPUT_INVALID, "Cannot create unique output file after 999 attempts.");
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
		while (name.startsWith(".")) {
			name = name.substring(1);
		}
		
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
			Log.w("SimpleDownloader: ", "FileProvider cannot be resolved. The outputUri will be null for file path outputs. " + error.toString());
			return null;
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
	
	static void clearOutput(DownloadTask task) throws IOException {
		if (task == null) return;
		OutputStream clear = null;
		
		try {
			if (task.mOutputFile != null) {
				clear = new FileOutputStream(task.mOutputFile, false);
			} else if (task.mOutputUri != null) {
				clear = task.mContext.getContentResolver().openOutputStream(task.mOutputUri, "w");
			}
			
			if (clear == null) return;
			clear.flush();
		} finally {
			closeQuietly(clear);
		}
		
		markFreshOutput(task);
		
		if (task.mDownloader.taskDatabase != null) {
			task.mDownloader.taskDatabase.updateResumeData(task.mId, 0, -1, 0, null, null);
			task.mDownloader.taskDatabase.updateStatus(task.mId, task.status, task.mBytesDownloaded, task.mProgress);
		}
	}
	
	static void deleteIfEmpty(DownloadTask task) {
		if (task == null) return;
		
		try {
			if (task.mOutputFile != null) {
				if (task.mBytesDownloaded <= 0 && task.mOutputFile.exists() && task.mOutputFile.length() <= 0) {
					task.mOutputFile.delete();
					task.mOutputFile = null;
					task.mOutputPath = null;
					task.mOutputUri = null;
					task.mOutputName = null;
					if (task.mDownloader.taskDatabase != null) updateOutputData(task);
				}
				return;
			}
			
			if (task.mBytesDownloaded <= 0 && task.mOutputDocFile != null && task.mOutputDocFile.exists() && task.mOutputDocFile.length() <= 0) {
				task.mOutputDocFile.delete();
				task.mOutputDocFile = null;
				task.mOutputUri = null;
				task.mOutputName = null;
				if (task.mDownloader.taskDatabase != null) updateOutputData(task);
			}
		} catch (Throwable ignored) {}
	}
	
	private static void markFreshOutput(DownloadTask task) {
		task.mBytesDownloaded = 0;
		task.mProgress = 0;
		task.mIgnoredRange = true;
		task.mSpeed = 0;
		task.mEta = -1;
	}
	
	private static void updateOutputData(DownloadTask task) {
		if (task == null || task.mDownloader.taskDatabase == null) return;
		task.mDownloader.taskDatabase.updateOutputData(task);
	}
	
	private static void updateFreshOutputState(DownloadTask task) {
		if (task == null || task.mDownloader.taskDatabase == null) return;
		task.mDownloader.taskDatabase.updateResumeData(task.mId, 0, -1, 0, null, null);
		task.mDownloader.taskDatabase.updateStatus(task.mId, task.status, task.mBytesDownloaded, task.mProgress);
	}
	
	private static long safeLength(DocumentFile file) {
		try {
			if (file == null || !file.exists()) return 0;
			long length = file.length();
			return Math.max(0, length);
		} catch (Throwable ignored) {
			return 0;
		}
	}
	
	private static long safeLength(File file) {
		try {
			if (file == null || !file.exists()) return 0;
			return Math.max(0, file.length());
		} catch (Throwable ignored) {
			return 0;
		}
	}
	
	private static void closeQuietly(OutputStream out) {
		if (out == null) return;
		try {
			out.close();
		} catch (Throwable ignored) {}
	}
	
	static boolean isOutputValid(DownloadTask task) {
		if (task == null) return false;
		try {
			if (task.mOutputFile != null) return task.mOutputFile.exists();
			return task.mOutputDocFile != null && task.mOutputDocFile.getUri() != null && task.mOutputDocFile.exists();
		} catch (Throwable ignored) {
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
