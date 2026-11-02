package dev.jason.gboardpatches.extension.flagsettings;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Method;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardFlagRuntimeContext {
    private static volatile Context applicationContext;

    private GboardFlagRuntimeContext() {
    }

    public static SharedPreferences preferencesOrNull() {
        Context context = resolveContext();
        return context == null ? null : GboardPatchesSettings.preferences(context);
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
