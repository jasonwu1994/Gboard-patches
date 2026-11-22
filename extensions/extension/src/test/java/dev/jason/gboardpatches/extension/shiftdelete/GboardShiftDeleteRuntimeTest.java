package dev.jason.gboardpatches.extension.shiftdelete;

import static dev.jason.gboardpatches.extension.shiftdelete.ShiftDeleteSession.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.view.MotionEvent;
import org.junit.After;
import org.junit.Test;

public class GboardShiftDeleteRuntimeTest {
    @After public void clear() { GboardShiftDeleteRuntime.reset(); }

    @Test public void touchStreamStartOrCancelClearsAShiftWhoseUpNeverArrived() {
        for (int clearing : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL}) {
            GboardShiftDeleteRuntime.mapKey("DOWN", SHIFT_DOWN);
            assertEquals(FORWARD_DELETE, GboardShiftDeleteRuntime.mapKey("PRESS", DELETE));
            GboardShiftDeleteRuntime.onTouch(clearing);
            assertEquals(DELETE, GboardShiftDeleteRuntime.mapKey("PRESS", DELETE));
        }
    }

    @Test public void otherTouchActionsKeepForwardDeleteAndResetDropsIt() {
        GboardShiftDeleteRuntime.mapKey("DOWN", SHIFT_DOWN);
        GboardShiftDeleteRuntime.onTouch(MotionEvent.ACTION_MOVE);
        GboardShiftDeleteRuntime.onTouch(MotionEvent.ACTION_POINTER_DOWN);
        assertEquals(FORWARD_DELETE, GboardShiftDeleteRuntime.mapKey("PRESS", DELETE));
        GboardShiftDeleteRuntime.reset();
        assertEquals(DELETE, GboardShiftDeleteRuntime.mapKey("PRESS", DELETE));
    }

    @Test public void settingIsUnknownUntilAReadSucceeds() {
        assertEquals(false, GboardShiftDeleteRuntime.hasDefiniteSetting());
    }

    @Test public void hostEntriesPassThroughWhenThereIsNothingToRemap() {
        Object event = new Object();
        assertSame(event, GboardShiftDeleteRuntime.remapInput(new Object(), event));
        assertNull(GboardShiftDeleteRuntime.remapInput(new Object(), null));
        GboardShiftDeleteRuntime.onTouchEvent(null);
        assertNull(GboardShiftDeleteRuntime.failure());
    }
}
