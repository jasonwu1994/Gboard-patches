package dev.jason.gboardpatches.extension.clipboard;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import dev.jason.gboardpatches.extension.clipboard.header.GboardClipboardHeaderActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardClipboardSelectAllFailureBoundaryTest {
    private final Object keyboard = new Object();
    private final FaultyAction action = new FaultyAction();
    private Activity activity;
    private FrameLayout header;
    private LinearLayout icons;
    private View nativeControl;
    private ImageButton button;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        header = new FrameLayout(activity);
        icons = new LinearLayout(activity);
        nativeControl = new View(activity);
        icons.addView(nativeControl);
        View recycler = new View(activity);
        header.addView(icons);
        header.addView(recycler);
        activity.setContentView(header);
        GboardClipboardHeaderActions.stateChanged(keyboard, true);
        GboardClipboardHeaderActions.attach(keyboard, header, icons, nativeControl, null,
                recycler, action, action);
        button = (ImageButton) icons.getChildAt(0);
        header.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(144, View.MeasureSpec.EXACTLY));
        header.layout(0, 0, 600, 144);
    }

    @After public void tearDown() {
        GboardClipboardHeaderActions.close(keyboard);
        activity.finish();
    }

    @Test public void injectedOpenThatCannotResolveBindingsReturnsToHost() {
        GboardClipboardSelectAllRuntime.afterOpen(new Object());
        assertFalse(GboardClipboardSelectAllRuntime.interceptTouch(null, null));
        assertSame(nativeControl, icons.getChildAt(1));
    }

    @Test public void settingsAndReportingFailureHideOnlySelectAll() {
        action.settingsFault = true;
        GboardClipboardHeaderActions.update(keyboard);
        assertEquals(View.GONE, button.getVisibility());
        assertSame(nativeControl, icons.getChildAt(1));
        assertEquals(View.VISIBLE, nativeControl.getVisibility());
    }

    @Test public void clickAndReportingFailureHideOnlySelectAll() {
        action.selectionFault = true;
        button.performClick();
        assertEquals(View.GONE, button.getVisibility());
        assertSame(nativeControl, icons.getChildAt(1));
    }

    @Test public void failingButtonDoesNotReturnTheRestOfItsGestureToStock() {
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                throw new LinkageError("button dispatch unavailable");
            }
            return false;
        });
        Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
        header.offsetDescendantRectToMyCoords(button, bounds);
        int x = bounds.centerX();
        int y = bounds.centerY();
        assertTrue(touch(MotionEvent.ACTION_DOWN, x, y));
        assertTrue(touch(MotionEvent.ACTION_MOVE, x, y));
        assertTrue(touch(MotionEvent.ACTION_UP, x, y));
        assertEquals(View.GONE, button.getVisibility());
        assertFalse(touch(MotionEvent.ACTION_DOWN, 590, y));
        assertFalse(touch(MotionEvent.ACTION_UP, 590, y));
    }

    private boolean touch(int action, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(time, time, action, x, y, 0);
        try {
            return GboardClipboardSelectAllRuntime.interceptTouch(header, event);
        } finally {
            event.recycle();
        }
    }

    private static final class FaultyAction implements GboardClipboardHeaderActions.Access,
            GboardClipboardHeaderActions.Selection {
        boolean settingsFault;
        boolean selectionFault;

        @Override public boolean isCurrent(Object owner, View header) {
            return true;
        }

        @Override public GboardClipboardHeaderActions.Settings settings(Context context) {
            if (settingsFault) throw new AssertionError("settings unavailable");
            return new GboardClipboardHeaderActions.Settings("Select all", "No clips", false);
        }

        @Override public void report(Throwable failure) {
            throw new AssertionError("reporter unavailable");
        }

        @Override public boolean hasSelectableItems(Object owner, boolean excludePinned) {
            return true;
        }

        @Override public void selectAll(Object owner, boolean excludePinned) {
            if (selectionFault) throw new LinkageError("selection unavailable");
        }
    }
}
