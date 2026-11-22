package dev.jason.gboardpatches.extension.shiftdelete;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import org.robolectric.RuntimeEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardShiftDeleteSettingsTest {
    private static SharedPreferences preferences() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = context.getSharedPreferences("shift-delete-settings", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        return preferences;
    }

    @Test
    public void defaultsToOffWhenNothingWasSaved() {
        assertFalse(GboardShiftDeleteSettings.enabled(preferences()));
        assertFalse(GboardShiftDeleteSettings.enabled(null));
    }

    @Test
    public void readsTheSavedValue() {
        SharedPreferences preferences = preferences();
        preferences.edit().putBoolean(GboardShiftDeleteSettings.ENABLED, true).commit();
        assertTrue(GboardShiftDeleteSettings.enabled(preferences));
        preferences.edit().putBoolean(GboardShiftDeleteSettings.ENABLED, false).commit();
        assertFalse(GboardShiftDeleteSettings.enabled(preferences));
    }
}
