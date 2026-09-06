package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

public final class LanFtpNotificationContentTest {
    @Test
    public void downloadShowsBasenameBytesAndDeterminatePercentage() {
        LanFtpNotificationContent content = LanFtpNotificationContent.from(
                "idle",
                new LanFtpSessionInfo(
                        "id", "peer", 0L, 0L, "RETR", "/private/movie.mkv", "download",
                        128L * 1024L * 1024L,
                        512L * 1024L * 1024L,
                        12L * 1024L * 1024L));

        Assert.assertEquals(
                "↓ movie.mkv • 128.0 MiB / 512.0 MiB • 25% • 12.0 MiB/s",
                content.text());
        Assert.assertEquals(25, content.progress());
        Assert.assertFalse(content.indeterminate());
        Assert.assertFalse(content.text().contains("/private/"));
    }

    @Test
    public void uploadShowsTransferredBytesAndIndeterminateProgress() {
        LanFtpNotificationContent content = LanFtpNotificationContent.from(
                "idle",
                new LanFtpSessionInfo(
                        "id", "peer", 0L, 0L, "STOR", "/folder/video.mp4", "upload",
                        1536L, -1L, 3L * 1024L * 1024L));

        Assert.assertEquals("↑ video.mp4 • 1.5 KiB • 3.0 MiB/s", content.text());
        Assert.assertTrue(content.indeterminate());
    }

    @Test
    public void idleUsesExistingServerStatusAndNoProgress() {
        LanFtpNotificationContent content = LanFtpNotificationContent.from(
                "ftp://192.168.1.2:2121 • read-write", null);

        Assert.assertEquals("ftp://192.168.1.2:2121 • read-write", content.text());
        Assert.assertEquals(0, content.progress());
        Assert.assertFalse(content.indeterminate());
    }
}
