package dev.jason.gboardpatches.extension.lanftp;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

public final class LanFtpPathTest {
    @Test
    public void resolvesAbsoluteAndRelativePathsWithoutEscapingTheExportRoot() {
        Assert.assertEquals("/folder/file.txt", LanFtpPath.resolve("/folder", "file.txt"));
        Assert.assertEquals("/other.bin", LanFtpPath.resolve("/folder", "../other.bin"));
        Assert.assertEquals("/folder/sub", LanFtpPath.resolve("/folder", "/folder/./sub"));
        Assert.assertEquals("/", LanFtpPath.resolve("/folder", ".."));
    }

    @Test
    public void traversalAndAmbiguousSeparatorsFailClosed() {
        assertRejected(() -> LanFtpPath.resolve("/", "../secret"));
        assertRejected(() -> LanFtpPath.resolve("/folder", "../../secret"));
        assertRejected(() -> LanFtpPath.resolve("/", "folder\\secret"));
        assertRejected(() -> LanFtpPath.resolve("/", "bad\u0000name"));
        assertRejected(() -> LanFtpPath.resolve("/", ".lanftp-private"));
        assertRejected(() -> LanFtpPath.resolve("/", "safe%2Fescape"));
        assertRejected(() -> LanFtpPath.resolve("/", "safe%5cescape"));
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected unsafe FTP path to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
