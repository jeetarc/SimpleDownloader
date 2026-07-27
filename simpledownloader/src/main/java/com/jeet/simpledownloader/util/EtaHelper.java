package com.jeet.simpledownloader.util;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.concurrent.TimeUnit;

/** Internal class */
public class EtaHelper {
    static final long ETA_UNKNOWN = -1L;
    static final long ETA_STALLED = -2L;
    private static final double ALPHA = 0.20;
    private static final int MIN_SAMPLES = 3;
    private static final long ETA_UPDATE_INTERVAL_MS = 500L;
    private static final long SPEED_SAMPLE_INTERVAL_MS = 500L;
    private static final long STALL_TIMEOUT_MS = 5000L;
    
    private static final long ETA_UPDATE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(ETA_UPDATE_INTERVAL_MS);
    private static final long SPEED_SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(SPEED_SAMPLE_INTERVAL_MS);
    private static final long STALL_TIMEOUT_NS = TimeUnit.MILLISECONDS.toNanos(STALL_TIMEOUT_MS);

    private double emaSpeed = 0.0;
    private int sampleCount = 0;
    private boolean initialized = false;
    private long lastBytes = -1L;
    private long bytesAtLastSpeedSample = -1L;
    private long lastProgressNs = 0L;
    private long lastEtaUpdateNs = 0L;
    private long lastSpeedSampleNs = 0L;
    private long lastEta = ETA_UNKNOWN;

    public synchronized long update(long speedBytesPerSecond, long bytesDownloaded, long totalBytes) {
        long now = System.nanoTime();

        if (totalBytes <= 0L) {
            lastEta = ETA_UNKNOWN;
            return lastEta;
        }

        long bytes = Math.max(0L, bytesDownloaded);
        long speed = Math.max(0L, speedBytesPerSecond);

        if (bytes >= totalBytes) {
            lastEta = 0L;
            return 0L;
        }

        if (lastBytes < 0L) {
            initialize(now, bytes);
            return ETA_UNKNOWN;
        }

        if (bytes < lastBytes) {
            initialize(now, bytes);
            return ETA_UNKNOWN;
        }

        boolean progressed = bytes > lastBytes;

        if (progressed) {
            lastBytes = bytes;
            lastProgressNs = now;

            if (lastEta == ETA_STALLED) {
                lastEta = ETA_UNKNOWN;
            }
        }

        if (lastProgressNs > 0L && now - lastProgressNs >= STALL_TIMEOUT_NS) {
            resetSamplesForStall(bytes);
            lastEta = ETA_STALLED;
            return ETA_STALLED;
        }

        if (speed > 0L && bytes > bytesAtLastSpeedSample && shouldTakeSpeedSample(now)) {
            addSpeedSample(speed);
            bytesAtLastSpeedSample = bytes;
            lastSpeedSampleNs = now;
        }

        if (lastEtaUpdateNs > 0L && now - lastEtaUpdateNs < ETA_UPDATE_INTERVAL_NS) return lastEta;
        lastEtaUpdateNs = now;

        if (sampleCount < MIN_SAMPLES || emaSpeed <= 0.0) {
            lastEta = ETA_UNKNOWN;
            return lastEta;
        }

        long remaining = Math.max(0L, totalBytes - bytes);
        double etaMs = (remaining / emaSpeed) * 1000.0;
        lastEta = safeCeil(etaMs);
        return lastEta;
    }

    public synchronized void reset() {
        emaSpeed = 0.0;
        sampleCount = 0;
        initialized = false;
        lastBytes = -1L;
        bytesAtLastSpeedSample = -1L;
        lastProgressNs = 0L;
        lastEtaUpdateNs = 0L;
        lastSpeedSampleNs = 0L;
        lastEta = ETA_UNKNOWN;
    }

    public synchronized long getLastEta() {
        return lastEta;
    }

    private void initialize(long nowNs, long bytes) {
        emaSpeed = 0.0;
        sampleCount = 0;
        initialized = false;
        lastBytes = bytes;
        bytesAtLastSpeedSample = bytes;
        lastProgressNs = nowNs;
        lastEtaUpdateNs = 0L;
        lastSpeedSampleNs = 0L;

        lastEta = ETA_UNKNOWN;
    }

    private boolean shouldTakeSpeedSample(long nowNs) {
        return lastSpeedSampleNs == 0L || nowNs - lastSpeedSampleNs >= SPEED_SAMPLE_INTERVAL_NS;
    }

    private void addSpeedSample(long speed) {
        if (!initialized) {
            emaSpeed = speed;
            initialized = true;
        } else {
            emaSpeed = (ALPHA * speed) + ((1.0 - ALPHA) * emaSpeed);
        }

        sampleCount++;
    }

    private void resetSamplesForStall(long bytes) {
        emaSpeed = 0.0;
        sampleCount = 0;
        initialized = false;
        bytesAtLastSpeedSample = bytes;
        lastSpeedSampleNs = 0L;
    }

    private static long safeCeil(double value) {
        if (!Double.isFinite(value) || value < 0.0) return ETA_UNKNOWN;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) Math.ceil(value);
    }
}
