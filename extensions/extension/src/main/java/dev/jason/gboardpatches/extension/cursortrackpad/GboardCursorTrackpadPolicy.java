package dev.jason.gboardpatches.extension.cursortrackpad;

public final class GboardCursorTrackpadPolicy {
    public static final String FREE_CURSOR_FLAG = "free_cursor";
    public static final String FREE_CURSOR_LOCK_MODE_FLAG = "free_cursor_lock_mode";

    private GboardCursorTrackpadPolicy() {
    }

    public static Object maybeForceFlag(
            String flagName,
            Object stockResult,
            boolean enabled) {
        if (!enabled
                || !(FREE_CURSOR_FLAG.equals(flagName)
                        || FREE_CURSOR_LOCK_MODE_FLAG.equals(flagName))
                || !Boolean.FALSE.equals(stockResult)) {
            return stockResult;
        }
        return Boolean.TRUE;
    }

}
