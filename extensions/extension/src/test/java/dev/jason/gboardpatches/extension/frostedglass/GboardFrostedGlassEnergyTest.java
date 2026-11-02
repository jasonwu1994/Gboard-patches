package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Looper;
import android.os.UserManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.libraries.inputmethod.inputview.InputView;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Main-thread work bounds that keep the glass effect but not redundant per-frame work. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE,
        shadows = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class GboardFrostedGlassEnergyTest {
    static int childReads;
    static int rootLocationReads;

    @Test public void stableFramesWithNavigationAndKeyOpacityDoNotWalkTheTree() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.enableNavigationAndKeys(77);
            f.preDraw();
            assertTrue("navigation glass must be active", f.input.willNotDraw());
            assertEquals(77, f.keyFills[0].getAlpha());
            assertEquals(77, f.theme.getAlpha());
            childReads = rootLocationReads = 0;
            int settingsReads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            for (int i = 0; i < 20; i++) f.preDraw();
            assertEquals("draw-only frames reuse discovered targets", 0, childReads);
            assertEquals("navigation admission is resolved once per frame", 20, rootLocationReads);
            assertEquals(settingsReads, GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads);
            assertEquals(77, f.keyFills[39].getAlpha());
            assertTrue(f.input.willNotDraw());
        }
    }

    @Test public void drawOnlyChangesToExistingTargetsAreStillCorrectedOnTheNextFrame() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.enableNavigationAndKeys(77);
            f.preDraw();
            f.keyFills[5].setAlpha(255);
            GradientDrawable replacement = new GradientDrawable();
            replacement.setColor(Color.GREEN);
            f.keys[6].setBackground(replacement);
            f.preDraw();
            assertEquals(77, f.keyFills[5].getAlpha());
            assertEquals(77, replacement.getAlpha());
        }
    }

    @Test public void leafRelayoutDoesNotReloadSettingsOrQueueFallbacks() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.idle(500);
            int reads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            for (int round = 0; round < 5; round++) {
                f.candidate.setText(round % 2 == 0 ? "ab" : "abc");
                f.candidate.requestLayout();
                f.idle(20);
                assertNull("a prepared keyboard leaves no delayed work", f.pendingFallback());
            }
            f.idle(300);
            assertEquals(reads, GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads);
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void layoutStillRefreshesSettingsAfterTheThrottleWindow() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.idle(500);
            int reads = GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads;
            f.idle(2100);
            f.candidate.requestLayout();
            f.idle(20);
            assertTrue(GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads > reads);
        }
    }

    @Test public void keyAddedThroughALayoutPassIsFadedBeforeItsFirstDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.enableNavigationAndKeys(77);
            f.idle(100);
            GradientDrawable fill = new GradientDrawable();
            fill.setColor(Color.WHITE);
            View key = new View(f.context);
            key.setTag(".keytop.with-popup");
            key.setBackground(new InsetDrawable(fill, 3));
            f.rows[2].addView(key, new FrameLayout.LayoutParams(10, 10));
            // One traversal (layout + pre-draw), well before any 48 ms fallback could run.
            f.idle(20);
            assertEquals(77, fill.getAlpha());
            assertNull(f.pendingFallback());
        }
    }

    @Test public void unpreparedKeyboardStillUsesTheDelayedFallbackOnce() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.setTag("not-yet");
            f.show();
            f.candidate.requestLayout();
            f.idle(20);
            assertNotNull("fallback is pending while no root is available", f.pendingFallback());
            f.root.setTag(".keyboard-base-area.keyboard-outline");
            f.idle(40);
            assertEquals("the fallback prepares the late root", 77, f.theme.getAlpha());
            assertNull(f.pendingFallback());
        }
    }

    @Test public void navigationStripSharesTheKeyboardBlurRadius() throws Exception {
        try (Fixture f = new Fixture()) {
            f.show();
            f.enableNavigationAndKeys(-1);
            f.preDraw();
            Object state = f.state();
            Field navigation = state.getClass().getDeclaredField("navigation");
            navigation.setAccessible(true);
            Object surface = navigation.get(state);
            Field radius = surface.getClass().getDeclaredField("radius");
            radius.setAccessible(true);
            Field local = state.getClass().getDeclaredField("localRadiusPx");
            local.setAccessible(true);
            // The compositor caches one blur per layer and radius; a different strip radius
            // would make it generate a second blur on every composition.
            assertEquals(local.getInt(state), radius.getInt(surface));
            assertEquals(32, radius.getInt(surface));
        }
    }

    @Test public void hideCancelsPendingFallbackWork() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.setTag("not-yet");
            f.show();
            f.candidate.requestLayout();
            f.idle(20);
            Object state = f.state();
            Field fallback = state.getClass().getDeclaredField("fallback");
            fallback.setAccessible(true);
            assertNotNull(fallback.get(state));
            GboardFrostedGlassRuntime.onWindowHidden(f.ime);
            assertNull(fallback.get(state));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> host = Robolectric.buildActivity(Activity.class).setup().visible();
        final Context context = host.get();
        final Dialog dialog = new Dialog(context);
        final GboardFrostedGlassLifecycleDirectBootTest.GuardedIme ime =
                Robolectric.setupService(GboardFrostedGlassLifecycleDirectBootTest.GuardedIme.class);
        final InputView input = new InputView(context);
        final Group root = new Group(context);
        final GradientDrawable theme = new GradientDrawable();
        final Group[] rows = new Group[4];
        final FrameLayout[] keys = new FrameLayout[40];
        final GradientDrawable[] keyFills = new GradientDrawable[40];
        final TextView candidate = new TextView(context);

        Fixture() {
            shadowOf(RuntimeEnvironment.getApplication().getSystemService(UserManager.class))
                    .setUserUnlocked(true);
            GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.enabled = true;
            GboardFrostedGlassLifecycleDirectBootTest.CountingSettings.reads = 0;
            input.setPadding(0, 0, 0, 100);
            theme.setColor(Color.BLACK);
            theme.setCornerRadius(32);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setBackground(theme);
            Group header = new Group(context);
            header.setTag(".keyboard-header-area");
            candidate.setText("a");
            header.addView(candidate, new FrameLayout.LayoutParams(400, 100));
            root.addView(header, new FrameLayout.LayoutParams(1000, 100));
            Group body = new Group(context);
            body.setTag(".keyboard-body-area");
            body.setBackground(new ColorDrawable(Color.DKGRAY));
            root.addView(body, new FrameLayout.LayoutParams(1000, 500));
            for (int r = 0; r < 4; r++) {
                rows[r] = new Group(context);
                body.addView(rows[r], new FrameLayout.LayoutParams(1000, 100));
                for (int k = 0; k < 10; k++) {
                    FrameLayout key = new FrameLayout(context);
                    key.setTag(".keytop");
                    GradientDrawable fill = new GradientDrawable();
                    fill.setColor(Color.WHITE);
                    fill.setCornerRadius(12);
                    keyFills[r * 10 + k] = fill;
                    keys[r * 10 + k] = key;
                    key.setBackground(new InsetDrawable(fill, 3));
                    rows[r].addView(key, new FrameLayout.LayoutParams(100, 100));
                }
            }
            input.addView(root, new FrameLayout.LayoutParams(1000, 600));
            dialog.setContentView(input, new FrameLayout.LayoutParams(1000, 700));
            dialog.show();
            shadowOf(Looper.getMainLooper()).idle();
            ime.overrideDialog = dialog;
        }

        View decor() { return dialog.getWindow().getDecorView(); }

        void show() {
            GboardFrostedGlassRuntime.onWindowShown(ime);
        }

        void preDraw() {
            ViewTreeObserver tree = decor().getViewTreeObserver();
            assertFalse(tree.dispatchOnPreDraw());
        }

        void idle(long ms) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        }

        Object state() throws Exception {
            Field states = GboardFrostedGlassRuntime.class.getDeclaredField("STATES");
            states.setAccessible(true);
            return ((Map<?, ?>) states.get(null)).get(dialog.getWindow());
        }

        Object pendingFallback() throws Exception {
            Object state = state();
            Field fallback = state.getClass().getDeclaredField("fallback");
            fallback.setAccessible(true);
            return fallback.get(state);
        }

        /** The seamless-nav setting has no host provider; set the loaded snapshot directly. */
        void enableNavigationAndKeys(int keyAlpha) throws Exception {
            preDraw();
            Object state = state();
            Field nav = state.getClass().getDeclaredField("navigationEnabled");
            nav.setAccessible(true);
            nav.setBoolean(state, true);
            Field keys = state.getClass().getDeclaredField("customKeyOpacity");
            keys.setAccessible(true);
            keys.setInt(state, keyAlpha);
            preDraw();
        }

        @Override public void close() {
            GboardFrostedGlassRuntime.onWindowHidden(ime);
            dialog.dismiss();
            host.close();
        }
    }

    static final class Group extends FrameLayout {
        Group(Context context) { super(context); }
        @Override public View getChildAt(int index) {
            childReads++;
            return super.getChildAt(index);
        }
        @Override public void getLocationInWindow(int[] out) {
            if (".keyboard-base-area.keyboard-outline".equals(getTag())) rootLocationReads++;
            super.getLocationInWindow(out);
        }
    }
}
