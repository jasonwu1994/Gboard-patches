package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpSessionInfoTest {
    @Test
    public void exposesBasenameAndKnownDownloadProgressWithoutOverflow() {
        LanFtpSessionInfo session = new LanFtpSessionInfo(
                "id", "peer", 1L, 2L, "RETR", "/folder/video.mp4", "download",
                3L * 1024L * 1024L * 1024L,
                4L * 1024L * 1024L * 1024L,
                11L * 1024L * 1024L);

        Assert.assertTrue(session.isTransferActive());
        Assert.assertEquals("video.mp4", session.fileName());
        Assert.assertEquals(75, session.progressPercent());
        Assert.assertEquals(11L * 1024L * 1024L, session.bytesPerSecond());
    }

    @Test
    public void representsUnknownUploadTotalAndClampsKnownProgress() {
        LanFtpSessionInfo upload = new LanFtpSessionInfo(
                "id", "peer", 0L, 0L, "STOR", "folder\\archive.zip", "upload",
                12L, -1L);
        LanFtpSessionInfo complete = new LanFtpSessionInfo(
                "id", "peer", 0L, 0L, "RETR", "empty.bin", "download",
                20L, 10L);
        LanFtpSessionInfo empty = new LanFtpSessionInfo(
                "id", "peer", 0L, 0L, "RETR", "empty.bin", "download",
                0L, 0L);

        Assert.assertEquals("archive.zip", upload.fileName());
        Assert.assertEquals(-1, upload.progressPercent());
        Assert.assertEquals(100, complete.progressPercent());
        Assert.assertEquals(100, empty.progressPercent());
    }

    @Test
    public void sanitizationPreservesBasenameAfterADeepPath() {
        String path = "/" + "directory".repeat(40) + "/archive.zip";
        LanFtpSessionInfo session = new LanFtpSessionInfo(
                "id", "peer", 0L, 0L, "RETR", path, "download", 1L);

        LanFtpSessionInfo sanitized = session.sanitized();

        Assert.assertEquals("archive.zip", sanitized.path());
        Assert.assertEquals("archive.zip", sanitized.fileName());
    }

    @Test
    public void sanitizationKeepsIdlePathEmpty() {
        LanFtpSessionInfo idle = new LanFtpSessionInfo("id", "peer");

        Assert.assertEquals("", idle.sanitized().path());
    }
}
