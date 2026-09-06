package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.apache.mina.core.session.DummySession;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpControlQueueLimiterTest {
    @Test
    public void rejectsBeyondCapacityAndReopensAfterAnEventIsPolled() {
        LanFtpControlQueueLimiter limiter = new LanFtpControlQueueLimiter(2, 1);
        IoEvent command = event(IoEventType.MESSAGE_RECEIVED);

        Assert.assertTrue(limiter.accept(null, command));
        Assert.assertTrue(limiter.accept(null, command));
        Assert.assertFalse(limiter.accept(null, command));
        Assert.assertEquals(2, limiter.pendingCount());

        limiter.polled(null, command);
        Assert.assertTrue(limiter.accept(null, command));
        Assert.assertEquals(2, limiter.pendingCount());
    }

    @Test
    public void extraPollCannotMakeTheReservationCountNegative() {
        LanFtpControlQueueLimiter limiter = new LanFtpControlQueueLimiter(1, 1);
        IoEvent command = event(IoEventType.MESSAGE_RECEIVED);

        limiter.polled(null, command);

        Assert.assertEquals(0, limiter.pendingCount());
        Assert.assertTrue(limiter.accept(null, command));
    }

    @Test
    public void sessionClosedUsesReservedOrderedCapacity() {
        LanFtpControlQueueLimiter limiter = new LanFtpControlQueueLimiter(1, 1);
        IoEvent command = event(IoEventType.MESSAGE_RECEIVED);
        IoEvent closed = event(IoEventType.SESSION_CLOSED);

        Assert.assertTrue(limiter.accept(null, command));
        Assert.assertTrue(limiter.accept(null, closed));

        Assert.assertEquals(2, limiter.pendingCount());
        Assert.assertFalse(limiter.accept(null, command));
        limiter.polled(null, command);
        limiter.polled(null, closed);
        Assert.assertEquals(0, limiter.pendingCount());
    }

    @Test
    public void lifecycleEventsAlsoConsumeAndReleaseGlobalCapacity() {
        LanFtpControlQueueLimiter limiter = new LanFtpControlQueueLimiter(1, 1);
        IoEvent opened = event(IoEventType.SESSION_OPENED);

        Assert.assertTrue(limiter.accept(null, opened));
        Assert.assertEquals(1, limiter.pendingCount());
        limiter.polled(null, opened);
        Assert.assertEquals(0, limiter.pendingCount());
    }

    private static IoEvent event(IoEventType type) {
        return new IoEvent(type, new DummySession(), null);
    }

}
