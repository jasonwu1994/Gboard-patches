package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardLatinGlobeKeyIgnoreIntervalRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-latin-globe-18.5.4] ";
    private static final String TARGET_CLASS_NAME = "yrt";
    private static final String TARGET_FIELD_NAME = "h";
    private static final int MAX_FAILURE_LOGS = 3;
    private static final Map<ClassLoader, WeakReference<Handles>> HANDLES_BY_LOADER =
            new WeakHashMap<ClassLoader, WeakReference<Handles>>();
    private static final AtomicInteger FAILURE_LOGS = new AtomicInteger();
    private static final SettingsSnapshot DISABLED_SETTINGS =
            new SettingsSnapshot(
                    false,
                    GboardLatinGlobeKeyIgnoreIntervalSettings.DEFAULT_INTERVAL_MS);

    private static volatile Context applicationContext;

    private GboardLatinGlobeKeyIgnoreIntervalRuntime() {
    }

    public static Object applyOverride(Object runtimeParams) {
        return applyOverride(runtimeParams, resolveContext());
    }

    static Object applyOverride(Object runtimeParams, Context context) {
        if (runtimeParams == null || context == null) {
            return runtimeParams;
        }
        try {
            SettingsSnapshot settings = loadSettingsFailClosed(context);
            if (!settings.enabled) {
                return runtimeParams;
            }

            ClassLoader targetLoader = context.getClassLoader();
            if (targetLoader == null) {
                return runtimeParams;
            }
            Handles handles = handles(targetLoader);
            if (runtimeParams.getClass() != handles.targetClass) {
                return runtimeParams;
            }

            float configuredIntervalMs = (float) settings.intervalMs;
            float currentValue = handles.globeKeyIgnoreIntervalField.getFloat(runtimeParams);
            if (Float.compare(currentValue, configuredIntervalMs) == 0) {
                return runtimeParams;
            }
            handles.globeKeyIgnoreIntervalField.setFloat(runtimeParams, configuredIntervalMs);
            Log.i(TAG, LOG_PREFIX + "forced LatinIme.N() yrt.h globe key ignore interval to "
                    + settings.intervalMs + "ms");
            return runtimeParams;
        } catch (Throwable failure) {
            logFailure("failed to override Latin globe key ignore interval", failure);
            return runtimeParams;
        }
    }

    private static SettingsSnapshot loadSettingsFailClosed(Context context) {
        try {
            SharedPreferences preferences =
                    GboardLatinGlobeKeyIgnoreIntervalSettings.preferences(context);
            if (preferences == null) {
                return DISABLED_SETTINGS;
            }
            GboardLatinGlobeKeyIgnoreIntervalSettings.ensureDefaults(preferences);
            Map<String, ?> values = preferences.getAll();
            Object enabledValue = values.get(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_ENABLED);
            Object intervalValue = values.get(
                    GboardLatinGlobeKeyIgnoreIntervalSettings.PREF_KEY_INTERVAL_MS);
            if (!(enabledValue instanceof Boolean)) {
                logFailure("invalid enabled setting type; using disabled/500", null);
                return DISABLED_SETTINGS;
            }
            if (!((Boolean) enabledValue).booleanValue()) {
                return DISABLED_SETTINGS;
            }
            if (!(intervalValue instanceof Integer)) {
                logFailure("invalid interval setting type; using disabled/500", null);
                return DISABLED_SETTINGS;
            }
            int intervalMs = GboardLatinGlobeKeyIgnoreIntervalSettings.sanitizeIntervalMs(
                    ((Integer) intervalValue).intValue());
            return new SettingsSnapshot(true, intervalMs);
        } catch (Throwable failure) {
            logFailure("failed to read settings; using disabled/500", failure);
            return DISABLED_SETTINGS;
        }
    }

    private static Handles handles(ClassLoader targetLoader) throws Exception {
        synchronized (HANDLES_BY_LOADER) {
            WeakReference<Handles> reference = HANDLES_BY_LOADER.get(targetLoader);
            Handles cached = reference != null ? reference.get() : null;
            if (cached != null) {
                return cached;
            }
            Handles created = new Handles(targetLoader);
            HANDLES_BY_LOADER.put(targetLoader, new WeakReference<Handles>(created));
            return created;
        }
    }

    private static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }

        Throwable activityThreadFailure = null;
        try {
            Context reflected = reflectedApplicationContext(
                    "android.app.ActivityThread",
                    "currentApplication");
            if (reflected != null) {
                applicationContext = reflected;
                return reflected;
            }
        } catch (Throwable failure) {
            activityThreadFailure = failure;
        }

        try {
            Context reflected = reflectedApplicationContext(
                    "android.app.AppGlobals",
                    "getInitialApplication");
            if (reflected != null) {
                applicationContext = reflected;
                return reflected;
            }
        } catch (Throwable failure) {
            if (activityThreadFailure != null) {
                failure.addSuppressed(activityThreadFailure);
            }
            logFailure("failed to resolve target application context", failure);
            return null;
        }

        if (activityThreadFailure != null) {
            logFailure("failed to resolve target application context", activityThreadFailure);
        }
        return null;
    }

    private static Context reflectedApplicationContext(String className, String methodName)
            throws Exception {
        Class<?> owner = Class.forName(className);
        Method method = owner.getMethod(methodName);
        Object application = method.invoke(null);
        if (!(application instanceof Context)) {
            return null;
        }
        Context context = (Context) application;
        Context resolved = context.getApplicationContext();
        return resolved != null ? resolved : context;
    }

    private static void logFailure(String message, Throwable failure) {
        int count = FAILURE_LOGS.incrementAndGet();
        if (count <= MAX_FAILURE_LOGS) {
            if (failure != null) {
                Log.w(TAG, LOG_PREFIX + message, failure);
            } else {
                Log.w(TAG, LOG_PREFIX + message);
            }
        } else if (count == MAX_FAILURE_LOGS + 1) {
            Log.w(TAG, LOG_PREFIX + "further runtime failures suppressed");
        }
    }

    private static final class Handles {
        final Class<?> targetClass;
        final Field globeKeyIgnoreIntervalField;

        Handles(ClassLoader targetLoader) throws Exception {
            targetClass = Class.forName(TARGET_CLASS_NAME, false, targetLoader);
            int classModifiers = targetClass.getModifiers();
            if (classModifiers != (Modifier.PUBLIC | Modifier.FINAL)) {
                throw new IllegalStateException(
                        "Expected exact public final yrt, modifiers=" + classModifiers);
            }

            globeKeyIgnoreIntervalField = targetClass.getDeclaredField(TARGET_FIELD_NAME);
            int fieldModifiers = globeKeyIgnoreIntervalField.getModifiers();
            if (globeKeyIgnoreIntervalField.getDeclaringClass() != targetClass
                    || globeKeyIgnoreIntervalField.getType() != Float.TYPE
                    || fieldModifiers != Modifier.PUBLIC) {
                throw new IllegalStateException(
                        "Expected exact declared public primitive-float yrt.h, modifiers="
                                + fieldModifiers
                                + ", type="
                                + globeKeyIgnoreIntervalField.getType().getName());
            }
        }
    }

    private static final class SettingsSnapshot {
        final boolean enabled;
        final int intervalMs;

        SettingsSnapshot(boolean enabled, int intervalMs) {
            this.enabled = enabled;
            this.intervalMs = intervalMs;
        }
    }
}
