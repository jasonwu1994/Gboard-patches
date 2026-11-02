package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.Window;
import java.lang.reflect.Field;
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
public class GboardFrostedGlassShapeTest {
    @Test public void localBlurMustNotRoundSquareThemeWhenOpacityIsOverridden() throws Exception {
        try (Fixture f = new Fixture(0, 0, 0, 0)) {
            f.apply(77);
            f.assertCorners(0, 0, 0, 0);
            assertEquals(77, f.theme.getAlpha());
            assertSame(f.original, ((LayerDrawable) f.root.getBackground()).getDrawable(0));
            assertFalse(f.root.getClipToOutline());
        }
    }

    @Test public void wrappedThemeOwnCornersSurviveEvenAtZeroAlpha() throws Exception {
        try (Fixture f = new Fixture(18, 18, 18, 18)) {
            f.root.setClipToOutline(true);
            f.apply(0);
            f.assertCorners(18, 18, 18, 18);
            Outline outline = new Outline();
            f.root.getBackground().getOutline(outline);
            assertEquals(18f, outline.getRadius(), 0f);
            assertTrue(f.root.getClipToOutline());
            assertEquals(0, f.theme.getAlpha());
        }
    }

    @Test public void asymmetricCornersKeepAndroidBottomLeftAndBottomRightOrder() throws Exception {
        try (Fixture f = new Fixture(8, 16, 24, 40)) {
            f.apply(77);
            f.assertCorners(8, 16, 24, 40);
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(f.root, f.original);
            assertArrayEquals(new float[]{8, 8, 16, 16, 40, 40, 24, 24}, shape.pathRadii(), 0f);
        }
    }

    @Test public void topOnlyAndBottomOnlyDoNotGrowExtraCorners() throws Exception {
        try (Fixture f = new Fixture(32, 32, 0, 0)) {
            f.apply(77);
            f.assertCorners(32, 32, 0, 0);
            f.theme.setCornerRadii(new float[]{0, 0, 0, 0, 48, 48, 48, 48});
            f.apply(120);
            f.assertCorners(0, 0, 48, 48);
            assertEquals(120, f.theme.getAlpha());
            assertEquals(2, ((LayerDrawable) f.root.getBackground()).getNumberOfLayers());
        }
    }

    @Test public void opacityReapplyAndFollowThemeNeverChangeBlurAlphaOrGeometry() throws Exception {
        try (Fixture f = new Fixture(13, 13, 13, 13)) {
            f.original.setAlpha(190);
            f.apply(77);
            Object blur = f.blur();
            int blurAlpha = ((Drawable) blur).getAlpha();
            f.apply(0);
            assertSame(blur, f.blur());
            assertEquals(0, f.original.getAlpha());
            assertEquals(blurAlpha, ((Drawable) blur).getAlpha());
            f.apply(-1);
            assertEquals(190, f.original.getAlpha());
            f.assertCorners(13, 13, 13, 13);
        }
    }

    @Test public void disableRestoresBackgroundAndAlphaAndInvalidatesOldSession() throws Exception {
        try (Fixture f = new Fixture(22, 22, 0, 0)) {
            f.original.setAlpha(173);
            f.apply(77);
            invoke("disable", new Class<?>[]{Window.class}, f.window);
            assertSame(f.original, f.root.getBackground());
            assertEquals(173, f.original.getAlpha());
            assertEquals(false, invoke("isCurrent", new Class<?>[]{Window.class, f.stateClass},
                    f.window, f.state));
            assertEquals(0, f.window.getAttributes().getBlurBehindRadius());
        }
    }

    @Test public void themeReplacementOnSameViewDoesNotRestoreOrWrapOldTheme() throws Exception {
        try (Fixture f = new Fixture(32, 32, 32, 32)) {
            f.apply(77);
            GradientDrawable replacement = new GradientDrawable();
            replacement.setColor(Color.BLUE);
            replacement.setCornerRadius(7);
            replacement.setAlpha(201);
            f.root.setBackground(replacement);
            f.apply(99);
            f.assertCorners(7, 7, 7, 7);
            assertSame(replacement, ((LayerDrawable) f.root.getBackground()).getDrawable(0));
            invoke("disable", new Class<?>[]{Window.class}, f.window);
            assertSame(replacement, f.root.getBackground());
            assertEquals(201, replacement.getAlpha());
            assertEquals(255, f.original.getAlpha());
        }
    }

    @Test public void unsupportedEllipseDoesNotBecomeRectangularBlur() throws Exception {
        try (Fixture f = new Fixture(0, 0, 0, 0)) {
            f.theme.setShape(GradientDrawable.OVAL);
            assertEquals(false, f.tryApply(77));
            assertSame(f.original, f.root.getBackground());
            assertEquals(255, f.original.getAlpha());
        }
    }

    @Test public void rootReinflateRestoresDetachedThemeAndUsesNewRootShape() throws Exception {
        try (Fixture f = new Fixture(32, 32, 32, 32)) {
            f.apply(77);
            View replacement = new View(f.controller.get());
            replacement.setTag(".keyboard-base-area");
            GradientDrawable theme = new GradientDrawable();
            theme.setColor(Color.BLACK);
            theme.setCornerRadius(20);
            replacement.setBackground(theme);
            f.controller.get().setContentView(replacement);
            replacement.layout(0, 0, 700, 500);
            f.apply(90);
            f.assertCorners(20, 20, 20, 20);
            assertSame(f.original, f.root.getBackground());
            assertEquals(255, f.original.getAlpha());
            assertEquals(90, theme.getAlpha());
        }
    }

    @Test public void partialApplyStillRestoresThemeBeforeAppliedFlagIsSet() throws Exception {
        try (Fixture f = new Fixture(0, 0, 0, 0)) {
            f.original.setAlpha(167);
            invoke("applyThemeOpacity", new Class<?>[]{View.class, int.class, f.stateClass},
                    f.root, 0, f.state);
            assertEquals(0, f.original.getAlpha());
            invoke("disable", new Class<?>[]{Window.class}, f.window);
            assertEquals(167, f.original.getAlpha());
        }
    }

    @Test public void windowFallbackRequiresUniformShapeFillingTheWholeWindow() throws Exception {
        try (Fixture f = new Fixture(20, 20, 0, 0)) {
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(f.root, f.original);
            assertEquals(false, invoke("canUseWindowShape",
                    new Class<?>[]{Window.class, View.class, GboardFrostedGlassShape.class},
                    f.window, f.root, shape));
            f.theme.setCornerRadius(0);
            shape = GboardFrostedGlassShape.resolve(f.root, f.original);
            assertEquals(false, invoke("canUseWindowShape",
                    new Class<?>[]{Window.class, View.class, GboardFrostedGlassShape.class},
                    f.window, f.root, shape));
        }
    }

    @Test public void windowFallbackReusesUnchangedBackgroundAcrossDraws() throws Exception {
        try (Fixture f = new Fixture(20, 20, 20, 20)) {
            Drawable originalWindow = f.window.getDecorView().getBackground();
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(f.root, f.original);
            Class<?>[] types = {Window.class, View.class, GboardFrostedGlassShape.class,
                    int.class, int.class, f.stateClass};
            invoke("applyWindowBlur", types, f.window, f.root, shape, 32, 77, f.state);
            Drawable first = f.window.getDecorView().getBackground();
            for (int i = 0; i < 20; i++) {
                invoke("applyWindowBlur", types, f.window, f.root, shape, 32, 77, f.state);
            }
            assertSame(first, f.window.getDecorView().getBackground());
            assertEquals(77, f.original.getAlpha());
            invoke("disable", new Class<?>[]{Window.class}, f.window);
            assertSame(originalWindow, f.window.getDecorView().getBackground());
            assertEquals(255, f.original.getAlpha());
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
        final View root = new View(controller.get());
        final GradientDrawable theme = new GradientDrawable();
        final Drawable original;
        final Class<?> stateClass;
        final Object state;

        Fixture(float tl, float tr, float bl, float br) throws Exception {
            root.setTag(".keyboard-base-area.keyboard-outline");
            theme.setColor(Color.BLACK);
            if (tl == tr && tl == bl && tl == br) theme.setCornerRadius(tl);
            else theme.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
            original = new DrawableWrapper(theme) {};
            root.setBackground(original);
            controller.get().setContentView(root);
            root.layout(0, 0, 1000, 600);
            original.setBounds(0, 0, 1000, 600);
            stateClass = Class.forName(GboardFrostedGlassRuntime.class.getName() + "$State");
            state = invoke("stateFor", new Class<?>[]{Window.class}, window);
        }

        Object tryApply(int alpha) throws Exception {
            return invoke("applyMiuiLocalBlur", new Class<?>[]{Window.class, View.class,
                    int.class, int.class, stateClass}, window, window.getDecorView(), 32, alpha, state);
        }

        void apply(int alpha) throws Exception {
            assertEquals(true, tryApply(alpha));
        }

        Object blur() throws Exception {
            Field field = stateClass.getDeclaredField("localBlurDrawable");
            field.setAccessible(true);
            Object blur = field.get(state);
            assertNotNull("Exercise the actual BackgroundBlurDrawable backend", blur);
            return blur;
        }

        void assertCorners(float tl, float tr, float bl, float br) throws Exception {
            Object blur = blur();
            String[] names = {"mCornerRadiusTL", "mCornerRadiusTR", "mCornerRadiusBL", "mCornerRadiusBR"};
            float[] expected = {tl, tr, bl, br};
            for (int i = 0; i < names.length; i++) {
                Field corner = blur.getClass().getDeclaredField(names[i]);
                corner.setAccessible(true);
                assertEquals("Blur geometry must match the resolved theme: " + names[i],
                        expected[i], corner.getFloat(blur), 0f);
            }
        }

        @Override public void close() throws Exception {
            invoke("disable", new Class<?>[]{Window.class}, window);
            controller.close();
        }
    }
}
