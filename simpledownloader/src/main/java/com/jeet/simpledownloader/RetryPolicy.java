package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/**
 * Controls how many times to retry a failed download and how long to wait 
 * between attempts. Use the Builder to configure.
 */

public final class RetryPolicy {
	private final int maxRetryCount;
	private final long initialDelayMs;
	private final long maxDelayMs;
	private final double multiplier;
	
	private RetryPolicy(Builder builder) {
		maxRetryCount = builder.maxRetryCount;
		initialDelayMs = builder.initialDelayMs;
		maxDelayMs = builder.maxDelayMs;
		multiplier = builder.multiplier;
	}
	
	public static RetryPolicy ofAttempts(int maxRetryCount) {
		return builder().maxRetryCount(maxRetryCount).build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public int getMaxRetryCount() {
		return maxRetryCount;
	}
	
	long getDelayMs(int attempt) {
		if (attempt <= 0) return 0;
		double value = initialDelayMs * Math.pow(multiplier, attempt - 1);
		return Math.min(maxDelayMs, Math.max(0, (long) value));
	}
	
	public long getInitialDelayMs() {
		return initialDelayMs;
	}
	
	public long getMaxDelayMs() {
		return maxDelayMs;
	}
	
	public double getMultiplier() {
		return multiplier;
	}
	
	boolean shouldRetry(Throwable error) {
		if (error instanceof DownloadException) return ((DownloadException) error).isRetryable();
		if (error instanceof SocketTimeoutException) return true;
		if (error instanceof UnknownHostException) return true;
		if (error instanceof InterruptedIOException) return false;
		if (error instanceof SSLException) return true;
		return error instanceof java.io.IOException;
	}
	
	public static final class Builder {
		private int maxRetryCount = 1;
		private long initialDelayMs = 750;
		private long maxDelayMs = 30000;
		private double multiplier = 2.0;
		
		public Builder maxRetryCount(int maxRetryCount) {
			this.maxRetryCount = Math.max(0, maxRetryCount);
			return this;
		}
		
		public Builder initialDelayMs(long initialDelayMs) {
			this.initialDelayMs = Math.max(0, initialDelayMs);
			return this;
		}
		
		public Builder maxDelayMs(long maxDelayMs) {
			this.maxDelayMs = Math.max(0, maxDelayMs);
			return this;
		}
		
		public Builder multiplier(double multiplier) {
			this.multiplier = multiplier <= 1 ? 1 : multiplier;
			return this;
		}
		
		public RetryPolicy build() {
			return new RetryPolicy(this);
		}
	}
}
