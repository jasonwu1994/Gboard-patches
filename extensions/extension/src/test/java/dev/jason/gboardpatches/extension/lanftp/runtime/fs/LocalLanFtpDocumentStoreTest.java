package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AccessDeniedException;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public final class LocalLanFtpDocumentStoreTest {
    private Path temporaryRoot;
    private LocalLanFtpDocumentStore store;

    @Before
    public void setUp() throws Exception {
        temporaryRoot = Files.createTempDirectory("lanftp-local-");
        store = new LocalLanFtpDocumentStore(temporaryRoot.toFile(), false);
    }

    @After
    public void tearDown() throws Exception {
        if (temporaryRoot != null) {
            deleteRecursively(temporaryRoot.toFile());
        }
    }

    @Test
    public void statListAndCrudStayInsideCanonicalRoot() throws Exception {
        Assert.assertTrue(store.createDirectory("/資料"));
        try (OutputStream output = store.openOutputStream("/資料/夜景-🌃.txt", 0L)) {
            output.write("abcdef".getBytes(StandardCharsets.UTF_8));
        }

        LanFtpDocument document = store.stat("/資料/夜景-🌃.txt");
        Assert.assertNotNull(document);
        Assert.assertEquals(6L, document.size());
        Assert.assertTrue(document.isReadable());
        Assert.assertTrue(document.isWritable());
        Assert.assertTrue(document.isRemovable());
        List<LanFtpDocument> children = store.list("/資料");
        Assert.assertEquals(1, children.size());
        Assert.assertEquals("/資料/夜景-🌃.txt", children.get(0).absolutePath());

        long modified = 1_700_000_000_000L;
        Assert.assertTrue(store.setLastModified("/資料/夜景-🌃.txt", modified));
        Assert.assertEquals(modified, store.stat("/資料/夜景-🌃.txt").lastModified());
        Assert.assertTrue(store.move("/資料/夜景-🌃.txt", "/資料/renamed.txt"));
        Assert.assertNull(store.stat("/資料/夜景-🌃.txt"));
        Assert.assertTrue(store.delete("/資料/renamed.txt"));
        Assert.assertTrue(store.delete("/資料"));
    }

    @Test
    public void interactiveListingFailsClosedAboveItsEntryLimit() throws Exception {
        Files.write(temporaryRoot.resolve("one.bin"), new byte[]{1});
        Files.write(temporaryRoot.resolve("two.bin"), new byte[]{2});
        Files.write(temporaryRoot.resolve("three.bin"), new byte[]{3});
        LocalLanFtpDocumentStore bounded =
                new LocalLanFtpDocumentStore(temporaryRoot.toFile(), false, 2);

        assertIoRejected(() -> bounded.list("/"), "entry limit");
    }

    @Test
    public void existingOpaqueSharedStorageDirectoryIsPresentedAsEmpty() throws Exception {
        LocalLanFtpDocumentStore opaque = new LocalLanFtpDocumentStore(
                temporaryRoot.toFile(), false, 2,
                path -> { throw new AccessDeniedException(path.toString()); });

        Assert.assertEquals(List.of(), opaque.list("/"));
    }

    @Test
    public void streamsUseLongOffsetsAndResumeTruncatesAtRestartPoint() throws Exception {
        try (OutputStream output = store.openOutputStream("/large.bin", 0L)) {
            output.write("abcdef".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = store.openInputStream("/large.bin", 3L)) {
            Assert.assertEquals("def", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (OutputStream output = store.openOutputStream("/large.bin", 3L)) {
            output.write('Z');
        }
        try (InputStream input = store.openInputStream("/large.bin", 0L)) {
            Assert.assertEquals("abcZ", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertIoRejected(() -> store.openInputStream("/large.bin", 5L), "offset");
        assertIoRejected(() -> store.openOutputStream("/large.bin", 5L), "offset");
        Assert.assertTrue(store.supportsRandomAccess());
    }

    @Test
    public void directoryMovePreservesChildrenAndCannotMoveIntoItsOwnSubtree() throws Exception {
        Assert.assertTrue(store.createDirectory("/folder"));
        Assert.assertTrue(store.createDirectory("/folder/nested"));
        try (OutputStream output = store.openOutputStream("/folder/nested/file.bin", 0L)) {
            output.write(1);
        }

        Assert.assertFalse(store.move("/folder", "/folder/nested/moved"));
        Assert.assertFalse(store.delete("/folder"));
        Assert.assertTrue(store.move("/folder", "/moved"));
        Assert.assertNotNull(store.stat("/moved/nested/file.bin"));
        Assert.assertNull(store.stat("/folder"));
    }

    @Test
    public void rootAndTraversalAreRejected() throws Exception {
        Path sibling = Files.createTempFile(temporaryRoot.getParent(), "lanftp-outside-", ".txt");
        try {
            Assert.assertFalse(store.delete("/"));
            Assert.assertFalse(store.move("/", "/moved-root"));
            Assert.assertFalse(store.move("/missing", "/"));
            assertIoRejected(() -> store.stat("/../../" + sibling.getFileName()), "root");
            assertIoRejected(() -> store.openOutputStream("/../escape.bin", 0L), "root");
            assertIoRejected(() -> store.stat("relative.txt"), "absolute");
        } finally {
            Files.deleteIfExists(sibling);
        }
    }

    @Test
    public void symlinksAreNotExposedOrFollowed() throws Exception {
        Path outside = Files.createTempDirectory(temporaryRoot.getParent(), "lanftp-secret-");
        Files.write(outside.resolve("secret.txt"), "secret".getBytes(StandardCharsets.UTF_8));
        Path link = temporaryRoot.resolve("link");
        try {
            try {
                Files.createSymbolicLink(link, outside);
            } catch (IOException | UnsupportedOperationException | SecurityException exception) {
                Assume.assumeNoException("Symbolic links are unavailable on this platform", exception);
            }
            assertIoRejected(() -> store.stat("/link/secret.txt"), "root");
            Assert.assertTrue(store.list("/").stream()
                    .noneMatch(document -> "/link".equals(document.absolutePath())));
            assertIoRejected(() -> store.openInputStream("/link/secret.txt", 0L), "root");
        } finally {
            Files.deleteIfExists(link);
            deleteRecursively(outside.toFile());
        }
    }

    @Test
    public void readOnlyStoreRejectsEveryMutation() throws Exception {
        Files.write(temporaryRoot.resolve("existing.txt"),
                "data".getBytes(StandardCharsets.UTF_8));
        LocalLanFtpDocumentStore readOnly =
                new LocalLanFtpDocumentStore(temporaryRoot.toFile(), true);

        Assert.assertFalse(readOnly.stat("/existing.txt").isWritable());
        Assert.assertFalse(readOnly.stat("/existing.txt").isRemovable());
        assertIoRejected(() -> readOnly.createDirectory("/new"), "read-only");
        assertIoRejected(() -> readOnly.delete("/existing.txt"), "read-only");
        assertIoRejected(() -> readOnly.move("/existing.txt", "/renamed.txt"), "read-only");
        assertIoRejected(() -> readOnly.setLastModified("/existing.txt", 1L), "read-only");
        assertIoRejected(() -> readOnly.openOutputStream("/upload.bin", 0L), "read-only");
    }

    @Test
    public void stagingStoreCanPromoteACompletedLocalUpload() throws Exception {
        LanFtpStagingDocumentStore staging = new LanFtpStagingDocumentStore(store);

        try (OutputStream output = staging.openOutputStream("/video.bin", 0L)) {
            output.write("payload".getBytes(StandardCharsets.UTF_8));
        }
        Assert.assertNull(staging.stat("/video.bin"));
        staging.completeUpload("/video.bin");

        try (InputStream input = staging.openInputStream("/video.bin", 0L)) {
            Assert.assertEquals("payload",
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        Assert.assertEquals(1, staging.list("/").size());
    }

    @Test
    public void sparseFileSupportsOffsetBeyondTwoGibibytesWhenFilesystemAllowsIt()
            throws Exception {
        long offset = (2L * 1024L * 1024L * 1024L) + 4096L;
        File file = temporaryRoot.resolve("sparse.bin").toFile();
        try {
            try (RandomAccessFile sparse = new RandomAccessFile(file, "rw")) {
                sparse.setLength(offset);
            }
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException("Sparse files are unavailable on this filesystem", exception);
        }

        try (OutputStream output = store.openOutputStream("/sparse.bin", offset)) {
            output.write(0x5a);
        }

        Assert.assertEquals(offset + 1L, store.stat("/sparse.bin").size());
        try (InputStream input = store.openInputStream("/sparse.bin", offset)) {
            Assert.assertEquals(0x5a, input.read());
            Assert.assertEquals(-1, input.read());
        }
    }

    private static void assertIoRejected(IoAction action, String expectedText) throws Exception {
        try {
            action.run();
            Assert.fail("Expected local FTP filesystem operation to fail");
        } catch (IOException expected) {
            Assert.assertTrue(
                    "Unexpected message: " + expected.getMessage(),
                    expected.getMessage().toLowerCase(java.util.Locale.ROOT)
                            .contains(expectedText.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (Files.isSymbolicLink(file.toPath())) {
            Files.deleteIfExists(file.toPath());
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        IOException failure = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(file.toPath());
                return;
            } catch (IOException exception) {
                failure = exception;
                try {
                    Thread.sleep(25L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
        throw failure;
    }

    private interface IoAction {
        void run() throws Exception;
    }
}
