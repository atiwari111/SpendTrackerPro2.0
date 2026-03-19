package com.spendtracker.pro;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AppExecutors — shared background executors for database and IO operations.
 * Prevents thread proliferation from ad-hoc Executors.newSingleThreadExecutor() calls.
 *
 * Thread budget:
 *  DB  — 1 thread (serial): all Room writes/reads stay ordered, no WAL contention.
 *  IO  — 2–4 threads (bounded): CSV import, SMS batch import, file export.
 *        Queue capacity 64 prevents OOM under burst load; tasks beyond that are
 *        rejected with CallerRunsPolicy so the caller blocks rather than crashing.
 *
 * Usage:
 *   AppExecutors.db().execute(() -> { /* Room op *​/ });
 *   AppExecutors.io().execute(() -> { /* file / network op *​/ });
 */
public class AppExecutors {

    private static final ExecutorService DB_EXECUTOR =
            Executors.newSingleThreadExecutor();

    // Bounded pool: min 2, max 4 threads, 60 s keep-alive, queue depth 64.
    // newCachedThreadPool() was removed — it had no upper bound and could
    // spawn dozens of threads under heavy SMS import load.
    private static final ExecutorService IO_EXECUTOR =
            new ThreadPoolExecutor(
                    2, 4,
                    60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(64),
                    new ThreadPoolExecutor.CallerRunsPolicy());

    /** Single serialised thread for all Room database operations. */
    public static ExecutorService db() {
        return DB_EXECUTOR;
    }

    /** Bounded thread pool for file, CSV, and SMS batch IO. */
    public static ExecutorService io() {
        return IO_EXECUTOR;
    }

    private AppExecutors() {}
}
