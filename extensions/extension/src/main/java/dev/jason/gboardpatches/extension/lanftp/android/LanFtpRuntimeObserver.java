package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.Objects;

/** Visible-page-only observer for FTP runtime events; it owns no polling timer. */
public final class LanFtpRuntimeObserver implements AutoCloseable {
    static final long PROGRESS_REFRESH_MIN_INTERVAL_MS = 1_000L;

    private final ContentResolver resolver;
    private final Uri statusUri;
    private final Handler handler;
    private final Runnable refresh;
    private final Runnable delayedProgressRefresh = this::runDelayedProgressRefresh;
    private final ContentObserver observer;
    private boolean registered;
    private boolean progressRefreshPosted;
    private long lastRefreshElapsedMs = Long.MIN_VALUE;

    public LanFtpRuntimeObserver(Context context, Runnable refresh) {
        Context safeContext = Objects.requireNonNull(context, "context");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
        resolver = safeContext.getContentResolver();
        statusUri = LanFtpStatusProvider.contentUri(safeContext);
        handler = new Handler(Looper.getMainLooper());
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                handleChange(uri);
            }
        };
    }

    public void start() {
        if (registered) {
            return;
        }
        resolver.registerContentObserver(statusUri, true, observer);
        registered = true;
    }

    @Override
    public void close() {
        cancelProgressRefresh();
        if (!registered) {
            return;
        }
        registered = false;
        try {
            resolver.unregisterContentObserver(observer);
        } catch (Throwable ignored) {
            // Lifecycle cleanup remains idempotent if an OEM already removed the observer.
        }
    }

    private void handleChange(Uri uri) {
        if (!registered) {
            return;
        }
        if (!LanFtpStatusProvider.isProgressUri(uri)) {
            cancelProgressRefresh();
            dispatchRefresh();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long delay = progressDelayMillis(lastRefreshElapsedMs, now);
        if (delay == 0L) {
            cancelProgressRefresh();
            dispatchRefresh();
        } else if (!progressRefreshPosted) {
            progressRefreshPosted = handler.postDelayed(delayedProgressRefresh, delay);
        }
    }

    private void runDelayedProgressRefresh() {
        progressRefreshPosted = false;
        dispatchRefresh();
    }

    private void cancelProgressRefresh() {
        handler.removeCallbacks(delayedProgressRefresh);
        progressRefreshPosted = false;
    }

    private void dispatchRefresh() {
        if (!registered) {
            return;
        }
        lastRefreshElapsedMs = SystemClock.elapsedRealtime();
        try {
            refresh.run();
        } catch (Throwable ignored) {
            // A failed UI refresh must not unregister future runtime events.
        }
    }

    static long progressDelayMillis(long lastRefreshElapsedMs, long nowElapsedMs) {
        if (lastRefreshElapsedMs == Long.MIN_VALUE) {
            return 0L;
        }
        long elapsed = Math.max(0L, nowElapsedMs - lastRefreshElapsedMs);
        return Math.max(0L, PROGRESS_REFRESH_MIN_INTERVAL_MS - elapsed);
    }
}
