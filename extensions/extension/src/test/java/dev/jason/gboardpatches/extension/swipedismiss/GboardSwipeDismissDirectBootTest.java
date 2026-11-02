package dev.jason.gboardpatches.extension.swipedismiss;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowUserManager;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardSwipeDismissDirectBootTest {
    private ShadowUserManager users;
    private View header;
    private Context context;
    private SharedPreferences preferences;
    private int storageReads;
    private boolean storageUnavailable;

    @Before public void setUp() {
        Context app = RuntimeEnvironment.getApplication();
        users = shadowOf(app.getSystemService(UserManager.class));
        users.setUserUnlocked(true);
        GboardSwipeDismissRuntime.reset();
        preferences = GboardPatchesSettings.preferences(app);
        preferences.edit().clear().putBoolean(GboardSwipeDismissSettings.PREF_KEY_ENABLED, true)
                .commit();
        context = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                storageReads++;
                if (!getSystemService(UserManager.class).isUserUnlocked() || storageUnavailable) {
                    throw new IllegalStateException("SharedPreferences in credential encrypted "
                            + "storage are not available until after user (id 0) is unlocked");
                }
                return preferences;
            }
        };
        header = new View(context) {
            @Override public boolean isShown() { return true; }
            @Override public void getLocationOnScreen(int[] out) { out[0] = 0; out[1] = 0; }
        };
        header.setId(123);
        header.layout(0, 0, 300, 40);
    }

    @After public void tearDown() { GboardSwipeDismissRuntime.reset(); }

    @Test public void lockedHeaderTouchDoesNotOpenCredentialStorage() {
        users.setUserUnlocked(false);
        assertFalse(touch(MotionEvent.ACTION_DOWN, 10));
        assertFalse(touch(MotionEvent.ACTION_MOVE, 1000));
        assertEquals(0, storageReads);
    }

    @Test public void lockedSettingsReaderReturnsDisabledWithoutWritingDefaults() {
        users.setUserUnlocked(false);
        preferences.edit().clear().commit();
        assertFalse(GboardSwipeDismissSettings.readEnabled(context));
        assertEquals(0, storageReads);
        assertTrue(preferences.getAll().isEmpty());
    }

    @Test public void lockDiscardsEnabledCacheAndAnInFlightGesture() {
        assertFalse(touch(MotionEvent.ACTION_DOWN, 10));
        assertEquals(1, storageReads);
        users.setUserUnlocked(false);
        assertFalse(touch(MotionEvent.ACTION_MOVE, 1000));
        assertFalse(touch(MotionEvent.ACTION_DOWN, 10));
        assertFalse(touch(MotionEvent.ACTION_MOVE, 1000));
        assertEquals(1, storageReads);
    }

    @Test public void unlockImmediatelyRestoresSavedSetting() {
        users.setUserUnlocked(false);
        touch(MotionEvent.ACTION_DOWN, 10);
        users.setUserUnlocked(true);
        assertFalse(touch(MotionEvent.ACTION_DOWN, 10));
        assertTrue(touch(MotionEvent.ACTION_MOVE, 1000));
        assertTrue(preferences.getBoolean(GboardSwipeDismissSettings.PREF_KEY_ENABLED, false));
    }

    @Test public void credentialStorageFailurePreservesStockTouch() {
        storageUnavailable = true;
        assertFalse(touch(MotionEvent.ACTION_DOWN, 10));
        assertFalse(touch(MotionEvent.ACTION_MOVE, 1000));
    }

    @Test public void lockedWriteFailsWithoutOpeningStorageOrChangingSavedSetting() {
        users.setUserUnlocked(false);
        assertFalse(GboardSwipeDismissSettings.writeEnabled(context, false));
        assertEquals(0, storageReads);
        assertTrue(preferences.getBoolean(GboardSwipeDismissSettings.PREF_KEY_ENABLED, false));
    }

    private boolean touch(int action, float y) {
        // Model the target's resolved semantic header ID; no Gboard resources in this fixture.
        try {
            Field field = GboardSwipeDismissRuntime.class.getDeclaredField("headerId");
            field.setAccessible(true);
            field.set(null, 123);
        } catch (Exception e) { throw new AssertionError(e); }
        MotionEvent event = MotionEvent.obtain(0, action == MotionEvent.ACTION_DOWN ? 0 : 50,
                action, 10, y, 0);
        try { return GboardSwipeDismissRuntime.onHeaderTouch(header, event); }
        finally { event.recycle(); }
    }
}
