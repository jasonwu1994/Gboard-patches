package dev.jason.gboardpatches.extension.zhuyintoggle;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardZhuyinSimplifiedCandidateButtonSettings {
    public static final String PREF_KEY_ENABLED =
            "pref_zhuyin_simplified_candidate_button_enabled";
    public static final boolean DEFAULT_ENABLED = true;

    private GboardZhuyinSimplifiedCandidateButtonSettings() {
    }

    public static boolean readEnabled(Context context) {
        try {
            return GboardPatchesSettings.preferences(context).getBoolean(
                    PREF_KEY_ENABLED, DEFAULT_ENABLED);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void ensureDefault(SharedPreferences preferences) {
        if (preferences != null && !preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit().putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED).apply();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        try {
            boolean saved = GboardPatchesSettings.preferences(context).edit()
                    .putBoolean(PREF_KEY_ENABLED, enabled)
                    .commit();
            if (saved) {
                GboardZhuyinTraditionalSimplifiedToggleRuntime.onCandidateButtonSettingChanged();
            }
            return saved;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
