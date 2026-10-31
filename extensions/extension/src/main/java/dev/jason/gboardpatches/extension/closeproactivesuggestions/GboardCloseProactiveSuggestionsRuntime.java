package dev.jason.gboardpatches.extension.closeproactivesuggestions;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardCloseProactiveSuggestionsRuntime {
    private GboardCloseProactiveSuggestionsRuntime() {
    }

    public static Object applyFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardCloseProactiveSuggestionsPolicy.isTargetFlagName(flagName)
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
            if (!GboardCloseProactiveSuggestionsPolicy.isTargetFlagName(flagName)
                    || !(stockResult instanceof Boolean)) {
                return stockResult;
            }
            if (preferences == null) {
                return stockResult;
            }
            return GboardCloseProactiveSuggestionsPolicy.maybeForce(
                    flagName,
                    stockResult,
                    GboardCloseProactiveSuggestionsSettings.readEnabled(preferences));
        } catch (Throwable ignored) {
            return stockResult;
        }
    }
}
