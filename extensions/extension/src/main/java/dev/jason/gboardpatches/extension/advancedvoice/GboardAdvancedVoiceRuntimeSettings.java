package dev.jason.gboardpatches.extension.advancedvoice;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardAdvancedVoiceRuntimeSettings {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-advanced-voice-settings] ";
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger();

    private static volatile Snapshot cachedSnapshot;
    private static volatile Snapshot directBootSnapshot;
    private static volatile Boolean enabledOverrideForTest;
    private static volatile String backendOverrideForTest;
    private static volatile Boolean zhTwPunctuationEnabledOverrideForTest;
    private static volatile Context applicationContext;

    private GboardAdvancedVoiceRuntimeSettings() {
    }

    public static boolean isEnabled() {
        try {
            Boolean override = enabledOverrideForTest;
            return override != null ? override.booleanValue() : snapshot().enabled;
        } catch (Throwable ignored) {
            return GboardAdvancedVoiceSettings.DEFAULT_ENABLED;
        }
    }

    public static boolean isRamblerEnabled() {
        return false;
    }

    public static GboardVoiceInputMode effectiveMode() {
        Boolean enabledOverride = enabledOverrideForTest;
        if (enabledOverride != null) {
            boolean enabled = enabledOverride != null
                    ? enabledOverride.booleanValue()
                    : GboardAdvancedVoiceSettings.DEFAULT_ENABLED;
            return enabled ? GboardVoiceInputMode.ADVANCED : GboardVoiceInputMode.STANDARD;
        }
        return snapshot().effectiveMode;
    }

    public static boolean isZhTwPunctuationInterventionEnabled() {
        try {
            Boolean punctuationOverride = zhTwPunctuationEnabledOverrideForTest;
            boolean punctuationEnabled = punctuationOverride != null
                    ? punctuationOverride.booleanValue()
                    : snapshot().zhTwPunctuationEnabled;
            return isEnabled() && punctuationEnabled;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static Snapshot snapshot() {
        Snapshot cached = cachedSnapshot;
        if (cached != null) {
            return cached;
        }
        synchronized (SNAPSHOT_LOCK) {
            Snapshot current = cachedSnapshot;
            if (current != null) {
                return current;
            }
            Context context = resolveContext();
            if (context == null) {
                return defaultSnapshot("transient-defaults");
            }
            Snapshot authoritative = loadAuthoritativeSnapshot(context);
            if (authoritative != null) {
                cachedSnapshot = authoritative;
                directBootSnapshot = null;
                return authoritative;
            }
            Snapshot directBoot = loadDirectBootSnapshot(context);
            return directBoot != null ? directBoot : defaultSnapshot("direct-boot-defaults");
        }
    }

    static Snapshot snapshotFromPreferences(SharedPreferences preferences) {
        if (preferences == null) {
            return defaultSnapshot("defaults");
        }
        try {
            return new Snapshot(
                    GboardAdvancedVoiceSettings.readEnabled(preferences),
                    GboardAdvancedVoiceSettings.readBackend(preferences),
                    GboardAdvancedVoiceSettings.readZhTwPunctuationEnabled(preferences),
                    "local");
        } catch (Throwable failure) {
            logFailure("failed to parse settings", failure);
            return defaultSnapshot("local-error");
        }
    }

    public static void setEnabledOverrideForTest(boolean enabled) {
        enabledOverrideForTest = Boolean.valueOf(enabled);
    }

    public static void setZhTwPunctuationEnabledOverrideForTest(boolean enabled) {
        zhTwPunctuationEnabledOverrideForTest = Boolean.valueOf(enabled);
    }

    public static void setBackendOverrideForTest(String backend) {
        backendOverrideForTest = backend;
    }

    public static void clearEnabledOverrideForTest() {
        enabledOverrideForTest = null;
        backendOverrideForTest = null;
        zhTwPunctuationEnabledOverrideForTest = null;
        cachedSnapshot = null;
        directBootSnapshot = null;
        applicationContext = null;
        FAILURE_LOG_COUNT.set(0);
    }

    static void invalidateCachedSnapshot() {
        cachedSnapshot = null;
        directBootSnapshot = null;
    }

    public static Context resolveApplicationContext() {
        return resolveContext();
    }

    static void seedApplicationContext(Context context) {
        if (context == null) {
            return;
        }
        Context resolved = context;
        try {
            Context app = context.getApplicationContext();
            if (app != null) {
                resolved = app;
            }
        } catch (Throwable ignored) {
            // The constructor context is still a safer fallback than hidden-API reflection.
        }
        if (applicationContext == resolved) {
            return;
        }
        synchronized (SNAPSHOT_LOCK) {
            if (applicationContext != resolved) {
                applicationContext = resolved;
                cachedSnapshot = null;
                directBootSnapshot = null;
            }
        }
    }

    private static Snapshot loadAuthoritativeSnapshot(Context context) {
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
            if (preferences == null) {
                return null;
            }
            Snapshot loaded = snapshotFromPreferences(preferences);
            GboardAdvancedVoiceSettings.mirrorToDeviceProtectedStorage(
                    context, loaded.enabled, loaded.zhTwPunctuationEnabled);
            return loaded;
        } catch (Throwable failure) {
            logFailure("failed to read local settings", failure);
            return null;
        }
    }

    private static Snapshot loadDirectBootSnapshot(Context context) {
        Snapshot cachedDirectBoot = directBootSnapshot;
        if (cachedDirectBoot != null) {
            return cachedDirectBoot;
        }
        try {
            SharedPreferences preferences =
                    GboardPatchesSettings.deviceProtectedPreferencesOrNull(context);
            if (preferences == null) {
                return null;
            }
            Snapshot loaded = new Snapshot(
                    GboardAdvancedVoiceSettings.readEnabled(preferences),
                    GboardAdvancedVoiceSettings.DEFAULT_BACKEND,
                    GboardAdvancedVoiceSettings.readZhTwPunctuationEnabled(preferences),
                    "direct-boot-de");
            directBootSnapshot = loaded;
            return loaded;
        } catch (Throwable failure) {
            logFailure("failed to read Direct Boot settings", failure);
            return null;
        }
    }

    private static Snapshot defaultSnapshot(String source) {
        return new Snapshot(
                GboardAdvancedVoiceSettings.DEFAULT_ENABLED,
                GboardAdvancedVoiceSettings.DEFAULT_BACKEND,
                GboardAdvancedVoiceSettings.DEFAULT_ZH_TW_PUNCTUATION_ENABLED,
                source);
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
        if (resolved != null) {
            applicationContext = resolved;
        }
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
        if (FAILURE_LOG_COUNT.getAndIncrement() >= 6) {
            return;
        }
        try {
            Log.w(TAG, LOG_PREFIX + message, failure);
        } catch (Throwable ignored) {
            // Logging must not affect Gboard.
        }
    }

    static final class Snapshot {
        final boolean enabled;
        final String backend;
        final GboardVoiceInputMode effectiveMode;
        final boolean zhTwPunctuationEnabled;
        final String source;

        Snapshot(boolean enabled, String backend, boolean zhTwPunctuationEnabled,
                String source) {
            this.enabled = enabled;
            this.backend = backend == null
                    ? GboardAdvancedVoiceSettings.DEFAULT_BACKEND : backend;
            this.effectiveMode = enabled
                    ? GboardVoiceInputMode.ADVANCED
                    : GboardVoiceInputMode.STANDARD;
            this.zhTwPunctuationEnabled = zhTwPunctuationEnabled;
            this.source = source == null ? "unknown" : source;
        }

        boolean isZhTwPunctuationInterventionEnabled() {
            return enabled && zhTwPunctuationEnabled;
        }
    }
}
