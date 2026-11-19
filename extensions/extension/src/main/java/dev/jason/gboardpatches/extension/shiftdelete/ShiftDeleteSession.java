package dev.jason.gboardpatches.extension.shiftdelete;

/**
 * Version-neutral chord policy shared with the Morphe port: direction follows the
 * currently held Shift key, including during native repetition. Callers run it on
 * Gboard's UI thread; it holds no Gboard type, View or editor reference.
 */
public final class ShiftDeleteSession {
    public static final int SHIFT_DOWN = -0x2730;
    public static final int SHIFT_UP = -0x2731;
    public static final int DELETE = 67;
    public static final int FORWARD_DELETE = 112;
    private boolean shiftHeld;

    public boolean isShiftHeld() { return shiftHeld; }

    /** A new touch stream or a cancelled one; a Shift UP may never arrive. */
    public void cancel() { shiftHeld = false; }

    public int key(String action, int code) {
        if (code == SHIFT_DOWN && "DOWN".equals(action)) shiftHeld = true;
        if (code == SHIFT_UP && "UP".equals(action)) shiftHeld = false;
        return shiftHeld && code == DELETE && "PRESS".equals(action)
                ? FORWARD_DELETE : code;
    }
}
