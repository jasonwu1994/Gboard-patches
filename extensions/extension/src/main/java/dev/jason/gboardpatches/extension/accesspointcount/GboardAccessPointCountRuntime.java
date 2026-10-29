package dev.jason.gboardpatches.extension.accesspointcount;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardAccessPointCountRuntime {
    private GboardAccessPointCountRuntime() {
    }

    public static Object applyFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardAccessPointCountPolicy.isTargetFlagName(flagName)
                    || !(stockResult instanceof Long)) {
                return stockResult;
            }
            return applyFlagValue(
                    flagName,
                    stockResult,
                    GboardFlagRuntimeContext.preferencesOrNull());
        } catch (Throwable ignored) {
            return stockResult;
        }
    }

    static Object applyFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        try {
            if (!GboardAccessPointCountPolicy.isTargetFlagName(flagName)
                    || !(stockResult instanceof Long)) {
                return stockResult;
            }
            return GboardAccessPointCountPolicy.maybeForce(
                    flagName,
                    stockResult,
                    GboardAccessPointCountSettings.readRuntimeOrNull(preferences));
        } catch (Throwable ignored) {
            return stockResult;
        }
    }
}
