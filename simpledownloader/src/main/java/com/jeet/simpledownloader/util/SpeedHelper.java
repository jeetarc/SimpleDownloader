package com.jeet.simpledownloader.util;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/

import java.util.concurrent.TimeUnit;

/** Internal class*/
public class SpeedHelper {
    private static final double ALPHA = 0.25;
    private static final long UPDATE_INTERVAL_MS = 2000L;
    private static final long STALL_TIMEOUT_MS = 5000L;
    private static final long UPDATE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(UPDATE_INTERVAL_MS);
    private static final long STALL_TIMEOUT_NS = TimeUnit.MILLISECONDS.toNanos(STALL_TIMEOUT_MS);

    private long windowStartNs = 0L;
    private long windowStartBytes = 0L;
    private long lastBytes = -1L;
    private long lastProgressNs = 0L;

    private double emaSpeed = 0.0;
    private long cachedSpeed = 0L;
    private boolean initialized = false;

    public synchronized long update(long totalBytesDownloaded) {
        long now = System.nanoTime();
        long bytes = Math.max(0L, totalBytesDownloaded);

        if (lastBytes < 0L) {
            initialize(now, bytes);
            return 0L;
        }

        if (bytes < lastBytes) {
            initialize(now, bytes);
            return 0L;
        }

        if (bytes > lastBytes) {
            lastBytes = bytes;
            lastProgressNs = now;
        }

        if (lastProgressNs > 0L && now - lastProgressNs >= STALL_TIMEOUT_NS) {
            resetSpeedWindow(now, bytes);
            return 0L;
        }

        long elapsedNs = now - windowStartNs;
        if (elapsedNs < UPDATE_INTERVAL_NS) return cachedSpeed;
        long downloaded = Math.max(0L, bytes - windowStartBytes);
        double instantSpeed = elapsedNs > 0L ? (downloaded * 1_000_000_000.0) / elapsedNs : 0.0;

        if (!initialized) {
            if (downloaded > 0L) {
                emaSpeed = instantSpeed;
                initialized = true;
            } else {
                emaSpeed = 0.0;
            }
        } else {
            double weight = getWeight(elapsedNs, UPDATE_INTERVAL_NS, ALPHA);
            emaSpeed = (weight * instantSpeed) + ((1.0 - weight) * emaSpeed);
        }

        windowStartNs = now;
        windowStartBytes = bytes;
        cachedSpeed = initialized ? safeRound(emaSpeed) : 0L;
        return cachedSpeed;
    }

    public synchronized void reset(long currentBytes) {
        initialize(System.nanoTime(), Math.max(0L, currentBytes));
    }

    synchronized long getCachedSpeed() {
        return cachedSpeed;
    }

    private void initialize(long nowNs, long bytes) {
        windowStartNs = nowNs;
        windowStartBytes = bytes;
        lastBytes = bytes;
        lastProgressNs = nowNs;

        emaSpeed = 0.0;
        cachedSpeed = 0L;
        initialized = false;
    }

    private void resetSpeedWindow(long nowNs, long bytes) {
        windowStartNs = nowNs;
        windowStartBytes = bytes;

        emaSpeed = 0.0;
        cachedSpeed = 0L;
        initialized = false;
    }

    private static double getWeight(long elapsedNs, long targetIntervalNs, double baseAlpha) {
        double factor = Math.min(2.0, Math.max(0.5, (double) elapsedNs / targetIntervalNs));
        double weight = baseAlpha * factor;

        if (weight < 0.10) return 0.10;
        if (weight > 0.60) return 0.60;
        return weight;
    }

    private static long safeRound(double value) {
        if (!Double.isFinite(value) || value <= 0.0) return 0L;
        if (value >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(value);
    }
}