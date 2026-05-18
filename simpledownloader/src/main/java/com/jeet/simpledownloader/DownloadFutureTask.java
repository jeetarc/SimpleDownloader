package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.util.concurrent.FutureTask;

class DownloadFutureTask extends FutureTask<Object> implements Comparable<DownloadFutureTask> {
	private final DownloadRunnable runnable;
	
	DownloadFutureTask(DownloadRunnable runnable) {
		super(runnable, null);
		this.runnable = runnable;
	}
	
	@Override
	public int compareTo(DownloadFutureTask other) {
		return runnable.compareTo(other.runnable);
	}
	
	DownloadTask getTask() {
		return runnable.getTask();
	}
}
