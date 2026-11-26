package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.SharedPreferences;
import java.util.Map;

/**
 * Core editing buttons (Select All/Copy/Cut/Paste) default on; extended buttons and automatic
 * select-all default off. Unavailable settings fail closed.
 */
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
        if (values == null || key == null) return false;
        if (!values.containsKey(key)) return defaultValue(key);
        Object value = values.get(key);
        // Accept portable settings backups containing Boolean or string values.
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    static boolean defaultValue(String key) {
        if (ENABLED.equals(key)) return true;
        if (SELECT_ALL_WHEN_UNSELECTED.equals(key)) return false;
        for (EditorAction action : EditorAction.values()) {
            if (key(action).equals(key)) return action.defaultEnabled;
        }
        return false;
    }
}
