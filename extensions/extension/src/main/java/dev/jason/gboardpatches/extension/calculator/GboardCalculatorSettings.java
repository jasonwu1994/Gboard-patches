package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardCalculatorSettings {
    public static final String PREF_KEY_ENABLED = "pref_simple_calculator_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private GboardCalculatorSettings() {
    }

    public static boolean readEnabled(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        ensureDefault(preferences);
        return readEnabled(preferences);
    }

    static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return false;
        }
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
        if (raw instanceof Boolean value) {
            return value.booleanValue();
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return DEFAULT_ENABLED;
    }

    static void ensureDefault(SharedPreferences preferences) {
        if (preferences != null && !preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED).commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        return GboardPatchesSettings.preferences(context).edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .commit();
    }
}
