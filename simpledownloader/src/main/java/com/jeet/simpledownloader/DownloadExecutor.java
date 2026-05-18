package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class DownloadExecutor extends ThreadPoolExecutor {
	private static final int UNLIMITED_MAX_THREADS = Integer.MAX_VALUE;
	private final boolean unlimited;
	
	DownloadExecutor(int maxThreads) {
		super(
		maxThreads <= 0 ? 0 : maxThreads,
		maxThreads <= 0 ? UNLIMITED_MAX_THREADS : maxThreads,
		60L,
		TimeUnit.SECONDS,
		maxThreads <= 0 ? new SynchronousQueue<Runnable>() : new PriorityBlockingQueue<Runnable>()
		);
		unlimited = maxThreads <= 0;
		allowCoreThreadTimeOut(true);
	}
	
	boolean isUnlimited() {
		return unlimited;
	}
	
	boolean canResizeTo(int maxThreads) {
		return (maxThreads <= 0) == unlimited;
	}
	
	void setMaxThreads(int maxThreads) {
		if (!canResizeTo(maxThreads)) return;
		
		if (maxThreads <= 0) {
			setCorePoolSize(0);
			setMaximumPoolSize(UNLIMITED_MAX_THREADS);
			allowCoreThreadTimeOut(true);
			return;
		}
		
		int currentCore = getCorePoolSize();
		if (maxThreads > currentCore) {
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
		if (unlimited) return false;
		for (Runnable r : getQueue()) {
			if (r instanceof DownloadFutureTask && ((DownloadFutureTask) r).getTask() == task) return remove(r);
		}
		return false;
	}
	
	boolean hasRunnableQueuedTask() {
		if (isUnlimited()) return false;
		for (Runnable r : getQueue()) {
			if (r instanceof DownloadFutureTask) {
				DownloadTask task = ((DownloadFutureTask) r).getTask();
				if (task != null && SimpleDownloader.networkManager.canRunNow(task)) return true;
			}
		}
		return false;
	}
	
	int getQueuePosition(DownloadTask task) {
		if (unlimited) return 0;
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
		return unlimited ? 0 : getQueue().size();
	}
	
	boolean canStartImmediately() {
		if (unlimited) return true;
		return getActiveCount() < getMaximumPoolSize() && getQueue().isEmpty();
	}
}
