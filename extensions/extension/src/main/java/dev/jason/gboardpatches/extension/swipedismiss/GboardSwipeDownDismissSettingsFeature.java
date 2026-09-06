package dev.jason.gboardpatches.extension.swipedismiss;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardSwipeDownDismissSettingsFeature
        extends GboardBooleanFlagSettingsFeature {
    public GboardSwipeDownDismissSettingsFeature(Context context) {
        super(
                GboardPatchesFeatureAvailability.FEATURE_SWIPE_DOWN_DISMISS_KEYBOARD,
                text(context, R.string.gboard_patches_swipe_down_dismiss_title),
                text(context, R.string.gboard_patches_swipe_down_dismiss_summary),
                text(context, R.string.gboard_patches_swipe_down_dismiss_enabled_title),
                text(context, R.string.gboard_patches_header_badge),
                text(context, R.string.gboard_patches_flag_patch_error_title),
                text(context, R.string.gboard_patches_flag_patch_error_summary),
                text(context, R.string.gboard_patches_flag_patch_section_feature),
                null,
                new SettingsStore() {
                    @Override
                    public void ensureDefault(SharedPreferences preferences) {
                        GboardSwipeDownDismissSettings.ensureDefault(preferences);
                    }

                    @Override
                    public boolean readEnabled(SharedPreferences preferences) {
                        return GboardSwipeDownDismissSettings.readEnabled(preferences);
                    }

                    @Override
                    public boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
                        return GboardSwipeDownDismissSettings.writeEnabled(preferences, enabled);
                    }
                });
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
