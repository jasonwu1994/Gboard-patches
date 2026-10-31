package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.UserManager;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.ViewTreeObserver;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.DrawableWrapper;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE,
        shadows = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class GboardFrostedGlassFirstFrameTest {
    @Test public void firstDrawSeesCustomAlphaWithoutAdvancingDelayedQueue() throws Exception {
        try (Fixture f = new Fixture()) {
            int[] drawnAlpha = {-1};
            f.window.getDecorView().getViewTreeObserver().addOnDrawListener(
                    () -> drawnAlpha[0] = f.theme.getAlpha());
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals("Stock alpha must never reach the first content draw", 77, drawnAlpha[0]);
        }
    }

    @Test public void configureAfterShownRetainsLocalBlurWhenPublicWindowBlurIsDisabled() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(f.window, false, false);
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void rootInflatedAfterShowIsPreparedBeforeItsFirstDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.setTag("not-a-keyboard-yet");
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            assertEquals(255, f.theme.getAlpha());
            f.root.setTag(".keyboard-base-area.keyboard-outline");
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void sameViewNewThemeAndRestyledAlphaAreCorrectedWithoutLayoutOrDelay() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            GradientDrawable replacement = new GradientDrawable();
            replacement.setColor(Color.RED);
            replacement.setCornerRadius(20);
            replacement.setAlpha(180);
            f.root.setBackground(replacement);
            f.draw();
            assertEquals(77, replacement.getAlpha());
            assertSame(replacement, ((LayerDrawable) f.root.getBackground()).getDrawable(0));
            Object composite = f.root.getBackground();
            replacement.setAlpha(255);
            f.draw();
            assertEquals(77, replacement.getAlpha());
            assertSame(composite, f.root.getBackground());
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            f.finishHide();
            assertSame(replacement, f.root.getBackground());
            assertEquals(180, replacement.getAlpha());
        }
    }

    @Test public void newChildPanelIsCaughtEvenWhenDecorSizeDoesNotChange() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            View panel = new View(f.host.get());
            GradientDrawable panelTheme = new GradientDrawable();
            panelTheme.setColor(Color.BLUE);
            panel.setTag(".keyboard-header-area");
            panel.setBackground(panelTheme);
            f.root.addView(panel);
            f.draw();
            assertEquals(77, panelTheme.getAlpha());
        }
    }

    @Test public void floatingReinflateUsesNewGeometryBeforeDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            View floating = new View(f.host.get());
            GradientDrawable floatingTheme = new GradientDrawable();
            floatingTheme.setColor(Color.BLACK);
            floatingTheme.setCornerRadius(20);
            floating.setTag(".keyboard-base-area");
            floating.setBackground(floatingTheme);
            f.dialog.setContentView(floating);
            floating.layout(0, 0, 700, 500);
            f.draw();
            assertEquals(77, floatingTheme.getAlpha());
            assertSame(f.theme, f.root.getBackground());
            assertEquals(255, f.theme.getAlpha());
            assertEquals(20f, GboardFrostedGlassShape.resolve(floating, floatingTheme).topLeft, 0f);
        }
    }

    @Test public void repeatedDrawsDoNotReloadSettingsOrRewriteUnchangedAlpha() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            Object background = f.root.getBackground();
            int reads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            int alphaWrites = f.theme.writes;
            for (int i = 0; i < 20; i++) f.draw();
            assertEquals(reads, GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads);
            assertEquals(alphaWrites, f.theme.writes);
            assertSame(background, f.root.getBackground());
        }
    }

    @Test public void hiddenObserverCannotResurrectBlurAndNextShowWorks() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            Field states = GboardFrostedGlassRuntime.class.getDeclaredField("STATES");
            states.setAccessible(true);
            Object state = ((Map<?, ?>) states.get(null)).get(f.window);
            Field field = state.getClass().getDeclaredField("preDrawObserver");
            field.setAccessible(true);
            ViewTreeObserver.OnPreDrawListener old = (ViewTreeObserver.OnPreDrawListener) field.get(state);
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            f.finishHide();
            assertTrue(old.onPreDraw());
            f.draw();
            assertEquals(255, f.theme.getAlpha());
            assertSame(f.theme, f.root.getBackground());
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            assertTrue(old.onPreDraw());
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void configuredExclusionsCannotBeReenabledByShownOrPreDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(f.window, true, false);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals(255, f.theme.getAlpha());
            GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(f.window, false, true);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals(255, f.theme.getAlpha());
            GboardFrostedGlassLifecycleRuntime.afterConfigureWindow(f.window, false, false);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void resizeUsesMeasuredBoundsBeforeDrawableDrawUpdatesItsBounds() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.root.layout(0, 0, 800, 400);
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(f.root, f.theme);
            assertEquals(800, shape.bounds.width());
            assertEquals(400, shape.bounds.height());
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void stockWrapperThatRejectsAlphaStillKeepsItsShapeAndChildOpacity() throws Exception {
        try (Fixture f = new Fixture()) {
            DrawableWrapper wrapper = new DrawableWrapper(f.theme) {
                @Override public void setAlpha(int ignored) { /* Stock rbk behavior. */ }
            };
            f.theme.setCornerRadius(20);
            f.root.setBackground(wrapper);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals(77, f.theme.getAlpha());
            assertSame(wrapper, ((LayerDrawable) f.root.getBackground()).getDrawable(0));
            assertEquals(20f, GboardFrostedGlassShape.resolve(f.root, wrapper).topLeft, 0f);
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            f.finishHide();
            assertSame(wrapper, f.root.getBackground());
            assertEquals(255, f.theme.getAlpha());
        }
    }

    @Test public void colorDrawableAlphaIsAbsoluteAndRestoresWithoutRoundingOrRgbChanges() throws Exception {
        try (Fixture f = new Fixture()) {
            View panel = new View(f.host.get());
            ColorDrawable color = new ColorDrawable(0x80123456);
            panel.setTag(".keyboard-body-area");
            panel.setBackground(new DrawableWrapper(color) {});
            f.root.addView(panel);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            assertEquals(0x4d123456, color.getColor());
            for (int i = 0; i < 20; i++) f.draw();
            assertEquals(77, color.getAlpha());
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            f.finishHide();
            assertEquals(0x80123456, color.getColor());
        }
    }

    @Test public void cachedSettingsNeverBypassUnlockGateDuringPreDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.theme.setAlpha(255);
            int reads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            var user = shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class));
            user.setUserUnlocked(false);
            f.draw();
            assertEquals(255, f.theme.getAlpha());
            assertEquals(reads, GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads);
            user.setUserUnlocked(true);
            f.draw();
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void hideKeepsGlassWhileTheKeyboardSlidesOutThenRestoresStock() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            Object composite = f.root.getBackground();
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            // Frames drawn during the hide animation must not flash the opaque theme.
            f.draw();
            assertEquals(77, f.theme.getAlpha());
            assertSame(composite, f.root.getBackground());
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            assertEquals(77, f.theme.getAlpha());
            f.finishHide();
            assertEquals(255, f.theme.getAlpha());
            assertSame(f.theme, f.root.getBackground());
        }
    }

    @Test public void showDuringHideAnimationKeepsTheSameGlassWithoutRestoring() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            Object composite = f.root.getBackground();
            int writes = f.theme.writes;
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.finishHide();
            f.draw();
            assertSame(composite, f.root.getBackground());
            assertEquals(77, f.theme.getAlpha());
            assertEquals("no restore/reapply round trip", writes, f.theme.writes);
        }
    }

    @Test public void hidingStopsPerFrameWorkImmediately() throws Exception {
        try (Fixture f = new Fixture()) {
            GboardFrostedGlassLifecycleRuntime.onInputViewStarted(f.ime);
            f.draw();
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(f.ime);
            int reads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            f.theme.setAlpha(200);
            f.draw();
            assertEquals("a hiding window is not re-styled", 200, f.theme.getAlpha());
            assertEquals(reads, GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup().visible();
        final Dialog dialog = new Dialog(host.get());
        final GboardFrostedGlassLifecycleDirectBootTest.GuardedIme ime =
                Robolectric.setupService(GboardFrostedGlassLifecycleDirectBootTest.GuardedIme.class);
        final FrameLayout root = new FrameLayout(host.get());
        final CountingGradient theme = new CountingGradient();
        final Window window;

        Fixture() {
            shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class))
                    .setUserUnlocked(true);
            GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.enabled = true;
            GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads = 0;
            theme.setColor(Color.BLACK);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setBackground(theme);
            dialog.setContentView(root);
            dialog.show();
            shadowOf(Looper.getMainLooper()).idle();
            assertTrue(root.isAttachedToWindow());
            root.layout(0, 0, 1000, 600);
            theme.setBounds(0, 0, 1000, 600);
            window = dialog.getWindow();
            ime.overrideDialog = dialog;
        }

        /** The stock theme returns only after the IME hide animation window. */
        void finishHide() {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600));
        }

        void draw() {
            assertFalse(window.getDecorView().getViewTreeObserver().dispatchOnPreDraw());
            window.getDecorView().getViewTreeObserver().dispatchOnDraw();
        }

        @Override public void close() {
            shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class))
                    .setUserUnlocked(true);
            GboardFrostedGlassLifecycleRuntime.onInputWindowHidden(ime);
            dialog.dismiss();
            host.close();
        }
    }

    private static final class CountingGradient extends GradientDrawable {
        int writes;
        @Override public void setAlpha(int alpha) { writes++; super.setAlpha(alpha); }
    }
}
