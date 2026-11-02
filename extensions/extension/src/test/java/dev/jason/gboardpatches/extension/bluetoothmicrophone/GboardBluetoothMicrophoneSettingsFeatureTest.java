package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardBluetoothMicrophoneSettingsFeatureTest {
    @Test
    public void flatScreenShowsPersistedOnToggleWithExactCopy() {
        GboardBluetoothMicrophoneSettingsFeature feature =
                new GboardBluetoothMicrophoneSettingsFeature(
                        "Use Bluetooth microphone",
                        "Voice typing -> Use Bluetooth microphone",
                        "Gboard",
                        "Bluetooth microphone unavailable",
                        "Unable to load settings.",
                        "Behavior");

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                true,
                value -> {
                });

        Assert.assertEquals("Use Bluetooth microphone", feature.getEntryTitle());
        Assert.assertEquals(
                "Voice typing -> Use Bluetooth microphone",
                feature.getEntrySummary());
        Assert.assertEquals("Use Bluetooth microphone", screen.getToolbarTitle());
        Assert.assertEquals("Use Bluetooth microphone", screen.getHeaderTitle());
        Assert.assertEquals("", screen.getHeaderSummary());
        Assert.assertEquals(
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                screen.getPanelStyle());
        Assert.assertEquals(1, screen.getSections().size());

        List<GboardPatchesSettingsContract.Row> rows = screen.getRows();
        Assert.assertEquals(1, rows.size());
        Assert.assertTrue(rows.get(0) instanceof GboardPatchesSettingsContract.ToggleRow);
        GboardPatchesSettingsContract.ToggleRow toggleRow =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(0);
        Assert.assertEquals("Use Bluetooth microphone", toggleRow.getTitle());
        Assert.assertEquals(
                "Voice typing -> Use Bluetooth microphone",
                toggleRow.getSummary());
        Assert.assertTrue(toggleRow.isChecked());
        Assert.assertTrue(toggleRow.isEnabled());
    }
}
