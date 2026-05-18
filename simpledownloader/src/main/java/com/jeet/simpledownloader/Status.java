package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
public enum Status {
	STARTING, QUEUED, CONNECTING, 
    DOWNLOADING, PAUSED, CANCELLED, 
    WAITING_FOR_NETWORK, RETRYING, 
    COMPLETED, FAILED
}
