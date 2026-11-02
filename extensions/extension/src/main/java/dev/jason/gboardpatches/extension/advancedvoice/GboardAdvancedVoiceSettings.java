package dev.jason.gboardpatches.extension.advancedvoice;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardAdvancedVoiceSettings {
    public static final String PREF_KEY_ENABLED =
            "pref_advanced_voice_typing_enabled";
    public static final String PREF_KEY_BACKEND =
            "pref_advanced_voice_backend";
    public static final String PREF_KEY_ZH_TW_PUNCTUATION_ENABLED =
            "pref_advanced_voice_zh_tw_punctuation_enabled";
    public static final String BACKEND_ADVANCED = "advanced";
    public static final String BACKEND_RAMBLER = "rambler";
    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_BACKEND = BACKEND_ADVANCED;
    public static final boolean DEFAULT_ZH_TW_PUNCTUATION_ENABLED = false;

    private GboardAdvancedVoiceSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        return GboardPatchesSettings.preferences(context);
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
            changed = true;
        }
        if (!preferences.contains(PREF_KEY_BACKEND)) {
            editor.putString(PREF_KEY_BACKEND, DEFAULT_BACKEND);
            changed = true;
        }
        if (!preferences.contains(PREF_KEY_ZH_TW_PUNCTUATION_ENABLED)) {
            editor.putBoolean(
                    PREF_KEY_ZH_TW_PUNCTUATION_ENABLED,
                    DEFAULT_ZH_TW_PUNCTUATION_ENABLED);
            changed = true;
        }
        if (changed) {
            if (editor.commit()) {
                GboardAdvancedVoiceRuntimeSettings.invalidateCachedSnapshot();
            }
        }
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return readBooleanStrict(preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean readZhTwPunctuationEnabled(SharedPreferences preferences) {
        return readBooleanStrict(
                preferences,
                PREF_KEY_ZH_TW_PUNCTUATION_ENABLED,
                DEFAULT_ZH_TW_PUNCTUATION_ENABLED);
    }

    public static String readBackend(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_BACKEND;
        }
        Object raw = preferences.getAll().get(PREF_KEY_BACKEND);
        return BACKEND_RAMBLER.equals(raw) ? BACKEND_RAMBLER : BACKEND_ADVANCED;
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        return context != null && writeEnabled(preferences(context), enabled);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        return writeBoolean(preferences, PREF_KEY_ENABLED, enabled);
    }

    public static boolean writeBackend(Context context, String backend) {
        return context != null && writeBackend(preferences(context), backend);
    }

    public static boolean writeBackend(SharedPreferences preferences, String backend) {
        if (preferences == null) {
            return false;
        }
        String sanitized = BACKEND_RAMBLER.equals(backend)
                ? BACKEND_RAMBLER : BACKEND_ADVANCED;
        boolean committed = preferences.edit().putString(PREF_KEY_BACKEND, sanitized).commit();
        if (committed) {
            GboardAdvancedVoiceRuntimeSettings.invalidateCachedSnapshot();
        }
        return committed;
    }

    public static boolean writeZhTwPunctuationEnabled(Context context, boolean enabled) {
        return context != null
                && writeZhTwPunctuationEnabled(preferences(context), enabled);
    }

    public static boolean writeZhTwPunctuationEnabled(
            SharedPreferences preferences,
            boolean enabled) {
        return writeBoolean(
                preferences,
                PREF_KEY_ZH_TW_PUNCTUATION_ENABLED,
                enabled);
    }

    private static boolean readBooleanStrict(
            SharedPreferences preferences,
            String key,
            boolean defaultValue) {
        if (preferences == null) {
            return defaultValue;
        }
        Map<String, ?> values = preferences.getAll();
        Object raw = values.get(key);
        if (raw instanceof Boolean) {
            return ((Boolean) raw).booleanValue();
        }
        if (raw instanceof String) {
            if ("true".equalsIgnoreCase((String) raw)) {
                return true;
            }
            if ("false".equalsIgnoreCase((String) raw)) {
                return false;
            }
        }
        return defaultValue;
    }

    private static boolean writeBoolean(
            SharedPreferences preferences,
            String key,
            boolean value) {
        if (preferences == null) {
            return false;
        }
        boolean committed = preferences.edit().putBoolean(key, value).commit();
        if (committed) {
            GboardAdvancedVoiceRuntimeSettings.invalidateCachedSnapshot();
        }
        return committed;
    }
}
