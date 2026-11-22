package dev.jason.gboardpatches.extension.shiftdelete;

import android.content.SharedPreferences;

public final class GboardShiftDeleteSettings {
    public static final String ENABLED = "pref_shift_forward_delete_enabled";

    private GboardShiftDeleteSettings() {}

    public static boolean enabled(SharedPreferences preferences) {
        if (preferences == null) return false;
        Object value = preferences.getAll().get(ENABLED);
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }
}
