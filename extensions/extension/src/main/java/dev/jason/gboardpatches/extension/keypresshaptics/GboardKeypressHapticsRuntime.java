package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Selected-only flag callback. Every failure preserves the incoming composed result. */
@SuppressWarnings("unused")
public final class GboardKeypressHapticsRuntime {
    private GboardKeypressHapticsRuntime() {}

    public static Object applyFlagValue(String name, Object original) {
        if (!GboardKeypressHapticsPolicy.isTarget(name, original)) return original;
        try {
            return applyFlagValue(name, original, GboardFlagRuntimeContext.resolveContext());
        } catch (Throwable unavailable) {
            return original;
        }
    }

    static Object applyFlagValue(String name, Object original, Context context) {
        if (!GboardKeypressHapticsPolicy.isTarget(name, original)) return original;
        try {
            Boolean enabled = GboardPatchesSettings.readRuntimeSetting(context,
                    GboardKeypressHapticsSettings::readEnabled, (Boolean) null);
            return GboardKeypressHapticsPolicy.maybeOverride(name, original, enabled);
        } catch (Throwable unavailable) {
            return original;
        }
    }
}
