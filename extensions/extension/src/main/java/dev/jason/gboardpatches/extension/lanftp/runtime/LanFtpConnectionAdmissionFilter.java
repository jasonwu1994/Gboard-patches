package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ipfilter.SessionFilter;
import org.apache.mina.core.session.IoSession;

/** Bounds raw FTP control connections before they can enqueue executor events. */
final class LanFtpConnectionAdmissionFilter implements SessionFilter {
    private static final String ATTRIBUTE_ADMISSION =
            "lanftp.connection.admission";
    private final int capacity;
    private final AtomicInteger active = new AtomicInteger();

    LanFtpConnectionAdmissionFilter(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override public boolean accept(IoSession session) {
        if (session == null || !reserve()) {
            return false;
        }
        Admission admission = new Admission(this);
        try {
            session.setAttribute(ATTRIBUTE_ADMISSION, admission);
            return true;
        } catch (Throwable throwable) {
            admission.release();
            return false;
        }
    }

    int activeCount() {
        return active.get();
    }

    static void release(IoSession session) {
        if (session == null) {
            return;
        }
        Object value;
        try {
            value = session.removeAttribute(ATTRIBUTE_ADMISSION);
        } catch (Throwable ignored) {
            return;
        }
        release(value);
    }

    static void release(FtpSession session) {
        if (session == null) {
            return;
        }
        Object value;
        try {
            value = session.getAttribute(ATTRIBUTE_ADMISSION);
            session.removeAttribute(ATTRIBUTE_ADMISSION);
        } catch (Throwable ignored) {
            return;
        }
        release(value);
    }

    private boolean reserve() {
        while (true) {
            int current = active.get();
            if (current >= capacity) {
                return false;
            }
            if (active.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private static void release(Object value) {
        if (value instanceof Admission admission) {
            admission.release();
        }
    }

    private static final class Admission {
        private final LanFtpConnectionAdmissionFilter owner;
        private final AtomicBoolean released = new AtomicBoolean();

        private Admission(LanFtpConnectionAdmissionFilter owner) {
            this.owner = owner;
        }

        private void release() {
            if (released.compareAndSet(false, true)) {
                owner.active.decrementAndGet();
            }
        }
    }
}
