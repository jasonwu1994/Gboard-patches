package dev.jason.gboardpatches.extension.developeroptions;

import android.content.Context;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardDeveloperOptionsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final int TARGET_HEADER_KEY_RESOURCE_ID = 0x7f140a6d;

    private final String entryTitle;
    private final String entrySummary;

    public GboardDeveloperOptionsSettingsFeature(Context context) {
        this(
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_developer_options_title),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_developer_options_summary));
    }

    GboardDeveloperOptionsSettingsFeature(String entryTitle, String entrySummary) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
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
    public int getEntryIcon() {
        return GboardPatchesIcons.CODE;
    }

    @Override
    public String getEntryTone() {
        return "red";
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_DEVELOPER_OPTIONS);
    }

    @Override
    public void openRootEntry(GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.openTargetSettingsHeader(host, TARGET_HEADER_KEY_RESOURCE_ID);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                "Gboard",
                entryTitle,
                entrySummary,
                Collections.emptyList());
    }
}
