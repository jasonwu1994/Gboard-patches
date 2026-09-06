package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

public final class LanFtpRuntimeRegistryTest {
    @Test
    public void publishReportsOnlyMeaningfulRuntimeChanges() {
        LanFtpRuntimeRegistry.reset();

        Assert.assertFalse(LanFtpRuntimeRegistry.publish(LanFtpRuntimeStatus.stopped()));
        Assert.assertTrue(LanFtpRuntimeRegistry.publish(LanFtpRuntimeStatus.starting()));
        Assert.assertFalse(LanFtpRuntimeRegistry.publish(LanFtpRuntimeStatus.starting()));
        Assert.assertTrue(LanFtpRuntimeRegistry.publish(LanFtpRuntimeStatus.stopping()));
        Assert.assertTrue(LanFtpRuntimeRegistry.reset());
    }

    @Test
    public void registryPublishesAnImmutableSanitizedSnapshot() {
        LanFtpRuntimeRegistry.reset();

        LanFtpRuntimeRegistry.publish(
                LanFtpRuntimeStatus.running(
                        "ftp://192.168.1.20:2121",
                        List.of(
                                new LanFtpSessionInfo("1", "192.168.1.2"),
                                new LanFtpSessionInfo("2", "192.168.1.3"),
                                new LanFtpSessionInfo("3", "192.168.1.4")),
                        1));

        LanFtpRuntimeStatus snapshot = LanFtpRuntimeRegistry.snapshot();
        Assert.assertTrue(snapshot.active);
        Assert.assertEquals("Running", snapshot.status);
        Assert.assertEquals("ftp://192.168.1.20:2121", snapshot.endpoint);
        Assert.assertEquals(3, snapshot.clientCount());
        Assert.assertEquals(1, snapshot.transferCount);
    }

    @Test
    public void invalidPublishedDataFailsClosed() {
        LanFtpRuntimeRegistry.publish(new LanFtpRuntimeStatus(
                true, "", "http://wrong-scheme", null, -5));

        LanFtpRuntimeStatus snapshot = LanFtpRuntimeRegistry.snapshot();

        Assert.assertFalse(snapshot.active);
        Assert.assertEquals("Unavailable", snapshot.status);
        Assert.assertEquals("", snapshot.endpoint);
        Assert.assertEquals(0, snapshot.clientCount());
    }

    @Test
    public void resetReturnsStoppedSnapshot() {
        LanFtpRuntimeRegistry.publish(
                LanFtpRuntimeStatus.running(
                        "ftp://192.168.1.20:2121", List.of(), 0));

        LanFtpRuntimeRegistry.reset();

        Assert.assertFalse(LanFtpRuntimeRegistry.snapshot().active);
        Assert.assertEquals("Stopped", LanFtpRuntimeRegistry.snapshot().status);
    }

    @Test
    public void providerClearsTransientRuntimeWhenServiceNoLongerExists() {
        LanFtpRuntimeStatus starting = LanFtpStatusProvider.runtimeForServiceState(
                LanFtpRuntimeStatus.starting(), false);
        LanFtpRuntimeStatus stopping = LanFtpStatusProvider.runtimeForServiceState(
                LanFtpRuntimeStatus.stopping(), false);
        LanFtpRuntimeStatus running = LanFtpStatusProvider.runtimeForServiceState(
                LanFtpRuntimeStatus.running("ftp://192.168.1.20:2121", List.of(), 0), false);
        LanFtpRuntimeStatus failed = LanFtpStatusProvider.runtimeForServiceState(
                LanFtpRuntimeStatus.failed("Android foreground-service time limit reached"),
                false);

        Assert.assertEquals("Stopped", starting.status);
        Assert.assertEquals("Stopped", stopping.status);
        Assert.assertFalse(running.active);
        Assert.assertEquals("Stopped", running.status);
        Assert.assertTrue(failed.startupFailed);
        Assert.assertEquals("Android foreground-service time limit reached", failed.status);
    }
}
