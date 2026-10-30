package dev.jason.gboardpatches.extension.frostedglass;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardFrostedGlassKeyOpacityTest {
    @Test public void keyBackgroundsAndActionShapesFadeWithoutFadingLabelsOrIcons() throws Exception {
        try (Fixture f = new Fixture()) {
            GradientDrawable keyBg = new GradientDrawable();
            keyBg.setColor(Color.WHITE);
            keyBg.setCornerRadius(24);
            FrameLayout key = new FrameLayout(f.activity.get());
            key.setTag(".keytop.with-popup");
            key.setBackground(new InsetDrawable(keyBg, 3));
            TextView label = new TextView(f.activity.get());
            label.setText("Q");
            label.setTextColor(Color.BLACK);
            key.addView(label);
            f.root.addView(key);
            ImageView actionBackground = f.image(".background-icon.for-action-key.non-linear-scale", Color.GREEN);
            ImageView icon = f.image(".icon.for-action-key.non-linear-scale", Color.BLACK);
            f.keys(77);
            f.apply();
            assertEquals(77, keyBg.getAlpha());
            assertEquals(24f, keyBg.getCornerRadius(), 0f);
            assertEquals(77, actionBackground.getDrawable().getAlpha());
            assertEquals(255, icon.getDrawable().getAlpha());
            assertEquals(1f, label.getAlpha(), 0f);
            assertEquals(Color.BLACK, label.getCurrentTextColor());
            assertEquals(1f, key.getAlpha(), 0f);
            assertEquals(77, f.root.getBackground().getAlpha());
        }
    }

    @Test public void followingThemeAndCleanupRestoreTheOriginalKeyAlpha() throws Exception {
        try (Fixture f = new Fixture()) {
            ColorDrawable keyBg = new ColorDrawable(0x80123456);
            View key = new View(f.activity.get());
            key.setTag(".keytop.pill-shaped");
            key.setBackground(keyBg);
            f.root.addView(key);
            f.keys(-1);
            f.apply();
            assertEquals(128, keyBg.getAlpha());
            f.keys(51);
            f.apply();
            assertEquals(51, keyBg.getAlpha());
            f.keys(-1);
            f.apply();
            assertEquals(128, keyBg.getAlpha());
            f.keys(0);
            f.apply();
            assertEquals(0, keyBg.getAlpha());
            f.restore();
            assertEquals(128, keyBg.getAlpha());
            assertEquals(0x80123456, keyBg.getColor());
        }
    }

    @Test public void transparentShapesStayInvisibleAndStableDrawsDoNotRewriteAlpha() throws Exception {
        try (Fixture f = new Fixture()) {
            View empty = new View(f.activity.get());
            empty.setTag(".space_bar");
            empty.setBackgroundColor(Color.TRANSPARENT);
            f.root.addView(empty);
            CountingBackground background = new CountingBackground();
            background.setColor(Color.WHITE);
            View key = new View(f.activity.get());
            key.setTag(".keytop");
            key.setBackground(background);
            f.root.addView(key);
            f.keys(77);
            f.apply();
            int writes = background.writes;
            for (int i = 0; i < 20; i++) f.apply();
            assertEquals(writes, background.writes);
            assertEquals(0, empty.getBackground().getAlpha());
            f.keys(255);
            f.apply();
            assertEquals(255, background.getAlpha());
        }
    }

    static final class CountingBackground extends GradientDrawable {
        int writes;
        @Override public void setAlpha(int value) { writes++; super.setAlpha(value); }
    }

    @Test public void pressedStateKeepsItsOwnColorAndUsesTheSelectedAlpha() throws Exception {
        try (Fixture f = new Fixture()) {
            android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Color.RED));
            background.addState(new int[]{}, new ColorDrawable(Color.BLUE));
            View key = new View(f.activity.get());
            key.setTag(".keytop");
            key.setBackground(new InsetDrawable(background, 4));
            f.root.addView(key);
            f.keys(77);
            f.apply();
            assertEquals(Color.BLUE & 0x00ffffff,
                    ((ColorDrawable) background.getCurrent()).getColor() & 0x00ffffff);
            background.setState(new int[]{android.R.attr.state_pressed});
            f.apply();
            assertEquals(Color.RED & 0x00ffffff,
                    ((ColorDrawable) background.getCurrent()).getColor() & 0x00ffffff);
            assertEquals(77, background.getCurrent().getAlpha(), 1);
            f.restore();
            assertEquals(255, background.getCurrent().getAlpha());
        }
    }

    @Test public void followingThemeRestoresEmbeddedPressedStateTransparency() throws Exception {
        try (Fixture f = new Fixture()) {
            android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x80ff0000));
            background.addState(new int[]{}, new ColorDrawable(Color.BLUE));
            View key = new View(f.activity.get());
            key.setTag(".keytop");
            key.setBackground(background);
            f.root.addView(key);
            f.keys(77);
            f.apply();
            background.setState(new int[]{android.R.attr.state_pressed});
            f.apply();
            f.keys(-1);
            f.apply();
            assertEquals(128, background.getCurrent().getAlpha());
            background.setState(new int[]{});
            assertEquals(255, background.getCurrent().getAlpha());
        }
    }

    static final class Fixture implements AutoCloseable {
        final ActivityController<Activity> activity = Robolectric.buildActivity(Activity.class).setup();
        final Window window = activity.get().getWindow();
        final FrameLayout root = new FrameLayout(activity.get());
        final Class<?> type = Class.forName(GboardFrostedGlassRuntime.class.getName() + "$State");
        final Object state;
        Fixture() throws Exception {
            Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod("stateFor", Window.class);
            method.setAccessible(true);
            state = method.invoke(null, window);
            root.setTag(".keyboard-body-area");
            root.setBackgroundColor(Color.WHITE);
        }
        void keys(int alpha) throws Exception {
            Field field = type.getDeclaredField("customKeyOpacity");
            field.setAccessible(true);
            field.setInt(state, alpha);
        }
        ImageView image(String tag, int color) {
            ImageView image = new ImageView(activity.get());
            image.setTag(tag);
            image.setImageDrawable(new ColorDrawable(color));
            root.addView(image);
            return image;
        }
        void apply() throws Exception {
            Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod(
                    "applyThemeOpacity", View.class, int.class, type);
            method.setAccessible(true);
            method.invoke(null, root, 77, state);
        }
        void restore() throws Exception {
            Method method = GboardFrostedGlassRuntime.class.getDeclaredMethod("restoreLocalBackground", type);
            method.setAccessible(true);
            method.invoke(null, state);
        }
        @Override public void close() throws Exception {
            restore();
            activity.pause().stop().destroy();
        }
    }
}
