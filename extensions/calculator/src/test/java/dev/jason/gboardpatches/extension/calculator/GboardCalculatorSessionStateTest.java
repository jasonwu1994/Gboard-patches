package dev.jason.gboardpatches.extension.calculator;

import org.junit.Assert;
import org.junit.Test;

public final class GboardCalculatorSessionStateTest {
    @Test
    public void acceptsOnlyCollapsedSelectionFromTheCurrentInputViewSession() {
        GboardCalculatorSessionState state = new GboardCalculatorSessionState();

        long firstSession = state.beginInputView();
        Assert.assertFalse(state.canAccept(firstSession));
        state.updateSelection(true);
        Assert.assertTrue(state.canAccept(firstSession));
        state.updateSelection(false);
        Assert.assertFalse(state.canAccept(firstSession));

        long secondSession = state.beginInputView();
        state.updateSelection(true);
        Assert.assertFalse(state.canAccept(firstSession));
        Assert.assertTrue(state.canAccept(secondSession));
        state.endInputView();
        Assert.assertFalse(state.canAccept(secondSession));
    }
}
