package dev.jason.gboardpatches.extension.hideaccentpopups;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardHideAccentPopupsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private final String headerBadge;
    private final String entryTitle;
    private final String entrySummary;
    private final String errorTitle;
    private final String errorSummary;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String sectionBehavior;

    public GboardHideAccentPopupsSettingsFeature(Context context) {
        headerBadge = GboardSettingsText.get(context, R.string.gboard_patches_header_badge);
        entryTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_title);
        entrySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_summary);
        errorTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_error_title);
        errorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_error_summary);
        enabledTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_enabled_title);
        enabledSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_enabled_summary);
        sectionBehavior = GboardSettingsText.get(context,
                R.string.gboard_patches_hide_accent_popups_section_behavior);
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_HIDE_ACCENT_POPUPS);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            SharedPreferences preferences =
                    GboardHideAccentPopupsSettings.preferences(host.getContext());
            GboardHideAccentPopupsSettings.ensureDefaults(preferences);

            boolean enabled = GboardHideAccentPopupsSettings.readEnabled(preferences);

            List<GboardPatchesSettingsContract.Row> behaviorRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            behaviorRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    enabledTitle,
                    enabledSummary,
                    true,
                    enabled,
                    value -> GboardHideAccentPopupsSettings.writeEnabled(
                            preferences,
                            value)));

            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    "",
                    Collections.emptyList(),
                    Collections.singletonList(new GboardPatchesSettingsContract.Section(
                            sectionBehavior,
                            behaviorRows)),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render hide accent popups settings screen", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                errorTitle,
                errorSummary,
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                statusBlocks,
                Collections.emptyList());
    }
}
