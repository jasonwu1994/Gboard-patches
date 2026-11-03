package dev.jason.gboardpatches.extension.clipboardcontentlimit;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
public final class GboardClipboardContentLimitRuntime {
    private static volatile Context applicationContext;

    private GboardClipboardContentLimitRuntime() {
    }

    public static Object applyOverriddenFlagValue(String flagName, Object stockResult) {
        if (!GboardClipboardContentLimitPolicy.TEXT_CLIP_ITEM_CHAR_LIMIT.equals(flagName)
                || !(stockResult instanceof Long)) {
            return stockResult;
        }
        Context context = resolveContext();
        if (context == null) {
            return stockResult;
        }
        return applyOverriddenFlagValue(
                flagName,
                stockResult,
                GboardClipboardContentLimitSettings.preferences(context));
    }

    static Object applyOverriddenFlagValue(String flagName, Object stockResult,
            SharedPreferences preferences) {
        return GboardClipboardContentLimitPolicy.maybeOverride(
                flagName,
                stockResult,
                GboardClipboardContentLimitSettings.readMaxCharacters(preferences));
    }

    private static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context reflected = reflectedApplicationContext(
                "android.app.ActivityThread", "currentApplication");
        if (reflected == null) {
            reflected = reflectedApplicationContext(
                    "android.app.AppGlobals", "getInitialApplication");
        }
        if (reflected != null) {
            applicationContext = reflected;
        }
        return reflected;
    }

    private static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Method method = Class.forName(className).getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object application = method.invoke(null);
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
}
