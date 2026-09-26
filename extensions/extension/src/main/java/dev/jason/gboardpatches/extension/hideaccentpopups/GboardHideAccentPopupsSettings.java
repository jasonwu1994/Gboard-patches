package dev.jason.gboardpatches.extension.hideaccentpopups;

import android.content.Context;
import android.content.SharedPreferences;

public final class GboardHideAccentPopupsSettings {
    public static final String PREF_FILE = "gboard_hide_accent_popups";
    public static final String PREF_KEY_ENABLED = "pref_hide_accent_popups_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private GboardHideAccentPopupsSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        if (lookupContext == null) {
            return null;
        }
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit()
                    .putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED)
                    .apply();
        }
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        try {
            return preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        } catch (ClassCastException ignored) {
            return DEFAULT_ENABLED;
        }
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit()
                    .putBoolean(PREF_KEY_ENABLED, enabled)
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
