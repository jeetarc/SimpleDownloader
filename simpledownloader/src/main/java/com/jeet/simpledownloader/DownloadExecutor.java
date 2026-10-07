package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class DownloadExecutor extends ThreadPoolExecutor {
	
	DownloadExecutor(int maxThreads) {
		super(Math.max(1, maxThreads), Math.max(1, maxThreads), 60L, TimeUnit.SECONDS, new PriorityBlockingQueue<Runnable>());
		allowCoreThreadTimeOut(true);
	}
	
	void setMaxThreads(int maxThreads) {
		maxThreads = Math.max(1, maxThreads);
		int currentMaximum = getMaximumPoolSize();
		
		if (maxThreads > currentMaximum) {
			setMaximumPoolSize(maxThreads);
			setCorePoolSize(maxThreads);
		} else {
			setCorePoolSize(maxThreads);
			setMaximumPoolSize(maxThreads);
		}
		
		allowCoreThreadTimeOut(true);
	}
	
	Future<?> submitTask(DownloadTask task, long sequence) {
		DownloadFutureTask futureTask = new DownloadFutureTask(new DownloadRunnable(task, sequence));
		execute(futureTask);
		return futureTask;
	}
	
	boolean removeTask(DownloadTask task) {
		for (Runnable runnable : getQueue()) {
			if (!(runnable instanceof DownloadFutureTask)) continue;
			DownloadFutureTask future = (DownloadFutureTask) runnable;
			if (future.getTask() != task) continue;
			
			if (remove(future)) {
				future.cancel(false);
				return true;
			}
		}
		
		return false;
	}
	
	boolean hasRunnableQueuedTask() {
		for (Runnable r : getQueue()) {
			if (r instanceof DownloadFutureTask) {
				DownloadTask task = ((DownloadFutureTask) r).getTask();
				if (task != null && task.mDownloader.networkManager.canRunNow(task)) return true;
			}
		}
		return false;
	}
	
	int getQueuePosition(DownloadTask task) {
		List<DownloadFutureTask> list = new ArrayList<>();
		
		for (Runnable r : getQueue()) {
			if (r instanceof DownloadFutureTask) list.add((DownloadFutureTask) r);
		}
		
		Collections.sort(list);
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).getTask() == task) return i + 1;
		}
		
		return 0;
	}
	
	int getQueuedCount() {
		return getQueue().size();
	}
	
	boolean canStartImmediately() {
		return getActiveCount() < getMaximumPoolSize() && getQueue().isEmpty();
	}
	
	boolean awaitTerminationQuietly(long timeoutMs) {
		try {
			return awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
