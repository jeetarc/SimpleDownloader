package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet / Jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */

/**
 * Tells current status of the {@code DownloadTask},
 * for example DOWNLOADING, CONNECTING, PAUSED, COMPLETED, FAILED, etc.
 */
public enum Status {
	STARTING(0), QUEUED(1), CONNECTING(2),
	DOWNLOADING(3), PAUSED(4), CANCELLED(5),
	WAITING_FOR_NETWORK(6), RETRYING(7),
	COMPLETED(8), FAILED(9);
	
	final int code;
	
	Status(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return code;
	}
	
	static Status fromCode(int code) {
		for (Status status : values()) {
			if (status.code == code) return status;
		}
		return PAUSED;
	}
	
	static Status fromDatabaseValue(String value) {
		if (value == null || value.length() <= 0) return PAUSED;
		try {
			return fromCode(Integer.parseInt(value));
		} catch (Exception ignored) {}
		try {
			return valueOf(value);
		} catch (Exception ignored) {}
		return PAUSED;
	}
}
