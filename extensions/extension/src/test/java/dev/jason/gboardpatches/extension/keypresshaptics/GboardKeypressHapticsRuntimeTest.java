package dev.jason.gboardpatches.extension.keypresshaptics;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GboardKeypressHapticsRuntimeTest {
    private Context context;
    private SharedPreferences preferences;
    private static final String FLAG = GboardKeypressHapticsPolicy.FLAG_NAME;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        preferences = context.getSharedPreferences(GboardPatchesSettings.PREF_FILE, 0);
        preferences.edit().clear().commit();
        ReflectionHelpers.setStaticField(GboardFlagRuntimeContext.class, "applicationContext", null);
    }

    @After public void clearContext() {
        ReflectionHelpers.setStaticField(GboardFlagRuntimeContext.class, "applicationContext", null);
    }

    @Test public void defaultOffAndBothStoredBooleanFormatsAreEquivalent() {
        assertFalse(GboardKeypressHapticsSettings.DEFAULT_ENABLED);
        assertEquals(1024L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L));
        assertTrue(GboardPatchesSettings.writeKeypressHapticsEnabled(context, true));
        assertEquals(30L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L));
        preferences.edit().putString(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, "true").commit();
        assertEquals(30L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L));
        preferences.edit().putString(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, "false").commit();
        assertEquals(1024L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L));
    }

    @Test public void everyOtherFlagTypeAndRolloutValueIsPreservedByIdentity() {
        preferences.edit().putBoolean(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, true).commit();
        for (Object original : new Object[] { null, true, 1024, "1024", 29L, 30L, 37L, 2048L }) {
            assertSame(original, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, original));
        }
        for (String name : new String[] { null, "config_haptic_cutoff", "enable_grammar_checker",
                "text_clip_item_char_limit", "vibration_effect_min_sdk_extra" }) {
            Object original = Long.valueOf(1024L);
            assertSame(original, GboardKeypressHapticsRuntime.applyFlagValue(name, original));
        }
    }

    @Test public void lockedUserAndMalformedSettingPreserveStock() {
        preferences.edit().putBoolean(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, true).commit();
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(false);
        assertEquals(1024L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L, context));
        Shadows.shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        preferences.edit().putInt(GboardKeypressHapticsSettings.PREF_KEY_ENABLED, 1).commit();
        assertEquals(1024L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L, context));
        assertEquals(1024L, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, 1024L, null));
    }

    @Test public void lookupReadWriteAndLinkageErrorsCannotEscapeIntoGboard() {
        for (Throwable fault : new Throwable[] { new IllegalStateException("read failed"),
                new AssertionError("linkage or initialization failure") }) {
            SharedPreferences broken = (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { SharedPreferences.class }, (proxy, method, args) -> { throw fault; });
            Context failing = new ContextWrapper(context) {
                @Override public Context getApplicationContext() { return this; }
                @Override public SharedPreferences getSharedPreferences(String name, int mode) { return broken; }
            };
            Object original = Long.valueOf(1024L);
            assertSame(original, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, original, failing));
            assertNull(GboardPatchesSettings.readKeypressHapticsEnabled(failing));
            assertFalse(GboardPatchesSettings.writeKeypressHapticsEnabled(failing, true));
            ReflectionHelpers.setStaticField(GboardFlagRuntimeContext.class, "applicationContext", failing);
            assertSame(original, GboardKeypressHapticsRuntime.applyFlagValue(FLAG, original));
        }
    }

    @Test public void writingTheSwitchDoesNotChangeAnyOtherSetting() {
        preferences.edit().putString("other_flag", "original").commit();
        assertTrue(GboardPatchesSettings.writeKeypressHapticsEnabled(context, true));
        assertEquals("original", preferences.getString("other_flag", null));
        assertTrue(GboardPatchesSettings.writeKeypressHapticsEnabled(context, false));
        assertEquals("original", preferences.getString("other_flag", null));
    }
}
