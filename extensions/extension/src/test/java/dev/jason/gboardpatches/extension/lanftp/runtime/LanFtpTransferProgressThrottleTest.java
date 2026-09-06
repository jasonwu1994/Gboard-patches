package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpTransferProgressThrottleTest {
    @Test
    public void progressPublicationHasAHardHalfSecondMinimumInterval() {
        Assert.assertFalse(LanFtpTransferCoordinator.shouldPublishProgress(
                1_000_000_000L, 1_499_999_999L));
        Assert.assertTrue(LanFtpTransferCoordinator.shouldPublishProgress(
                1_000_000_000L, 1_500_000_000L));
    }
}
