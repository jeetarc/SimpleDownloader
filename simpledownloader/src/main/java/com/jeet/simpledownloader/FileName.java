package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

/**
 * Use to get automatically resolved name for an output file.
 * Tow modes {@code FileName.AUTO} and {@code FileName.TIME_BASED}
 */
public enum FileName {
	AUTO, TIME_BASED;
	
	static FileName fromDatabaseValue(String value) {
		if (value == null) return null;
		try {
			return valueOf(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
