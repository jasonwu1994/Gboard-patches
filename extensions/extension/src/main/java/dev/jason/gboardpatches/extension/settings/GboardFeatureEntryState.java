package dev.jason.gboardpatches.extension.settings;

import android.util.Log;

import java.util.List;

public final class GboardFeatureEntryState {
    private static final String TAG = "GboardPatches";

    private final GboardPatchesSettingsContract.ToggleRow masterToggle;

    private GboardFeatureEntryState(GboardPatchesSettingsContract.ToggleRow masterToggle) {
        this.masterToggle = masterToggle;
    }

    public GboardPatchesSettingsContract.ToggleRow getMasterToggle() {
        return masterToggle;
    }

    public static GboardFeatureEntryState inspect(GboardPatchesSettingsContract.Feature feature,
            GboardPatchesSettingsContract.FeatureHost host) {
        if (feature == null) {
            return new GboardFeatureEntryState(null);
        }
        try {
            GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);
            return new GboardFeatureEntryState(masterToggle(screen));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to inspect settings entry state: "
                    + feature.getClass().getName(), throwable);
            return new GboardFeatureEntryState(null);
        }
    }

    public static GboardPatchesSettingsContract.ToggleRow masterToggle(
            GboardPatchesSettingsContract.Screen screen) {
        if (screen == null || screen.getSections().isEmpty()) {
            return null;
        }
        List<GboardPatchesSettingsContract.Row> items = screen.getSections().get(0).getItems();
        if (items.isEmpty()) {
            return null;
        }
        GboardPatchesSettingsContract.Row row = items.get(0);
        if (row.isMasterToggleExplicit() != null && !row.isMasterToggleExplicit()) {
            return null;
        }
        if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow) {
            return toggleRow;
        }
        if (row instanceof GboardPatchesSettingsContract.SwitchRow switchRow) {
            GboardPatchesSettingsContract.ToggleRow converted =
                    new GboardPatchesSettingsContract.ToggleRow(
                            switchRow.getTitle(),
                            switchRow.getSummary(),
                            switchRow.isEnabled(),
                            switchRow.isChecked(),
                            switchRow.getToggleAction(),
                            switchRow.getPreviewSpec());
            converted.setRestartRequired(switchRow.getRestartKey());
            if (switchRow.isMasterToggleExplicit() != null) {
                converted.setMasterToggle(switchRow.isMasterToggleExplicit());
            }
            return converted;
        }
        return null;
    }
}
