package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Color;
import android.os.UserManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;
import java.lang.reflect.Method;
import java.util.OptionalInt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardSeamlessNavBarRuntimeTest {
    public static final class Params {
        public final int a, b;
        public final OptionalInt c, d;
        public final boolean e;
        public Params(int a, int b, OptionalInt c, OptionalInt d, boolean e) {
            this.a = a; this.b = b; this.c = c; this.d = d; this.e = e;
        }
    }

    public static final class Controller {
        Params last;
        boolean replayed;
        public void f(Window window, Params params) {
            last = params;
            replayed = GboardSeamlessNavBarRuntime.isReplaying();
            window.setNavigationBarColor(params.a);
            window.setNavigationBarDividerColor(params.b);
        }
    }

    @Test public void glassCopiesImmutableParamsAndDoesNotChangeGeometry() throws Exception {
        try (Fixture f = new Fixture()) {
            int width = f.window.getAttributes().width, height = f.window.getAttributes().height;
            int padding = f.root.getPaddingBottom();
            Params original = f.params(true);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            Params actual = f.apply(original);
            assertNotSame(original, actual);
            assertEquals(0, actual.a);
            assertEquals(0, actual.b);
            assertEquals(0xff123456, original.a);
            assertEquals(0xff654321, original.b);
            assertSame(original.c, actual.c);
            assertSame(original.d, actual.d);
            assertTrue(actual.e);
            assertFalse(original.e);
            assertEquals(width, f.window.getAttributes().width);
            assertEquals(height, f.window.getAttributes().height);
            assertEquals(padding, f.root.getPaddingBottom());
            assertFalse(f.window.isNavigationBarContrastEnforced());
            assertTrue((f.window.getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) != 0);
        }
    }

    @Test public void withoutAGlassSurfaceSolidThemesKeepAllStockWindowStyling() throws Exception {
        try (Fixture f = new Fixture()) {
            Params original = f.params(true);
            assertSame(original, f.apply(original));
            assertEquals(original.a, f.window.getNavigationBarColor());
            assertEquals(original.b, f.window.getNavigationBarDividerColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertEquals(0, f.window.getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Test public void glassStartingAfterTheStockPaletteRefreshesItsPaintImmediately() throws Exception {
        try (Fixture f = new Fixture()) {
            Params original = f.params(true);
            f.apply(original);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            assertEquals(Color.TRANSPARENT, f.controller.last.a);
            assertTrue(f.controller.replayed);
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertSame(original, f.controller.last);
        }
    }

    @Test public void withoutAGlassSurfaceCustomThemesKeepTheirDeclaredNavPalette() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.setBackgroundColor(Color.WHITE);
            Params original = f.params(false);
            assertSame(original, f.apply(original));
            assertEquals(original.a, f.window.getNavigationBarColor());
            assertEquals(original.b, f.window.getNavigationBarDividerColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertFalse(original.e);
            f.root.setBackgroundColor(Color.BLUE);
            assertSame(original, f.apply(original));
            assertEquals(original.a, f.window.getNavigationBarColor());
        }
    }

    @Test public void unknownThemeNeverBorrowsAnotherWindowsColorOrAnUnrelatedBackground() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.setBackgroundColor(Color.WHITE);
            f.apply(f.params(false));
        }
        try (Fixture f = new Fixture()) {
            f.root.setTag(null);
            f.root.setBackgroundColor(Color.RED);
            Params original = f.params(false);
            assertSame(original, f.apply(original));
            assertEquals(original.a, f.window.getNavigationBarColor());
        }
    }

    @Test public void anInactiveSessionMustNotReplayOrOverwriteLaterStockWindowUpdates() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply(f.params(true));
            f.window.setNavigationBarColor(Color.YELLOW);
            f.window.setNavigationBarDividerColor(Color.GREEN);
            f.window.setNavigationBarContrastEnforced(false);
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertFalse(f.controller.replayed);
            assertEquals(Color.YELLOW, f.window.getNavigationBarColor());
            assertEquals(Color.GREEN, f.window.getNavigationBarDividerColor());
            assertFalse(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void glassRestoresStylingCapturedWhenItActuallyStarts() throws Exception {
        try (Fixture f = new Fixture()) {
            Params original = f.params(true);
            f.apply(original);
            f.window.setNavigationBarContrastEnforced(false);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertEquals(original.a, f.window.getNavigationBarColor());
            assertFalse(f.window.isNavigationBarContrastEnforced());
            assertSame(original, f.apply(original));
            assertEquals(original.b, f.window.getNavigationBarDividerColor());
            assertFalse(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void hideReplaysOriginalPaletteAndRestoresOnlyOwnedWindowBits() throws Exception {
        try (Fixture f = new Fixture()) {
            View decor = f.window.getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            f.window.setNavigationBarColor(Color.MAGENTA);
            f.window.setNavigationBarDividerColor(Color.CYAN);
            Params original = f.params(true);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            f.apply(original);
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertSame(original, f.controller.last);
            assertTrue(f.controller.replayed);
            assertEquals(original.a, f.window.getNavigationBarColor());
            assertEquals(original.b, f.window.getNavigationBarDividerColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertEquals(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR, decor.getSystemUiVisibility());
            assertFalse(GboardSeamlessNavBarRuntime.isReplaying());
        }
    }

    @Test public void lockedUserCannotStartSeamlessStylingOrReadSettings() throws Exception {
        try (Fixture f = new Fixture()) {
            Shadows.shadowOf(f.users).setUserUnlocked(false);
            Params original = f.params(false);
            assertFalse(GboardFrostedGlassSettingsRuntime.isSeamlessNavBarEnabled());
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            assertSame(original, f.apply(original));
            assertTrue(f.window.isNavigationBarContrastEnforced());
            assertEquals(0, f.window.getDecorView().getSystemUiVisibility()
                    & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Test public void nightModeRetainsTheLatestStockIconContrast() throws Exception {
        try (Fixture f = new Fixture()) {
            f.controllerActivity.get().getResources().getConfiguration().uiMode =
                    android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Params light = new Params(Color.WHITE, Color.WHITE, OptionalInt.of(Color.WHITE),
                    OptionalInt.empty(), true);
            f.apply(light);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            Params actual = f.apply(light);
            assertTrue(actual.e);
            assertEquals(0, actual.a);
            GboardSeamlessNavBarRuntime.restore(f.window);
            assertEquals(Color.WHITE, f.window.getNavigationBarColor());
        }
    }

    @Test public void exitUsesTheEntryWindowAndPatchedEntryReplaysTheStockMethod() throws Exception {
        try (Fixture f = new Fixture()) {
            Params original = f.params(true);
            // The 18.5.4 body overwrites its Window register; the exit only receives the model.
            f.apply(original);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            assertTrue("glass start replays the patched stock method", f.controller.replayed);
            assertEquals(Color.TRANSPARENT, f.window.getNavigationBarColor());
            f.window.setNavigationBarColor(Color.RED);
            GboardFrostedGlassLifecycleRuntime.onApplyNavBarExit(original);
            assertEquals("no pending entry: exit must not style a guessed Window",
                    Color.RED, f.window.getNavigationBarColor());
            f.apply(original);
            assertEquals(Color.TRANSPARENT, f.window.getNavigationBarColor());
        }
    }

    @Test public void disabledSettingKeepsStockModelAndReleasesGlassStyling() throws Exception {
        try (Fixture f = new Fixture()) {
            Params original = f.params(true);
            f.apply(original);
            GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(f.window);
            f.seamless(false);
            assertSame(original, f.apply(original));
            assertEquals(original.a, f.window.getNavigationBarColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void ambiguousOwnerMethodIsNeverReplayed() {
        assertNull(GboardSeamlessNavBarRuntime.applyMethod(new Ambiguous(), new Params(0, 0,
                OptionalInt.empty(), OptionalInt.empty(), false)));
        assertNotNull(GboardSeamlessNavBarRuntime.applyMethod(new Controller(), new Params(0, 0,
                OptionalInt.empty(), OptionalInt.empty(), false)));
    }

    public static final class Ambiguous {
        public void f(Window window, Params params) {}
        public void g(Window window, Params params) {}
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> controllerActivity = Robolectric.buildActivity(Activity.class).setup().visible();
        final Window window = controllerActivity.get().getWindow();
        final FrameLayout root = new FrameLayout(controllerActivity.get());
        final UserManager users = controllerActivity.get().getSystemService(UserManager.class);
        final Controller controller = new Controller();
        final Method method = Controller.class.getMethod("f", Window.class, Params.class);
        Fixture() throws Exception {
            Shadows.shadowOf(users).setUserUnlocked(true);
            seamless(true);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setPadding(0, 0, 0, 100);
            controllerActivity.get().setContentView(root);
            root.layout(0, 0, 1000, 600);
            window.setNavigationBarContrastEnforced(true);
        }
        /** Writes the store the runtime reads (its process Application context). */
        void seamless(boolean enabled) {
            assertTrue(GboardFlagRuntimeContext.preferencesOrNull().edit().putBoolean(
                    GboardFrostedGlassSettings.PREF_KEY_SEAMLESS_NAV_BAR_ENABLED, enabled).commit());
        }
        Params params(boolean solid) {
            return new Params(0xff123456, 0xff654321,
                    solid ? OptionalInt.of(Color.GRAY) : OptionalInt.empty(), OptionalInt.of(123), false);
        }
        Params apply(Params value) {
            // Same order as the patched rra.f: entry may replace the model, exit re-styles.
            Params actual = (Params) GboardFrostedGlassLifecycleRuntime.onApplyNavBarEntry(window, controller, value);
            controller.f(window, actual);
            GboardFrostedGlassLifecycleRuntime.onApplyNavBarExit(actual);
            return actual;
        }
        @Override public void close() {
            GboardSeamlessNavBarRuntime.restore(window);
            controllerActivity.pause().stop().destroy();
        }
    }
}
