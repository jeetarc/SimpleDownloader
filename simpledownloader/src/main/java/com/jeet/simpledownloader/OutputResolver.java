package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.documentfile.provider.DocumentFile;
import java.io.IOException;
import java.io.OutputStream;

final class OutputResolver {
	private OutputResolver() {}

	static OutputState resolve(DownloadTask task) throws IOException {
		if (task.mOutputFile == null && task.mOutputFileUri != null) {
			task.mOutputFile = DocumentFile.fromSingleUri(task.mContext, task.mOutputFileUri);
		}

		if (task.mOutputFile != null && task.mOutputFile.exists()) return new OutputState(task.mOutputFile, task.mOutputFile.getUri(), safeLength(task.mOutputFile));
		if (task.mOverwriteUri != null) {
			DocumentFile file = DocumentFile.fromSingleUri(task.mContext, task.mOverwriteUri);
			if (file == null || !file.exists()) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Invalid or missing overwrite file Uri.");
            
			task.mOutputFile = file;
			task.mOutputFileUri = task.mOverwriteUri;
			return new OutputState(file, file.getUri(), safeLength(file));
		}

		if (task.mTreeUri == null) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Output tree Uri is missing.");
		Uri docUri = DocumentsContract.buildDocumentUriUsingTree(task.mTreeUri, DocumentsContract.getTreeDocumentId(task.mTreeUri));
		DocumentFile folder = DocumentFile.fromTreeUri(task.mContext, docUri);
		if (folder == null || !folder.exists() || !folder.isDirectory()) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Invalid output folder.");
		DocumentFile file = folder.createFile(task.mMimeType, task.mFileName);
		if (file == null) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Failed to create output file.");

		task.mOutputFile = file;
		task.mOutputFileUri = file.getUri();
        task.mOutputFileName = file.getName();
		task.mBytesDownloaded = 0;
		task.mProgress = 0;
		task.mIgnoredRange = true;
		task.mSpeed = 0;
        task.mEta = -1;

		if (SimpleDownloader.sDatabase != null) {
			SimpleDownloader.sDatabase.updateOutputUri(task.mId, task.mOutputFileUri);
			SimpleDownloader.sDatabase.updateStatus(task.mId, task.status.name(), task.mBytesDownloaded, task.mProgress);
		}

		return new OutputState(file, file.getUri(), 0);
	}

	static OutputStream openOutput(Context context, DocumentFile file, boolean append) throws IOException {
		if (file == null || file.getUri() == null) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Output file is invalid.");
		OutputStream out = context.getContentResolver().openOutputStream(file.getUri(), append ? "wa" : "w");
		if (out == null) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Cannot open output stream.");
		return out;
	}

	static void clearOutput(DownloadTask task) throws IOException {
		if (task.mOutputFileUri == null) return;
		OutputStream clear = task.mContext.getContentResolver().openOutputStream(task.mOutputFileUri, "w");
		if (clear == null) throw new DownloadException(DownloadException.Type.OUTPUT_URI_INVALID, "Cannot clear output file.");
		
        try {
			clear.flush();
		} finally {
			clear.close();
		}

		task.mBytesDownloaded = 0;
		task.mProgress = 0;
		task.mIgnoredRange = true;
		task.mSpeed = 0;
        task.mEta = -1;
		if (SimpleDownloader.sDatabase != null) SimpleDownloader.sDatabase.updateResumeData(task.mId, 0, -1, null, null);
	}

	private static long safeLength(DocumentFile file) {
		try {
			long length = file.length();
			return Math.max(0, length);
		} catch (Throwable ignored) {
			return 0;
		}
	}

	static final class OutputState {
		final DocumentFile file;
		final Uri uri;
		final long length;

		OutputState(DocumentFile file, Uri uri, long length) {
			this.file = file;
			this.uri = uri;
			this.length = Math.max(0, length);
		}
	}
}
