package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpFileSystemTest {
    @Test
    public void directoryListingPreservesUtf8AndLongMetadata() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        long modifiedAt = 1_912_345_678_901L;
        long size = 5_368_709_120L;
        store.putFileMetadata("/相片-🌃.jpg", size, modifiedAt);
        store.putFile("/.lanftp-private", new byte[0], 1L);
        LanFtpFile root = new LanFtpFile(store, "/", "ftpuser", false);

        List<? extends FtpFile> files = root.listFiles();

        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.size());
        Assert.assertEquals("相片-🌃.jpg", files.get(0).getName());
        Assert.assertEquals(size, files.get(0).getSize());
        Assert.assertEquals(modifiedAt, files.get(0).getLastModified());
    }

    @Test
    public void writableClientCanCreateMoveAndDeleteButReadOnlyClientCannot() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        store.putDirectory("/資料", 100L);
        LanFtpFile created = new LanFtpFile(store, "/資料/新資料夾", "ftpuser", false);

        Assert.assertTrue(created.mkdir());
        Assert.assertTrue(created.doesExist());
        LanFtpFile renamed = new LanFtpFile(store, "/資料/已重新命名", "ftpuser", false);
        Assert.assertTrue(created.move(renamed));
        Assert.assertFalse(created.doesExist());
        Assert.assertTrue(renamed.doesExist());
        Assert.assertTrue(renamed.delete());

        LanFtpFile readOnly = new LanFtpFile(store, "/資料/禁止", "ftpuser", true);
        Assert.assertFalse(readOnly.isWritable());
        Assert.assertFalse(readOnly.mkdir());
        try {
            readOnly.createOutputStream(0L);
            Assert.fail("Expected read-only upload to fail");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("read-only"));
        }
    }

    @Test
    public void streamsResumeAtByteOffsetsAndZeroOffsetTruncates() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        store.putFile("/large.bin", "abcdef".getBytes(StandardCharsets.UTF_8), 1L);
        LanFtpFile file = new LanFtpFile(store, "/large.bin", "ftpuser", false);

        try (InputStream input = file.createInputStream(3L)) {
            Assert.assertEquals("def", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (OutputStream output = file.createOutputStream(3L)) {
            output.write("Z".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = file.createInputStream(0L)) {
            Assert.assertEquals("abcZ", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (OutputStream output = file.createOutputStream(0L)) {
            output.write("new".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = file.createInputStream(0L)) {
            Assert.assertEquals("new", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void nonSeekableStoreRejectsNonZeroOffsets() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        store.putFile("/cloud.bin", "abcdef".getBytes(StandardCharsets.UTF_8), 1L);
        store.setRandomAccess(false);
        LanFtpFile file = new LanFtpFile(store, "/cloud.bin", "ftpuser", false);

        assertSeekRejected(() -> file.createInputStream(1L));
        assertSeekRejected(() -> file.createOutputStream(1L));
    }

    @Test
    public void transferOffsetsRemain64BitBeyondFourGibibytes() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        store.putFileMetadata("/huge.bin", 5_368_709_120L, 1L);
        LanFtpFile file = new LanFtpFile(store, "/huge.bin", "ftpuser", false);
        long offset = 4_294_967_296L;

        try (InputStream ignored = file.createInputStream(offset)) {
            Assert.assertEquals(offset, store.lastInputOffset());
        }
        try (OutputStream ignored = file.createOutputStream(offset)) {
            Assert.assertEquals(offset, store.lastOutputOffset());
        }
    }

    @Test
    public void fileSystemViewContainsWorkingDirectoryAndRejectsUnsafePaths() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        store.putDirectory("/資料", 100L);
        FileSystemView view = new LanFtpFileSystemView(store, "ftpuser", false);

        Assert.assertEquals("/", view.getHomeDirectory().getAbsolutePath());
        Assert.assertTrue(view.changeWorkingDirectory("資料"));
        Assert.assertEquals("/資料", view.getWorkingDirectory().getAbsolutePath());
        Assert.assertEquals("/資料/檔案.txt", view.getFile("檔案.txt").getAbsolutePath());
        Assert.assertFalse(view.changeWorkingDirectory("missing"));
        Assert.assertTrue(view.isRandomAccessible());

        try {
            view.getFile("../../escape");
            Assert.fail("Expected traversal outside root to fail");
        } catch (FtpException expected) {
            Assert.assertTrue(expected.getMessage().contains("Unsafe FTP path"));
        }
        try {
            view.getFile("/.lanftp-private");
            Assert.fail("Expected reserved internal path to fail");
        } catch (FtpException expected) {
            Assert.assertTrue(expected.getMessage().contains("Unsafe FTP path"));
        }
    }

    @Test
    public void factoryCreatesViewsOnlyForTheConfiguredUser() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        FileSystemFactory factory = new LanFtpFileSystemFactory(store, "ftpuser", false);
        BaseUser configuredUser = new BaseUser();
        configuredUser.setName("ftpuser");

        Assert.assertEquals("/",
                factory.createFileSystemView(configuredUser).getHomeDirectory().getAbsolutePath());

        BaseUser otherUser = new BaseUser();
        otherUser.setName("other");
        try {
            factory.createFileSystemView(otherUser);
            Assert.fail("Expected filesystem owner mismatch to fail");
        } catch (FtpException expected) {
            Assert.assertTrue(expected.getMessage().contains("does not match"));
        }
    }

    @Test
    public void factoryAcceptsAnonymousOnlyWhenExplicitlyEnabled() throws Exception {
        InMemoryLanFtpDocumentStore store = new InMemoryLanFtpDocumentStore();
        FileSystemFactory disabled = new LanFtpFileSystemFactory(
                store, "ftpuser", false, false);
        FileSystemFactory enabled = new LanFtpFileSystemFactory(
                store, "ftpuser", false, true);
        BaseUser anonymous = new BaseUser();
        anonymous.setName("anonymous");

        try {
            disabled.createFileSystemView(anonymous);
            Assert.fail("Expected disabled anonymous filesystem access to fail");
        } catch (FtpException expected) {
            Assert.assertTrue(expected.getMessage().contains("does not match"));
        }
        Assert.assertEquals("/",
                enabled.createFileSystemView(anonymous).getHomeDirectory().getAbsolutePath());
    }

    private static void assertSeekRejected(IoAction action) throws Exception {
        try {
            action.run();
            Assert.fail("Expected non-seekable provider to reject resume");
        } catch (java.io.IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("seek"));
        }
    }

    private interface IoAction {
        void run() throws Exception;
    }
}
