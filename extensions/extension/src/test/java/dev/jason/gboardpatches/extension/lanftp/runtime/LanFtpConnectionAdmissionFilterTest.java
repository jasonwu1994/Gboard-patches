package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.apache.mina.core.session.DummySession;
import org.apache.mina.core.session.IoEvent;
import org.apache.mina.core.session.IoEventType;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpConnectionAdmissionFilterTest {
    @Test
    public void rawControlConnectionsAreBoundedBeforeTheExecutor() {
        LanFtpConnectionAdmissionFilter filter = new LanFtpConnectionAdmissionFilter(2);
        DummySession first = new DummySession();
        DummySession second = new DummySession();
        DummySession excess = new DummySession();

        Assert.assertTrue(filter.accept(first));
        Assert.assertTrue(filter.accept(second));
        Assert.assertFalse(filter.accept(excess));
        Assert.assertEquals(2, filter.activeCount());

        first.closeNow().awaitUninterruptibly();

        Assert.assertFalse(filter.accept(excess));
        LanFtpControlQueueLimiter limiter = new LanFtpControlQueueLimiter(1, 2);
        IoEvent closed = new IoEvent(IoEventType.SESSION_CLOSED, first, null);
        Assert.assertTrue(limiter.accept(null, closed));
        limiter.polled(null, closed);

        Assert.assertFalse(filter.accept(excess));
        LanFtpConnectionAdmissionFilter.release(first);

        Assert.assertTrue(filter.accept(excess));
        Assert.assertEquals(2, filter.activeCount());
    }
}
