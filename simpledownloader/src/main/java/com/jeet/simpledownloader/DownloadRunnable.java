package com.jeet.simpledownloader;

/*
 * Copyright (c) 2026 Jeet Jati, under jeetarc.
 *
 * This source code is part of SimpleDownloader.
 */
 
class DownloadRunnable implements Runnable, Comparable<DownloadRunnable> {
	private final DownloadTask task;
	private final long sequence;
	
	DownloadRunnable(DownloadTask task, long sequence) {
		this.task = task;
		this.sequence = sequence;
	}
	
	@Override
	public void run() {
		new DownloadWorker(task).run();
	}
	
	@Override
	public int compareTo(DownloadRunnable other) {
		int p1 = task.getPriority() != null ? task.getPriority().getWeight() : 0;
		int p2 = other.task.getPriority() != null ? other.task.getPriority().getWeight() : 0;
		if (p1 != p2) return p2 - p1;
		return Long.compare(sequence, other.sequence);
	}
	
	DownloadTask getTask() {
		return task;
	}
}
