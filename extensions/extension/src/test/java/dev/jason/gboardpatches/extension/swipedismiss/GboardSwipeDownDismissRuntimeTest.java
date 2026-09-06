package dev.jason.gboardpatches.extension.swipedismiss;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSwipeDownDismissRuntimeTest {
    @Test
    public void tracksToolbarEditModeTransitions() {
        GboardSwipeDownDismissRuntime.onToolbarEditModeChanged(true);
        Assert.assertTrue(GboardSwipeDownDismissRuntime.isToolbarEditModeForTesting());

        GboardSwipeDownDismissRuntime.onToolbarEditModeChanged(false);
        Assert.assertFalse(GboardSwipeDownDismissRuntime.isToolbarEditModeForTesting());
    }
}
