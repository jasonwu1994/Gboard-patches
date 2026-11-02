package dev.jason.gboardpatches.extension.accesspointsmenu;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class GboardAccessPointsMenuRuntimeTest {
    @Test public void mirroredBodyAndFullKeyboardLayoutUseLiveLocalPreferences() {
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("menu1853", 0);
        prefs.edit().clear().commit();
        assertEquals(0x7f0b070c, GboardAccessPointsMenuRuntime.selectBody(0x7f0b070d, prefs));
        prefs.edit().putBoolean(GboardAccessPointsMenuSettings.PREF_KEY_ENABLED, true).commit();
        assertEquals(0x7f0b070d, GboardAccessPointsMenuRuntime.selectBody(0x7f0b070c, prefs));
        assertEquals(0x7f0e01aa, GboardAccessPointsMenuRuntime.selectKeyboardLayout(0x7f0e062e, prefs));
        prefs.edit().putBoolean(GboardAccessPointsMenuSettings.PREF_KEY_ENABLED, false).commit();
        assertEquals(0x7f0e062e, GboardAccessPointsMenuRuntime.selectKeyboardLayout(0x7f0e01aa, prefs));
        assertEquals(123, GboardAccessPointsMenuRuntime.selectBody(123, prefs));
        assertEquals(456, GboardAccessPointsMenuRuntime.selectKeyboardLayout(456, prefs));
    }

    @Test public void unreadableMalformedAndUnrelatedValuesPreserveStock() {
        assertEquals(0x7f0b070d, GboardAccessPointsMenuRuntime.selectBody(0x7f0b070d, null));
        SharedPreferences prefs = RuntimeEnvironment.getApplication().getSharedPreferences("menu1853-invalid", 0);
        prefs.edit().putInt(GboardAccessPointsMenuSettings.PREF_KEY_ENABLED, 1).commit();
        assertNull(GboardAccessPointsMenuRuntime.readModernOrNull(prefs));
        assertEquals(0x7f0b070d, GboardAccessPointsMenuRuntime.selectBody(0x7f0b070d, prefs));
        prefs.edit().putString(GboardAccessPointsMenuSettings.PREF_KEY_ENABLED, "TRUE").commit();
        assertEquals(Boolean.TRUE, GboardAccessPointsMenuRuntime.readModernOrNull(prefs));
    }

    @Test public void legacyDragCommitsGeometryAndDoesNotLeakIntoFreshModernPopup() {
        Fixture f = new Fixture();
        ImageView icon = new ImageView(f.context);
        GboardAccessPointsMenuRuntime.applyLegacyPresentation(f.popup, icon, f.frame,
                89, 48, 5, new ColorDrawable(Color.GRAY));
        assertSame(icon, f.frame.getChildAt(0));
        assertEquals(89, f.frame.getLayoutParams().width);
        assertEquals(48, icon.getLayoutParams().height);
        assertEquals(5, f.frame.getPaddingLeft());
        assertEquals(View.GONE, f.frame.getVisibility());
        assertTrue(GboardAccessPointsMenuRuntime.dragMode(f.popup, true));
        GboardAccessPointsMenuRuntime.completeDrag(f.popup);
        assertFalse(GboardAccessPointsMenuRuntime.dragMode(f.popup, true));
        assertEquals(1.3f, GboardAccessPointsMenuRuntime.dragScale(f.popup, 1), 0);
        Fixture modern = new Fixture();
        GboardAccessPointsMenuRuntime.prepareDrag(modern.popup, new ImageView(modern.context), true);
        GboardAccessPointsMenuRuntime.completeDrag(modern.popup);
        assertTrue(GboardAccessPointsMenuRuntime.dragMode(modern.popup, true));
        assertEquals(1, GboardAccessPointsMenuRuntime.dragScale(modern.popup, 1), 0);
    }

    @Test public void stockInflateFailureRollsBackAllChildrenLayoutPaddingAndVisibility() {
        Fixture f = new Fixture();
        View old = f.frame.getChildAt(0);
        Object layout = f.frame.getLayoutParams();
        GboardAccessPointsMenuRuntime.applyLegacyPresentation(f.popup, new ImageView(f.context), f.frame,
                89, 48, 5, new ColorDrawable(Color.GRAY));
        GboardAccessPointsMenuRuntime.rollbackDrag(f.popup);
        assertSame(old, f.frame.getChildAt(0));
        assertSame(layout, f.frame.getLayoutParams());
        assertEquals(1, f.frame.getPaddingLeft());
        assertEquals(2, f.frame.getPaddingTop());
        assertEquals(3, f.frame.getPaddingRight());
        assertEquals(4, f.frame.getPaddingBottom());
        assertEquals(View.VISIBLE, f.frame.getVisibility());
        assertTrue(GboardAccessPointsMenuRuntime.dragMode(f.popup, true));
        assertEquals(1, GboardAccessPointsMenuRuntime.dragScale(f.popup, 1), 0);
    }

    @Test public void invalidThemeDimensionsAndMissingChildrenLeaveStockUntouched() {
        Fixture f = new Fixture();
        View old = f.frame.getChildAt(0);
        GboardAccessPointsMenuRuntime.applyLegacyPresentation(f.popup, new ImageView(f.context), f.frame,
                0, 48, 5, new ColorDrawable(Color.GRAY));
        GboardAccessPointsMenuRuntime.completeDrag(f.popup);
        assertSame(old, f.frame.getChildAt(0));
        assertTrue(GboardAccessPointsMenuRuntime.dragMode(f.popup, true));
        GboardAccessPointsMenuRuntime.prepareDrag(new FrameLayout(f.context), new ImageView(f.context), false);
        assertSame(old, f.frame.getChildAt(0));
    }

    private static class Fixture {
        final Context context = RuntimeEnvironment.getApplication();
        final FrameLayout popup = new FrameLayout(context);
        final FrameLayout frame = new FrameLayout(context);
        Fixture() {
            frame.setId(0x7f0b007f);
            frame.setPadding(1, 2, 3, 4);
            ImageView old = new ImageView(context); old.setId(0x7f0b03c2);
            frame.addView(old, new FrameLayout.LayoutParams(30, 40));
            popup.addView(frame, new FrameLayout.LayoutParams(50, 60));
        }
    }
}
