package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardLongPressQuickActionsRuntimeSettingsTest {
    @Test
    public void localSnapshotHonorsDisabledValue() {
        TestSharedPreferences preferences = new TestSharedPreferences();
        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED,
                "false");

        GboardLongPressQuickActionsRuntimeSettings.Snapshot snapshot =
                GboardLongPressQuickActionsRuntimeSettings.snapshotFromPreferences(
                        preferences,
                        123L);

        Assert.assertEquals(123L, snapshot.loadedAtElapsedMs);
        Assert.assertFalse(snapshot.enabled);
        Assert.assertFalse(snapshot.globeDragEnabled);
        Assert.assertEquals("local", snapshot.source);
    }

    @Test
    public void nullSnapshotFailsDisabled() {
        GboardLongPressQuickActionsRuntimeSettings.Snapshot snapshot =
                GboardLongPressQuickActionsRuntimeSettings.snapshotFromPreferences(
                        null,
                        456L);

        Assert.assertEquals(456L, snapshot.loadedAtElapsedMs);
        Assert.assertFalse(snapshot.enabled);
        Assert.assertFalse(snapshot.globeDragEnabled);
        Assert.assertEquals("unavailable", snapshot.source);
    }

    @Test
    public void publicAccessorFailsDisabledWhenRuntimeSettingsAreUnavailable() {
        GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        try {
            Assert.assertFalse(GboardLongPressQuickActionsRuntimeSettings.isEnabled());
        } finally {
            GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        }
    }

    @Test
    public void selectAllSnapshotDefaultsOffAndParentGateIsRequired() {
        TestSharedPreferences preferences = new TestSharedPreferences();
        Assert.assertFalse(GboardLongPressQuickActionsRuntimeSettings
                .snapshotFromPreferences(preferences, 1L).selectAllWhenUnselected);
        preferences.values.put(GboardLongPressQuickActionsSettings.PREF_KEY_SELECT_ALL_WHEN_UNSELECTED, true);
        Assert.assertTrue(GboardLongPressQuickActionsRuntimeSettings
                .snapshotFromPreferences(preferences, 2L).selectAllWhenUnselected);
        try {
            GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(false, true, true);
            Assert.assertFalse(GboardLongPressQuickActionsRuntimeSettings.isSelectAllWhenUnselectedEnabled());
            GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, false, true);
            Assert.assertTrue(GboardLongPressQuickActionsRuntimeSettings.isSelectAllWhenUnselectedEnabled());
        } finally {
            GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        }
    }

    @Test
    public void publicAccessorHonorsTestOverride() {
        GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        try {
            GboardLongPressQuickActionsRuntimeSettings.setEnabledOverrideForTest(false);
            Assert.assertFalse(GboardLongPressQuickActionsRuntimeSettings.isEnabled());

            GboardLongPressQuickActionsRuntimeSettings.setEnabledOverrideForTest(true);
            Assert.assertTrue(GboardLongPressQuickActionsRuntimeSettings.isEnabled());
            Assert.assertTrue(GboardLongPressQuickActionsRuntimeSettings
                    .isGlobeDragEnabled());

            GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, false);
            Assert.assertTrue(GboardLongPressQuickActionsRuntimeSettings.isEnabled());
            Assert.assertFalse(GboardLongPressQuickActionsRuntimeSettings
                    .isGlobeDragEnabled());
        } finally {
            GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        }
    }
}
