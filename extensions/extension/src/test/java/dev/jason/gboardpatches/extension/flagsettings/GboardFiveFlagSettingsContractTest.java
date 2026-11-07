package dev.jason.gboardpatches.extension.flagsettings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

import dev.jason.gboardpatches.extension.accessibilitylayout.GboardAccessibilityLayoutSettings;
import dev.jason.gboardpatches.extension.accessibilitylayout.GboardAccessibilityLayoutSettingsFeature;
import dev.jason.gboardpatches.extension.accesspointsmenu.GboardAccessPointsMenuSettings;
import dev.jason.gboardpatches.extension.accesspointsmenu.GboardAccessPointsMenuSettingsFeature;
import dev.jason.gboardpatches.extension.cursortrackpad.GboardCursorTrackpadSettings;
import dev.jason.gboardpatches.extension.cursortrackpad.GboardCursorTrackpadSettingsFeature;
import dev.jason.gboardpatches.extension.emojisize.GboardEmojiSizeSettings;
import dev.jason.gboardpatches.extension.emojisize.GboardEmojiSizeSettingsFeature;
import dev.jason.gboardpatches.extension.keyboard.GboardKeyboardLayoutSettingsGroupFeature;
import dev.jason.gboardpatches.extension.keyboard.GboardKeyboardToolsSettingsGroupFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;
import dev.jason.gboardpatches.extension.splitkeyboard.GboardSplitKeyboardSettings;
import dev.jason.gboardpatches.extension.splitkeyboard.GboardSplitKeyboardSettingsFeature;

@RunWith(RobolectricTestRunner.class)
public final class GboardFiveFlagSettingsContractTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
                GboardPatchesSettings.PREF_FILE,
                Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
    }

    @Test
    public void defaultsAndPreferenceKeysMatchTheAcceptedContract() {
        Assert.assertEquals("pref_force_emoji_scale_supported",
                GboardEmojiSizeSettings.PREF_KEY_ENABLED);
        Assert.assertTrue(GboardEmojiSizeSettings.DEFAULT_ENABLED);
        Assert.assertTrue(GboardEmojiSizeSettings.readEnabled(preferences));

        Assert.assertEquals("pref_force_cursor_trackpad_mode",
                GboardCursorTrackpadSettings.PREF_KEY_ENABLED);
        Assert.assertFalse(GboardCursorTrackpadSettings.DEFAULT_ENABLED);
        Assert.assertFalse(GboardCursorTrackpadSettings.readEnabled(preferences));

        Assert.assertEquals("pref_access_points_menu_redesign_enabled",
                GboardAccessPointsMenuSettings.PREF_KEY_ENABLED);
        Assert.assertTrue(GboardAccessPointsMenuSettings.DEFAULT_ENABLED);
        Assert.assertTrue(GboardAccessPointsMenuSettings.readEnabled(preferences));

        Assert.assertEquals("pref_force_split_keyboard_enabled",
                GboardSplitKeyboardSettings.PREF_KEY_ENABLED);
        Assert.assertTrue(GboardSplitKeyboardSettings.DEFAULT_ENABLED);
        Assert.assertTrue(GboardSplitKeyboardSettings.readEnabled(preferences));
        Assert.assertEquals("pref_auto_split_keyboard_enabled",
                GboardSplitKeyboardSettings.PREF_KEY_AUTO_SPLIT_ENABLED);
        Assert.assertFalse(GboardSplitKeyboardSettings.DEFAULT_AUTO_SPLIT_ENABLED);
        Assert.assertFalse(GboardSplitKeyboardSettings.readAutoSplitEnabled(preferences));

        Assert.assertEquals("pref_show_accessibility_layout",
                GboardAccessibilityLayoutSettings.PREF_KEY_ENABLED);
        Assert.assertTrue(GboardAccessibilityLayoutSettings.DEFAULT_ENABLED);
        Assert.assertTrue(GboardAccessibilityLayoutSettings.readEnabled(preferences));
    }

    @Test
    public void previewsKeepSuppliedNumberOrderAndStackedLayout() {
        assertPreview(
                new GboardCursorTrackpadSettingsFeature(null),
                "Enable cursor trackpad mode",
                "settings-previews/cursortrackpad/01_cursor_trackpad_mode.png",
                "settings-previews/cursortrackpad/02_cursor_trackpad_setting.jpg");
        assertPreview(
                new GboardAccessPointsMenuSettingsFeature(null),
                "Enable new Access Points menu style",
                "settings-previews/accesspointsmenu/03_access_points_legacy.png",
                "settings-previews/accesspointsmenu/04_access_points_redesigned.png");
        assertPreview(
                new GboardEmojiSizeSettingsFeature(null),
                "Change emoji size",
                "settings-previews/emojisize/05_emoji_size_setting.jpg");
        assertPreview(
                new GboardAccessibilityLayoutSettingsFeature(null),
                "Show accessibility layout",
                "settings-previews/accessibilitylayout/06_accessibility_keyboard.png",
                "settings-previews/accessibilitylayout/07_accessibility_layout_setting.jpg");
        GboardPatchesSettingsContract.Screen splitScreen =
                new GboardSplitKeyboardSettingsFeature(null).buildScreenForState(
                        true,
                        false,
                        enabled -> {
                        },
                        enabled -> {
                        });
        assertPreview(
                (GboardPatchesSettingsContract.ToggleRow) splitScreen.getRows().get(0),
                "Enable split keyboard",
                "settings-previews/splitkeyboard/08_split_keyboard.png");
    }

    @Test
    public void splitKeyboardScreenKeepsAutoSplitIndependentAndMasterGated() {
        GboardSplitKeyboardSettingsFeature feature =
                new GboardSplitKeyboardSettingsFeature(null);
        GboardPatchesSettingsContract.Screen enabled = feature.buildScreenForState(
                true,
                false,
                value -> {
                },
                value -> {
                });
        Assert.assertEquals(2, enabled.getRows().size());
        GboardPatchesSettingsContract.ToggleRow master =
                (GboardPatchesSettingsContract.ToggleRow) enabled.getRows().get(0);
        GboardPatchesSettingsContract.ToggleRow automatic =
                (GboardPatchesSettingsContract.ToggleRow) enabled.getRows().get(1);
        Assert.assertEquals("Enable split keyboard", master.getTitle());
        Assert.assertTrue(master.isChecked());
        Assert.assertEquals("Auto split", automatic.getTitle());
        Assert.assertFalse(automatic.isChecked());
        Assert.assertTrue(automatic.isEnabled());

        GboardPatchesSettingsContract.Screen disabled = feature.buildScreenForState(
                false,
                true,
                value -> {
                },
                value -> {
                });
        GboardPatchesSettingsContract.ToggleRow disabledAutomatic =
                (GboardPatchesSettingsContract.ToggleRow) disabled.getRows().get(1);
        Assert.assertTrue(disabledAutomatic.isChecked());
        Assert.assertFalse(disabledAutomatic.isEnabled());
    }

    @Test
    public void selectedSplitKeyboardPatchMakesItsUiEntryAvailable() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle markers = new Bundle();
        markers.putBoolean(GboardPatchesFeatureAvailability.FEATURE_SPLIT_KEYBOARD, true);
        context.getApplicationInfo().metaData = markers;

        try {
            GboardSplitKeyboardSettingsFeature feature =
                    new GboardSplitKeyboardSettingsFeature(context);
            Assert.assertTrue(feature.isAvailable(context));

            GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost(context);
            List<String> visibleTitles = new GboardKeyboardLayoutSettingsGroupFeature(context)
                    .buildScreen(host)
                    .getRows()
                    .stream()
                    .map(row -> row.getTitle().toString())
                    .collect(java.util.stream.Collectors.toList());
            Assert.assertTrue(visibleTitles.contains(feature.getEntryTitle()));
        } finally {
            context.getApplicationInfo().metaData = new Bundle();
        }
    }

    @Test
    public void unselectedFiveFlagPatchesDoNotAppearInPatchesUi() {
        Context context = RuntimeEnvironment.getApplication();
        Bundle markers = new Bundle();
        markers.putBoolean(GboardPatchesFeatureAvailability.FEATURE_TOP_ROW_SWIPE, true);
        markers.putBoolean(
                GboardPatchesFeatureAvailability.FEATURE_LATIN_GLOBE_KEY_IGNORE_INTERVAL,
                true);
        context.getApplicationInfo().metaData = markers;

        try {
            List<GboardPatchesSettingsContract.Feature> fiveFlagFeatures = Arrays.asList(
                    new GboardEmojiSizeSettingsFeature(context),
                    new GboardCursorTrackpadSettingsFeature(context),
                    new GboardAccessPointsMenuSettingsFeature(context),
                    new GboardSplitKeyboardSettingsFeature(context),
                    new GboardAccessibilityLayoutSettingsFeature(context));
            for (GboardPatchesSettingsContract.Feature feature : fiveFlagFeatures) {
                Assert.assertFalse(feature.isAvailable(context));
            }

            GboardPatchesSettingsTestHost host = new GboardPatchesSettingsTestHost(context);
            List<String> visibleTitles = Arrays.asList(
                    new GboardKeyboardToolsSettingsGroupFeature(context).buildScreen(host),
                    new GboardKeyboardLayoutSettingsGroupFeature(context).buildScreen(host))
                    .stream()
                    .flatMap(screen -> screen.getRows().stream())
                    .map(row -> row.getTitle().toString())
                    .collect(java.util.stream.Collectors.toList());

            for (GboardPatchesSettingsContract.Feature feature : fiveFlagFeatures) {
                Assert.assertFalse(visibleTitles.contains(feature.getEntryTitle()));
            }
        } finally {
            context.getApplicationInfo().metaData = new Bundle();
        }
    }

    private static void assertPreview(
            GboardBooleanFlagSettingsFeature feature,
            String expectedToggleTitle,
            String... expectedPaths) {
        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                true,
                enabled -> {
                });
        List<GboardPatchesSettingsContract.Row> rows = screen.getRows();
        Assert.assertEquals(1, rows.size());
        Assert.assertTrue(rows.get(0) instanceof GboardPatchesSettingsContract.ToggleRow);
        GboardPatchesSettingsContract.ToggleRow toggle =
                (GboardPatchesSettingsContract.ToggleRow) rows.get(0);
        Assert.assertEquals(expectedToggleTitle, toggle.getTitle());
        assertPreview(toggle, expectedToggleTitle, expectedPaths);
    }

    private static void assertPreview(
            GboardPatchesSettingsContract.ToggleRow toggle,
            String expectedToggleTitle,
            String... expectedPaths) {
        Assert.assertEquals(expectedToggleTitle, toggle.getTitle());
        GboardPatchesSettingsContract.PreviewSpec preview = toggle.getPreviewSpec();
        Assert.assertNotNull(preview);
        Assert.assertEquals(GboardPatchesSettingsContract.PreviewLayout.STACKED,
                preview.getLayout());
        Assert.assertEquals(expectedPaths.length, preview.getMediaItems().size());
        for (int index = 0; index < expectedPaths.length; index++) {
            Assert.assertTrue(preview.getMediaItems().get(index)
                    instanceof GboardPatchesSettingsContract.PreviewImage);
            Assert.assertEquals(expectedPaths[index],
                    ((GboardPatchesSettingsContract.PreviewImage)
                            preview.getMediaItems().get(index)).getAssetPath());
        }
    }
}
