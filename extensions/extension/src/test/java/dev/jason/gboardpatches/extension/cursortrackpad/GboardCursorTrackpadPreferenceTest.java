package dev.jason.gboardpatches.extension.cursortrackpad;

import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class GboardCursorTrackpadPreferenceTest {
    @Test public void localSwitchPromotesOnlyFalseWithoutPersistingOfficialPreference() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("cursor1853", 0);
        prefs.edit().clear().commit();
        assertFalse(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
        prefs.edit().putBoolean(GboardCursorTrackpadSettings.PREF_KEY_ENABLED, true).commit();
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(true, prefs));
        assertFalse(prefs.contains("enable_scrub_move"));
        prefs.edit().putBoolean(GboardCursorTrackpadSettings.PREF_KEY_ENABLED, false).commit();
        assertFalse(GboardCursorTrackpadRuntime.applyPreference(false, prefs));
    }
    @Test public void wrongStringWrongResourceAndMissingStoragePreserveStock() {
        assertFalse(GboardCursorTrackpadRuntime.applyBooleanPreference("unrelated", false));
        assertFalse(GboardCursorTrackpadRuntime.applyResourceBooleanPreference(0x7f140996, false));
        assertTrue(GboardCursorTrackpadRuntime.applyBooleanPreference("unrelated", true));
        assertTrue(GboardCursorTrackpadRuntime.applyResourceBooleanPreference(0, true));
        assertFalse(GboardCursorTrackpadRuntime.applyPreference(false, null));
        assertTrue(GboardCursorTrackpadRuntime.applyPreference(true, null));
    }
}
