package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardLongPressQuickActionsSettings {
    public static final String PREF_KEY_ENABLED =
            "pref_long_press_quick_actions_enabled";
    public static final String PREF_KEY_GLOBE_DRAG_ENABLED =
            "pref_long_press_quick_actions_globe_drag_enabled";
    public static final String PREF_KEY_POSITION =
            "pref_long_press_quick_actions_position";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_GLOBE_DRAG_ENABLED = false;
    public static final int POSITION_LAST = -1;
    public static final int POSITION_FIRST = 0;
    public static final int DEFAULT_POSITION = POSITION_LAST;

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

    public static int readPosition(Context context) {
        if (context == null) {
            return DEFAULT_POSITION;
        }
        return readPosition(GboardPatchesSettings.preferences(context));
    }

    public static int readPosition(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_POSITION;
        }
        Object raw = preferences.getAll().get(PREF_KEY_POSITION);
        if (raw instanceof Integer value) {
            return normalizePosition(value.intValue());
        }
        if (raw instanceof String value) {
            try {
                return normalizePosition(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return DEFAULT_POSITION;
            }
        }
        return DEFAULT_POSITION;
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
        if (!preferences.contains(PREF_KEY_POSITION)) {
            editor.putInt(PREF_KEY_POSITION, DEFAULT_POSITION);
            changed = true;
        }
        if (changed) {
            editor.commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = GboardPatchesSettings.preferences(context);
        return writeEnabled(preferences, enabled);
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
        if (context == null) {
            return false;
        }
        return writeGlobeDragEnabled(GboardPatchesSettings.preferences(context), enabled);
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

    public static boolean writePosition(SharedPreferences preferences, int position) {
        if (preferences == null) {
            return false;
        }
        return preferences.edit()
                .putInt(PREF_KEY_POSITION, normalizePosition(position))
                .commit();
    }

    static int insertionIndex(int configuredPosition, int existingCount) {
        int safeCount = Math.max(0, existingCount);
        return configuredPosition == POSITION_LAST
                ? safeCount : 0;
    }

    private static int normalizePosition(int position) {
        return position == POSITION_FIRST ? POSITION_FIRST : POSITION_LAST;
    }
}
