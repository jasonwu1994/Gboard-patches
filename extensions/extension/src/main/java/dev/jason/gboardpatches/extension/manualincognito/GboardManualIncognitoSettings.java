package dev.jason.gboardpatches.extension.manualincognito;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public final class GboardManualIncognitoSettings {
    public static final String PREF_FILE = "gboard_manual_incognito";
    public static final String PREF_FEATURE_ENABLED =
            "pref_manual_incognito_feature_enabled";
    public static final String PREF_MANUAL_REQUESTED =
            "pref_manual_incognito_requested";
    public static final String PREF_ALLOW_CLIPBOARD =
            "pref_incognito_allow_clipboard";
    public static final String PREF_ALLOW_VOICE_TYPING =
            "pref_incognito_allow_voice_typing";

    public static final boolean DEFAULT_FEATURE_ENABLED = false;
    public static final boolean DEFAULT_MANUAL_REQUESTED = false;
    public static final boolean DEFAULT_ALLOW_CLIPBOARD = false;
    public static final boolean DEFAULT_ALLOW_VOICE_TYPING = false;

    private static final Object WRITE_LOCK = new Object();

    private GboardManualIncognitoSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        if (lookupContext == null) {
            throw new IllegalStateException("Context required for manual incognito settings.");
        }
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static boolean ensureDefaults(Context context) {
        SharedPreferences preferences = preferences(context);
        synchronized (WRITE_LOCK) {
            Map<String, ?> values = preferences.getAll();
            SharedPreferences.Editor editor = preferences.edit();
            boolean changed = false;
            if (!values.containsKey(PREF_FEATURE_ENABLED)) {
                editor.putBoolean(PREF_FEATURE_ENABLED, DEFAULT_FEATURE_ENABLED);
                changed = true;
            }
            if (!values.containsKey(PREF_MANUAL_REQUESTED)) {
                editor.putBoolean(PREF_MANUAL_REQUESTED, DEFAULT_MANUAL_REQUESTED);
                changed = true;
            }
            if (!values.containsKey(PREF_ALLOW_CLIPBOARD)) {
                editor.putBoolean(PREF_ALLOW_CLIPBOARD, DEFAULT_ALLOW_CLIPBOARD);
                changed = true;
            }
            if (!values.containsKey(PREF_ALLOW_VOICE_TYPING)) {
                editor.putBoolean(PREF_ALLOW_VOICE_TYPING, DEFAULT_ALLOW_VOICE_TYPING);
                changed = true;
            }
            if (!readBoolean(values, PREF_FEATURE_ENABLED, DEFAULT_FEATURE_ENABLED)
                    && readBoolean(values, PREF_MANUAL_REQUESTED,
                            DEFAULT_MANUAL_REQUESTED)) {
                editor.putBoolean(PREF_MANUAL_REQUESTED, false);
                changed = true;
            }
            return !changed || editor.commit();
        }
    }

    public static RequestedPolicy read(Context context) {
        ensureDefaults(context);
        return read(preferences(context));
    }

    static RequestedPolicy read(SharedPreferences preferences) {
        if (preferences == null) {
            return RequestedPolicy.defaults();
        }
        Map<String, ?> values = preferences.getAll();
        boolean enabled = readBoolean(
                values, PREF_FEATURE_ENABLED, DEFAULT_FEATURE_ENABLED);
        return new RequestedPolicy(
                enabled,
                enabled && readBoolean(
                        values, PREF_MANUAL_REQUESTED, DEFAULT_MANUAL_REQUESTED),
                readBoolean(values, PREF_ALLOW_CLIPBOARD, DEFAULT_ALLOW_CLIPBOARD),
                readBoolean(values, PREF_ALLOW_VOICE_TYPING, DEFAULT_ALLOW_VOICE_TYPING));
    }

    public static ToggleResult toggleManualRequested(Context context) {
        SharedPreferences preferences = preferences(context);
        synchronized (WRITE_LOCK) {
            RequestedPolicy current = read(preferences);
            if (!current.featureEnabled) {
                return ToggleResult.failure(false);
            }
            boolean requested = !current.manualRequested;
            boolean success = preferences.edit()
                    .putBoolean(PREF_MANUAL_REQUESTED, requested)
                    .commit();
            return success
                    ? ToggleResult.success(requested)
                    : ToggleResult.failure(current.manualRequested);
        }
    }

    public static boolean writeFeatureEnabled(Context context, boolean enabled) {
        SharedPreferences preferences = preferences(context);
        synchronized (WRITE_LOCK) {
            SharedPreferences.Editor editor = preferences.edit()
                    .putBoolean(PREF_FEATURE_ENABLED, enabled);
            if (!enabled) {
                editor.putBoolean(PREF_MANUAL_REQUESTED, false);
            }
            return editor.commit();
        }
    }

    public static boolean writeAllowClipboard(Context context, boolean allowed) {
        return writeBoolean(context, PREF_ALLOW_CLIPBOARD, allowed);
    }

    public static boolean writeAllowVoiceTyping(Context context, boolean allowed) {
        return writeBoolean(context, PREF_ALLOW_VOICE_TYPING, allowed);
    }

    private static boolean writeBoolean(Context context, String key, boolean value) {
        SharedPreferences preferences = preferences(context);
        synchronized (WRITE_LOCK) {
            return preferences.edit().putBoolean(key, value).commit();
        }
    }

    private static boolean readBoolean(Map<String, ?> values, String key,
            boolean defaultValue) {
        Object value = values.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue.booleanValue();
        }
        if (value instanceof String stringValue) {
            if ("true".equalsIgnoreCase(stringValue) || "1".equals(stringValue)) {
                return true;
            }
            if ("false".equalsIgnoreCase(stringValue) || "0".equals(stringValue)) {
                return false;
            }
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return defaultValue;
    }

    public static final class RequestedPolicy {
        public final boolean featureEnabled;
        public final boolean manualRequested;
        public final boolean allowClipboardRequested;
        public final boolean allowVoiceRequested;

        RequestedPolicy(boolean featureEnabled, boolean manualRequested,
                boolean allowClipboardRequested, boolean allowVoiceRequested) {
            this.featureEnabled = featureEnabled;
            this.manualRequested = manualRequested;
            this.allowClipboardRequested = allowClipboardRequested;
            this.allowVoiceRequested = allowVoiceRequested;
        }

        static RequestedPolicy defaults() {
            return new RequestedPolicy(false, false, false, false);
        }

        RequestedPolicy withManualRequested(boolean requested) {
            return new RequestedPolicy(
                    featureEnabled,
                    requested,
                    allowClipboardRequested,
                    allowVoiceRequested);
        }
    }

    public static final class ToggleResult {
        public final boolean success;
        public final boolean requested;

        private ToggleResult(boolean success, boolean requested) {
            this.success = success;
            this.requested = requested;
        }

        static ToggleResult success(boolean requested) {
            return new ToggleResult(true, requested);
        }

        static ToggleResult failure(boolean requested) {
            return new ToggleResult(false, requested);
        }
    }
}
