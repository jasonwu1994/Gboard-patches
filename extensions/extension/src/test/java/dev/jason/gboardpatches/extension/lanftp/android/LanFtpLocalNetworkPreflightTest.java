package dev.jason.gboardpatches.extension.lanftp.android;

import java.io.IOException;
import java.net.InetAddress;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpLocalNetworkPreflightTest {
    @Test
    public void loopbackCannotPassLanListenerPreflight() throws Exception {
        try {
            LanFtpLocalNetworkPreflight.verifyLanListenerAllowed(
                    InetAddress.getLoopbackAddress());
            Assert.fail("Expected loopback preflight rejection");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("private Wi-Fi"));
        }
    }
}
