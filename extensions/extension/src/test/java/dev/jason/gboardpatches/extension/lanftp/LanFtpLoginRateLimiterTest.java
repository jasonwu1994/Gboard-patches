package dev.jason.gboardpatches.extension.lanftp;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpLoginRateLimiter;

public final class LanFtpLoginRateLimiterTest {
    @Test
    public void failuresAreBoundedAcrossReconnectsPerPeerAndGlobally() {
        LanFtpLoginRateLimiter limiter = new LanFtpLoginRateLimiter(3, 5, 60_000L);

        limiter.recordFailure("192.168.1.10", 1_000L);
        limiter.recordFailure("192.168.1.10", 2_000L);
        Assert.assertTrue(limiter.isAllowed("192.168.1.10", 3_000L));
        limiter.recordFailure("192.168.1.10", 3_000L);

        Assert.assertFalse(limiter.isAllowed("192.168.1.10", 4_000L));
        Assert.assertTrue(limiter.isAllowed("192.168.1.11", 4_000L));
        limiter.recordFailure("192.168.1.11", 4_000L);
        limiter.recordFailure("192.168.1.12", 5_000L);
        Assert.assertFalse(limiter.isAllowed("192.168.1.13", 6_000L));

        Assert.assertTrue(limiter.isAllowed("192.168.1.10", 63_001L));
    }

    @Test
    public void successfulLoginClearsOnlyThePeerWindow() {
        LanFtpLoginRateLimiter limiter = new LanFtpLoginRateLimiter(2, 10, 60_000L);
        limiter.recordFailure("peer", 1L);
        limiter.recordFailure("peer", 2L);
        Assert.assertFalse(limiter.isAllowed("peer", 3L));

        limiter.recordSuccess("peer");

        Assert.assertTrue(limiter.isAllowed("peer", 4L));
    }

    @Test
    public void rotatingPeersCannotGrowFailureStateWithoutBound() throws Exception {
        LanFtpLoginRateLimiter limiter = new LanFtpLoginRateLimiter(3, 5, 60_000L);

        for (int index = 0; index < 1_000; index++) {
            limiter.recordFailure("peer-" + index, 1_000L);
        }

        Assert.assertTrue(peerFailures(limiter).size() <= 5);
        Assert.assertTrue(globalFailures(limiter).size() <= 5);
    }

    @Test
    public void anyRequestPrunesExpiredStateForEveryPeer() throws Exception {
        LanFtpLoginRateLimiter limiter = new LanFtpLoginRateLimiter(3, 5, 60_000L);
        limiter.recordFailure("expired-one", 1L);
        limiter.recordFailure("expired-two", 2L);

        Assert.assertTrue(limiter.isAllowed("new-peer", 60_003L));

        Assert.assertTrue(peerFailures(limiter).isEmpty());
        Assert.assertTrue(globalFailures(limiter).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ArrayDeque<Long>> peerFailures(
            LanFtpLoginRateLimiter limiter) throws Exception {
        Field field = LanFtpLoginRateLimiter.class.getDeclaredField("peerFailures");
        field.setAccessible(true);
        return (Map<String, ArrayDeque<Long>>) field.get(limiter);
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Long> globalFailures(
            LanFtpLoginRateLimiter limiter) throws Exception {
        Field field = LanFtpLoginRateLimiter.class.getDeclaredField("globalFailures");
        field.setAccessible(true);
        return (ArrayDeque<Long>) field.get(limiter);
    }
}
