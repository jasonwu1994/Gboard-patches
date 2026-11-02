package dev.jason.gboardpatches.extension.settings;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowUserManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardRuntimeSettingsTest {
    private Context app;
    private ShadowUserManager users;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        users = shadowOf(app.getSystemService(UserManager.class));
        users.setUserUnlocked(true);
        GboardPatchesSettings.preferences(app).edit().clear().commit();
    }

    @Test public void lockedAndNullContextsDoNotInvokeTheReaderAndUnlockRecovers() {
        GboardPatchesSettings.preferences(app).edit().putString("value", "saved").commit();
        users.setUserUnlocked(false);
        assertEquals("stock", GboardPatchesSettings.readRuntimeSetting(app,
                prefs -> { fail("Locked reader invoked"); return "wrong"; }, "stock"));
        assertEquals("stock", GboardPatchesSettings.readRuntimeSetting(null,
                prefs -> { fail("Null-context reader invoked"); return "wrong"; }, "stock"));
        users.setUserUnlocked(true);
        assertEquals("saved", GboardPatchesSettings.readRuntimeSetting(app,
                prefs -> prefs.getString("value", "missing"), "stock"));
    }

    @Test public void unavailableStorageDoesNotInvokeReader() {
        Context failing = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                throw new IllegalStateException("Credential encrypted storage unavailable");
            }
        };
        assertEquals("stock", GboardPatchesSettings.readRuntimeSetting(failing,
                prefs -> { fail("Unavailable reader invoked"); return "wrong"; }, "stock"));
    }

    @Test public void failedOrNullValueReadUsesFallback() {
        assertEquals("stock", GboardPatchesSettings.readRuntimeSetting(app,
                prefs -> { throw new IllegalStateException("Read failed after acquisition"); }, "stock"));
        assertEquals("stock", GboardPatchesSettings.readRuntimeSetting(app,
                prefs -> null, "stock"));
    }

    @Test public void defaultInitializationDoesNotWriteDuringDirectBoot() {
        users.setUserUnlocked(false);
        GboardPatchesSettings.ensureDefaults(app);
        users.setUserUnlocked(true);
        assertTrue(GboardPatchesSettings.preferences(app).getAll().isEmpty());
    }
}
