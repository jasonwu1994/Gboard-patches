package dev.jason.gboardpatches.extension.frostedglass;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Settings owned by the module; the theme ZIP remains the source of palette and alpha. */
public final class GboardFrostedGlassSettings {
    public static final String PREF_KEY_ENABLED = "pref_frosted_glass_enabled";
    public static final String PREF_KEY_BLUR_RADIUS_PX = "pref_frosted_glass_blur_radius_px";
    public static final String PREF_KEY_TRANSPARENCY_MODE = "pref_frosted_glass_transparency_mode";
    public static final String PREF_KEY_CUSTOM_OPACITY = "pref_frosted_glass_custom_opacity";
    public static final String PREF_KEY_KEY_TRANSPARENCY_MODE = "pref_frosted_glass_key_transparency_mode";
    public static final String PREF_KEY_KEY_CUSTOM_OPACITY = "pref_frosted_glass_key_custom_opacity";
    // Keep the seamless navigation bar preference in the Frosted Glass settings page.
    public static final String PREF_KEY_SEAMLESS_NAV_BAR_ENABLED = "pref_seamless_nav_bar_enabled";
    public static final boolean DEFAULT_SEAMLESS_NAV_BAR_ENABLED = true;
    private static final String PREF_KEY_HEADER_ALPHA = "pref_frosted_glass_header_alpha";
    private static final String PREF_KEY_BODY_ALPHA = "pref_frosted_glass_body_alpha";
    private static final String PREF_KEY_KEYTOP_ALPHA = "pref_frosted_glass_keytop_alpha";
    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_BLUR_STRENGTH = 20;
    public static final String TRANSPARENCY_MODE_THEME = "theme";
    public static final String TRANSPARENCY_MODE_CUSTOM = "custom";
    public static final int DEFAULT_CUSTOM_OPACITY = 20;
    public static final int MIN_BLUR_STRENGTH = 1;
    public static final int MAX_BLUR_STRENGTH = 100;
    public static final int MIN_BLUR_RADIUS_PX = 1;
    public static final int MAX_BLUR_RADIUS_PX = 160;

    private GboardFrostedGlassSettings() {
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
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
        return DEFAULT_ENABLED;
    }

    public static int readBlurStrength(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_BLUR_STRENGTH;
        }
        Object raw = preferences.getAll().get(PREF_KEY_BLUR_RADIUS_PX);
        int value;
        try {
            value = raw instanceof Number number
                    ? number.intValue() : Integer.parseInt(String.valueOf(raw));
        } catch (Throwable ignored) {
            return DEFAULT_BLUR_STRENGTH;
        }
        return sanitizeBlurStrength(value);
    }

    public static String readTransparencyMode(SharedPreferences preferences) {
        if (preferences == null) {
            return TRANSPARENCY_MODE_THEME;
        }
        String value = preferences.getString(PREF_KEY_TRANSPARENCY_MODE,
                TRANSPARENCY_MODE_THEME);
        return TRANSPARENCY_MODE_CUSTOM.equals(value)
                ? TRANSPARENCY_MODE_CUSTOM : TRANSPARENCY_MODE_THEME;
    }

    public static int readCustomOpacity(SharedPreferences preferences) {
        return readOpacity(preferences, PREF_KEY_CUSTOM_OPACITY);
    }

    public static String readKeyTransparencyMode(SharedPreferences preferences) {
        Object value = preferences == null ? null : preferences.getAll().get(PREF_KEY_KEY_TRANSPARENCY_MODE);
        return TRANSPARENCY_MODE_CUSTOM.equals(value) ? TRANSPARENCY_MODE_CUSTOM : TRANSPARENCY_MODE_THEME;
    }

    public static int readKeyCustomOpacity(SharedPreferences preferences) {
        return readOpacity(preferences, PREF_KEY_KEY_CUSTOM_OPACITY);
    }

    public static boolean readSeamlessNavBarEnabled(SharedPreferences preferences) {
        Object value = preferences == null ? null : preferences.getAll().get(PREF_KEY_SEAMLESS_NAV_BAR_ENABLED);
        if (value instanceof Boolean enabled) return enabled;
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text)) return true;
            if ("false".equalsIgnoreCase(text)) return false;
        }
        return DEFAULT_SEAMLESS_NAV_BAR_ENABLED;
    }

    private static int readOpacity(SharedPreferences preferences, String key) {
        if (preferences == null) {
            return DEFAULT_CUSTOM_OPACITY;
        }
        Object raw = preferences.getAll().get(key);
        try {
            int value = raw instanceof Number number
                    ? number.intValue() : Integer.parseInt(String.valueOf(raw));
            return sanitizeOpacity(value);
        } catch (Throwable ignored) {
            return DEFAULT_CUSTOM_OPACITY;
        }
    }

    public static int sanitizeOpacity(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static int sanitizeBlurStrength(int value) {
        return Math.max(MIN_BLUR_STRENGTH, Math.min(MAX_BLUR_STRENGTH, value));
    }

    public static int blurStrengthToRadiusPx(int value) {
        int strength = sanitizeBlurStrength(value);
        float span = MAX_BLUR_RADIUS_PX - MIN_BLUR_RADIUS_PX;
        float strengthSpan = MAX_BLUR_STRENGTH - MIN_BLUR_STRENGTH;
        return Math.round(MIN_BLUR_RADIUS_PX
                + (strength - MIN_BLUR_STRENGTH) * (span / strengthSpan));
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences != null && !preferences.contains(PREF_KEY_ENABLED)) {
            preferences.edit()
                    .putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED)
                    .putString(PREF_KEY_BLUR_RADIUS_PX,
                            Integer.toString(DEFAULT_BLUR_STRENGTH))
                    .putString(PREF_KEY_TRANSPARENCY_MODE, TRANSPARENCY_MODE_THEME)
                    .putInt(PREF_KEY_CUSTOM_OPACITY, DEFAULT_CUSTOM_OPACITY)
                    .apply();
        } else if (preferences != null && !preferences.contains(PREF_KEY_BLUR_RADIUS_PX)) {
            preferences.edit().putString(PREF_KEY_BLUR_RADIUS_PX,
                    Integer.toString(DEFAULT_BLUR_STRENGTH)).apply();
        }
        if (preferences != null && !preferences.contains(PREF_KEY_TRANSPARENCY_MODE)) {
            preferences.edit().putString(PREF_KEY_TRANSPARENCY_MODE,
                    TRANSPARENCY_MODE_THEME).apply();
        }
        if (preferences != null && !preferences.contains(PREF_KEY_CUSTOM_OPACITY)) {
            preferences.edit().putInt(PREF_KEY_CUSTOM_OPACITY,
                    DEFAULT_CUSTOM_OPACITY).apply();
        }
        if (preferences != null && !preferences.contains(PREF_KEY_KEY_TRANSPARENCY_MODE)) {
            preferences.edit().putString(PREF_KEY_KEY_TRANSPARENCY_MODE,
                    TRANSPARENCY_MODE_THEME).apply();
        }
        if (preferences != null && !preferences.contains(PREF_KEY_KEY_CUSTOM_OPACITY)) {
            preferences.edit().putInt(PREF_KEY_KEY_CUSTOM_OPACITY, DEFAULT_CUSTOM_OPACITY).apply();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        boolean committed = editor(context).putBoolean(PREF_KEY_ENABLED, enabled).commit();
        if (committed) {

        }
        return committed;
    }

    public static boolean writeBlurStrength(Context context, int strength) {
        if (context == null) {
            return false;
        }
        boolean committed = editor(context).putString(PREF_KEY_BLUR_RADIUS_PX,
                        Integer.toString(sanitizeBlurStrength(strength))).commit();
        if (committed) {

        }
        return committed;
    }

    public static boolean writeTransparencyMode(Context context, String mode) {
        return writeMode(context, PREF_KEY_TRANSPARENCY_MODE, mode);
    }

    public static boolean writeKeyTransparencyMode(Context context, String mode) {
        return writeMode(context, PREF_KEY_KEY_TRANSPARENCY_MODE, mode);
    }

    private static boolean writeMode(Context context, String key, String mode) {
        if (context == null) {
            return false;
        }
        String value = TRANSPARENCY_MODE_CUSTOM.equals(mode)
                ? TRANSPARENCY_MODE_CUSTOM : TRANSPARENCY_MODE_THEME;
        return editor(context)
                .putString(key, value).commit();
    }

    public static boolean writeCustomOpacity(Context context, int opacity) {
        return writeOpacity(context, PREF_KEY_CUSTOM_OPACITY, opacity);
    }

    public static boolean writeKeyCustomOpacity(Context context, int opacity) {
        return writeOpacity(context, PREF_KEY_KEY_CUSTOM_OPACITY, opacity);
    }

    private static boolean writeOpacity(Context context, String key, int opacity) {
        if (context == null) {
            return false;
        }
        SharedPreferences.Editor editor = editor(context)
                .putInt(key, sanitizeOpacity(opacity));
        if (PREF_KEY_CUSTOM_OPACITY.equals(key)) {
            editor.putString(PREF_KEY_HEADER_ALPHA, Integer.toString(alphaByte(opacity)))
                    .putString(PREF_KEY_BODY_ALPHA, Integer.toString(alphaByte(opacity)))
                    .putString(PREF_KEY_KEYTOP_ALPHA, Integer.toString(alphaByte(opacity)));
        }
        return editor.commit();
    }

    public static boolean writeSeamlessNavBarEnabled(Context context, boolean enabled) {
        if (context == null) {
            return false;
        }
        return editor(context)
                .putBoolean(PREF_KEY_SEAMLESS_NAV_BAR_ENABLED, enabled).commit();
    }

    /** The settings page's single write path; runtime reads use the guarded runtime store. */
    private static SharedPreferences.Editor editor(Context context) {
        return GboardPatchesSettings.preferences(context).edit();
    }

    private static int alphaByte(int opacity) {
        return Math.round((100 - sanitizeOpacity(opacity)) * 255f / 100f);
    }
}
