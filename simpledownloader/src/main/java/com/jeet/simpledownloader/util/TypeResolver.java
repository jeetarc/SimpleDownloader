package com.jeet.simpledownloader.util;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.content.ContentResolver;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves file extensions and MIME types.
 * You can use this class too.
 */
public final class TypeResolver {
	public static final String DEFAULT_MIME = "application/octet-stream";
	private static final MimeTypeMap ANDROID_MIME_MAP = MimeTypeMap.getSingleton();
	private static final Map<String, String> EXTENSION_TO_MIME;
	private static final Map<String, String> MIME_TO_EXTENSION;
	
	static {
		Map<String, String> extToMime = new LinkedHashMap<>();
		
		// Common wrong mappings.
		put(extToMime, "csv", "text/csv");
		put(extToMime, "xml", "application/xml");
		put(extToMime, "rtf", "application/rtf");
		put(extToMime, "rar", "application/vnd.rar");
		put(extToMime, "ico", "image/vnd.microsoft.icon");
		put(extToMime, "m4a", "audio/mp4");
		put(extToMime, "m4r", "audio/mp4");
		put(extToMime, "m4b", "audio/mp4");
		put(extToMime, "m4p", "audio/mp4");
		put(extToMime, "flac", "audio/flac");
		put(extToMime, "wav", "audio/wav");
		put(extToMime, "3g2", "video/3gpp2");
		
		// Sometimes missing on older Android versions.
		put(extToMime, "md", "text/markdown");
		put(extToMime, "markdown", "text/markdown");
		put(extToMime, "js", "text/javascript");
		put(extToMime, "mjs", "text/javascript");
		put(extToMime, "jsonld", "application/ld+json");
		put(extToMime, "svg", "image/svg+xml");
		put(extToMime, "avif", "image/avif");
		put(extToMime, "heic", "image/heic");
		put(extToMime, "heif", "image/heif");
		put(extToMime, "weba", "audio/webm");
		put(extToMime, "mkv", "video/x-matroska");
		put(extToMime, "wasm", "application/wasm");
		put(extToMime, "woff", "font/woff");
		put(extToMime, "woff2", "font/woff2");
		put(extToMime, "7z", "application/x-7z-compressed");
		put(extToMime, "gz", "application/gzip");
		put(extToMime, "xz", "application/x-xz");
		put(extToMime, "epub", "application/epub+zip");
		put(extToMime, "m3u8", "application/vnd.apple.mpegurl");
		
		EXTENSION_TO_MIME = Collections.unmodifiableMap(extToMime);
		Map<String, String> mimeToExt = new LinkedHashMap<>();
		
		for (Map.Entry<String, String> entry : extToMime.entrySet()) {
			if (!mimeToExt.containsKey(entry.getValue())) {
				mimeToExt.put(entry.getValue(), entry.getKey());
			}
		}
		
		// Older / alternative MIME names.
		putMime(mimeToExt, "text/comma-separated-values", "csv");
		putMime(mimeToExt, "text/x-csv", "csv");
		putMime(mimeToExt, "text/xml", "xml");
		putMime(mimeToExt, "text/rtf", "rtf");
		putMime(mimeToExt, "application/rar", "rar");
		putMime(mimeToExt, "application/x-rar-compressed", "rar");
		putMime(mimeToExt, "image/x-icon", "ico");
		putMime(mimeToExt, "image/ico", "ico");
		putMime(mimeToExt, "audio/x-m4a", "m4a");
		putMime(mimeToExt, "audio/x-wav", "wav");
		putMime(mimeToExt, "application/x-flac", "flac");
		putMime(mimeToExt, "application/javascript", "js");
		putMime(mimeToExt, "application/x-javascript", "js");
		putMime(mimeToExt, "application/x-gzip", "gz");
		
		MIME_TO_EXTENSION = Collections.unmodifiableMap(mimeToExt);
	}
	
	private TypeResolver() {}
	
	// Returns lowercase extension without dot.
	public static String getExtension(String name) {
		if (name == null) return "";
		String cleanName = name.trim();
		if (cleanName.isEmpty()) return "";
		
		int queryIndex = cleanName.indexOf('?');
		if (queryIndex >= 0) cleanName = cleanName.substring(0, queryIndex);
		
		int fragmentIndex = cleanName.indexOf('#');
		if (fragmentIndex >= 0) cleanName = cleanName.substring(0, fragmentIndex);
		
		int slashIndex = Math.max(cleanName.lastIndexOf('/'), cleanName.lastIndexOf('\\'));
		String fileName = cleanName.substring(slashIndex + 1);
		int dotIndex = fileName.lastIndexOf('.');
		
		if (dotIndex <= 0 || dotIndex == fileName.length() - 1) return "";
		return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
	}
	
	public static String getExtensionFromUrl(String url) {
		if (url == null || url.trim().isEmpty()) return "";
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		extension = normalizeExtension(extension);
		if (!extension.isEmpty()) return extension;
		
		try {
			Uri uri = Uri.parse(url);
			String lastPathSegment = uri.getLastPathSegment();
			extension = getExtension(lastPathSegment);
			if (!extension.isEmpty()) return extension;
			return getExtension(uri.getPath());
            
		} catch (Exception ignored) {
			return getExtension(url);
		}
	}
	
	public static String getExtensionFromMime(String mime) {
		String normalizedMime = normalizeMime(mime);
		if (normalizedMime.isEmpty()) return "";
		
		String extension = MIME_TO_EXTENSION.get(normalizedMime);
		if (extension != null) return extension;
		extension = ANDROID_MIME_MAP.getExtensionFromMimeType(normalizedMime);
		extension = normalizeExtension(extension);
		
		return extension.isEmpty() ? "" : extension;
	}
	
	public static String getMimeFromExtension(String extension) {
		String ext = normalizeExtension(extension);
		if (ext.isEmpty()) return "";
		
		String mime = EXTENSION_TO_MIME.get(ext);
		if (mime != null) return mime;
		mime = ANDROID_MIME_MAP.getMimeTypeFromExtension(ext);
		mime = normalizeMime(mime);
		
		return mime.isEmpty() ? "" : mime;
	}
	
	public static String getMimeFromName(String name) {
		return getMimeFromExtension(getExtension(name));
	}
	
	public static String getMimeFromUrl(String url) {
		return getMimeFromExtension(getExtensionFromUrl(url));
	}
	
	
	// Useing ContentResolver.
	public static String getMime(ContentResolver resolver, Uri uri) {
		if (uri == null) return "";
		
		if (resolver != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
			String mime = normalizeMime(resolver.getType(uri));
			if (!mime.isEmpty()) return mime;
		}
		
		String mime = getMimeFromUrl(uri.toString());
		if (!mime.isEmpty()) return mime;
		
		return getMimeFromName(uri.getPath());
	}
	
	public static boolean hasExtension(String extension) {
		String ext = normalizeExtension(extension);
		if (ext.isEmpty()) return false;
		
		return EXTENSION_TO_MIME.containsKey(ext) || ANDROID_MIME_MAP.hasExtension(ext);
	}
	
	public static boolean hasMime(String mime) {
		String normalizedMime = normalizeMime(mime);
		if (normalizedMime.isEmpty()) return false;
		return MIME_TO_EXTENSION.containsKey(normalizedMime) || ANDROID_MIME_MAP.hasMimeType(normalizedMime);
	}
	
	private static void put(Map<String, String> map, String extension, String mime) {
		map.put(extension, mime);
	}
	
	private static void putMime(Map<String, String> map, String mime, String extension) {
		map.put(mime, extension);
	}
	
	private static String normalizeExtension(String extension) {
		if (extension == null) return "";
		String ext = extension.trim();
		
		while (ext.startsWith(".")) {
			ext = ext.substring(1);
		}
		
		return ext.toLowerCase(Locale.ROOT);
	}
	
	private static String normalizeMime(String mime) {
		if (mime == null) return "";
		int semicolonIndex = mime.indexOf(';');
		if (semicolonIndex >= 0) mime = mime.substring(0, semicolonIndex);
        return mime.trim().toLowerCase(Locale.ROOT);
	}
}
