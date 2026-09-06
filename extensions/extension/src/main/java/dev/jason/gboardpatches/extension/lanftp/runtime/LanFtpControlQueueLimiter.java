package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.filter.executor.IoEventQueueHandler;

/** Globally bounds pending MINA control-channel events and closes abusive sessions. */
final class LanFtpControlQueueLimiter implements IoEventQueueHandler {
    private final int regularCapacity;
    private final int totalCapacity;
    private final AtomicInteger pending = new AtomicInteger();

    LanFtpControlQueueLimiter(int regularCapacity, int reservedCloseEvents) {
        if (regularCapacity < 1 || reservedCloseEvents < 1) {
            throw new IllegalArgumentException("queue capacities must be positive");
        }
        this.regularCapacity = regularCapacity;
        totalCapacity = Math.addExact(regularCapacity, reservedCloseEvents);
    }

    @Override public boolean accept(Object source, IoEvent event) {
        int capacity = isSessionClosed(event) ? totalCapacity : regularCapacity;
        while (true) {
            int current = pending.get();
            if (current >= capacity) {
                closeSession(event);
                return false;
            }
            if (pending.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    @Override public void offered(Object source, IoEvent event) {
        // The reservation is acquired atomically in accept().
    }

    @Override public void polled(Object source, IoEvent event) {
        while (true) {
            int current = pending.get();
            if (current == 0 || pending.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    int pendingCount() {
        return pending.get();
    }

    private static boolean isSessionClosed(IoEvent event) {
        return event != null && event.getType() == IoEventType.SESSION_CLOSED;
    }

    private static void closeSession(IoEvent event) {
        if (event == null) {
            return;
        }
        try {
            event.getSession().closeNow();
        } catch (Throwable ignored) {
            // Dropping the over-capacity event remains fail-closed.
        }
    }
}
