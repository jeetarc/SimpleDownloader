package com.jeetarc.simpledownloader;

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
	STARTING, QUEUED, CONNECTING, DOWNLOADING, PAUSED, CANCELLED,
	WAITING_FOR_NETWORK, RETRYING, COMPLETED, FAILED;
	
	public boolean isActive() {
		return this == Status.DOWNLOADING || this == Status.CONNECTING || this == Status.RETRYING;
	}
	
	public boolean isFinished() {
		return this == Status.COMPLETED || this == Status.FAILED || this == Status.CANCELLED;
	}
    
    public boolean isQueued() { 
		return this == Status.QUEUED; 
	}
    
    public boolean isDownloading() {
        return this == Status.DOWNLOADING;
    }
    
    public boolean isConnecting() {
        return this == Status.CONNECTING;
    }
	
	public boolean isWaitingForNetwork() { 
		return this == Status.WAITING_FOR_NETWORK;
	}
	
	public boolean isPaused() { 
		return this == Status.PAUSED; 
	}
	
	public boolean isComplete() { 
		return this == Status.COMPLETED; 
	}
	
	public boolean isFailed() { 
		return this == Status.FAILED; 
	}
    
    public boolean isRetrying() {
        return this == Status.RETRYING;
    }
    
	public boolean isCancelled() { 
		return this == Status.CANCELLED; 
	}
	
	static Status fromDatabaseValue(String value) {
		if (value == null || value.isEmpty()) return PAUSED;
		try {
			return valueOf(value);
		} catch (Exception ignored) {}
		return PAUSED;
	}
}
