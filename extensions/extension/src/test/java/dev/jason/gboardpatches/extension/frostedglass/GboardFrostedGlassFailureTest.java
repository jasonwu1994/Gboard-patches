package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.os.UserManager;
import android.view.Window;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPhoneWindow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 32, 33, 35}, manifest = Config.NONE, shadows = {
        GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.class,
        GboardFrostedGlassFailureTest.ThrowingWindow.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardFrostedGlassFailureTest {
    @Test public void oneFailingWindowSetterMustNotEscapeOrSkipOtherRestoration() throws Exception {
        try (Fixture f = new Fixture()) {
            f.window.setNavigationBarDividerColor(Color.CYAN);
            f.window.setNavigationBarContrastEnforced(true);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            ThrowingWindow.rejectNav = true;
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertEquals(Color.CYAN, f.window.getNavigationBarDividerColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertFalse(GboardSeamlessNavBarRuntime.isReplaying());
        }
    }

    @Test public void applicationFailurePlusWindowCleanupFailureCannotEscapePreDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassRuntime.onWindowShown(f.ime);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            f.root.rejectTag = true;
            ThrowingWindow.rejectNav = true;
            // This is Android's real observer dispatch, outside the lifecycle hook try/catch.
            assertFalse(f.window.getDecorView().getViewTreeObserver().dispatchOnPreDraw());
            assertTrue(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void missingOemNavigationApiFailsClosedAndStillRestoresOtherProperties() throws Exception {
        try (Fixture f = new Fixture()) {
            f.window.setNavigationBarDividerColor(Color.CYAN);
            ThrowingWindow.rejectMissingApi = true;
            assertFalse(GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window));
            assertEquals(Color.CYAN, f.window.getNavigationBarDividerColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertFalse(GboardSeamlessNavBarRuntime.isReplaying());
        }
    }

    @Test public void layoutObserverFailureCannotEscapeTheTraversal() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassRuntime.onWindowShown(f.ime);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            var states = GboardFrostedGlassRuntime.class.getDeclaredField("STATES");
            states.setAccessible(true);
            Object state = ((java.util.Map<?, ?>) states.get(null)).get(f.window);
            var field = state.getClass().getDeclaredField("decorLayoutObserver");
            field.setAccessible(true);
            android.view.View.OnLayoutChangeListener listener =
                    (android.view.View.OnLayoutChangeListener) field.get(state);
            android.view.View brokenObserver = new android.view.View(f.host.get()) {
                @Override public android.view.ViewTreeObserver getViewTreeObserver() {
                    throw new NoSuchMethodError("Injected OEM observer failure");
                }
            };
            listener.onLayoutChange(brokenObserver, 0, 0, 1000, 600, 0, 0, 1000, 600);
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertFalse(GboardSeamlessNavBarRuntime.isReplaying());
        }
    }

    @Implements(className = "com.android.internal.policy.PhoneWindow", isInAndroidSdk = false)
    public static class ThrowingWindow extends ShadowPhoneWindow {
        static boolean rejectNav;
        static boolean rejectMissingApi;
        public static int failedNavCalls;
        @Implementation protected void setNavigationBarColor(int color) {
            if (rejectMissingApi) throw new NoSuchMethodError("Injected unavailable OEM API");
            if (rejectNav) {
                failedNavCalls++;
                throw new IllegalStateException("Injected OEM Window failure");
            }
            Shadow.directlyOn(realWindow, "com.android.internal.policy.PhoneWindow",
                    "setNavigationBarColor", ClassParameter.from(int.class, color));
        }
    }

    static final class ThrowingRoot extends FrameLayout {
        boolean rejectTag;
        ThrowingRoot(android.content.Context context) { super(context); }
        @Override public Object getTag() {
            if (rejectTag) throw new IllegalStateException("Injected theme inspection failure");
            return super.getTag();
        }
    }

    static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup().visible();
        final Dialog dialog = new Dialog(host.get());
        final GboardFrostedGlassLifecycleDirectBootTest.GuardedIme ime =
                Robolectric.setupService(GboardFrostedGlassLifecycleDirectBootTest.GuardedIme.class);
        final ThrowingRoot root = new ThrowingRoot(host.get());
        final Window window;
        Fixture() {
            ThrowingWindow.rejectNav = false;
            ThrowingWindow.rejectMissingApi = false;
            shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class)).setUserUnlocked(true);
            GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.enabled = true;
            GradientDrawable theme = new GradientDrawable();
            theme.setColor(Color.BLACK);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setBackground(theme);
            dialog.setContentView(root);
            dialog.show();
            shadowOf(Looper.getMainLooper()).idle();
            root.layout(0, 0, 1000, 600);
            theme.setBounds(0, 0, 1000, 600);
            window = dialog.getWindow();
            window.setNavigationBarContrastEnforced(true);
            ime.overrideDialog = dialog;
        }
        @Override public void close() {
            ThrowingWindow.rejectNav = false;
            ThrowingWindow.rejectMissingApi = false;
            root.rejectTag = false;
            GboardFrostedGlassRuntime.onWindowHidden(ime);
            GboardSeamlessNavBarRuntime.restore(window);
            dialog.dismiss();
            host.close();
        }
    }
}
