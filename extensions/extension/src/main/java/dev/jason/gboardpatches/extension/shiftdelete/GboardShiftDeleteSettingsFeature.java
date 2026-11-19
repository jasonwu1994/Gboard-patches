package dev.jason.gboardpatches.extension.shiftdelete;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardShiftDeleteSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private final Context context;

    public GboardShiftDeleteSettingsFeature(Context context) {
        this.context = context;
    }

    @Override public String getEntryTitle() {
        return text(R.string.gboard_patches_shift_delete_title);
    }

    @Override public String getEntrySummary() {
        return text(R.string.gboard_patches_shift_delete_entry_summary);
    }

    @Override public boolean isAvailable(Context host) {
        return GboardPatchesFeatureAvailability.hasFeature(host,
                GboardPatchesFeatureAvailability.FEATURE_SHIFT_FORWARD_DELETE);
    }

    @Override public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            return buildSettingsScreen(host);
        } catch (Throwable failure) {
            try {
                Log.w("GboardPatches", "Failed to render Shift forward delete settings", failure);
            } catch (Throwable ignored) {
                // The warning screen below is still returned.
            }
            return new GboardPatchesSettingsContract.Screen(
                    getEntryTitle(), text(R.string.gboard_patches_header_badge), getEntryTitle(),
                    text(R.string.gboard_patches_shift_delete_summary),
                    List.of(new GboardPatchesSettingsContract.StatusBlock(
                            text(R.string.gboard_patches_flag_patch_error_title),
                            text(R.string.gboard_patches_flag_patch_error_summary),
                            GboardPatchesSettingsContract.StatusTone.WARNING)), List.of());
        }
    }

    private GboardPatchesSettingsContract.Screen buildSettingsScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        Context owner = host.getContext();
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(owner);
        var toggle = new GboardPatchesSettingsContract.ToggleRow(
                getEntryTitle(), "", preferences != null,
                GboardShiftDeleteSettings.enabled(preferences),
                value -> write(preferences, value))
                .setRestartRequired(GboardShiftDeleteSettings.ENABLED)
                .setMasterToggle(true);
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), text(R.string.gboard_patches_header_badge), getEntryTitle(),
                text(R.string.gboard_patches_shift_delete_summary), List.of(),
                List.of(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_flag_patch_section_feature), List.of(toggle))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    @SuppressLint("ApplySharedPref")
    private static void write(SharedPreferences preferences, boolean value) {
        if (preferences == null
                || !preferences.edit().putBoolean(GboardShiftDeleteSettings.ENABLED, value).commit()) {
            throw new IllegalStateException("Unable to save Shift forward delete");
        }
    }

    private String text(int id) {
        return GboardSettingsText.get(context, id);
    }
}
