package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import com.google.android.libraries.inputmethod.inputview.InputView;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardFrostedGlassNavigationTest {
    @Test public void navigationUsesTheSameTintWithoutChangingInsetsOrKeyboardGeometry() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(Color.RED, f.pixel(500, 950));
            f.apply();
            assertTrue("Suppress only the admitted InputView's stock nav paint", f.input.willNotDraw());
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
            assertEquals(Color.WHITE, f.navigationPixel(500, 50));
            assertEquals(100, f.input.getPaddingBottom());
            assertEquals(600, f.root.getHeight());
            assertEquals(300, f.root.getTop());
            assertNull(f.input.getBackground());
            assertEquals(77, f.theme.getAlpha());
        }
    }

    @Test public void hidingRestoresStockPaintAndThemeAlpha() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            f.disable();
            assertFalse(f.input.willNotDraw());
            assertEquals(Color.RED, f.pixel(500, 950));
            assertEquals(255, f.theme.getAlpha());
        }
    }

    @Test public void transparentOutlineUsesTheBodyThemeForNavigationTint() throws Exception {
        try (Fixture f = new Fixture()) {
            f.theme.setColor(Color.TRANSPARENT);
            View body = new View(f.controller.get());
            body.setTag(".keyboard-body-area");
            body.setBackgroundColor(Color.BLACK);
            f.root.addView(body);
            body.layout(0, 0, 1000, 600);
            f.apply();
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
        }
    }

    @Test public void navigationCloneRetainsTheLiveThemeColorFilter() throws Exception {
        try (Fixture f = new Fixture()) {
            f.theme.setColor(Color.RED);
            f.theme.setColorFilter(Color.BLUE, android.graphics.PorterDuff.Mode.SRC_IN);
            f.apply();
            assertEquals(255, Color.blue(f.navigationPixel(500, 950)));
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
            assertEquals(Color.RED, f.theme.getColor().getDefaultColor());
            f.theme.setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN);
            f.apply();
            assertEquals(255, Color.green(f.navigationPixel(500, 950)));
            assertEquals(178, Color.blue(f.navigationPixel(500, 950)), 1);
            f.theme.clearColorFilter();
            f.apply();
            assertEquals(255, Color.red(f.navigationPixel(500, 950)));
            assertEquals(178, Color.blue(f.navigationPixel(500, 950)), 1);
        }
    }

    @Test public void coloredThemesUseTheBodyPaletteRatherThanTheOutlinePalette() throws Exception {
        try (Fixture f = new Fixture()) {
            f.theme.setColor(0xff17345c);
            View body = new View(f.controller.get());
            body.setTag(".keyboard-body-area");
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.RED);
            background.setColorFilter(0xff286cb5, android.graphics.PorterDuff.Mode.SRC_IN);
            body.setBackground(background);
            f.root.addView(body);
            body.layout(0, 0, 1000, 600);
            f.apply();
            int actual = f.navigationPixel(500, 950);
            assertEquals(190, Color.red(actual), 1);
            assertEquals(211, Color.green(actual), 1);
            assertEquals(233, Color.blue(actual), 1);
            assertEquals(0xff17345c, f.theme.getColor().getDefaultColor());
            assertEquals(Color.RED, background.getColor().getDefaultColor());
        }
    }

    @Test public void dockedGlassJoinsAtSquareInternalCornersWithoutChangingTheTheme() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            assertEquals(32f, f.effectiveShape().topLeft, 0f);
            assertEquals(0f, f.effectiveShape().bottomLeft, 0f);
            assertEquals(0f, f.effectiveShape().bottomRight, 0f);
            assertEquals(new android.graphics.Rect(0, 900, 1000, 1000), f.navigationSurface().getBounds());
            assertEquals(32f, f.theme.getCornerRadius(), 0f);
            assertEquals(100, f.input.getPaddingBottom());
            assertEquals(600, f.root.getHeight());
        }
    }

    @Test public void disablingSeamlessRetainsAllOfTheOriginalCorners() throws Exception {
        try (Fixture f = new Fixture()) {
            var field = f.stateClass.getDeclaredField("navigationEnabled");
            field.setAccessible(true);
            field.set(f.state, false);
            f.apply();
            assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
            assertEquals(32f, f.effectiveShape().bottomRight, 0f);
            assertFalse(f.input.willNotDraw());
        }
    }

    @Test public void anUnsupportedNavigationTintCannotChangeThePanelCorners() throws Exception {
        try (Fixture f = new Fixture()) {
            f.theme.setColor(Color.TRANSPARENT);
            View body = new View(f.controller.get());
            body.setTag(".keyboard-body-area");
            body.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.RED, Color.BLUE}));
            f.root.addView(body);
            f.apply();
            assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
            assertFalse(f.input.willNotDraw());
        }
    }

    @Test public void anImageThemeMustNotClearTheWindowWithoutANavigationBackdrop() throws Exception {
        try (Fixture f = new Fixture()) {
            org.robolectric.Shadows.shadowOf(f.controller.get().getSystemService(android.os.UserManager.class))
                    .setUserUnlocked(true);
            f.window.setNavigationBarColor(Color.DKGRAY);
            var loaded = f.stateClass.getDeclaredField("settingsLoaded");
            loaded.setAccessible(true);
            loaded.set(f.state, true);
            // A supported theme may already own a transparent Window in this session.
            f.apply();
            assertEquals(Color.TRANSPARENT, f.window.getNavigationBarColor());
            Bitmap image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            image.eraseColor(Color.LTGRAY);
            View body = new View(f.controller.get());
            body.setTag(".keyboard-body-area");
            body.setBackground(new android.graphics.drawable.BitmapDrawable(
                    f.controller.get().getResources(), image));
            f.root.addView(body);
            f.apply();
            assertEquals("No backdrop must mean stock color, not an empty transparent hole",
                    Color.DKGRAY, f.window.getNavigationBarColor());
            assertFalse(f.input.willNotDraw());
            assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
        }
    }

    @Test public void joiningRetainsAsymmetricTopCornersAndTheOriginalOutline() throws Exception {
        try (Fixture f = new Fixture()) {
            float[] corners = {16,16,24,24,48,48,8,8};
            f.theme.setCornerRadii(corners);
            f.root.setClipToOutline(true);
            f.apply();
            assertEquals(16f, f.effectiveShape().topLeft, 0f);
            assertEquals(24f, f.effectiveShape().topRight, 0f);
            assertEquals(0f, f.effectiveShape().bottomLeft, 0f);
            assertArrayEquals(corners, f.theme.getCornerRadii(), 0f);
            android.graphics.Outline outline = new android.graphics.Outline();
            f.root.getBackground().getOutline(outline);
            assertTrue(outline.canClip());
        }
    }

    @Test public void floatingTransitionRemovesOnlyTheNavigationSurface() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            f.input.setPadding(0, 0, 0, 0);
            f.root.setTag(".keyboard-base-area");
            f.apply();
            assertFalse(f.input.willNotDraw());
            assertEquals(Color.WHITE, f.pixel(500, 950));
            assertEquals(77, f.theme.getAlpha());
            assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
        }
    }

    @Test public void unknownInputViewBackgroundAndNonAdjacentPanelRemainStock() throws Exception {
        try (Fixture f = new Fixture()) {
            f.root.layout(0, 300, 1000, 880);
            f.apply();
            assertEquals(Color.RED, f.pixel(500, 950));
            assertFalse(f.input.willNotDraw());
        }
        try (Fixture f = new Fixture()) {
            f.input.setBackgroundColor(Color.BLUE);
            f.apply();
            assertEquals(Color.RED, f.pixel(500, 950));
            assertFalse(f.input.willNotDraw());
        }
    }

    @Test public void stockNavRefreshCannotRestoreOpaquePaintOverTheGlassSurface() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Drawable first = f.navigationSurface();
            // InputView.a() recomputes this bit after stock palette/inset refreshes.
            f.input.setWillNotDraw(false);
            f.apply();
            assertTrue(f.input.willNotDraw());
            assertSame(first, f.navigationSurface());
        }
    }

    @Test public void themeChangesAndRepeatedDrawsDoNotStackNavigationTint() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            Drawable first = f.navigationSurface();
            for (int i = 0; i < 12; i++) f.apply();
            assertSame(first, f.navigationSurface());
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
            f.theme.setColor(Color.BLUE);
            f.apply();
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
            assertEquals(255, Color.blue(f.navigationPixel(500, 950)));
        }
    }

    @Test public void colorDrawablesAndOpacityChangesKeepTheirOriginalRgb() throws Exception {
        try (Fixture f = new Fixture()) {
            android.graphics.drawable.ColorDrawable theme =
                    new android.graphics.drawable.ColorDrawable(Color.BLACK);
            f.root.setBackground(theme);
            theme.setBounds(0, 0, 1000, 600);
            f.apply();
            assertEquals(178, Color.red(f.navigationPixel(500, 950)), 1);
            f.apply(0);
            assertEquals(Color.WHITE, f.navigationPixel(500, 950));
            f.disable();
            assertEquals(Color.BLACK, theme.getColor());
        }
    }

    @Test public void controlsInsideTheNavigationAreaAreNeverCovered() throws Exception {
        try (Fixture f = new Fixture()) {
            View control = new View(f.controller.get());
            f.input.addView(control);
            control.layout(0, 900, 50, 1000);
            f.apply();
            assertFalse(f.input.willNotDraw());
            assertEquals(Color.RED, f.pixel(500, 950));
        }
    }

    @Test public void failingTintCloneRestoresTheOriginalBottomCorners() throws Exception {
        try (Fixture f = new Fixture()) {
            f.apply();
            assertEquals(0f, f.effectiveShape().bottomLeft, 0f);
            View body = new View(f.controller.get());
            body.setTag(".keyboard-body-area");
            body.setBackground(new UnclonableGradient());
            f.root.addView(body);
            f.apply();
            assertFalse(f.input.willNotDraw());
            assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
            assertEquals(32f, f.effectiveShape().bottomRight, 0f);
        }
    }

    @Test public void oneFailingKeyRestoreCannotStrandTheNavOverlayAndMainBackground() throws Exception {
        try (Fixture f = new Fixture()) {
            RejectRestoreGradient background = new RejectRestoreGradient();
            background.setColor(Color.WHITE);
            View key = new View(f.controller.get());
            key.setTag(".keytop");
            key.setBackground(background);
            f.root.addView(key);
            var alpha = f.stateClass.getDeclaredField("customKeyOpacity");
            alpha.setAccessible(true);
            alpha.setInt(f.state, 77);
            f.apply();
            background.rejectRestore = true;
            try {
                f.disable();
                assertFalse("Navigation paint must be returned to stock", f.input.willNotDraw());
                assertSame("Main theme must be restored despite a bad key", f.theme, f.root.getBackground());
                assertEquals(255, f.theme.getAlpha());
            } finally {
                background.rejectRestore = false;
            }
        }
    }

    static final class RejectRestoreGradient extends GradientDrawable {
        boolean rejectRestore;
        @Override public void setAlpha(int alpha) {
            if (rejectRestore && alpha == 255) throw new IllegalStateException("Injected key restore failure");
            super.setAlpha(alpha);
        }
    }

    @Test
    @Config(shadows = GboardFrostedGlassFailureTest.ThrowingWindow.class)
    public void failingWindowApplicationReleasesTheNavSurfaceAndDoesNotRetryEachDraw() throws Exception {
        try (Fixture f = new Fixture()) {
            org.robolectric.Shadows.shadowOf(f.controller.get().getSystemService(android.os.UserManager.class))
                    .setUserUnlocked(true);
            var loaded = f.stateClass.getDeclaredField("settingsLoaded");
            loaded.setAccessible(true);
            loaded.setBoolean(f.state, true);
            GboardFrostedGlassFailureTest.ThrowingWindow.rejectNav = true;
            try {
                f.apply();
                assertFalse(f.input.willNotDraw());
                assertEquals(32f, f.effectiveShape().bottomLeft, 0f);
                int failures = GboardFrostedGlassFailureTest.ThrowingWindow.failedNavCalls;
                assertTrue(failures > 0);
                f.apply();
                assertEquals("A failed Window must not be retried each pre-draw", failures,
                        GboardFrostedGlassFailureTest.ThrowingWindow.failedNavCalls);
            } finally {
                GboardFrostedGlassFailureTest.ThrowingWindow.rejectNav = false;
            }
        }
    }

    static final class UnclonableGradient extends GradientDrawable {
        UnclonableGradient() { setColor(Color.BLUE); }
        @Override public ConstantState getConstantState() {
            return new ConstantState() {
                @Override public Drawable newDrawable() { throw new IllegalStateException("Injected clone failure"); }
                @Override public int getChangingConfigurations() { return 0; }
            };
        }
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
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
            input.setPadding(0, 0, 0, 100);
            theme.setColor(Color.BLACK);
            theme.setCornerRadius(32);
            root.setTag(".keyboard-base-area.keyboard-outline");
            root.setBackground(theme);
            input.addView(root, new FrameLayout.LayoutParams(1000, 600));
            controller.get().setContentView(input);
            input.layout(0, 0, 1000, 1000);
            root.layout(0, 300, 1000, 900);
            stateClass = Class.forName(GboardFrostedGlassRuntime.class.getName() + "$State");
            state = invoke("stateFor", new Class<?>[]{Window.class}, window);
        }

        void apply() throws Exception {
            apply(77);
        }

        void apply(int alpha) throws Exception {
            assertEquals(true, invoke("applyMiuiLocalBlur", new Class<?>[]{Window.class,
                    View.class, int.class, int.class, stateClass},
                    window, window.getDecorView(), 32, alpha, state));
        }

        int pixel(int x, int y) {
            root.setVisibility(View.INVISIBLE);
            try {
                return drawPixel(input::draw, x, y);
            } finally {
                root.setVisibility(View.VISIBLE);
            }
        }

        int navigationPixel(int x, int y) throws Exception {
            android.graphics.drawable.LayerDrawable surface = navigationSurface();
            assertEquals(1000, surface.getBounds().bottom);
            assertEquals(surface.getBounds(), surface.getDrawable(1).getBounds());
            // The compositor blur is exercised on-device; verify the actual cloned tint
            // and its bounds here using a software Canvas, which cannot draw RenderNodes.
            return drawPixel(surface.getDrawable(1)::draw, x, y);
        }

        GboardFrostedGlassShape effectiveShape() throws Exception {
            var field = stateClass.getDeclaredField("localShape");
            field.setAccessible(true);
            return (GboardFrostedGlassShape) field.get(state);
        }

        android.graphics.drawable.LayerDrawable navigationSurface() throws Exception {
            java.lang.reflect.Field field = stateClass.getDeclaredField("navigation");
            field.setAccessible(true);
            Object navigation = field.get(state);
            field = navigation.getClass().getDeclaredField("surface");
            field.setAccessible(true);
            android.graphics.drawable.LayerDrawable surface =
                    (android.graphics.drawable.LayerDrawable) field.get(navigation);
            assertNotNull(surface);
            assertTrue(surface.getDrawable(0).getClass().getName().endsWith("BackgroundBlurDrawable"));
            return surface;
        }

        int drawPixel(java.util.function.Consumer<Canvas> draw, int x, int y) {
            Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            draw.accept(new Canvas(bitmap));
            int color = bitmap.getPixel(x, y);
            bitmap.recycle();
            return color;
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
