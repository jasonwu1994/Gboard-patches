package dev.jason.gboardpatches.extension.modevariant;

import android.content.Context;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Focused UI entry under Patches > Keyboard Layout; no changes to Settings Activity. */
public final class GboardCoverResizeSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private final String title;
    private final String summary;
    private final String toggleTitle;
    private final String toggleSummary;
    private final String sectionTitle;
    private final String errorSummary;

    public GboardCoverResizeSettingsFeature(Context context) {
        title = GboardSettingsText.get(context, R.string.gboard_patches_cover_resize_title);
        summary = GboardSettingsText.get(context, R.string.gboard_patches_cover_resize_summary);
        toggleTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_cover_resize_toggle_title);
        toggleSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_cover_resize_toggle_summary);
        sectionTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_cover_resize_section);
        errorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_cover_resize_error_summary);
    }

    @Override public String getEntryTitle() {
        return title;
    }

    @Override public String getEntrySummary() {
        return summary;
    }

    @Override public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context,
                GboardPatchesFeatureAvailability.FEATURE_COVER_RESIZE_PROFILES);
    }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        Context context = host == null ? null : host.getContext();
        if (context == null) {
            return new GboardPatchesSettingsContract.Screen(
                    title, "Gboard", title, "",
                    Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                            title, errorSummary,
                            GboardPatchesSettingsContract.StatusTone.WARNING)),
                    Collections.emptyList());
        }
        return buildScreenForState(GboardCoverResizeSettings.isEnabled(context), enabled -> {
            if (!GboardCoverResizeSettings.writeEnabled(context, enabled)) {
                GboardPatchesSettingsContract.showMessage(host, errorSummary);
            }
            GboardPatchesSettingsContract.refresh(host);
        });
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggle) {
        return new GboardPatchesSettingsContract.Screen(
                title, "Gboard", title, summary, Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionTitle,
                        Collections.singletonList(new GboardPatchesSettingsContract.ToggleRow(
                                toggleTitle, toggleSummary, true, enabled, toggle)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }
}