package dev.jason.gboardpatches.extension.lanftp;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;

public final class LanFtpServerConfigTest {
    @Test
    public void defaultsExposeTheSafeTrustedLanProfile() {
        LanFtpServerConfig config = LanFtpServerConfig.defaults(
                "gboard",
                "abcdef",
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FFtp");

        Assert.assertEquals(2121, config.controlPort());
        Assert.assertEquals(50000, config.passivePortStart());
        Assert.assertEquals(50009, config.passivePortEnd());
        Assert.assertEquals(4, config.maxSessions());
        Assert.assertEquals(5 * 60, config.idleTimeoutSeconds());
        Assert.assertFalse(config.readOnly());
        Assert.assertTrue(config.allowAnonymous());
        Assert.assertEquals("gboard", config.username());
    }

    @Test
    public void invalidPortRangeAndWeakCredentialsFailClosed() {
        assertRejected(() -> new LanFtpServerConfig(
                21, 50000, 50009, 4, 900, true,
                "gboard", "abcdef", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50009, 50000, 4, 900, true,
                "gboard", "abcdef", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50000, 50032, 4, 900, true,
                "gboard", "abcdef", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50000, 50009, 0, 900, true,
                "gboard", "abcdef", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50000, 50009, 4, 900, true,
                "gboard", "short", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50000, 50009, 4, 900, true,
                "other", "abcdef", "content://tree"));
        assertRejected(() -> new LanFtpServerConfig(
                2121, 50000, 50009, 4, 900, true,
                "gboard", "abcdef", ""));
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected invalid FTP configuration to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
