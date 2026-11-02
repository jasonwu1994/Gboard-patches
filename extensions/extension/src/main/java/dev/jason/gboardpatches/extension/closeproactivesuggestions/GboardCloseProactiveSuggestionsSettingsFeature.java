package dev.jason.gboardpatches.extension.closeproactivesuggestions;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.flagsettings.GboardBooleanFlagSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardCloseProactiveSuggestionsSettingsFeature
        extends GboardBooleanFlagSettingsFeature {
    private static final String PREVIEW_IMAGE =
            "settings-previews/closeproactivesuggestions/"
                    + "01_close_proactive_suggestions.png";

    public GboardCloseProactiveSuggestionsSettingsFeature(Context context) {
        super(
                GboardPatchesFeatureAvailability.FEATURE_CLOSE_PROACTIVE_SUGGESTIONS,
                text(context, R.string.gboard_patches_close_proactive_suggestions_title),
                "",
                text(context, R.string.gboard_patches_close_proactive_suggestions_title),
                text(context, R.string.gboard_patches_header_badge),
                text(context,
                        R.string.gboard_patches_close_proactive_suggestions_error_title),
                text(context,
                        R.string.gboard_patches_close_proactive_suggestions_error_summary),
                text(context,
                        R.string.gboard_patches_close_proactive_suggestions_section_feature),
                new GboardPatchesSettingsContract.PreviewSpec(
                        text(context,
                                R.string.gboard_patches_close_proactive_suggestions_title),
                        "",
                        GboardPatchesSettingsContract.PreviewLayout.STACKED,
                        new GboardPatchesSettingsContract.PreviewImage(PREVIEW_IMAGE, "")),
                new SettingsStore() {
                    @Override
                    public void ensureDefault(SharedPreferences preferences) {
                        GboardCloseProactiveSuggestionsSettings.ensureDefault(preferences);
                    }

                    @Override
                    public boolean readEnabled(SharedPreferences preferences) {
                        return GboardCloseProactiveSuggestionsSettings.readEnabled(preferences);
                    }

                    @Override
                    public boolean writeEnabled(
                            SharedPreferences preferences,
                            boolean enabled) {
                        return GboardCloseProactiveSuggestionsSettings
                                .writeEnabled(preferences, enabled);
                    }
                });
        declareRestartRequired(GboardCloseProactiveSuggestionsSettings.PREF_KEY_ENABLED);
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
