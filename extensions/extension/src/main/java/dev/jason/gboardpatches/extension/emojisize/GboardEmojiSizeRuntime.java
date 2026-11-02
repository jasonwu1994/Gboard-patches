package dev.jason.gboardpatches.extension.emojisize;

import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardEmojiSizeRuntime {
    private GboardEmojiSizeRuntime() {
    }

    public static Object applyOverriddenFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardEmojiSizePolicy.EMOJI_SCALE_SUPPORTED_FLAG.equals(flagName)
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
        return GboardEmojiSizePolicy.maybeForce(
                flagName,
                stockResult,
                GboardEmojiSizeSettings.readEnabled(preferences));
    }
}
