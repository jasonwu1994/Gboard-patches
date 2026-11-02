package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import android.util.Log;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;
import dev.jason.gboardpatches.extension.keypresshaptics.GboardKeypressHapticsSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardKeypressHapticsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private final String title;
    private final String summary;
    private final String entrySummary;
    private final String badge;
    private final String section;
    private final String errorTitle;
    private final String errorSummary;

    public GboardKeypressHapticsSettingsFeature(Context context) {
        title = text(context, R.string.gboard_patches_keypress_haptics_title);
        summary = text(context, R.string.gboard_patches_keypress_haptics_summary);
        entrySummary = text(context, R.string.gboard_patches_keypress_haptics_entry_summary);
        badge = text(context, R.string.gboard_patches_header_badge);
        section = text(context, R.string.gboard_patches_flag_patch_section_feature);
        errorTitle = text(context, R.string.gboard_patches_flag_patch_error_title);
        errorSummary = text(context, R.string.gboard_patches_flag_patch_error_summary);
    }

    @Override public String getEntryTitle() { return title; }
    @Override public String getEntrySummary() { return entrySummary; }
    @Override public int getEntryIcon() { return GboardPatchesIcons.BOLT; }
    @Override public String getEntryTone() { return "teal"; }

    @Override public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context,
                GboardPatchesFeatureAvailability.FEATURE_KEYPRESS_HAPTICS);
    }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            Context context = host == null ? null : host.getContext();
            if (context == null) return unavailableScreen();
            Boolean setting = GboardPatchesSettings.readKeypressHapticsEnabled(context);
            Boolean supported = GboardKeypressHapticsSupport.read(context);
            String support = text(context, supported == null
                    ? R.string.gboard_patches_keypress_haptics_support_unknown
                    : supported ? R.string.gboard_patches_keypress_haptics_supported
                                : R.string.gboard_patches_keypress_haptics_unsupported);
            return new GboardPatchesSettingsContract.Screen(title, badge, title, summary,
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(section,
                            Collections.singletonList(new GboardPatchesSettingsContract.ToggleRow(
                                    title, support, setting != null, Boolean.TRUE.equals(setting),
                                    value -> {
                                        if (!GboardPatchesSettings.writeKeypressHapticsEnabled(context, value)) {
                                            GboardPatchesSettingsContract.refresh(host);
                                        }
                                    }).setRestartRequired(GboardKeypressHapticsSettings.PREF_KEY_ENABLED)))),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable unavailable) {
            try { Log.w("GboardPatches", "Failed to render keypress haptics settings", unavailable); }
            catch (Throwable ignored) { /* The unavailable screen remains the fallback. */ }
            return unavailableScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen unavailableScreen() {
        return new GboardPatchesSettingsContract.Screen(title, badge, title, summary,
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle, errorSummary, GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int id) {
        return GboardSettingsText.get(context, id);
    }
}
