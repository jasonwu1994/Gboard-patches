package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpRuntimeObserverTest {
    @Test
    public void firstProgressEventIsImmediateAndLaterEventsAreThrottled() {
        Assert.assertEquals(0L, LanFtpRuntimeObserver.progressDelayMillis(
                Long.MIN_VALUE, 100L));
        Assert.assertEquals(750L, LanFtpRuntimeObserver.progressDelayMillis(
                100L, 350L));
        Assert.assertEquals(0L, LanFtpRuntimeObserver.progressDelayMillis(
                100L, 1_100L));
    }

    @Test
    public void elapsedClockRegressionCannotBypassTheThrottle() {
        Assert.assertEquals(1_000L, LanFtpRuntimeObserver.progressDelayMillis(
                500L, 400L));
    }
}
