package dev.jason.gboardpatches.extension.swipedismiss;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardSwipeDismissSettings {
    public static final String PREF_KEY_ENABLED = "pref_swipe_down_dismiss_keyboard_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private GboardSwipeDismissSettings() {
    }

    public static boolean readEnabled(Context context) {
        return GboardPatchesSettings.readRuntimeSetting(context,
                GboardSwipeDismissSettings::readEnabled, DEFAULT_ENABLED);
    }

    public static Boolean readEnabledOrNull(SharedPreferences preferences) {
        if (preferences == null) {
            return null;
        }
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
        if (raw == null && !preferences.contains(PREF_KEY_ENABLED)) {
            return DEFAULT_ENABLED;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(value)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        Boolean enabled = readEnabledOrNull(preferences);
        return enabled != null ? enabled.booleanValue() : DEFAULT_ENABLED;
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences != null && !preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED).commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        if (preferences == null) return false;
        try {
            return preferences.edit().putBoolean(PREF_KEY_ENABLED, enabled).commit();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
