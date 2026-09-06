package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpLifecycleExecutorTest {
    @Test
    public void saturationRejectsThirdApplyAndShutdownReplacesQueuedApply() throws Exception {
        LanFtpLifecycleExecutor executor = new LanFtpLifecycleExecutor(runnable -> {
            Thread thread = new Thread(runnable, "LanFtpLifecycleTest");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queuedApplyRan = new CountDownLatch(1);
        CountDownLatch shutdownRan = new CountDownLatch(1);
        try {
            Assert.assertTrue(executor.submit(() -> {
                running.countDown();
                await(release);
            }));
            Assert.assertTrue(running.await(1, TimeUnit.SECONDS));
            Assert.assertTrue(executor.submit(queuedApplyRan::countDown));
            Assert.assertFalse(executor.submit(() -> { }));

            executor.replaceQueueWithShutdown(shutdownRan::countDown);
            release.countDown();
            Assert.assertTrue(shutdownRan.await(1, TimeUnit.SECONDS));
            Assert.assertEquals(1L, queuedApplyRan.getCount());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    public void shutdownStillRunsAfterExecutorHasRejectedWork() throws Exception {
        LanFtpLifecycleExecutor executor = new LanFtpLifecycleExecutor(Thread::new);
        executor.shutdown();
        CountDownLatch cleanup = new CountDownLatch(1);
        executor.replaceQueueWithShutdown(cleanup::countDown);
        Assert.assertTrue(cleanup.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void latestApplyReplacesQueuedWorkDuringRapidToggle() throws Exception {
        LanFtpLifecycleExecutor executor = new LanFtpLifecycleExecutor(Thread::new);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch staleApply = new CountDownLatch(1);
        CountDownLatch latestApply = new CountDownLatch(1);
        try {
            Assert.assertTrue(executor.submit(() -> {
                running.countDown();
                await(release);
            }));
            Assert.assertTrue(running.await(1, TimeUnit.SECONDS));
            Assert.assertTrue(executor.submit(staleApply::countDown));

            Assert.assertTrue(executor.replaceQueuedTask(latestApply::countDown));
            release.countDown();

            Assert.assertTrue(latestApply.await(1, TimeUnit.SECONDS));
            Assert.assertEquals(1L, staleApply.getCount());
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
