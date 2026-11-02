package dev.jason.gboardpatches.extension.writingtools;

import android.content.Context;
import android.content.SharedPreferences;

public final class GboardAiWritingToolsSettings {
    public static final String PREF_FILE = "gboard_ai_writing_tools";
    public static final String PREF_KEY_ENABLED = "pref_ai_writing_tools_enabled";
    public static final String PREF_KEY_ALL_KEYBOARDS =
            "pref_ai_writing_tools_all_keyboards_enabled";
    public static final String PREF_KEY_BACKEND_TYPE =
            "pref_ai_writing_tools_backend_type";
    public static final String BACKEND_GBOARD_SERVER = "GBOARD_SERVER";
    public static final String BACKEND_PRIVATE_INFERENCE_AICORE =
            "PRIVATE_INFERENCE_AICORE";
    public static final String BACKEND_PRIVATE_INFERENCE_ASTREA =
            "PRIVATE_INFERENCE_ASTREA";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_ALL_KEYBOARDS = true;
    public static final String DEFAULT_BACKEND_TYPE = BACKEND_GBOARD_SERVER;

    private GboardAiWritingToolsSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        if (lookupContext == null) {
            return null;
        }
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        if (context == null) {
            return;
        }
        ensureDefaults(preferences(context));
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor = preferences.edit();
            editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_ALL_KEYBOARDS)) {
            if (editor == null) {
                editor = preferences.edit();
            }
            editor.putBoolean(PREF_KEY_ALL_KEYBOARDS, DEFAULT_ALL_KEYBOARDS);
        }
        if (!preferences.contains(PREF_KEY_BACKEND_TYPE)) {
            if (editor == null) {
                editor = preferences.edit();
            }
            editor.putString(PREF_KEY_BACKEND_TYPE, DEFAULT_BACKEND_TYPE);
        }
        if (editor != null) {
            editor.apply();
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

    public static boolean readAllKeyboardsEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ALL_KEYBOARDS;
        }
        try {
            return preferences.getBoolean(PREF_KEY_ALL_KEYBOARDS, DEFAULT_ALL_KEYBOARDS);
        } catch (ClassCastException ignored) {
            return DEFAULT_ALL_KEYBOARDS;
        }
    }

    public static String readBackendType(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_BACKEND_TYPE;
        }
        try {
            return sanitizeBackendType(
                    preferences.getString(PREF_KEY_BACKEND_TYPE, DEFAULT_BACKEND_TYPE));
        } catch (ClassCastException ignored) {
            return DEFAULT_BACKEND_TYPE;
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

    public static boolean writeAllKeyboardsEnabled(SharedPreferences preferences,
            boolean enabled) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit()
                    .putBoolean(PREF_KEY_ALL_KEYBOARDS, enabled)
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean writeBackendType(SharedPreferences preferences, String backendType) {
        if (preferences == null) {
            return false;
        }
        try {
            return preferences.edit()
                    .putString(PREF_KEY_BACKEND_TYPE, sanitizeBackendType(backendType))
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String sanitizeBackendType(String backendType) {
        if (BACKEND_PRIVATE_INFERENCE_AICORE.equals(backendType)
                || BACKEND_PRIVATE_INFERENCE_ASTREA.equals(backendType)) {
            return backendType;
        }
        return BACKEND_GBOARD_SERVER;
    }

    public static Snapshot snapshot(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return new Snapshot(
                readEnabled(preferences),
                readAllKeyboardsEnabled(preferences),
                readBackendType(preferences));
    }

    public static final class Snapshot {
        public final boolean featureEnabled;
        public final boolean allKeyboardsEnabled;
        public final String backendType;

        public Snapshot(boolean featureEnabled, boolean allKeyboardsEnabled) {
            this(featureEnabled, allKeyboardsEnabled, DEFAULT_BACKEND_TYPE);
        }

        public Snapshot(boolean featureEnabled, boolean allKeyboardsEnabled,
                String backendType) {
            this.featureEnabled = featureEnabled;
            this.allKeyboardsEnabled = featureEnabled && allKeyboardsEnabled;
            this.backendType = backendType;
        }
    }
}
