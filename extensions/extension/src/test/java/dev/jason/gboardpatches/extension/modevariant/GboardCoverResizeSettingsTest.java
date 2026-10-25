package dev.jason.gboardpatches.extension.modevariant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardCoverResizeSettingsTest {
    @Test public void absentPreferenceIsOffAndUserCanToggleBothWays() {
        Context app = RuntimeEnvironment.getApplication();
        SharedPreferences p = GboardPatchesSettings.preferences(app);
        p.edit().remove(GboardCoverResizeSettings.KEY_ENABLED).commit();
        try {
            assertFalse(GboardCoverResizeSettings.isEnabled(app));
            assertTrue(GboardCoverResizeSettings.writeEnabled(app, true));
            assertTrue(GboardCoverResizeSettings.isEnabled(app));
            assertTrue(GboardCoverResizeSettings.writeEnabled(app, false));
            assertFalse(GboardCoverResizeSettings.isEnabled(app));
        } finally {
            p.edit().remove(GboardCoverResizeSettings.KEY_ENABLED).commit();
        }
    }

    @Test public void malformedFlagAndMissingContextAreSafeOff() {
        Context app = RuntimeEnvironment.getApplication();
        SharedPreferences p = GboardPatchesSettings.preferences(app);
        p.edit().putString(GboardCoverResizeSettings.KEY_ENABLED, "true").commit();
        try {
            assertFalse(GboardCoverResizeSettings.isEnabled(app));
            assertFalse(GboardCoverResizeSettings.isEnabled(null));
            assertFalse(GboardCoverResizeSettings.writeEnabled(null, true));
        } finally {
            p.edit().remove(GboardCoverResizeSettings.KEY_ENABLED).commit();
        }
    }

    @Test public void keyboardLayoutFeatureIsRealToggleWithControllableAction() {
        Context app = RuntimeEnvironment.getApplication();
        GboardCoverResizeSettingsFeature ui = new GboardCoverResizeSettingsFeature(app);
        final boolean[] requested = {false};
        GboardPatchesSettingsContract.Screen screen = ui.buildScreenForState(
                false, value -> requested[0] = value);
        assertEquals(1, screen.getSections().size());
        GboardPatchesSettingsContract.ToggleRow row =
                (GboardPatchesSettingsContract.ToggleRow)
                        screen.getSections().get(0).getItems().get(0);
        assertFalse(row.isChecked());
        row.getToggleAction().accept(true);
        assertTrue(requested[0]);
        assertTrue(ui.getEntryTitle().contains("Resize"));
    }
}