package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Binder;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import com.google.android.libraries.inputmethod.inputview.InputView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardFrostedGlassHardeningTest {
    // ---- G1/G2: any software redraw of the IME window must skip the blur layers ----

    @Test public void softwareRedrawOfTheKeyboardBackgroundDrawsOnlyTheTheme() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Bitmap bitmap = whiteBitmap();
            // Real BackgroundBlurDrawable + software Canvas: the reported IME crash.
            f.root.getBackground().setBounds(0, 0, 1000, 600);
            f.root.getBackground().draw(new Canvas(bitmap));
            assertEquals(178, Color.red(bitmap.getPixel(500, 300)), 1);
            bitmap.recycle();
        }
    }

    @Test @Config(sdk = {31, 32, 33, 35})
    public void softwareRedrawOfTheWholeInputViewAlsoSurvivesTheNavigationOverlay() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            assertNotNull(f.navigationSurface());
            Bitmap bitmap = whiteBitmap();
            // Same traversal as CopyImageView: View.draw reaches the background, then the overlay.
            f.input.draw(new Canvas(bitmap));
            assertEquals("The tint is still drawn into the snapshot",
                    178, Color.red(bitmap.getPixel(500, 950)), 1);
            bitmap.recycle();
        }
    }

    @Test public void softwareRedrawDoesNotDisposeTheLiveGlass() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Drawable background = f.root.getBackground();
            Bitmap bitmap = whiteBitmap();
            f.input.draw(new Canvas(bitmap));
            bitmap.recycle();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertSame(background, f.root.getBackground());
            assertTrue(f.input.willNotDraw());
        }
    }

    // ---- G3: a blur drawable failing on a hardware Canvas degrades instead of crashing ----

    @Test public void hardwareCanvasStillDrawsEveryLayerInOrder() {
        RecordingLayer blur = new RecordingLayer(false);
        RecordingLayer tint = new RecordingLayer(false);
        new GboardFrostedGlassLayerDrawable(new Drawable[]{blur, tint}, blur).draw(hardwareCanvas());
        assertEquals(1, blur.draws);
        assertEquals(1, tint.draws);
    }

    @Test public void softwareCanvasNeverReachesTheBlurLayer() {
        RecordingLayer blur = new RecordingLayer(true);
        RecordingLayer theme = new RecordingLayer(false);
        Bitmap bitmap = whiteBitmap();
        new GboardFrostedGlassLayerDrawable(new Drawable[]{theme, blur}, blur).draw(new Canvas(bitmap));
        bitmap.recycle();
        assertEquals(0, blur.draws);
        assertEquals(1, theme.draws);
    }

    @Test public void aThrowingBlurIsReportedOnceAndNeverDrawnAgain() {
        RecordingLayer blur = new RecordingLayer(true);
        RecordingLayer theme = new RecordingLayer(false);
        int[] failures = {0};
        Throwable[] reported = new Throwable[1];
        GboardFrostedGlassLayerDrawable layer =
                new GboardFrostedGlassLayerDrawable(new Drawable[]{theme, blur}, blur);
        layer.setBlurFailureListener(failure -> {
            failures[0]++;
            reported[0] = failure;
        });
        layer.draw(hardwareCanvas());
        assertEquals("A failing blur must not paint the theme twice", 1, theme.draws);
        layer.draw(hardwareCanvas());
        assertEquals(1, blur.draws);
        assertEquals(2, theme.draws);
        assertEquals(1, failures[0]);
        assertNotNull("The cause must reach the log", reported[0]);
    }

    @Test public void aThrowingFailureListenerCannotEscapeDraw() {
        RecordingLayer blur = new RecordingLayer(true);
        GboardFrostedGlassLayerDrawable layer =
                new GboardFrostedGlassLayerDrawable(new Drawable[]{blur}, blur);
        layer.setBlurFailureListener(failure -> { throw new IllegalStateException("Injected"); });
        layer.draw(hardwareCanvas());
    }

    @Test public void aBlurFailureEndsThatSessionAndALaterSessionRetries() throws Exception {
        try (Fixture f = new Fixture()) {
            try {
                f.apply();
                // The real drawable throws from drawRenderNode on this non-recording Canvas.
                f.root.getBackground().draw(hardwareCanvas());
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertSame("The session falls back to the stock theme", f.theme, f.root.getBackground());
                assertEquals(255, f.theme.getAlpha());
                assertFalse(f.input.willNotDraw());
                assertNotNull("One failure must not end blur for the whole process",
                        invoke("createBackgroundBlurDrawable",
                        new Class<?>[]{View.class, int.class, GboardFrostedGlassShape.class},
                        f.root, 32, GboardFrostedGlassShape.rectangle(new android.graphics.Rect(0, 0, 10, 10))));
            } finally {
                Fixture.resetBlurBroken();
            }
        }
    }

    @Test public void aBackendThatKeepsFailingIsAbandonedForTheProcess() throws Exception {
        try (Fixture f = new Fixture()) {
            try {
                f.apply();
                Fixture.setBlurFailures(Fixture.maxBlurFailures() - 1);
                f.root.getBackground().draw(hardwareCanvas());
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertNull(invoke("createBackgroundBlurDrawable",
                        new Class<?>[]{View.class, int.class, GboardFrostedGlassShape.class},
                        f.root, 32, GboardFrostedGlassShape.rectangle(new android.graphics.Rect(0, 0, 10, 10))));
            } finally {
                Fixture.resetBlurBroken();
            }
        }
    }

    @Test public void thePanelAndStripFailingInOneFrameCountOnce() throws Exception {
        try (Fixture f = new Fixture()) {
            try {
                f.applyWithSettings(77);
                f.root.getBackground().draw(hardwareCanvas());
                f.navigationSurface().draw(hardwareCanvas());
                assertEquals(1, Fixture.blurFailures());
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            } finally {
                Fixture.resetBlurBroken();
            }
        }
    }

    @Test public void navigationReturnsToStockWhenTheStripBlurIsUnavailable() throws Exception {
        try (Fixture f = new Fixture()) {
            try {
                f.applyWithSettings(77);
                assertEquals(Color.TRANSPARENT, f.window.getNavigationBarColor());
                f.clearNavigation();
                Fixture.setBlurBroken(true);
                Drawable panel = f.root.getBackground();
                f.applyWithSettings(77);
                assertNull("Without a backdrop the strip must not take over",
                        f.rawNavigationSurface());
                assertFalse(f.input.willNotDraw());
                assertEquals(0xff123456, f.window.getNavigationBarColor());
                assertSame("The panel keeps its own glass", panel, f.root.getBackground());
            } finally {
                Fixture.resetBlurBroken();
            }
        }
    }

    // ---- G4: delayed callbacks run outside every lifecycle try/catch ----

    @Test public void aFailingFallbackRepostCannotEscapeTheLooper() throws Exception {
        try (Fixture f = new Fixture()) {
            int[] posts = {0};
            View host = new View(f.controller.get()) {
                @Override public boolean postDelayed(Runnable action, long delayMillis) {
                    if (++posts[0] > 1) throw new IllegalStateException("Injected post failure");
                    return true;
                }
            };
            Method schedule = GboardFrostedGlassRuntime.class.getDeclaredMethod("scheduleFallback",
                    View.class, Window.class, android.content.Context.class,
                    f.stateClass, long.class);
            schedule.setAccessible(true);
            schedule.invoke(null, host, f.window, null, f.state, 0L);
            Field fallback = f.stateClass.getDeclaredField("fallback");
            fallback.setAccessible(true);
            ((Runnable) fallback.get(f.state)).run();
        }
    }

    @Test public void hideRestoreRunnableIsGuardedAndStillRestores() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            invoke("hideAfterAnimation", new Class<?>[]{Window.class}, f.window);
            Field restore = f.stateClass.getDeclaredField("hideRestore");
            restore.setAccessible(true);
            ((Runnable) restore.get(f.state)).run();
            assertSame(f.theme, f.root.getBackground());
        }
    }

    // ---- G5: a blur drawable belongs to the ViewRootImpl that created it ----

    @Test public void aNewWindowTokenRebuildsTheKeyboardBlurDrawable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Object first = f.field("localBlurDrawable");
            f.setField("localBlurWindowToken", new Binder());
            f.apply();
            assertNotSame(first, f.field("localBlurDrawable"));
            assertSame(f.root.getWindowToken(), f.field("localBlurWindowToken"));
            assertEquals("Theme is not stacked on our composite", 77, f.theme.getAlpha());
        }
    }

    @Test public void theSameWindowTokenKeepsTheKeyboardBlurDrawable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Object first = f.field("localBlurDrawable");
            f.apply();
            assertSame(first, f.field("localBlurDrawable"));
        }
    }

    @Test public void aNewWindowTokenRebuildsTheNavigationSurface() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Drawable first = f.rawNavigationSurface();
            Field navigation = f.stateClass.getDeclaredField("navigation");
            navigation.setAccessible(true);
            Object surface = navigation.get(f.state);
            Field token = surface.getClass().getDeclaredField("windowToken");
            token.setAccessible(true);
            token.set(surface, new Binder());
            f.apply();
            assertNotSame(first, f.rawNavigationSurface());
        }
    }

    // ---- G6: outline requests reuse one joined Path ----

    @Test public void joinedOutlineReusesItsPathUntilTheGeometryChanges() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Drawable background = f.root.getBackground();
            Field path = background.getClass().getDeclaredField("joinedPath");
            path.setAccessible(true);
            Object first = path.get(background);
            assertNotNull(first);
            Outline outline = new Outline();
            background.getOutline(outline);
            background.getOutline(outline);
            assertTrue(outline.canClip());
            assertSame(first, path.get(background));
        }
    }

    @Test public void aThemeOutlineFailureCannotEscapeIntoTheViewTraversal() throws Exception {
        try (Fixture f = new Fixture()) {
            RejectOutlineGradient theme = new RejectOutlineGradient();
            theme.setColor(Color.BLACK);
            f.root.setBackground(theme);
            f.apply();
            // Not joined here: the request reaches the theme outline.
            f.root.layout(100, 300, 900, 900);
            f.apply();
            theme.rejectOutline = true;
            try {
                Outline outline = new Outline();
                f.root.getBackground().getOutline(outline);
                assertTrue(outline.isEmpty());
            } finally {
                theme.rejectOutline = false;
            }
        }
    }

    @Test public void oneFailingLayoutRestoreCannotStrandTheOtherViews() throws Exception {
        try (Fixture f = new Fixture()) {
            View bad = new View(f.controller.get()) {
                @Override public void setLayoutParams(ViewGroup.LayoutParams params) {
                    if (getLayoutParams() != null) throw new IllegalStateException("Injected layout failure");
                    super.setLayoutParams(params);
                }
            };
            bad.setLayoutParams(new FrameLayout.LayoutParams(10, 20));
            FrameLayout good = new FrameLayout(f.controller.get());
            good.setLayoutParams(new FrameLayout.LayoutParams(10, 20));
            Field layouts = f.stateClass.getDeclaredField("originalLayouts");
            layouts.setAccessible(true);
            Class<?> snapshotClass = Class.forName(
                    GboardFrostedGlassRuntime.class.getName() + "$LayoutSnapshot");
            java.lang.reflect.Constructor<?> snapshot = snapshotClass.getDeclaredConstructor(View.class);
            snapshot.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<View, Object> saved = (java.util.Map<View, Object>) layouts.get(f.state);
            saved.put(bad, snapshot.newInstance(bad));
            saved.put(good, snapshot.newInstance(good));
            good.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            invoke("restoreKeyboardLayout", new Class<?>[]{f.stateClass}, f.state);
            assertEquals("A view after a failing one must still be restored",
                    20, good.getLayoutParams().height);
            assertTrue(saved.isEmpty());
        }
    }

    // ---- N1: below Android 16 the strip is the system-drawn Window navigation color ----

    @Test @Config(sdk = {31, 33, 35})
    public void windowStripFollowsTheCustomOpacityWithoutChangingGeometry() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.applyWithSettings(77);
            assertEquals(Color.argb(77, 0x12, 0x34, 0x56), f.window.getNavigationBarColor());
            assertEquals(0, f.input.getPaddingBottom());
            assertNull("No overlay is needed for a system-drawn strip", f.rawNavigationSurface());
            assertEquals("A window strip must not lay content out behind the bar", 0,
                    f.window.getDecorView().getSystemUiVisibility()
                            & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            assertFalse(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void windowStripRestoresTheStockColorOnDisable() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.applyWithSettings(77);
            f.disable();
            assertEquals(0xff123456, f.window.getNavigationBarColor());
            assertTrue(f.window.isNavigationBarContrastEnforced());
        }
    }

    @Test public void windowStripTracksOpacityChangesAndReleasesWhenOpaque() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.applyWithSettings(77);
            f.applyWithSettings(128);
            assertEquals(Color.argb(128, 0x12, 0x34, 0x56), f.window.getNavigationBarColor());
            f.applyWithSettings(255);
            assertEquals(0xff123456, f.window.getNavigationBarColor());
        }
    }

    @Test public void themeModeUsesTheThemesOwnAlphaForTheWindowStrip() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.theme.setAlpha(100);
            f.applyWithSettings(-1);
            assertEquals(Color.argb(100, 0x12, 0x34, 0x56), f.window.getNavigationBarColor());
        }
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.theme.setColor(0x40000000);
            f.applyWithSettings(-1);
            assertEquals("A translucent theme color counts as the theme's alpha",
                    Color.argb(64, 0x12, 0x34, 0x56), f.window.getNavigationBarColor());
        }
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.applyWithSettings(-1);
            assertEquals("An opaque theme keeps stock", 0xff123456, f.window.getNavigationBarColor());
        }
    }

    @Test public void windowStripHonorsTheNavigationSetting() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.setField("navigationEnabled", false);
            f.applyWithSettings(77);
            assertEquals(0xff123456, f.window.getNavigationBarColor());
        }
    }

    @Test public void floatingKeyboardNeverTakesTheWindowStrip() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.root.setTag(".keyboard-base-area");
            f.applyWithSettings(77);
            assertEquals(0xff123456, f.window.getNavigationBarColor());
        }
    }

    @Test public void android16WithoutPaddingNeverTakesTheWindowStrip() throws Exception {
        int sdk = Build.VERSION.SDK_INT;
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 36);
            f.applyWithSettings(77);
            assertEquals("Android 16 stock paints its own strip; the Window color is not it",
                    0xff123456, f.window.getNavigationBarColor());
        } finally {
            ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", sdk);
        }
    }

    @Test public void aPaddedStripOnOlderAndroidKeepsUsingTheOverlay() throws Exception {
        try (Fixture f = new Fixture()) {
            f.applyWithSettings(77);
            assertNotNull(f.rawNavigationSurface());
            assertEquals(Color.TRANSPARENT, f.window.getNavigationBarColor());
        }
    }

    @Test public void stableWindowStripFramesDoNotRewriteTheWindow() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            f.applyWithSettings(77);
            f.window.setNavigationBarColor(Color.YELLOW);
            f.setField("structurePass", -1L);
            f.setField("structureDirty", false);
            f.applyWithSettings(77);
            assertEquals("A stable frame performs no Window writes",
                    Color.YELLOW, f.window.getNavigationBarColor());
        }
    }

    @Test @Config(shadows = GboardFrostedGlassFailureTest.ThrowingWindow.class)
    public void aFailingWindowStripKeepsThePanelAndDoesNotRetryEachDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            f.useWindowStrip();
            GboardFrostedGlassFailureTest.ThrowingWindow.rejectNav = true;
            try {
                f.applyWithSettings(77);
                int failures = GboardFrostedGlassFailureTest.ThrowingWindow.failedNavCalls;
                assertTrue(failures > 0);
                f.applyWithSettings(77);
                assertEquals(failures, GboardFrostedGlassFailureTest.ThrowingWindow.failedNavCalls);
                assertEquals("The panel keeps its glass", 77, f.theme.getAlpha());
            } finally {
                GboardFrostedGlassFailureTest.ThrowingWindow.rejectNav = false;
            }
        }
    }

    // ---- N2: one-handed and other narrow docked keyboards ----

    @Test public void narrowDockedKeyboardStillOwnsTheFullWidthStrip() throws Exception {
        try (Fixture f = new Fixture()) {
            f.narrow(100, 900);
            f.apply();
            assertTrue(f.input.willNotDraw());
            assertEquals(new android.graphics.Rect(0, 900, 1000, 1000),
                    f.rawNavigationSurface().getBounds());
            assertEquals("Only a full-width panel may square its corners",
                    32f, f.effectiveShape().bottomLeft, 0f);
            assertEquals(32f, f.effectiveShape().bottomRight, 0f);
        }
    }

    @Test public void aPanelOutsideTheInputViewKeepsStockNavigation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.narrow(-50, 900);
            f.apply();
            assertFalse(f.input.willNotDraw());
            assertNull(f.rawNavigationSurface());
        }
    }

    // ---- helpers ----

    private static Bitmap whiteBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        return bitmap;
    }

    private static Canvas hardwareCanvas() {
        return new Canvas(whiteBitmap()) {
            @Override public boolean isHardwareAccelerated() { return true; }
        };
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static final class RejectOutlineGradient extends GradientDrawable {
        boolean rejectOutline;
        @Override public void getOutline(Outline outline) {
            if (rejectOutline) throw new IllegalStateException("Injected outline failure");
            super.getOutline(outline);
        }
    }

    private static final class RecordingLayer extends ColorDrawable {
        final boolean fail;
        int draws;
        RecordingLayer(boolean fail) { this.fail = fail; }
        @Override public void draw(Canvas canvas) {
            draws++;
            if (fail) throw new IllegalArgumentException("Injected blur failure");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup().visible();
        final Window window = controller.get().getWindow();
        final InputView input = new InputView(controller.get());
        final FrameLayout root = new FrameLayout(controller.get());
        final GradientDrawable theme = new GradientDrawable();
        final Class<?> stateClass;
        final Object state;

        Fixture() throws Exception {
            Shadows.shadowOf(controller.get().getSystemService(android.os.UserManager.class))
                    .setUserUnlocked(true);
            input.setPadding(0, 0, 0, 100);
            theme.setColor(Color.BLACK);
            theme.setCornerRadius(32);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setBackground(theme);
            input.addView(root, new FrameLayout.LayoutParams(1000, 600));
            controller.get().setContentView(input);
            input.layout(0, 0, 1000, 1000);
            root.layout(0, 300, 1000, 900);
            window.setNavigationBarColor(0xff123456);
            window.setNavigationBarContrastEnforced(true);
            stateClass = Class.forName(GboardFrostedGlassRuntime.class.getName() + "$State");
            state = invoke("stateFor", new Class<?>[]{Window.class}, window);
        }

        /** The Android 15 shape: no InputView padding, the keyboard reaches the bottom. */
        void useWindowStrip() {
            input.setPadding(0, 0, 0, 0);
            root.layout(0, 300, 1000, 1000);
        }

        void narrow(int left, int right) {
            root.layout(left, 300, right, 900);
        }

        void apply() throws Exception {
            applyRaw(77);
        }

        void applyWithSettings(int alpha) throws Exception {
            setField("settingsLoaded", true);
            applyRaw(alpha);
        }

        private void applyRaw(int alpha) throws Exception {
            assertEquals(true, invoke("applyMiuiLocalBlur", new Class<?>[]{Window.class,
                    View.class, int.class, int.class, stateClass},
                    window, window.getDecorView(), 32, alpha, state));
        }

        Object field(String name) throws Exception {
            Field field = stateClass.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(state);
        }

        void setField(String name, Object value) throws Exception {
            Field field = stateClass.getDeclaredField(name);
            field.setAccessible(true);
            field.set(state, value);
        }

        GboardFrostedGlassShape effectiveShape() throws Exception {
            return (GboardFrostedGlassShape) field("localShape");
        }

        LayerDrawable rawNavigationSurface() throws Exception {
            Object navigation = field("navigation");
            Field surface = navigation.getClass().getDeclaredField("surface");
            surface.setAccessible(true);
            return (LayerDrawable) surface.get(navigation);
        }

        LayerDrawable navigationSurface() throws Exception {
            LayerDrawable surface = rawNavigationSurface();
            assertNotNull(surface);
            assertTrue(surface.getDrawable(0).getClass().getName().endsWith("BackgroundBlurDrawable"));
            return surface;
        }

        void clearNavigation() throws Exception {
            Object navigation = field("navigation");
            Method clear = navigation.getClass().getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(navigation);
        }

        static void setBlurBroken(boolean broken) throws Exception {
            setBlurFailures(broken ? maxBlurFailures() : 0);
        }

        static int maxBlurFailures() throws Exception {
            Field field = GboardFrostedGlassRuntime.class.getDeclaredField("MAX_BLUR_DRAW_FAILURES");
            field.setAccessible(true);
            return field.getInt(null);
        }

        static int blurFailures() throws Exception {
            return blurFailureCounter().get();
        }

        static void setBlurFailures(int count) throws Exception {
            blurFailureCounter().set(count);
        }

        private static java.util.concurrent.atomic.AtomicInteger blurFailureCounter() throws Exception {
            Field field = GboardFrostedGlassRuntime.class.getDeclaredField("BLUR_DRAW_FAILURES");
            field.setAccessible(true);
            return (java.util.concurrent.atomic.AtomicInteger) field.get(null);
        }

        static void resetBlurBroken() throws Exception {
            setBlurBroken(false);
        }

        void disable() throws Exception {
            invoke("disable", new Class<?>[]{Window.class}, window);
        }

        @Override public void close() throws Exception {
            disable();
            controller.pause().stop().destroy();
        }
    }
}
