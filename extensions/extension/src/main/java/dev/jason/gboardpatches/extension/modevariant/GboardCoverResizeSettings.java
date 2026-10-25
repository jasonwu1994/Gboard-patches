package dev.jason.gboardpatches.extension.modevariant;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Sole owner of the user opt-in flag. Never writes native Gboard layout data. */
public final class GboardCoverResizeSettings {
    public static final String KEY_ENABLED = "cover_resize_independent_profiles_enabled";

    private GboardCoverResizeSettings() {
    }

    /** Missing or malformed preferences fail closed to native Gboard routing. */
    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            return preferences.getBoolean(KEY_ENABLED, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Persist an explicit user action, returning false on failure. Never clear data. */
    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        try {
            return GboardPatchesSettings.preferences(context)
                    .edit().putBoolean(KEY_ENABLED, enabled).commit();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}