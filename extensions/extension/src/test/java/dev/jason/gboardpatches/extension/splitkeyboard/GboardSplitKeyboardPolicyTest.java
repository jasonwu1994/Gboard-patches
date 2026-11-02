package dev.jason.gboardpatches.extension.splitkeyboard;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

public final class GboardSplitKeyboardPolicyTest {
    @Test
    public void promotesOnlyUnavailableSplitAccessPointWhenEnabled() {
        Assert.assertEquals(2, GboardSplitKeyboardPolicy.forceAccessPointState(
                "split", 5, true));
        Assert.assertEquals(5, GboardSplitKeyboardPolicy.forceAccessPointState(
                "split", 5, false));
        Assert.assertEquals(3, GboardSplitKeyboardPolicy.forceAccessPointState(
                "split", 3, true));
        Assert.assertEquals(5, GboardSplitKeyboardPolicy.forceAccessPointState(
                "clipboard", 5, true));
    }

    @Test
    public void appendsSplitToOrderWithoutDuplicatesOnlyWhenEnabled() {
        Assert.assertEquals(
                Arrays.asList("clipboard", "split"),
                GboardSplitKeyboardPolicy.appendSplitAccessPoint(
                        Collections.singletonList("clipboard"), true));
        Assert.assertEquals(
                Collections.singletonList("clipboard"),
                GboardSplitKeyboardPolicy.appendSplitAccessPoint(
                        Collections.singletonList("clipboard"), false));
        Assert.assertEquals(
                Arrays.asList("clipboard", "split"),
                GboardSplitKeyboardPolicy.appendSplitAccessPoint(
                        Arrays.asList("clipboard", "split", "split"), true));
    }

    @Test
    public void autoSplitRequiresBothSettingsStockEligibilityAndNonSplitMode() {
        Assert.assertTrue(GboardSplitKeyboardPolicy.shouldSwitchToSplit(
                true, true, true, 1));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldSwitchToSplit(
                false, true, true, 1));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldSwitchToSplit(
                true, false, true, 1));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldSwitchToSplit(
                true, true, false, 1));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldSwitchToSplit(
                true, true, true, GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE));
    }

    @Test
    public void splitActionRedirectsOnlyWhenMasterIsEnabledAndAlreadySplit() {
        Assert.assertTrue(GboardSplitKeyboardPolicy.shouldRedirectSplitActionToRestore(
                true, GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldRedirectSplitActionToRestore(
                false, GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE));
        Assert.assertFalse(GboardSplitKeyboardPolicy.shouldRedirectSplitActionToRestore(
                true, 1));
    }
}
