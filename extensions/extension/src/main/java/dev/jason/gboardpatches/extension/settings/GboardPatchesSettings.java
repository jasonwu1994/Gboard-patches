package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;

import java.util.function.Function;

public final class GboardPatchesSettings {
    public static final String PREF_FILE = "gboard_patches_settings";
    public static final String PREF_KEY_SETTINGS_UI_LANGUAGE =
            "pref_settings_ui_language";
    public static final String DEFAULT_SETTINGS_UI_LANGUAGE =
            GboardSettingsLocaleManager.LANGUAGE_SYSTEM;

    private GboardPatchesSettings() {
    }

    /** Strict legacy/settings-UI access. Runtime features should use readRuntimeSetting(). */
    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        if (lookupContext == null) {
            throw new IllegalStateException("Context required for Gboard patch settings.");
        }
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static Boolean readKeypressHapticsEnabled(Context context) {
        try {
            SharedPreferences preferences = preferencesOrNull(context);
            return preferences == null ? null : dev.jason.gboardpatches.extension.keypresshaptics
                    .GboardKeypressHapticsSettings.readEnabled(preferences);
        } catch (Throwable unavailable) { return null; }
    }

    public static boolean writeKeypressHapticsEnabled(Context context, boolean enabled) {
        try {
            SharedPreferences preferences = preferencesOrNull(context);
            return preferences != null && preferences.edit().putBoolean(
                    dev.jason.gboardpatches.extension.keypresshaptics.GboardKeypressHapticsSettings
                            .PREF_KEY_ENABLED, enabled).commit();
        } catch (Throwable unavailable) { return false; }
    }

    public static void ensureDefaults(Context context) {
        if (context == null) {
            return;
        }
        ensureDefaults(preferencesOrNull(context));
    }

    /** Runtime storage lookup: unavailable CE storage must preserve stock behavior. */
    public static SharedPreferences preferencesOrNull(Context context) {
        if (!isUserUnlocked(context)) return null;
        try {
            return preferences(context);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    public static boolean isUserUnlocked(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return true;
        try {
            UserManager userManager = null;
            Object service = context.getSystemService(Context.USER_SERVICE);
            if (service instanceof UserManager manager) userManager = manager;
            if (userManager == null) userManager = context.getSystemService(UserManager.class);
            return userManager == null || userManager.isUserUnlocked();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    /** Use this for new runtime settings; neither lookup nor value reads escape the guard. */
    public static <T> T readRuntimeSetting(Context context,
            Function<SharedPreferences, T> reader, T fallback) {
        try {
            SharedPreferences preferences = preferencesOrNull(context);
            if (preferences == null) return fallback;
            T value = reader.apply(preferences);
            return value != null ? value : fallback;
        } catch (RuntimeException unavailable) {
            return fallback;
        }
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        if (!preferences.contains(PREF_KEY_SETTINGS_UI_LANGUAGE)) {
            preferences.edit()
                    .putString(
                            PREF_KEY_SETTINGS_UI_LANGUAGE,
                            DEFAULT_SETTINGS_UI_LANGUAGE)
                    .apply();
        }
    }
}
