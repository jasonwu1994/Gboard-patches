package dev.jason.gboardpatches.extension.quickinsert;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardQuickInsertSettingsFeature
        extends GboardBooleanFlagSettingsFeature {
    private static final String PREVIEW_ROOT = "settings-previews/quickinsert/";

    public GboardQuickInsertSettingsFeature(Context context) {
        super(
                GboardPatchesFeatureAvailability.FEATURE_QUICK_INSERT,
                text(context, R.string.gboard_patches_quick_insert_title),
                "",
                text(context, R.string.gboard_patches_quick_insert_title),
                text(context, R.string.gboard_patches_header_badge),
                text(context, R.string.gboard_patches_quick_insert_error_title),
                text(context, R.string.gboard_patches_quick_insert_error_summary),
                text(context, R.string.gboard_patches_quick_insert_section_feature),
                new GboardPatchesSettingsContract.PreviewSpec(
                        text(context, R.string.gboard_patches_quick_insert_title),
                        "",
                        GboardPatchesSettingsContract.PreviewLayout.STACKED,
                        new GboardPatchesSettingsContract.PreviewImage(
                                PREVIEW_ROOT + "01_quick_insert_panel.png",
                                ""),
                        new GboardPatchesSettingsContract.PreviewImage(
                                PREVIEW_ROOT + "02_quick_insert_access_point.png",
                                "")),
                new SettingsStore() {
                    @Override
                    public void ensureDefault(SharedPreferences preferences) {
                        GboardQuickInsertSettings.ensureDefault(preferences);
                    }

                    @Override
                    public boolean readEnabled(SharedPreferences preferences) {
                        return GboardQuickInsertSettings.readEnabled(preferences);
                    }

                    @Override
                    public boolean writeEnabled(
                            SharedPreferences preferences,
                            boolean enabled) {
                        return GboardQuickInsertSettings.writeEnabled(preferences, enabled);
                    }
                });
        declareRestartRequired(GboardQuickInsertSettings.PREF_KEY_ENABLED);
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
