package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardLongPressQuickActionsSettings {
    public static final String PREF_KEY_ENABLED =
            "pref_long_press_quick_actions_enabled";
    public static final String PREF_KEY_GLOBE_DRAG_ENABLED =
            "pref_long_press_quick_actions_globe_drag_enabled";
    public static final String PREF_KEY_SELECT_ALL_WHEN_UNSELECTED =
            "pref_long_press_quick_actions_select_all_when_unselected";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_GLOBE_DRAG_ENABLED = false;
    public static final boolean DEFAULT_SELECT_ALL_WHEN_UNSELECTED = false;

    private GboardLongPressQuickActionsSettings() {
    }

    public static boolean readEnabled(Context context) {
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        ensureDefault(preferences);
        return readEnabled(preferences);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return readBoolean(preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean readGlobeDragEnabled(Context context) {
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        ensureDefault(preferences);
        return readGlobeDragEnabled(preferences);
    }

    public static boolean readGlobeDragEnabled(SharedPreferences preferences) {
        return readBoolean(preferences, PREF_KEY_GLOBE_DRAG_ENABLED,
                DEFAULT_GLOBE_DRAG_ENABLED);
    }

    public static boolean readSelectAllWhenUnselected(SharedPreferences preferences) {
        return readBoolean(preferences, PREF_KEY_SELECT_ALL_WHEN_UNSELECTED,
                DEFAULT_SELECT_ALL_WHEN_UNSELECTED);
    }

    private static boolean readBoolean(SharedPreferences preferences, String key,
            boolean defaultValue) {
        if (preferences == null) {
            return defaultValue;
        }
        Object raw = preferences.getAll().get(key);
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
        return defaultValue;
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
            changed = true;
        }
        if (!preferences.contains(PREF_KEY_GLOBE_DRAG_ENABLED)) {
            editor.putBoolean(PREF_KEY_GLOBE_DRAG_ENABLED, DEFAULT_GLOBE_DRAG_ENABLED);
            changed = true;
        }
        if (!preferences.contains(PREF_KEY_SELECT_ALL_WHEN_UNSELECTED)) {
            editor.putBoolean(PREF_KEY_SELECT_ALL_WHEN_UNSELECTED,
                    DEFAULT_SELECT_ALL_WHEN_UNSELECTED);
            changed = true;
        }
        if (changed) {
            editor.commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        return writeBoolean(context, PREF_KEY_ENABLED, enabled);
    }

    public static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .commit();
    }

    public static boolean writeGlobeDragEnabled(Context context, boolean enabled) {
        return writeBoolean(context, PREF_KEY_GLOBE_DRAG_ENABLED, enabled);
    }

    public static boolean writeGlobeDragEnabled(SharedPreferences preferences,
            boolean enabled) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit()
                .putBoolean(PREF_KEY_GLOBE_DRAG_ENABLED, enabled)
                .commit();
    }

    public static boolean writeSelectAllWhenUnselected(Context context, boolean enabled) {
        return writeBoolean(context, PREF_KEY_SELECT_ALL_WHEN_UNSELECTED, enabled);
    }

    // Settings UI writes share the same owner; runtime reads use the guarded settings snapshot.
    private static boolean writeBoolean(Context context, String key, boolean enabled) {
        return context != null && GboardPatchesSettings.preferences(context).edit()
                .putBoolean(key, enabled).commit();
    }

    public static boolean writeSelectAllWhenUnselected(SharedPreferences preferences,
            boolean enabled) {
        return preferences != null && preferences.edit()
                .putBoolean(PREF_KEY_SELECT_ALL_WHEN_UNSELECTED, enabled)
                .commit();
    }
}
