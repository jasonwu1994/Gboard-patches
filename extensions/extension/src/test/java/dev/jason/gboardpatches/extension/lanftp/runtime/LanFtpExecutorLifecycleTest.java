package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.apache.ftpserver.impl.DefaultFtpServerContext;
import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.IoSession;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpExecutorLifecycleTest {
    @Test
    public void shutdownCannotMissAWorkerFromAnInFlightSubmission() throws Exception {
        assertShutdownCannotMissAWorker(true);
    }

    @Test
    public void gracefulShutdownCannotMissAWorkerFromAnInFlightSubmission() throws Exception {
        assertShutdownCannotMissAWorker(false);
    }

    private void assertShutdownCannotMissAWorker(boolean immediate) throws Exception {
        ThreadPoolExecutor executor = new LanFtpBoundedServerContext(
                new DefaultFtpServerContext(), 4, 64, 4).getThreadPoolExecutor();
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopping = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        IoEvent event = new IoEvent(IoEventType.MESSAGE_RECEIVED, new DummySession(), null) {
            @Override public IoSession getSession() {
                accepted.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Submission gate timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return super.getSession();
            }

            @Override public void run() {
                // Only event admission and worker lifetime matter in this reproduction.
            }
        };
        Thread sender = new Thread(() -> {
            try { executor.execute(event); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "ftp-submit-test");
        Thread stopper = new Thread(() -> {
            stopping.countDown();
            try {
                if (immediate) executor.shutdownNow();
                else executor.shutdown();
            } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "ftp-stop-test");
        try {
            sender.start();
            Assert.assertTrue(accepted.await(5, TimeUnit.SECONDS));
            stopper.start();
            Assert.assertTrue(stopping.await(5, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            // Observe shutdown either taking effect or waiting for the admitted submission.
            while (!executor.isShutdown() && stopper.getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            Assert.assertTrue("Shutdown did not reach the admission boundary",
                    executor.isShutdown() || stopper.getState() == Thread.State.BLOCKED);
            release.countDown();
            sender.join(5_000);
            stopper.join(5_000);
            Assert.assertFalse("Submission did not finish", sender.isAlive());
            Assert.assertFalse("Shutdown did not finish", stopper.isAlive());
            Assert.assertNull(failure.get());
            Assert.assertTrue("An admitted worker missed its shutdown signal",
                    executor.awaitTermination(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            sender.join(5_000);
            stopper.join(5_000);
            // Retire a stranded baseline worker too, so a failing test cannot leak threads.
            executor.setCorePoolSize(1);
            executor.setCorePoolSize(0);
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
