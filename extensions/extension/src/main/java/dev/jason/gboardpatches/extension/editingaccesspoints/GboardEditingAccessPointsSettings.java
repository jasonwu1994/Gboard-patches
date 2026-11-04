package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.SharedPreferences;
import java.util.Map;

/** Buttons default on; automatic select-all defaults off. Unavailable settings fail closed. */
public final class GboardEditingAccessPointsSettings {
    public static final String ENABLED = "pref_editing_access_points_enabled";

    public static final String SELECT_ALL_WHEN_UNSELECTED =
            "pref_editing_access_points_select_all_when_unselected";

    private GboardEditingAccessPointsSettings() {}

    public static String key(EditorAction action) {
        return "pref_editing_access_points_" + action.token;
    }

    public static boolean read(SharedPreferences preferences, String key) {
        try {
            return preferences != null && read(preferences.getAll(), key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean read(Map<String, ?> values, String key) {
        if (values == null) return false;
        if (!values.containsKey(key)) return !SELECT_ALL_WHEN_UNSELECTED.equals(key);
        Object value = values.get(key);
        // Accept portable settings backups containing Boolean or string values.
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }
}
