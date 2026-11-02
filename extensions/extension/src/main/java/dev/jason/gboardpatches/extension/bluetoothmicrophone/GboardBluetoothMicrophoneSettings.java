package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardBluetoothMicrophoneSettings {
    public static final String PREF_KEY_ENABLED =
            "pref_use_bluetooth_microphone_override_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private GboardBluetoothMicrophoneSettings() {
    }

    public static boolean readEnabled(Context context) {
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        ensureDefault(preferences);
        return readEnabled(preferences);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return false;
        }
        Map<String, ?> values = preferences.getAll();
        if (!values.containsKey(PREF_KEY_ENABLED)) {
            return DEFAULT_ENABLED;
        }
        Object raw = values.get(PREF_KEY_ENABLED);
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
        return false;
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences == null || preferences.contains(PREF_KEY_ENABLED)) {
            return;
        }
        preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED)
                .commit();
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        return writeEnabled(GboardPatchesSettings.preferences(context), enabled);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .commit();
    }
}
