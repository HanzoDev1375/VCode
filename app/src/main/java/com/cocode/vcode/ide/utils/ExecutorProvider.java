package com.cocode.vcode.ide.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thread coordinator managing background execution pools for the IDE application.
 * Segregates high-throughput disk I/O routines from heavy calculations,
 * providing simple conduits to pass results back to the Android main user interface thread.
 */
public class ExecutorProvider {

    private static volatile ExecutorProvider instance;

    private final ExecutorService ioExecutor;
    private final ExecutorService cpuExecutor;
    private final Handler mainHandler;

    private ExecutorProvider() {
        // Sequential single worker ensures file writes execute in deterministic order
        ioExecutor = Executors.newSingleThreadExecutor();
        // Fixed thread pool prevents layout compilation tasks from over-allocating core resources
        cpuExecutor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Thread-safe singleton accessor instantiation interface.
     */
    public static ExecutorProvider getInstance() {
        if (instance == null) {
            synchronized (ExecutorProvider.class) {
                if (instance == null) {
                    instance = new ExecutorProvider();
                }
            }
        }
        return instance;
    }

    /**
     * Enqueues tasks to the single thread background storage runner thread.
     */
    public void runOnIo(Runnable r) {
        if (r != null) ioExecutor.execute(r);
    }

    /**
     * Enqueues computational work blocks to the shared processing thread matrix pool.
     */
    public void runOnCpu(Runnable r) {
        if (r != null) cpuExecutor.execute(r);
    }

    /**
     * Returns process data feedback onto the main Android system main loop thread.
     */
    public void runOnMain(Runnable r) {
        if (r != null) mainHandler.post(r);
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

    /**
     * Performs clean environment teardown sequence on active worker instances.
     */
    public void shutdown() {
        ioExecutor.shutdown();
        cpuExecutor.shutdown();
    }
}