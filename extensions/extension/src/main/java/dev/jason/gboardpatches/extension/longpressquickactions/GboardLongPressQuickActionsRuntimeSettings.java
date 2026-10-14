package dev.jason.gboardpatches.extension.longpressquickactions;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardLongPressQuickActionsRuntimeSettings {
    private static final long CACHE_WINDOW_MS = 1_000L;
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX =
            "[gboard-long-press-quick-actions-settings-18.0.3] ";
    private static final int LOG_LIMIT = 6;
    private static final Object SETTINGS_LOCK = new Object();
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger();

    private static volatile Snapshot cachedSnapshot;
    private static volatile Snapshot snapshotOverrideForTest;
    private static volatile Context applicationContext;

    private GboardLongPressQuickActionsRuntimeSettings() {
    }

    public static boolean isEnabled() {
        try {
            return snapshot().enabled;
        } catch (Throwable failure) {
            logFailure("failed to resolve runtime settings", failure);
            return false;
        }
    }

    public static boolean isGlobeDragEnabled() {
        try {
            Snapshot current = snapshot();
            return current.enabled && current.globeDragEnabled;
        } catch (Throwable failure) {
            logFailure("failed to resolve globe-drag runtime settings", failure);
            return false;
        }
    }

    public static int position() {
        try {
            return snapshot().position;
        } catch (Throwable failure) {
            logFailure("failed to resolve editing-action position", failure);
            return GboardLongPressQuickActionsSettings.DEFAULT_POSITION;
        }
    }

    static Snapshot snapshot() {
        Snapshot override = snapshotOverrideForTest;
        if (override != null) {
            return override;
        }

        long now = SystemClock.elapsedRealtime();
        Snapshot cached = cachedSnapshot;
        if (isFresh(cached, now)) {
            return cached;
        }

        synchronized (SETTINGS_LOCK) {
            Snapshot current = cachedSnapshot;
            if (isFresh(current, now)) {
                return current;
            }
            Snapshot loaded = loadSnapshot(current, now);
            cachedSnapshot = loaded;
            return loaded;
        }
    }

    static Snapshot snapshotFromPreferences(SharedPreferences preferences,
            long loadedAtElapsedMs) {
        if (preferences == null) {
            return new Snapshot(loadedAtElapsedMs, false, false,
                    GboardLongPressQuickActionsSettings.DEFAULT_POSITION, "unavailable");
        }
        return new Snapshot(
                loadedAtElapsedMs,
                GboardLongPressQuickActionsSettings.readEnabled(preferences),
                GboardLongPressQuickActionsSettings.readGlobeDragEnabled(preferences),
                GboardLongPressQuickActionsSettings.readPosition(preferences),
                "local");
    }

    static void setEnabledOverrideForTest(boolean enabled) {
        setOverrideForTest(enabled, true);
    }

    static void setOverrideForTest(boolean enabled, boolean globeDragEnabled) {
        snapshotOverrideForTest = new Snapshot(0L, enabled, globeDragEnabled,
                GboardLongPressQuickActionsSettings.DEFAULT_POSITION, "test");
    }

    static void clearEnabledOverrideForTest() {
        snapshotOverrideForTest = null;
        cachedSnapshot = null;
        applicationContext = null;
        FAILURE_LOG_COUNT.set(0);
    }

    private static Snapshot loadSnapshot(Snapshot previous, long loadedAtElapsedMs) {
        Context context = resolveContext();
        if (context == null) {
            return previous != null
                    ? new Snapshot(loadedAtElapsedMs, previous.enabled,
                            previous.globeDragEnabled, previous.position, "last-known")
                    : snapshotFromPreferences(null, loadedAtElapsedMs);
        }
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            GboardLongPressQuickActionsSettings.ensureDefault(preferences);
            return snapshotFromPreferences(preferences, loadedAtElapsedMs);
        } catch (Throwable failure) {
            logFailure("failed to read local settings", failure);
            return previous != null
                    ? new Snapshot(loadedAtElapsedMs, previous.enabled,
                            previous.globeDragEnabled, previous.position, "last-known")
                    : snapshotFromPreferences(null, loadedAtElapsedMs);
        }
    }

    private static boolean isFresh(Snapshot snapshot, long now) {
        return snapshot != null && now - snapshot.loadedAtElapsedMs <= CACHE_WINDOW_MS;
    }

    private static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context resolved = reflectedApplicationContext(
                "android.app.ActivityThread", "currentApplication");
        if (resolved == null) {
            resolved = reflectedApplicationContext(
                    "android.app.AppGlobals", "getInitialApplication");
        }
        applicationContext = resolved;
        return resolved;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Class<?> owner = Class.forName(className);
            Method method = owner.getMethod(methodName);
            Object value = method.invoke(null);
            if (!(value instanceof Application)) {
                return null;
            }
            Context context = (Context) value;
            Context app = context.getApplicationContext();
            return app != null ? app : context;
        } catch (Throwable failure) {
            logFailure("failed to resolve application context via " + className, failure);
            return null;
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            int count = FAILURE_LOG_COUNT.incrementAndGet();
            if (count > LOG_LIMIT) {
                return;
            }
            Log.w(TAG, LOG_PREFIX + message, failure);
        } catch (Throwable ignored) {
            // Logging must never affect the keyboard path.
        }
    }

    static final class Snapshot {
        final long loadedAtElapsedMs;
        final boolean enabled;
        final boolean globeDragEnabled;
        final int position;
        final String source;

        Snapshot(long loadedAtElapsedMs, boolean enabled, boolean globeDragEnabled,
                int position, String source) {
            this.loadedAtElapsedMs = loadedAtElapsedMs;
            this.enabled = enabled;
            this.globeDragEnabled = globeDragEnabled;
            this.position = position;
            this.source = source == null ? "unknown" : source;
        }
    }
}
