package dev.jason.gboardpatches.extension.settingshomepage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardSettingsHomepageRuntime {
    private static final String TAG = "GboardPatches";
    private static final String POLICY_CLASS_NAME = "dto";
    private static final String POLICY_FIELD_NAME = "a";
    private static final int POLICY_STATE_AUTO = 1;
    private static final int POLICY_STATE_NEW = 2;
    private static final int POLICY_STATE_LEGACY = 3;
    private static final int LOG_LIMIT = 6;

    private static final WeakHashMap<ClassLoader, WeakReference<Field>>
            POLICY_FIELDS_BY_CLASS_LOADER = new WeakHashMap<>();
    private static final AtomicInteger SUCCESS_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger();

    private GboardSettingsHomepageRuntime() {
    }

    public static void applySettingsHomepagePolicy(Object receiver) {
        if (!(receiver instanceof Context)) {
            logFailure("Settings homepage receiver is not an Android Context", null);
            return;
        }

        Context context = (Context) receiver;
        Field policyField;
        try {
            ClassLoader classLoader = context.getClassLoader();
            if (classLoader == null) {
                throw new IllegalStateException("Settings homepage Context has no ClassLoader");
            }
            policyField = resolvePolicyField(classLoader);
        } catch (Throwable throwable) {
            logFailure("Failed to bind exact dto.a settings homepage policy field", throwable);
            return;
        }

        SharedPreferences preferences = null;
        try {
            preferences = GboardSettingsHomepageSettings.preferences(context);
            String mode = GboardSettingsHomepageSettings.readModeAndEnsureDefaults(preferences);
            int desiredState;
            if (GboardSettingsHomepageSettings.MODE_FORCE_NEW.equals(mode)) {
                desiredState = GboardSettingsHomepageSettings.resolveForceNewSettingsStyle(
                        context,
                        preferences)
                        ? POLICY_STATE_NEW
                        : POLICY_STATE_LEGACY;
            } else if (GboardSettingsHomepageSettings.MODE_FORCE_LEGACY.equals(mode)) {
                desiredState = POLICY_STATE_LEGACY;
            } else {
                desiredState = POLICY_STATE_AUTO;
            }

            boolean changed = writePolicyStateIfNeeded(policyField, desiredState);
            logSuccess(desiredState, changed);
        } catch (Throwable throwable) {
            GboardSettingsHomepageSettings.recoverForceNewPolicyFailure(
                    context,
                    preferences,
                    "Failed to apply settings homepage policy; falling back to legacy",
                    throwable);
            try {
                writePolicyStateIfNeeded(policyField, POLICY_STATE_LEGACY);
            } catch (Throwable writeFailure) {
                logFailure("Failed to write Legacy recovery policy state", writeFailure);
            }
            logFailure(
                    "Recovered settings homepage policy failure to Legacy when possible",
                    throwable);
        }
    }

    private static Field resolvePolicyField(ClassLoader classLoader) throws Throwable {
        synchronized (POLICY_FIELDS_BY_CLASS_LOADER) {
            WeakReference<Field> cachedReference =
                    POLICY_FIELDS_BY_CLASS_LOADER.get(classLoader);
            Field cached = cachedReference != null ? cachedReference.get() : null;
            if (cached != null) {
                validatePolicyField(cached.getDeclaringClass(), cached, classLoader);
                return cached;
            }
        }

        Class<?> policyClass = Class.forName(POLICY_CLASS_NAME, false, classLoader);
        Field policyField = policyClass.getDeclaredField(POLICY_FIELD_NAME);
        validatePolicyField(policyClass, policyField, classLoader);
        policyField.setAccessible(true);

        synchronized (POLICY_FIELDS_BY_CLASS_LOADER) {
            POLICY_FIELDS_BY_CLASS_LOADER.put(classLoader, new WeakReference<>(policyField));
        }
        return policyField;
    }

    private static void validatePolicyField(Class<?> policyClass, Field policyField,
            ClassLoader expectedClassLoader) {
        int expectedClassModifiers = Modifier.PUBLIC | Modifier.FINAL;
        if (!POLICY_CLASS_NAME.equals(policyClass.getName())
                || policyClass.getClassLoader() != expectedClassLoader
                || policyClass.getModifiers() != expectedClassModifiers) {
            throw new IllegalStateException(
                    "dto must be the exact public final class from the receiver ClassLoader");
        }

        int expectedFieldModifiers = Modifier.PRIVATE | Modifier.STATIC;
        if (policyField.getDeclaringClass() != policyClass
                || !POLICY_FIELD_NAME.equals(policyField.getName())
                || policyField.getType() != Integer.TYPE
                || policyField.getModifiers() != expectedFieldModifiers) {
            throw new IllegalStateException(
                    "dto.a must be the exact declared private static primitive-int field");
        }
    }

    private static boolean writePolicyStateIfNeeded(Field policyField, int desiredState)
            throws IllegalAccessException {
        if (desiredState != POLICY_STATE_AUTO
                && desiredState != POLICY_STATE_NEW
                && desiredState != POLICY_STATE_LEGACY) {
            throw new IllegalArgumentException("Unexpected settings homepage policy state");
        }
        if (policyField.getInt(null) == desiredState) {
            return false;
        }
        policyField.setInt(null, desiredState);
        return true;
    }

    private static void logSuccess(int state, boolean changed) {
        if (SUCCESS_LOG_COUNT.getAndIncrement() >= LOG_LIMIT) {
            return;
        }
        try {
            Log.i(
                    TAG,
                    "[gboard-settings-homepage-18.5.4] policy state=" + state
                            + " changed=" + changed);
        } catch (Throwable ignored) {
            // Logging must never affect the settings Activity entry contract.
        }
    }

    private static void logFailure(String message, Throwable throwable) {
        if (FAILURE_LOG_COUNT.getAndIncrement() >= LOG_LIMIT) {
            return;
        }
        String tagged = "[gboard-settings-homepage-18.5.4] " + message;
        try {
            if (throwable != null) {
                Log.w(TAG, tagged, throwable);
            } else {
                Log.w(TAG, tagged);
            }
        } catch (Throwable ignored) {
            // Logging must never affect the settings Activity entry contract.
        }
    }
}
