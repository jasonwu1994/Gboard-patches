package dev.jason.gboardpatches.extension.quickinsert;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardQuickInsertRuntime {
    private GboardQuickInsertRuntime() {
    }

    public static Object applyFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardQuickInsertPolicy.isTargetFlagName(flagName)
                    || !(stockResult instanceof Boolean)) {
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
            if (!GboardQuickInsertPolicy.isTargetFlagName(flagName)
                    || !(stockResult instanceof Boolean)) {
                return stockResult;
            }
            if (preferences == null) {
                return stockResult;
            }
            return GboardQuickInsertPolicy.maybeForce(
                    flagName,
                    stockResult,
                    GboardQuickInsertSettings.readEnabled(preferences));
        } catch (Throwable ignored) {
            return stockResult;
        }
    }
}
