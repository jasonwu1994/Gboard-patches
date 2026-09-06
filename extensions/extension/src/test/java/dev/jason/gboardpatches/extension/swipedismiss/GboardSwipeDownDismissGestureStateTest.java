package dev.jason.gboardpatches.extension.swipedismiss;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSwipeDownDismissGestureStateTest {
    @Test
    public void settingDefaultsOff() {
        Assert.assertFalse(GboardSwipeDownDismissSettings.DEFAULT_ENABLED);
    }

    @Test
    public void clearDownwardSwipeFromHeaderIsConsumed() {
        GboardSwipeDownDismissGestureState state = new GboardSwipeDownDismissGestureState();
        state.begin(true, 100f, 20f);

        Assert.assertFalse(state.move(106f, 60f, 1f));
        Assert.assertTrue(state.move(108f, 70f, 1f));
        Assert.assertTrue(state.isConsumed());
    }

    @Test
    public void gestureOutsideHeaderIsIgnored() {
        GboardSwipeDownDismissGestureState state = new GboardSwipeDownDismissGestureState();
        state.begin(false, 100f, 20f);

        Assert.assertFalse(state.move(100f, 100f, 1f));
        Assert.assertFalse(state.isConsumed());
    }

    @Test
    public void horizontalSuggestionScrollIsRejected() {
        GboardSwipeDownDismissGestureState state = new GboardSwipeDownDismissGestureState();
        state.begin(true, 100f, 20f);

        Assert.assertFalse(state.move(130f, 24f, 1f));
        Assert.assertFalse(state.move(132f, 100f, 1f));
        Assert.assertFalse(state.isConsumed());
    }

    @Test
    public void diagonalMovementMustBePredominantlyDownward() {
        GboardSwipeDownDismissGestureState state = new GboardSwipeDownDismissGestureState();
        state.begin(true, 100f, 20f);

        Assert.assertFalse(state.move(150f, 70f, 1f));
        Assert.assertFalse(state.isConsumed());
    }
}
