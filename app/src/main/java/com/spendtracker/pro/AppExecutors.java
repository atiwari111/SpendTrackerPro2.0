package com.spendtracker.pro;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppExecutors — single shared background thread for all database operations.
 * Prevents thread proliferation from repeated Executors.newSingleThreadExecutor() calls.
 *
 * Usage:
 *   AppExecutors.db().execute(() -> { ... });
 */
public class AppExecutors {

    private static final ExecutorService DB_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool();

    /** Single background thread for Room database operations */
    public static ExecutorService db() {
        return DB_EXECUTOR;
    }

    /** Cached thread pool for network / IO operations */
    public static ExecutorService io() {
        return IO_EXECUTOR;
    }

    // Prevent instantiation
    private AppExecutors() {}
}
