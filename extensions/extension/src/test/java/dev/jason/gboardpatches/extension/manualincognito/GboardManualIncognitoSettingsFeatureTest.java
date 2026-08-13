package dev.jason.gboardpatches.extension.manualincognito;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardManualIncognitoSettingsFeatureTest {
    @Test
    public void screenKeepsMasterPreviewAndDisablesChildrenWhenMasterIsOff() {
        GboardPatchesSettingsContract.Screen screen = feature().buildScreenForState(
                new GboardManualIncognitoSettings.RequestedPolicy(
                        false, false, true, true),
                value -> { },
                value -> { },
                value -> { });

        Assert.assertEquals("Incognito mode", screen.getToolbarTitle());
        Assert.assertEquals(1, screen.getSections().size());
        Assert.assertEquals("Behavior", screen.getSections().get(0).getTitle());
        Assert.assertEquals(3, screen.getSections().get(0).getItems().size());

        GboardPatchesSettingsContract.ToggleRow master = toggle(screen, 0);
        GboardPatchesSettingsContract.ToggleRow clipboard = toggle(screen, 1);
        GboardPatchesSettingsContract.ToggleRow voice = toggle(screen, 2);
        Assert.assertTrue(master.isEnabled());
        Assert.assertFalse(master.isChecked());
        Assert.assertFalse(clipboard.isEnabled());
        Assert.assertFalse(voice.isEnabled());
        Assert.assertTrue(clipboard.isChecked());
        Assert.assertTrue(voice.isChecked());

        GboardPatchesSettingsContract.PreviewSpec preview = master.getPreviewSpec();
        Assert.assertNotNull(preview);
        Assert.assertEquals(1, preview.getMediaItems().size());
        Assert.assertTrue(preview.getMediaItems().get(0)
                instanceof GboardPatchesSettingsContract.PreviewImage);
        Assert.assertEquals(
                "settings-previews/manualincognito/access_point.png",
                ((GboardPatchesSettingsContract.PreviewImage) preview.getMediaItems().get(0))
                        .getAssetPath());
    }

    @Test
    public void toggleActionsForwardExactValues() {
        AtomicReference<Boolean> master = new AtomicReference<>();
        AtomicReference<Boolean> clipboard = new AtomicReference<>();
        AtomicReference<Boolean> voice = new AtomicReference<>();
        GboardPatchesSettingsContract.Screen screen = feature().buildScreenForState(
                new GboardManualIncognitoSettings.RequestedPolicy(
                        true, false, false, false),
                master::set,
                clipboard::set,
                voice::set);

        toggle(screen, 0).getToggleAction().accept(false);
        toggle(screen, 1).getToggleAction().accept(true);
        toggle(screen, 2).getToggleAction().accept(true);

        Assert.assertEquals(Boolean.FALSE, master.get());
        Assert.assertEquals(Boolean.TRUE, clipboard.get());
        Assert.assertEquals(Boolean.TRUE, voice.get());
    }

    @Test
    public void previewAssetExists() {
        Assert.assertTrue(Files.exists(Path.of(
                "..", "..", "patches", "src", "main", "resources",
                "settings-previews", "manualincognito", "access_point.png")));
    }

    private static GboardPatchesSettingsContract.ToggleRow toggle(
            GboardPatchesSettingsContract.Screen screen, int index) {
        return (GboardPatchesSettingsContract.ToggleRow)
                screen.getSections().get(0).getItems().get(index);
    }

    private static GboardManualIncognitoSettingsFeature feature() {
        return new GboardManualIncognitoSettingsFeature(
                "Incognito mode",
                "Configure manual incognito.",
                "Gboard",
                "Behavior",
                "Add Incognito button",
                "Adds a toolbar button.",
                "Enable clipboard in incognito",
                "Allow clipboard.",
                "Enable voice typing in incognito",
                "Allow voice.",
                "Incognito settings unavailable",
                "Try again.");
    }
}
