package com.jeet.simpledownloader.thumbnail;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import android.net.Uri;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

public final class ThumbRequest {
	private static final long KB = 1024L;
	private static final long MB = 1024L * KB;
	
	static final long[] MILESTONES = {
		80L * KB, 400L * KB, 1L * MB, 4L * MB, 8L * MB, 16L * MB, 32L * MB
	};
	
	final long id;
	final File sourceFile;
	final Uri sourceUri;
	final String mimeType;
	final int targetWidth;
	final int targetHeight;
	final ThumbLoader.Callback callback;
	final boolean partialEnabled;
	private long availableBytes;
	private int attemptedMilestoneIndex = -1;
	private long generation;
	private boolean running;
	private boolean completed;
	private boolean finalAttempted;
	private boolean delivered;
	private boolean finished;
	private boolean cancelled;
	private boolean currentAttemptFinal;
	private Future<?> decodeFuture;
	private ScheduledFuture<?> timeoutFuture;
	
	ThumbRequest(long id, File sourceFile, Uri sourceUri, String mimeType, int targetWidth, int targetHeight, ThumbLoader.Callback callback) {
		this.id = id;
		this.sourceFile = sourceFile;
		this.sourceUri = sourceUri;
		this.mimeType = ThumbDecoder.normalizeMime(mimeType);
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.callback = callback;
		this.partialEnabled = ThumbDecoder.supportsPartial(this.mimeType, sourceFile, sourceUri);
	}
	
	public long getId() {
		return id;
	}
	
	public synchronized long getAvailableBytes() {
		return availableBytes;
	}
	
	public synchronized boolean isRunning() {
		return running;
	}
	
	public synchronized boolean isCompleted() {
		return completed;
	}
	
	public synchronized boolean isDelivered() {
		return delivered;
	}
	
	public synchronized boolean isFinished() {
		return finished;
	}
	
	public synchronized boolean isCancelled() {
		return cancelled;
	}
	
	synchronized Attempt onBytesAvailable(long bytes) {
		if (bytes > availableBytes) availableBytes = bytes;
		return claimNextAttemptLocked();
	}
	
	synchronized Attempt onCompleted(long bytes) {
		if (bytes > availableBytes) availableBytes = bytes;
		completed = true;
		return claimNextAttemptLocked();
	}
	
	private Attempt claimNextAttemptLocked() {
		if (running || finished || cancelled) return null;
		
		if (completed) {
			if (finalAttempted) return null;
			finalAttempted = true;
			return beginAttemptLocked(true, -1);
		}
		
		if (!partialEnabled) return null;
		int eligibleIndex = highestReachedMilestone(availableBytes);
		if (eligibleIndex <= attemptedMilestoneIndex) return null;
		attemptedMilestoneIndex = eligibleIndex;
		return beginAttemptLocked(false, eligibleIndex);
	}
	
	private Attempt beginAttemptLocked(boolean finalAttempt, int milestoneIndex) {
		running = true;
		currentAttemptFinal = finalAttempt;
		generation++;
		return new Attempt(generation, finalAttempt, milestoneIndex, availableBytes);
	}
	
	private static int highestReachedMilestone(long bytes) {
		for (int i = MILESTONES.length - 1; i >= 0; i--) {
			if (bytes >= MILESTONES[i]) return i;
		}
		return -1;
	}
	
	void attachFutures(Attempt attempt, Future<?> decode, ScheduledFuture<?> timeout) {
		boolean reject;
		
		synchronized (this) {
			reject = attempt == null || !running || cancelled || generation != attempt.generation;
			if (!reject) {
				decodeFuture = decode;
				timeoutFuture = timeout;
			}
		}
		
		if (reject) {
			if (decode != null) decode.cancel(true);
			if (timeout != null) timeout.cancel(false);
		}
	}
	
	Transition finishAttempt(long attemptGeneration, boolean success, boolean timedOut) {
		Future<?> decodeToCancel = null;
		ScheduledFuture<?> timeoutToCancel = null;
		Attempt nextAttempt = null;
		boolean accepted;
		boolean terminalFailure = false;
		
		synchronized (this) {
			accepted = running && !cancelled && generation == attemptGeneration;
			if (!accepted) return Transition.stale();
			
			if (timedOut) {
				decodeToCancel = decodeFuture;
			} else {
				timeoutToCancel = timeoutFuture;
			}
			
			decodeFuture = null;
			timeoutFuture = null;
			running = false;
			
			if (success) {
				delivered = true;
				finished = true;
				
			} else if (currentAttemptFinal) {
				finished = true;
				terminalFailure = true;
				
			} else {
				nextAttempt = claimNextAttemptLocked();
			}
		}
		
		return new Transition(true, success, terminalFailure, nextAttempt, decodeToCancel, timeoutToCancel);
	}
	
	void cancel() {
		Future<?> decode;
		ScheduledFuture<?> timeout;
		
		synchronized (this) {
			if (cancelled) return;
			cancelled = true;
			finished = true;
			running = false;
			generation++;
			decode = decodeFuture;
			timeout = timeoutFuture;
			decodeFuture = null;
			timeoutFuture = null;
		}
		
		if (decode != null) decode.cancel(true);
		if (timeout != null) timeout.cancel(false);
	}
	
	synchronized boolean canDeliver(long attemptGeneration) {
		return delivered && !cancelled && generation == attemptGeneration;
	}
	
	synchronized boolean canReportUnavailable(long attemptGeneration) {
		return finished && !delivered && !cancelled && generation == attemptGeneration;
	}
	
	static final class Attempt {
		final long generation;
		final boolean finalAttempt;
		final int milestoneIndex;
		final long availableBytes;
		
		Attempt(long generation, boolean finalAttempt, int milestoneIndex, long availableBytes) {
			this.generation = generation;
			this.finalAttempt = finalAttempt;
			this.milestoneIndex = milestoneIndex;
			this.availableBytes = availableBytes;
		}
	}
	
	static final class Transition {
		final boolean accepted;
		final boolean success;
		final boolean terminalFailure;
		final Attempt nextAttempt;
		final Future<?> decodeToCancel;
		final ScheduledFuture<?> timeoutToCancel;
		
		Transition(boolean accepted, boolean success, boolean terminalFailure, Attempt nextAttempt, Future<?> decodeToCancel, ScheduledFuture<?> timeoutToCancel) {
			this.accepted = accepted;
			this.success = success;
			this.terminalFailure = terminalFailure;
			this.nextAttempt = nextAttempt;
			this.decodeToCancel = decodeToCancel;
			this.timeoutToCancel = timeoutToCancel;
		}
		
		static Transition stale() {
			return new Transition(false, false, false, null, null, null);
		}
	}
}
