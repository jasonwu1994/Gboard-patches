package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.ftpserver.impl.FtpIoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Two-lane transfer executor whose active data socket can be cancelled by ABOR. */
final class LanFtpTransferCoordinator implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(LanFtpTransferCoordinator.class);
    private static final long PROGRESS_PUBLISH_NANOS = 500_000_000L;
    private final Map<UUID, ActiveTransfer> active = new ConcurrentHashMap<>();
    private final LanFtpActivityObserver observer;
    private final ThreadPoolExecutor executor;
    private volatile boolean closed;

    LanFtpTransferCoordinator(LanFtpActivityObserver observer, int maxTransfers) {
        this.observer = observer == null ? LanFtpActivityObserver.NONE : observer;
        int lanes = Math.max(1, maxTransfers);
        executor = new ThreadPoolExecutor(
                lanes,
                lanes,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "LanFtpTransfer");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    boolean submit(FtpIoSession session, String command, String path, String direction,
            long totalBytes, Runnable transfer) {
        ActiveTransfer state = new ActiveTransfer(session);
        if (active.putIfAbsent(session.getSessionId(), state) != null) {
            return false;
        }
        markTransfer(session, command, path, direction, totalBytes);
        notifyTransferCount();
        try {
            executor.execute(() -> {
                try {
                    transfer.run();
                } catch (Throwable throwable) {
                    LOG.warn("Contained unexpected LAN FTP transfer task failure", throwable);
                    try {
                        LanFtpDataCommandSupport.reply(
                                session, 451, "Transfer task failed safely.");
                    } catch (Throwable replyFailure) {
                        LOG.debug("Could not publish contained transfer failure", replyFailure);
                    }
                } finally {
                    try {
                        markIdle(session);
                    } finally {
                        active.remove(session.getSessionId(), state);
                        state.completed.countDown();
                        notifyTransferCount();
                        notifySessionActivity();
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            try {
                markIdle(session);
            } finally {
                active.remove(session.getSessionId(), state);
                state.completed.countDown();
                notifyTransferCount();
            }
            return false;
        }
    }

    boolean cancelAndAwait(FtpIoSession session, long timeoutMillis) {
        return cancelAndAwait(session.getSessionId(), timeoutMillis);
    }

    boolean cancelAndAwait(UUID sessionId, long timeoutMillis) {
        ActiveTransfer state = active.get(sessionId);
        if (state == null) {
            return true;
        }
        state.cancelRequested = true;
        state.cancelSignal.countDown();
        closeDataConnection(state.session);
        try {
            return state.completed.await(
                    Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    boolean isCancellationRequested(FtpIoSession session) {
        ActiveTransfer state = active.get(session.getSessionId());
        return state != null && state.cancelRequested;
    }

    boolean awaitCancellationRequest(FtpIoSession session, long timeoutMillis) {
        ActiveTransfer state = active.get(session.getSessionId());
        if (state == null || state.cancelRequested) {
            return state != null && state.cancelRequested;
        }
        try {
            state.cancelSignal.await(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return state.cancelRequested;
    }

    @Override public void close() {
        quiesce();
        try {
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        notifyTransferCount();
    }

    void quiesce() {
        closed = true;
        for (ActiveTransfer state : active.values()) {
            state.cancelRequested = true;
            state.cancelSignal.countDown();
            closeDataConnection(state.session);
        }
        executor.shutdownNow();
    }

    private void markTransfer(FtpIoSession session, String command, String path,
            String direction, long totalBytes) {
        try {
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_COMMAND,
                    command == null ? "" : command);
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_PATH, path == null ? "" : path);
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_DIRECTION,
                    direction == null ? "" : direction);
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES, 0L);
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_TOTAL_BYTES,
                    Math.max(-1L, totalBytes));
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES_PER_SECOND, 0L);
            session.setAttribute(
                    LanFtpSessionInfo.ATTRIBUTE_SPEED_SAMPLE_NANOS, System.nanoTime());
            session.setAttribute(LanFtpTransferProgress.ATTRIBUTE_CALLBACK,
                    new ProgressPublisher(session));
        } catch (Throwable throwable) {
            LOG.debug("Could not publish initial FTP transfer state", throwable);
        }
    }

    private static void markIdle(FtpIoSession session) {
        try {
            session.removeAttribute(LanFtpTransferProgress.ATTRIBUTE_CALLBACK);
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_COMMAND, "IDLE");
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_DIRECTION, "idle");
            session.removeAttribute(LanFtpSessionInfo.ATTRIBUTE_TOTAL_BYTES);
            session.removeAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES_PER_SECOND);
            session.removeAttribute(LanFtpSessionInfo.ATTRIBUTE_SPEED_SAMPLE_NANOS);
        } catch (Throwable throwable) {
            LOG.debug("Could not publish idle FTP transfer state", throwable);
        }
    }

    private void notifyTransferCount() {
        if (closed) {
            return;
        }
        try {
            observer.onTransferCountChanged(active.size());
        } catch (Throwable ignored) {
            // Management observers cannot break a data task.
        }
    }

    private void notifySessionActivity() {
        if (closed) {
            return;
        }
        try {
            observer.onSessionActivityChanged();
        } catch (Throwable ignored) {
            // Management observers cannot break a data task.
        }
    }

    private static void closeDataConnection(FtpIoSession session) {
        try {
            session.getDataConnection().closeDataConnection();
        } catch (Throwable ignored) {
            // The transfer worker also closes its descriptor and stream in finally paths.
        }
    }

    static boolean shouldPublishProgress(long lastProgressNanos, long nowNanos) {
        return nowNanos - lastProgressNanos >= PROGRESS_PUBLISH_NANOS;
    }

    private static final class ActiveTransfer {
        private final FtpIoSession session;
        private final CountDownLatch cancelSignal = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private volatile boolean cancelRequested;

        private ActiveTransfer(FtpIoSession session) {
            this.session = session;
        }
    }

    private final class ProgressPublisher implements java.util.function.LongConsumer {
        private final FtpIoSession session;
        private final long startedNanos = System.nanoTime();
        private final LanFtpTransferSpeedSampler speedSampler =
                new LanFtpTransferSpeedSampler(startedNanos);
        private long lastProgressNanos = startedNanos;

        private ProgressPublisher(FtpIoSession session) {
            this.session = session;
        }

        @Override public void accept(long bytes) {
            try {
                long now = System.nanoTime();
                if (!shouldPublishProgress(lastProgressNanos, now)) {
                    return;
                }
                long bytesPerSecond = speedSampler.sample(bytes, now);
                if (bytesPerSecond != LanFtpTransferSpeedSampler.NO_SAMPLE) {
                    session.setAttribute(
                            LanFtpSessionInfo.ATTRIBUTE_BYTES_PER_SECOND, bytesPerSecond);
                    session.setAttribute(
                            LanFtpSessionInfo.ATTRIBUTE_SPEED_SAMPLE_NANOS, now);
                }
                lastProgressNanos = now;
                notifySessionActivity();
            } catch (Throwable throwable) {
                LOG.debug("Could not publish FTP transfer speed", throwable);
            }
        }
    }
}
