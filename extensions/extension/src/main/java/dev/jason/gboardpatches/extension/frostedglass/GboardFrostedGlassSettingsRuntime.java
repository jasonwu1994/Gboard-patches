package dev.jason.gboardpatches.extension.frostedglass;

import android.content.SharedPreferences;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardFrostedGlassSettingsRuntime {
    private GboardFrostedGlassSettingsRuntime() {}

    private static SharedPreferences prefs() {
        try {
            return GboardFlagRuntimeContext.preferencesOrNull();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isEnabled() {
        try {
            return GboardFrostedGlassSettings.readEnabled(prefs());
        } catch (Throwable ignored) {
            return GboardFrostedGlassSettings.DEFAULT_ENABLED;
        }
    }

    public static int blurRadiusPx() {
        try {
            int strength = GboardFrostedGlassSettings.readBlurStrength(prefs());
            return GboardFrostedGlassSettings.blurStrengthToRadiusPx(strength);
        } catch (Throwable ignored) {
            return GboardFrostedGlassSettings.blurStrengthToRadiusPx(
                    GboardFrostedGlassSettings.DEFAULT_BLUR_STRENGTH);
        }
    }

    public static int customOpacityPercent() {
        try {
            SharedPreferences p = prefs();
            if (GboardFrostedGlassSettings.TRANSPARENCY_MODE_CUSTOM.equals(
                    GboardFrostedGlassSettings.readTransparencyMode(p))) {
                return Math.round((100 - GboardFrostedGlassSettings.readCustomOpacity(p)) * 255f / 100f);
            }
        } catch (Throwable ignored) {
            // Fall through to theme transparency mode when preferences are unavailable.
        }
        return -1;
    }

    /** Key background alpha 0–255, or -1 to follow the theme. */
    public static int customKeyOpacity() {
        try {
            SharedPreferences p = prefs();
            if (GboardFrostedGlassSettings.TRANSPARENCY_MODE_CUSTOM.equals(
                    GboardFrostedGlassSettings.readKeyTransparencyMode(p))) {
                return Math.round((100 - GboardFrostedGlassSettings.readKeyCustomOpacity(p)) * 255f / 100f);
            }
        } catch (Throwable ignored) {
            // Fall through to theme key transparency when preferences are unavailable.
        }
        return -1;
    }

    public static boolean isSeamlessNavBarEnabled() {
        try {
            SharedPreferences p = prefs();
            return p != null && GboardFrostedGlassSettings.readSeamlessNavBarEnabled(p);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
