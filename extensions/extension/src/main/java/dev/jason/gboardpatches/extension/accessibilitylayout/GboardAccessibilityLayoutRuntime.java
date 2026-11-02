package dev.jason.gboardpatches.extension.accessibilitylayout;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardAccessibilityLayoutRuntime {
    private GboardAccessibilityLayoutRuntime() {
    }

    public static Object applyOverriddenFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardAccessibilityLayoutPolicy.PK_SIMULATOR_FLAG.equals(flagName)
                    || !Boolean.FALSE.equals(stockResult)) {
                return stockResult;
            }
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return preferences == null
                    ? stockResult
                    : applyOverriddenFlagValue(flagName, stockResult, preferences);
        } catch (Throwable ignored) {
            return stockResult;
        }
    }

    static Object applyOverriddenFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        return GboardAccessibilityLayoutPolicy.maybeForce(
                flagName,
                stockResult,
                GboardAccessibilityLayoutSettings.readEnabled(preferences));
    }
}
