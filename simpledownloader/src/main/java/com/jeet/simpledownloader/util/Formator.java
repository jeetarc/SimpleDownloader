package com.jeet.simpledownloader.util;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

/**
 * Formatting utilities for byte sizes, download speeds, estimated
 * remaining time, etc.
 */
public final class Formator {
	private Formator() {}
	
	public static String formatBytes(long bytes) {
		if (bytes < 0) return "--";
		
		final long KB = 1024L;
		final long MB = KB * 1024L;
		final long GB = MB * 1024L;
		
		if (bytes >= GB) return formatDecimal(bytes, GB, 2) + " GB";
		if (bytes >= MB) return formatDecimal(bytes, MB, 1) + " MB";
		if (bytes >= KB) return (bytes / KB) + " KB";
		return bytes + " B";
	}
	
	public static String formatSpeed(long bytesPerSec) {
		if (bytesPerSec <= 0) return "0 B/s";
		return formatBytes(bytesPerSec) + "/s";
	}
    
    public static String formatEta(long etaMs) {
        if (etaMs < 0) return "...";

        long totalSeconds = etaMs / 1000L;
        if (totalSeconds < 60) return totalSeconds + "s";
        long totalMinutes = totalSeconds / 60L;
        long remainingSeconds = totalSeconds % 60L;
        
        if (totalMinutes < 60) return totalMinutes + "m " + remainingSeconds + "s";
        long hours = totalMinutes / 60L;
        long remainingMinutes = totalMinutes % 60L;
        return hours + "h " + remainingMinutes + "m";
    }
	
	public static String formatRatio(String part, String total, String separator) {
		if (part == null) part = "";
		if (total == null) total = "";
		if (separator == null) separator = " / ";
		return part + separator + total;
	}
	
	public static String formatRatio(String part, String total) {
		if (part == null) part = "";
		if (total == null) total = "";
		return part + " / " + total;
	}
	
	public static String formatDecimal(long bytes, long unit, int decimals) {
		long whole = bytes / unit;
		long remainder = bytes % unit;
		long scale = decimals == 2 ? 100L : 10L;
		long fraction = (remainder * scale + unit / 2L) / unit;
		
		if (fraction >= scale) {
			whole++;
			fraction = 0;
		}
		
		if (decimals == 2 && fraction < 10) return whole + ".0" + fraction;
		return whole + "." + fraction;
	}
}
