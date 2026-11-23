package dev.jason.gboardpatches.extension.accesspointcount;

import dev.jason.gboardpatches.extension.settings.GboardSettingsTextFiles;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardAccessPointCountSettingsFeatureTest {
    @Test
    public void screenMatchesTheRequestedTwoRowsAndCaptionlessPreview() {
        GboardAccessPointCountSettingsFeature feature = testFeature();
        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                null,
                new GboardAccessPointCountSettings.Snapshot(false, 6),
                value -> { },
                value -> true);

        Assert.assertEquals("Top Toolbar Item Count", feature.getEntryTitle());
        Assert.assertEquals("", feature.getEntrySummary());
        Assert.assertEquals(GboardPatchesSettingsContract.PanelStyle.FLAT,
                screen.getPanelStyle());
        List<GboardPatchesSettingsContract.Row> rows = screen.getRows();
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(rows.get(0) instanceof GboardPatchesSettingsContract.ToggleRow);
        Assert.assertTrue(rows.get(1) instanceof GboardPatchesSettingsContract.SelectorRow);

        GboardPatchesSettingsContract.ToggleRow toggle =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(0);
        Assert.assertEquals("Enable", toggle.getTitle());
        Assert.assertEquals("Force-stop and restart Gboard for the change to take effect.",
                toggle.getSummary());
        Assert.assertFalse(toggle.isChecked());

        GboardPatchesSettingsContract.SelectorRow count =
                (GboardPatchesSettingsContract.SelectorRow) rows.get(1);
        Assert.assertEquals("Count", count.getTitle());
        Assert.assertEquals("6", count.getCurrentValue());
        Assert.assertTrue(count.isEnabled());

        GboardPatchesSettingsContract.PreviewSpec preview = toggle.getPreviewSpec();
        Assert.assertNotNull(preview);
        Assert.assertEquals("", preview.getMessage());
        Assert.assertEquals(1, preview.getMediaItems().size());
        GboardPatchesSettingsContract.PreviewImage image =
                (GboardPatchesSettingsContract.PreviewImage) preview.getMediaItems().get(0);
        Assert.assertEquals("settings-previews/accesspointcount/enabled.png",
                image.getAssetPath());
        Assert.assertEquals("", image.getCaption());
        Assert.assertTrue(Files.exists(Path.of(
                "..", "..", "patches", "src", "main", "resources",
                image.getAssetPath())));
    }

    @Test
    public void countValidatorAcceptsOnlyThreeThroughEight() {
        GboardAccessPointCountSettingsFeature feature = testFeature();

        Assert.assertEquals(3, feature.validateCount(3));
        Assert.assertEquals(6, feature.validateCount(6));
        Assert.assertEquals(8, feature.validateCount(8));
        assertRejected(feature, 2);
        assertRejected(feature, 9);
    }

    @Test
    public void settingsTextMatchesTheRequestedEnglishAndTraditionalChineseCopy()
            throws Exception {
        String source = GboardSettingsTextFiles.englishAndTraditionalChinese();

        Assert.assertTrue(source.contains(
                ">頂端工具列項目數量</string>"));
        Assert.assertTrue(source.contains(
                ">"
                        + "需要強制停止並重新啟動 Gboard 才會生效。</string>"));
        Assert.assertTrue(source.contains(
                ">數量</string>"));
        Assert.assertTrue(source.contains(
                ">Top Toolbar Item Count</string>"));
        Assert.assertTrue(source.contains(
                ">Count</string>"));
    }

    private static void assertRejected(GboardAccessPointCountSettingsFeature feature,
            int value) {
        try {
            feature.validateCount(value);
            Assert.fail("Expected invalid count: " + value);
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("Count error", expected.getMessage());
        }
    }

    private static GboardAccessPointCountSettingsFeature testFeature() {
        return new GboardAccessPointCountSettingsFeature(
                "Top Toolbar Item Count",
                "",
                "Gboard",
                "Error",
                "Error summary",
                "Behavior",
                "Enable",
                "Force-stop and restart Gboard for the change to take effect.",
                "Count",
                "Count summary",
                "3-8",
                "Count error");
    }
}
