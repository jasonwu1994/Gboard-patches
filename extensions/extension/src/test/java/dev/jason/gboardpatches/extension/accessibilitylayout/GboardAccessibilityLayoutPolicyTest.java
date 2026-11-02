package dev.jason.gboardpatches.extension.accessibilitylayout;

import org.junit.Assert;
import org.junit.Test;

public final class GboardAccessibilityLayoutPolicyTest {
    @Test
    public void forcesOnlyPkSimulatorAndNeverTheStockSetting() {
        Assert.assertEquals(Boolean.TRUE, GboardAccessibilityLayoutPolicy.maybeForce(
                "enable_pk_simulator", Boolean.FALSE, true));
        Assert.assertSame(Boolean.FALSE, GboardAccessibilityLayoutPolicy.maybeForce(
                "enable_pk_simulator", Boolean.FALSE, false));
        Assert.assertSame(Boolean.FALSE, GboardAccessibilityLayoutPolicy.maybeForce(
                "enable_pk_simulator_setting", Boolean.FALSE, true));
    }
}
