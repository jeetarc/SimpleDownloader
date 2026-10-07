package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

/**
 * Use to get automatically resolved MIME type for an output file.
 * Two modes {@code MimeType.AUTO} and {@code MimeType.FROM_NAME}
 */
public enum MimeType {
	AUTO, FROM_NAME;
	
	static MimeType fromDatabaseValue(String value) {
		if (value == null) return null;
		
		try {
			return valueOf(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
