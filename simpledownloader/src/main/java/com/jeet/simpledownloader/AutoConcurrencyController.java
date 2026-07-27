package com.jeet.simpledownloader;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

final class AutoConcurrencyController {
	private static final long SPEED_SAMPLE_INTERVAL_MS = 500L;
	private static final long FIRST_ALLOCATION_DELAY_MS = 2500L;
	private static final long SAMPLE_MAX_AGE_MS = 15_000L;
	
	private boolean initialized;
	private boolean firstAllocationDone;
	private long lastRecordTime;
	private long firstSampleTime;
	private long lastSampleTime;
	private int lastSampleActiveCount;
	private long smoothedTotalSpeed;
	
	void resetLocked() {
		initialized = false;
		firstAllocationDone = false;
		
		firstSampleTime = 0;
		lastRecordTime = 0;
		lastSampleTime = 0;
		smoothedTotalSpeed = 0;
		lastSampleActiveCount = 0;
	}
	
	void ensureInitializedLocked() {
		if (initialized) return;
		SimpleDownloader.setEffectiveMaxConcurrentLocked(1);
		initialized = true;
		firstAllocationDone = false;
		firstSampleTime = 0;
		lastRecordTime = 0;
		lastSampleTime = 0;
		smoothedTotalSpeed = 0;
		lastSampleActiveCount = 0;
	}
	
	void markInitializedLocked() {
		initialized = true;
	}
	
	void recordSpeedSampleLocked() {
		if (!SimpleDownloader.isAutoConcurrentLocked()) return;
		if (SimpleDownloader.taskManager == null) return;
		
		ensureInitializedLocked();
		long now = System.currentTimeMillis();
		if (now - lastRecordTime < SPEED_SAMPLE_INTERVAL_MS) return;
		lastRecordTime = now;
		long totalSpeed = SimpleDownloader.taskManager.getCachedTotalActiveSpeedLocked();
		int activeCount = SimpleDownloader.taskManager.getCachedActiveSpeedTaskCountLocked();
		if (totalSpeed <= 0 || activeCount <= 0) return;
		
		if (smoothedTotalSpeed <= 0) {
			smoothedTotalSpeed = totalSpeed;
		} else {
			smoothedTotalSpeed = smoothSpeed(smoothedTotalSpeed, totalSpeed);
		}
		
		lastSampleTime = now;
		lastSampleActiveCount = activeCount;
		
		// Auto mode will start with only one task frist.
		if (!firstAllocationDone) doFirstAllocationLocked(now);
	}
	
	private void doFirstAllocationLocked(long now) {
		if (firstAllocationDone) return;
		if (firstSampleTime <= 0) {
			firstSampleTime = now;
			return;
		}
		
		if (now - firstSampleTime < FIRST_ALLOCATION_DELAY_MS) return;
		int targetSlot = calculateSlotFromSpeed(smoothedTotalSpeed);
		targetSlot = clamp(targetSlot, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
		SimpleDownloader.setEffectiveMaxConcurrentLocked(targetSlot);
		firstAllocationDone = true;
		
		if (SimpleDownloader.slotManager != null) {
			SimpleDownloader.slotManager.submitReadyHeldTasksLocked();
		}
	}
	
	void evaluateAfterTaskFinishedLocked() {
		if (!SimpleDownloader.isAutoConcurrentLocked()) return;
		ensureInitializedLocked();
		if (!firstAllocationDone) return;
		
		long now = System.currentTimeMillis();
		if (lastSampleTime <= 0 || now - lastSampleTime > SAMPLE_MAX_AGE_MS) return;
		
		int targetSlot = calculateSlotFromSpeed(smoothedTotalSpeed);
		targetSlot = clamp(targetSlot, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
		SimpleDownloader.setEffectiveMaxConcurrentLocked(targetSlot);
	}
	
	private int calculateSlotFromSpeed(long bytesPerSecond) {
		if (bytesPerSecond <= 0) return 1;
		double mbps = (bytesPerSecond * 8.0) / 1_000_000.0;
		int roundedMbps = (int) (Math.round(mbps / 10.0) * 10);
		int slots = roundedMbps / 10;
		return clamp(slots, SimpleDownloader.AUTO_MIN_SLOT, SimpleDownloader.AUTO_MAX_SLOT);
	}
	
	private long smoothSpeed(long oldSpeed, long newSpeed) {
		if (oldSpeed <= 0) return newSpeed;
		return (long) ((oldSpeed * 0.65f) + (newSpeed * 0.35f));
	}
	
	private int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
