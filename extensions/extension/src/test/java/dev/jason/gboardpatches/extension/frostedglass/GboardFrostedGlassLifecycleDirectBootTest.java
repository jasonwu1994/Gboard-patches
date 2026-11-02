package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Looper;
import android.os.UserManager;
import android.view.View;
import android.view.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowUserManager;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE,
        shadows = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardFrostedGlassLifecycleDirectBootTest {
    private ShadowUserManager users;
    private GuardedIme ime;
    private Window window;
    private Class<?> stateClass;
    private ActivityController<Activity> host;
    private Dialog dialog;

    @Before public void setUp() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        users = shadowOf(app.getSystemService(UserManager.class));
        users.setUserUnlocked(true);
        ime = Robolectric.setupService(GuardedIme.class);
        host = Robolectric.buildActivity(Activity.class).setup().visible();
        dialog = new Dialog(host.get());
        dialog.show();
        ime.overrideDialog = dialog;
        window = ime.getWindow().getWindow();
        ime.windowReads = 0;
        stateClass = Class.forName(GboardFrostedGlassRuntime.class.getName() + "$State");
        CountingSettings.reads = 0;
        CountingSettings.enabled = false;
    }

    @After public void tearDown() throws Exception {
        invoke("disable", new Class<?>[]{Window.class}, window);
        dialog.dismiss();
        host.close();
    }

    @Test public void allLifecycleEntriesSkipWindowAndSettingsUntilUnlock() {
        users.setUserUnlocked(false);
        assertFalse(GboardFrostedGlassRuntime.isUserUnlocked(ime));
        GboardFrostedGlassRuntime.onConfigureWindow(ime, false, false);
        GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(window, false, false);
        GboardFrostedGlassRuntime.onWindowShown(ime);
        GboardFrostedGlassRuntime.onWindowHidden(ime);
        assertEquals(0, ime.windowReads);
        assertEquals(0, CountingSettings.reads);

        users.setUserUnlocked(true);
        assertTrue(GboardFrostedGlassRuntime.isUserUnlocked(ime));
        GboardFrostedGlassRuntime.onWindowShown(ime);
        assertTrue("The guard must recover after unlock, not cache disabled forever",
                CountingSettings.reads > 0);
    }

    @Test public void delayedApplyChecksUnlockBeforeAnySettingsRead() throws Exception {
        Object state = invoke("stateFor", new Class<?>[]{Window.class}, window);
        users.setUserUnlocked(false);
        invoke("reapplyAfterLayout", new Class<?>[]{Window.class, Context.class,
                stateClass, long.class}, window, ime, state, 0L);
        invoke("verifyAndApply", new Class<?>[]{Window.class, Context.class, stateClass},
                window, ime, state);
        assertEquals(0, CountingSettings.reads);
        assertEquals(false, invoke("isCurrent", new Class<?>[]{Window.class, stateClass}, window, state));
    }

    @Test public void layoutObserverDoesNotSchedulePreferenceWorkWhileLocked() throws Exception {
        Object state = invoke("stateFor", new Class<?>[]{Window.class}, window);
        View decor = window.getDecorView();
        invoke("installDecorLayoutObserver", new Class<?>[]{Window.class, Context.class,
                View.class, stateClass}, window, ime, decor, state);
        Field field = stateClass.getDeclaredField("decorLayoutObserver");
        field.setAccessible(true);
        View.OnLayoutChangeListener listener = (View.OnLayoutChangeListener) field.get(state);
        users.setUserUnlocked(false);
        listener.onLayoutChange(decor, 0, 0, 1000, 600, 0, 0, 0, 0);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, CountingSettings.reads);
        invoke("disable", new Class<?>[]{Window.class}, window);
    }

    @Test public void preDrawRechecksUnlockBeforeReadingSettings() throws Exception {
        CountingSettings.enabled = true;
        GboardFrostedGlassRuntime.onWindowShown(ime);
        assertTrue(CountingSettings.reads > 0);
        CountingSettings.reads = 0;
        users.setUserUnlocked(false);
        window.getDecorView().getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(0, CountingSettings.reads);
        invoke("disable", new Class<?>[]{Window.class}, window);
    }

    @Test public void cePreferencesAreNeverOpenedWhileLockedAndExceptionsStayContained() {
        ThrowingCeContext context = new ThrowingCeContext(RuntimeEnvironment.getApplication());
        users.setUserUnlocked(false);
        assertNull(GboardFlagRuntimeContext.preferencesOrNull(context));
        assertEquals(0, context.reads);

        users.setUserUnlocked(true);
        assertNull(GboardFlagRuntimeContext.preferencesOrNull(context));
        assertTrue("An unavailable CE store must be caught even after unlock", context.reads > 0);
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    public static final class GuardedIme extends InputMethodService {
        int windowReads;
        Dialog overrideDialog;
        @Override public Dialog getWindow() {
            windowReads++;
            return overrideDialog != null ? overrideDialog : super.getWindow();
        }
    }

    private static final class ThrowingCeContext extends ContextWrapper {
        int reads;
        ThrowingCeContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            reads++;
            throw new IllegalStateException("SharedPreferences in credential encrypted storage "
                    + "are not available until after user (id 0) is unlocked");
        }
    }

    @Implements(value = GboardFrostedGlassSettingsRuntime.class, isInAndroidSdk = false)
    public static final class CountingSettings {
        static int reads;
        static boolean enabled;
        @Implementation public static boolean isEnabled() { reads++; return enabled; }
        @Implementation public static int blurRadiusPx() { reads++; return 32; }
        @Implementation public static int customOpacityPercent() { reads++; return 77; }
        @Implementation public static int customKeyOpacity() { reads++; return -1; }
        // No host settings provider for the folded seamless toggle; tests opt in via State.
        @Implementation public static boolean isSeamlessNavBarEnabled() { reads++; return false; }
    }
}
