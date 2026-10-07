package com.jeet.simpledownloader.thumbnail;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.graphics.pdf.PdfRenderer;
import com.jeet.simpledownloader.util.TypeResolver;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Locale;

/** Performs thumbnail decode (uused internally). */
final class ThumbDecoder {
	private static final int COPY_BUFFER_SIZE = 64 * 1024;
	
	private enum Kind {
		VIDEO, AUDIO, IMAGE, PDF, APK, UNSUPPORTED
	}
	
	private ThumbDecoder() {}
	
	static Bitmap decode(Context context, ThumbRequest request) throws Exception {
		if (context == null || request == null) return null;
		checkInterrupted();
		
		switch (resolveKind(request.mimeType, request.sourceFile, request.sourceUri)) {
			case VIDEO:
			return decodeVideo(context, request);
			case AUDIO:
			return decodeAudioArtwork(context, request);
			case IMAGE:
			return decodeImage(context, request);
			case PDF:
			return decodePdf(context, request);
			case APK:
			return decodeApk(context, request);
			default:
			return null;
		}
	}
	
	static boolean supportsPartial(String mimeType, File file, Uri uri) {
		Kind kind = resolveKind(mimeType, file, uri);
		return kind == Kind.VIDEO || kind == Kind.AUDIO;
	}
	
	static String normalizeMime(String mimeType) {
		if (mimeType == null) return "";
		int separator = mimeType.indexOf(';');
		if (separator >= 0) mimeType = mimeType.substring(0, separator);
		return mimeType.trim().toLowerCase(Locale.ROOT);
	}
	
	private static Kind resolveKind(String mimeType, File file, Uri uri) {
		String mime = normalizeMime(mimeType);
		if (mime.startsWith("video/")) return Kind.VIDEO;
		if (mime.startsWith("audio/")) return Kind.AUDIO;
		if (mime.startsWith("image/")) return Kind.IMAGE;
		if ("application/pdf".equals(mime)) return Kind.PDF;
		if ("application/vnd.android.package-archive".equals(mime)) return Kind.APK;
		
		String name = file != null ? file.getName() : uri != null ? uri.getLastPathSegment() : null;
		String extension = TypeResolver.getExtension(name);
		if ("pdf".equals(extension)) return Kind.PDF;
		if ("apk".equals(extension)) return Kind.APK;
		
		String guessed = TypeResolver.getMimeFromExtension(extension);
		if (guessed.startsWith("video/")) return Kind.VIDEO;
		if (guessed.startsWith("audio/")) return Kind.AUDIO;
		if (guessed.startsWith("image/")) return Kind.IMAGE;
		return Kind.UNSUPPORTED;
	}
	
	private static Bitmap decodeVideo(Context context, ThumbRequest request) throws Exception {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		ParcelFileDescriptor descriptor = null;
		
		try {
			descriptor = openDescriptor(context, request);
			if (descriptor == null) return null;
			retriever.setDataSource(descriptor.getFileDescriptor());
			checkInterrupted();
			
			Bitmap frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
			return scaleBitmap(frame, request.targetWidth, request.targetHeight);
			
		} finally {
			retriever.release();
			closeQuietly(descriptor);
		}
	}
	
	private static Bitmap decodeAudioArtwork(Context context, ThumbRequest request) throws Exception {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		ParcelFileDescriptor descriptor = null;
		
		try {
			descriptor = openDescriptor(context, request);
			if (descriptor == null) return null;
			retriever.setDataSource(descriptor.getFileDescriptor());
			checkInterrupted();
			
			byte[] artwork = retriever.getEmbeddedPicture();
			if (artwork == null || artwork.length == 0) return null;
			return decodeByteArray(artwork, request.targetWidth, request.targetHeight);
		} finally {
			retriever.release();
			closeQuietly(descriptor);
		}
	}
	
	private static Bitmap decodeImage(Context context, ThumbRequest request) throws Exception {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		ParcelFileDescriptor descriptor = null;
		
		try {
			descriptor = openDescriptor(context, request);
			if (descriptor == null) return null;
			BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, bounds);
		} finally {
			closeQuietly(descriptor);
		}
		
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
		checkInterrupted();
		
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, request.targetWidth, request.targetHeight);
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		
		try {
			descriptor = openDescriptor(context, request);
			if (descriptor == null) return null;
			Bitmap bitmap = BitmapFactory.decodeFileDescriptor(descriptor.getFileDescriptor(), null, options);
			return scaleBitmap(bitmap, request.targetWidth, request.targetHeight);
		} finally {
			closeQuietly(descriptor);
		}
	}
	
	static Bitmap decodeImage(InputStream input, int width, int height) throws Exception {
		if (input == null) return null;
		checkInterrupted();
		
		Bitmap bitmap = BitmapFactory.decodeStream(input);
		if (bitmap == null) return null;
		
		try {
			checkInterrupted();
			return scaleBitmap(bitmap, width, height);
			
		} catch (Throwable error) {
			if (!bitmap.isRecycled()) bitmap.recycle();
			throw error;
		}
	}
	
	private static Bitmap decodePdf(Context context, ThumbRequest request) throws Exception {
		ParcelFileDescriptor descriptor = openDescriptor(context, request);
		if (descriptor == null) return null;
		PdfRenderer renderer = null;
		PdfRenderer.Page page = null;
		
		try {
			renderer = new PdfRenderer(descriptor);
			if (renderer.getPageCount() <= 0) return null;
			page = renderer.openPage(0);
			checkInterrupted();
			
			Bitmap bitmap = Bitmap.createBitmap(request.targetWidth, request.targetHeight, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			canvas.drawColor(Color.WHITE);
			float scale = Math.min(request.targetWidth / (float) page.getWidth(), request.targetHeight / (float) page.getHeight());
			float offsetX = (request.targetWidth - page.getWidth() * scale) / 2f;
			float offsetY = (request.targetHeight - page.getHeight() * scale) / 2f;
			Matrix matrix = new Matrix();
			
			matrix.postScale(scale, scale);
			matrix.postTranslate(offsetX, offsetY);
			page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
			return bitmap;
			
		} finally {
			if (page != null) page.close();
			if (renderer != null) renderer.close();
			closeQuietly(descriptor);
		}
	}
	
	private static Bitmap decodeApk(Context context, ThumbRequest request) throws Exception {
		File apkFile = request.sourceFile;
		File temporaryFile = null;
		
		try {
			if (apkFile == null || !apkFile.isFile()) {
				temporaryFile = copyUriToTemporaryApk(context, request.sourceUri);
				apkFile = temporaryFile;
			}
			
			if (apkFile == null || !apkFile.isFile()) return null;
			PackageManager manager = context.getPackageManager();
			PackageInfo packageInfo;
			
			if (Build.VERSION.SDK_INT >= 33) {
				packageInfo = manager.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.PackageInfoFlags.of(0L));
			} else {
				packageInfo = manager.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
			}
			
			if (packageInfo == null || packageInfo.applicationInfo == null) return null;
			ApplicationInfo applicationInfo = packageInfo.applicationInfo;
			applicationInfo.sourceDir = apkFile.getAbsolutePath();
			applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
			Drawable icon = applicationInfo.loadIcon(manager);
			return drawableToBitmap(icon, request.targetWidth, request.targetHeight);
			
		} finally {
			if (temporaryFile != null && temporaryFile.exists()) temporaryFile.delete();
		}
	}
	
	private static ParcelFileDescriptor openDescriptor(Context context, ThumbRequest request) throws IOException {
		if (request.sourceFile != null && request.sourceFile.isFile()) {
			return ParcelFileDescriptor.open(request.sourceFile, ParcelFileDescriptor.MODE_READ_ONLY);
		}
		
		if (request.sourceUri == null) return null;
		return context.getContentResolver().openFileDescriptor(request.sourceUri, "r");
	}
	
	private static File copyUriToTemporaryApk(Context context, Uri uri) throws IOException {
		if (uri == null) return null;
		File temporaryFile = File.createTempFile("simpledownloader_apk_thumb_", ".apk", context.getCacheDir());
		
		InputStream input = null;
		FileOutputStream output = null;
		
		try {
			input = context.getContentResolver().openInputStream(uri);
			if (input == null) throw new IOException("Cannot open APK Uri.");
			output = new FileOutputStream(temporaryFile);
			byte[] buffer = new byte[COPY_BUFFER_SIZE];
			int read;
			
			while ((read = input.read(buffer)) != -1) {
				checkInterrupted();
				output.write(buffer, 0, read);
			}
			
			output.flush();
			return temporaryFile;
		} catch (IOException error) {
			temporaryFile.delete();
			throw error;
			
		} finally {
			closeQuietly(output);
			closeQuietly(input);
		}
	}
	
	private static Bitmap decodeByteArray(byte[] data, int width, int height) {
		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
		
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, width, height);
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
		return scaleBitmap(bitmap, width, height);
	}
	
	private static Bitmap scaleBitmap(Bitmap source, int width, int height) {
		if (source == null) return null;
		if (width <= 0 || height <= 0) return source;
		float sourceWidth = source.getWidth();
		float sourceHeight = source.getHeight();
		
		if (sourceWidth <= 0 || sourceHeight <= 0) return source;
		float scale = Math.max(width / sourceWidth, height / sourceHeight);
		int scaledWidth = Math.max(1, Math.round(sourceWidth * scale));
		int scaledHeight = Math.max(1, Math.round(sourceHeight * scale));
		Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
		if (scaled != source) source.recycle();
		
		int cropX = Math.max(0, (scaledWidth - width) / 2);
		int cropY = Math.max(0, (scaledHeight - height) / 2);
		int cropWidth = Math.min(width, scaledWidth - cropX);
		int cropHeight = Math.min(height, scaledHeight - cropY);
		Bitmap cropped = Bitmap.createBitmap(scaled, cropX, cropY, cropWidth,cropHeight);
		
		if (cropped != scaled) scaled.recycle();
		return cropped;
	}
	
	private static int calculateInSampleSize(int sourceWidth, int sourceHeight, int requestedWidth, int requestedHeight) {
		int inSampleSize = 1;
		if (requestedWidth <= 0 || requestedHeight <= 0) return inSampleSize;
		
		if (sourceWidth > requestedWidth || sourceHeight > requestedHeight) {
			int halfWidth = sourceWidth / 2;
			int halfHeight = sourceHeight / 2;
			
			while ((halfWidth / inSampleSize) >= requestedWidth && (halfHeight / inSampleSize) >= requestedHeight) {
				inSampleSize *= 2;
			}
		}
		
		return inSampleSize;
	}
	
	private static Bitmap scaleDownToFit(Bitmap source, int targetWidth, int targetHeight) {
		if (source == null) return null;
		if (source.getWidth() <= targetWidth && source.getHeight() <= targetHeight) return source;
		
		float scale = Math.min(targetWidth / (float) source.getWidth(), targetHeight / (float) source.getHeight());
		int width = Math.max(1, Math.round(source.getWidth() * scale));
		int height = Math.max(1, Math.round(source.getHeight() * scale));
		Bitmap scaled = Bitmap.createScaledBitmap(source, width, height, true);
		
		if (scaled != source) source.recycle();
		return scaled;
	}
	
	private static Bitmap drawableToBitmap(Drawable drawable, int width, int height) {
		if (drawable == null) return null;
		if (drawable instanceof BitmapDrawable) {
			Bitmap shared = ((BitmapDrawable) drawable).getBitmap();
			if (shared == null) return null;
			// PackageManager may return a shared drawable bitmap. Copy it so this
			// loader owns the result and may safely recycle stale deliveries.
			Bitmap owned = shared.copy(Bitmap.Config.ARGB_8888, false);
			if (owned == null) return null;
			return scaleDownToFit(owned, width, height);
		}
		
		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		int intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth());
		int intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
		float scale = Math.min(width / (float) intrinsicWidth, height / (float) intrinsicHeight);
		int drawWidth = Math.max(1, Math.round(intrinsicWidth * scale));
		int drawHeight = Math.max(1, Math.round(intrinsicHeight * scale));
		int left = (width - drawWidth) / 2;
		int top = (height - drawHeight) / 2;
		
		drawable.setBounds(left, top, left + drawWidth, top + drawHeight);
		drawable.draw(canvas);
		return bitmap;
	}
	
	private static void checkInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Thumbnail decoding was cancelled.");
	}
	
	private static void closeQuietly(java.io.Closeable closeable) {
		if (closeable == null) return;
		
		try {
			closeable.close();
		} catch (IOException ignored) {}
	}
}
