package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / under Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.concurrent.FutureTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

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
	
	@Override
	protected void done() {
		if (isCancelled()) return;
		
		try {
			get();
			
		} catch (CancellationException ignored) {
			// normal cancellation, no problem! 
			
		} catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(error);
			
		} catch (ExecutionException error) {
			Throwable cause = error.getCause();
			if (cause instanceof Error) throw (Error) cause;
			if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            
			throw new RuntimeException(cause);
		}
	}
	
	DownloadTask getTask() {
		return runnable.getTask();
	}
}
