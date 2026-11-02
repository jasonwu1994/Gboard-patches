package dev.jason.gboardpatches.extension.accesspointsmenu;

import org.junit.Assert;
import org.junit.Test;

public final class GboardAccessPointsMenuPolicyTest {
    @Test
    public void exactBooleanMirrorsSwitchInBothDirections() {
        Assert.assertEquals(Boolean.TRUE, GboardAccessPointsMenuPolicy.mirrorSwitch(
                "enable_access_points_menu_redesign", Boolean.FALSE, true));
        Assert.assertEquals(Boolean.FALSE, GboardAccessPointsMenuPolicy.mirrorSwitch(
                "enable_access_points_menu_redesign", Boolean.TRUE, false));
        Assert.assertSame(Boolean.TRUE, GboardAccessPointsMenuPolicy.mirrorSwitch(
                "unrelated", Boolean.TRUE, false));
        Assert.assertEquals("false", GboardAccessPointsMenuPolicy.mirrorSwitch(
                "enable_access_points_menu_redesign", "false", true));
    }
}
