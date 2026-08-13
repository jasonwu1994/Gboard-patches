package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

public final class GboardPatchesSettingsScrollStateTest {
    @Test
    public void nestedBackNavigationRestoresEachParentPosition() {
        GboardPatchesSettingsScrollState state = new GboardPatchesSettingsScrollState();

        state.enterFeature(true, 480);
        state.enterFeature(false, 760);

        Assert.assertEquals(760, state.leaveFeature(false));
        Assert.assertEquals(480, state.leaveFeature(true));
    }

    @Test
    public void directFeaturePathReturnsToTopForSyntheticAncestors() {
        GboardPatchesSettingsScrollState state = new GboardPatchesSettingsScrollState();

        state.resetForDirectPath(2);

        Assert.assertEquals(0, state.leaveFeature(false));
        Assert.assertEquals(0, state.leaveFeature(false));
        Assert.assertEquals(0, state.leaveFeature(true));
    }
}
