package dev.jason.gboardpatches.extension.cursortrackpad;

import org.junit.Assert;
import org.junit.Test;

public final class GboardCursorTrackpadPolicyTest {
    @Test
    public void enabledForcesBothFlags() {
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor", Boolean.FALSE, true));
        Assert.assertEquals(Boolean.TRUE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor_lock_mode", Boolean.FALSE, true));
    }

    @Test
    public void disabledOrUnrelatedPreservesStock() {
        Assert.assertSame(Boolean.FALSE, GboardCursorTrackpadPolicy.maybeForceFlag(
                "free_cursor", Boolean.FALSE, false));
    }
}
