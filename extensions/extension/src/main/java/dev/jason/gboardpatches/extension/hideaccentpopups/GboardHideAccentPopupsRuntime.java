package dev.jason.gboardpatches.extension.hideaccentpopups;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardHideAccentPopupsRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-hide-accent-popups-18.0.3] ";
    private static final long SETTINGS_CACHE_WINDOW_MS = 1_000L;

    private static final Map<ClassLoader, WeakReference<GboardHideAccentPopups1803ReflectionHandles>>
            HANDLES_BY_CLASS_LOADER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> PATCHED_METADATA_BY_ORIGINAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<Object>> ORIGINAL_METADATA_BY_PATCHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> UNPATCHED_METADATA_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final AtomicInteger PATCH_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ERROR_LOG_COUNT = new AtomicInteger();

    private static volatile Context applicationContext;
    private static volatile boolean cachedEnabled = GboardHideAccentPopupsSettings.DEFAULT_ENABLED;
    private static volatile long cachedEnabledAtElapsedMs = Long.MIN_VALUE;

    private GboardHideAccentPopupsRuntime() {
    }

    public static Object patchIncomingSoftKeyMetadata(Object softKeyView, Object metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            boolean enabled = isEnabled(softKeyView);
            WeakReference<Object> originalReference = ORIGINAL_METADATA_BY_PATCHED.get(metadata);
            if (originalReference != null) {
                Object original = originalReference.get();
                return enabled || original == null ? metadata : original;
            }
            if (!enabled || Boolean.TRUE.equals(UNPATCHED_METADATA_MARKERS.get(metadata))) {
                return metadata;
            }
            synchronized (PATCHED_METADATA_BY_ORIGINAL) {
                Object cached = PATCHED_METADATA_BY_ORIGINAL.get(metadata);
                if (cached != null) {
                    return cached;
                }
                GboardHideAccentPopups1803ReflectionHandles handles =
                        reflectionHandles(metadata.getClass().getClassLoader());
                String pressText = handles.extractPressText(metadata);
                Object patched = GboardHideAccentPopupsPolicy.isLatinLetterKey(pressText)
                        ? handles.withoutAccentedLongPressEntries(metadata, pressText) : null;
                if (patched == null) {
                    UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
                    return metadata;
                }
                PATCHED_METADATA_BY_ORIGINAL.put(metadata, patched);
                ORIGINAL_METADATA_BY_PATCHED.put(patched, new WeakReference<>(metadata));
                logInfo("hid accented long-press entries for key '" + pressText + "'");
                return patched;
            }
        } catch (Throwable throwable) {
            UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
            logError("metadata patch failed", throwable);
            return metadata;
        }
    }

    private static boolean isEnabled(Object softKeyView) {
        long now = SystemClock.elapsedRealtime();
        long cachedAt = cachedEnabledAtElapsedMs;
        if (cachedAt != Long.MIN_VALUE && now - cachedAt < SETTINGS_CACHE_WINDOW_MS) {
            return cachedEnabled;
        }
        Context context = applicationContext;
        if (context == null && softKeyView instanceof View view) {
            Context viewContext = view.getContext();
            context = viewContext == null ? null : viewContext.getApplicationContext();
            applicationContext = context;
        }
        boolean enabled = GboardHideAccentPopupsSettings.DEFAULT_ENABLED;
        if (context != null) {
            SharedPreferences preferences = GboardHideAccentPopupsSettings.preferences(context);
            enabled = GboardHideAccentPopupsSettings.readEnabled(preferences);
        }
        cachedEnabled = enabled;
        cachedEnabledAtElapsedMs = now;
        return enabled;
    }

    private static GboardHideAccentPopups1803ReflectionHandles reflectionHandles(
            ClassLoader classLoader) throws Throwable {
        synchronized (HANDLES_BY_CLASS_LOADER) {
            WeakReference<GboardHideAccentPopups1803ReflectionHandles> reference =
                    HANDLES_BY_CLASS_LOADER.get(classLoader);
            GboardHideAccentPopups1803ReflectionHandles cached =
                    reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            GboardHideAccentPopups1803ReflectionHandles created =
                    new GboardHideAccentPopups1803ReflectionHandles(classLoader);
            HANDLES_BY_CLASS_LOADER.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    private static void logInfo(String message) {
        try {
            if (PATCH_LOG_COUNT.getAndIncrement() < 30) {
                Log.i(TAG, LOG_PREFIX + message);
            }
        } catch (Throwable ignored) {
            // Logging must not affect the keyboard path.
        }
    }

    private static void logError(String message, Throwable throwable) {
        try {
            if (ERROR_LOG_COUNT.getAndIncrement() < 8) {
                Log.w(TAG, LOG_PREFIX + message, throwable);
            }
        } catch (Throwable ignored) {
            // Logging must not affect the keyboard path.
        }
    }
}
