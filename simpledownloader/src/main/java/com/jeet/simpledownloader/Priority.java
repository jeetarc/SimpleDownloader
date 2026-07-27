package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet / Jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */

/**
 * The priority of a download task in the queue.
 *
 * <p>Tasks with higher priority are start download before lower priority tasks. 
 * (eg. NEXT > HIGH > NORMAL > LOW) </p>
 */
public enum Priority {
	NEXT(4), HIGH(3), NORMAL(2), LOW(1);
	final int weight;
	
	Priority(int weight) {
		this.weight = weight;
	}
	
	public int getWeight() {
		return weight;
	}
	
	static Priority fromCode(int code) {
		for (Priority priority : values()) {
			if (priority.weight == code) return priority;
		}
		return NORMAL;
	}
	
	static Priority fromDatabaseValue(String value) {
		if (value == null || value.length() <= 0) return NORMAL;
		try {
			return fromCode(Integer.parseInt(value));
		} catch (Exception ignored) {}
		try {
			return valueOf(value);
		} catch (Exception ignored) {}
		return NORMAL;
	}
}
