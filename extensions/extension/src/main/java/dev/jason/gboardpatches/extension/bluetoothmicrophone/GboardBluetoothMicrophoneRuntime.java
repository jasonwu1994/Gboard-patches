package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardBluetoothMicrophoneRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-bluetooth-microphone] ";
    private static final int MAX_LOG_COUNT = 20;
    private static final ConcurrentHashMap<Class<?>, Field> FLAG_NAME_FIELDS =
            new ConcurrentHashMap<Class<?>, Field>();
    private static final AtomicInteger OVERRIDE_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger(0);

    private static volatile Context applicationContext;

    private GboardBluetoothMicrophoneRuntime() {
    }

    public static Object applyOverriddenFlagValue(Object receiver, Object stockResult) {
        try {
            String flagName = readFlagName(receiver);
            if (!isOverrideCandidate(flagName, stockResult)) {
                return stockResult;
            }
            Context context = resolveContext();
            if (context == null) {
                return stockResult;
            }
            return applyOverriddenFlagValue(
                    flagName,
                    stockResult,
                    GboardPatchesSettings.preferences(context));
        } catch (Throwable throwable) {
            logLimited(FAILURE_LOG_COUNT, "failed to apply flag override", throwable);
            return stockResult;
        }
    }

    static Object applyOverriddenFlagValue(
            Object receiver,
            Object stockResult,
            SharedPreferences preferences) {
        try {
            String flagName = readFlagName(receiver);
            return applyOverriddenFlagValue(flagName, stockResult, preferences);
        } catch (Throwable throwable) {
            logLimited(FAILURE_LOG_COUNT, "failed to evaluate flag override", throwable);
            return stockResult;
        }
    }

    private static Object applyOverriddenFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        if (!isOverrideCandidate(flagName, stockResult)) {
            return stockResult;
        }
        boolean enabled = GboardBluetoothMicrophoneSettings.readEnabled(preferences);
        Object overridden = GboardBluetoothMicrophonePolicy.maybeForce(
                flagName,
                stockResult,
                enabled);
        if (overridden != stockResult) {
            logLimited(OVERRIDE_LOG_COUNT, "forced " + flagName + "=true", null);
        }
        return overridden;
    }

    private static boolean isOverrideCandidate(String flagName, Object stockResult) {
        return GboardBluetoothMicrophonePolicy.BLUETOOTH_MICROPHONE_FLAG.equals(flagName)
                && Boolean.FALSE.equals(stockResult);
    }

    static String readFlagName(Object receiver) throws Throwable {
        if (receiver == null) {
            return null;
        }
        Class<?> receiverClass = receiver.getClass();
        Field field = FLAG_NAME_FIELDS.get(receiverClass);
        if (field == null) {
            Field resolved = receiverClass.getDeclaredField("a");
            if (resolved.getType() != String.class) {
                return null;
            }
            resolved.setAccessible(true);
            Field existing = FLAG_NAME_FIELDS.putIfAbsent(receiverClass, resolved);
            field = existing == null ? resolved : existing;
        }
        return (String) field.get(receiver);
    }

    static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context reflected = reflectedApplicationContext(
                "android.app.ActivityThread",
                "currentApplication");
        if (reflected == null) {
            reflected = reflectedApplicationContext(
                    "android.app.AppGlobals",
                    "getInitialApplication");
        }
        if (reflected != null) {
            applicationContext = reflected;
        }
        return reflected;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Object application = Class.forName(className).getMethod(methodName).invoke(null);
            if (!(application instanceof Context)) {
                return null;
            }
            Context context = (Context) application;
            Context resolved = context.getApplicationContext();
            return resolved == null ? context : resolved;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logLimited(
            AtomicInteger counter,
            String message,
            Throwable throwable) {
        if (counter.incrementAndGet() > MAX_LOG_COUNT) {
            return;
        }
        try {
            if (throwable == null) {
                Log.w(TAG, LOG_PREFIX + message);
            } else {
                Log.w(TAG, LOG_PREFIX + message, throwable);
            }
        } catch (Throwable ignored) {
            // Host-side unit tests do not provide android.util.Log.
        }
    }
}
