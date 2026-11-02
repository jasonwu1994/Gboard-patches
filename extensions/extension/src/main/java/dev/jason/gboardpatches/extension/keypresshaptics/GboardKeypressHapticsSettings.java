package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.SharedPreferences;

public final class GboardKeypressHapticsSettings {
    // Retain the accepted experiment's setting across this extraction.
    public static final String PREF_KEY_ENABLED = "pref_modern_keypress_haptics_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private GboardKeypressHapticsSettings() {}

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_ENABLED;
        Object value = preferences.getAll().get(PREF_KEY_ENABLED);
        // Accept preferences stored as either booleans or strings.
        return Boolean.TRUE.equals(value)
                || value instanceof String text && "true".equalsIgnoreCase(text);
    }
}
