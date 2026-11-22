package dev.jason.gboardpatches.extension.advancedvoice;

import org.junit.Assert;
import org.junit.Test;

public final class GboardS3AirplaneModePolicyTest {
    @Test
    public void overridesOnlyInAirplaneModeWithAUsableNetwork() {
        for (String usable : new String[] { "METERED", "NON_METERED", "PROXIED" }) {
            Assert.assertTrue(usable,
                    GboardS3AirplaneModePolicy.shouldOverride(false, true, usable));
        }
        for (String unusable : new String[] {
                "UNKNOWN", "NO_CONNECTION", "CONNECTION_UNKNOWN", "", "OTHER", null
        }) {
            Assert.assertFalse(String.valueOf(unusable),
                    GboardS3AirplaneModePolicy.shouldOverride(false, true, unusable));
        }
        Assert.assertFalse("not in airplane mode",
                GboardS3AirplaneModePolicy.shouldOverride(false, false, "NON_METERED"));
        Assert.assertFalse("stock already allowed S3",
                GboardS3AirplaneModePolicy.shouldOverride(true, true, "NON_METERED"));
    }
}
