package com.jeet.simpledownloader.util;

/*
* Copyright (c) 2026 Jeet / Jeetarc.
*
* This source code is part of SimpleDownloader.
*/
import com.jeet.simpledownloader.SimpleDownloader;

public class Logs {
    private Logs() {}

    public static void info(String msg) {
        if (!enabled()) return;
        System.out.println("SimpleDownloader: " + msg);
    }

    public static void warn(String msg) {
        if (!enabled()) return;
        System.err.println("SimpleDownloader WARNING: " + msg);
    }

    public static void warn(String msg, Throwable t) {
        if (!enabled()) return;
        warn(msg);
        t.printStackTrace(System.err);
    }

    public static void err(String msg) {
        if (!enabled()) return;
        System.err.println("SimpleDownloader ERROR: " + msg);
    }

    public static void err(String msg, Throwable t) {
        if (!enabled()) return;
        err(msg);
        t.printStackTrace(System.err);
    }

    public static void err(Throwable t) {
        if (!enabled()) return;
        t.printStackTrace(System.err);
    }

    public static boolean enabled() {
        return SimpleDownloader.loggingEnabled;
    }
}
