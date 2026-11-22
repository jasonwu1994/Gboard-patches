package dev.jason.gboardpatches.extension.shiftdelete;

import java.util.function.Supplier;

/**
 * The toggle is restart-required, so the first definitive read is kept for the process.
 * A null read means locked or unavailable storage: report off and retry sparingly, so
 * the per-key input path never reloads remote preferences on every event.
 */
public final class ShiftDeleteEnabledCache {
    static final long RETRY_MS = 1_000L;
    private Boolean value;
    private boolean attempted;
    private long lastAttemptMs;

    public synchronized boolean get(long nowMs, Supplier<Boolean> reader) {
        if (value != null) return value;
        if (attempted && nowMs - lastAttemptMs < RETRY_MS) return false;
        attempted = true;
        lastAttemptMs = nowMs;
        value = reader.get();
        return Boolean.TRUE.equals(value);
    }

    public synchronized Boolean cached() { return value; }
}
