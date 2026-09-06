package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded serial lifecycle lane with an emergency fail-closed shutdown path. */
final class LanFtpLifecycleExecutor {
    private final ThreadFactory threadFactory;
    private final ThreadPoolExecutor executor;

    LanFtpLifecycleExecutor(ThreadFactory threadFactory) {
        this.threadFactory = Objects.requireNonNull(threadFactory, "threadFactory");
        executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    synchronized boolean submit(Runnable task) {
        try {
            executor.execute(Objects.requireNonNull(task, "task"));
            return true;
        } catch (RejectedExecutionException rejected) {
            return false;
        }
    }

    synchronized boolean replaceQueuedTask(Runnable task) {
        executor.getQueue().clear();
        return submit(task);
    }

    synchronized void replaceQueueWithShutdown(Runnable cleanup) {
        executor.getQueue().clear();
        if (submit(cleanup)) {
            return;
        }
        Thread emergency = threadFactory.newThread(cleanup);
        emergency.setName("LanFtpShutdown");
        emergency.start();
    }

    synchronized void shutdown() {
        executor.shutdown();
    }
}
