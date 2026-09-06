package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpStagingDocumentStoreTest {
    @Test
    public void uploadRemainsHiddenUntilCompletion() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        try (OutputStream output = store.openOutputStream("/large.bin", 0L)) {
            output.write(bytes("complete"));
        }

        Assert.assertNull(store.stat("/large.bin"));
        Assert.assertTrue(store.list("/").isEmpty());
        Assert.assertTrue(store.isUploadIncomplete("/large.bin"));

        store.completeUpload("/large.bin");

        Assert.assertEquals("complete", read(store, "/large.bin"));
        Assert.assertEquals(List.of("/large.bin"), store.list("/").stream()
                .map(LanFtpDocument::absolutePath).toList());
        Assert.assertFalse(store.isUploadIncomplete("/large.bin"));
    }

    @Test
    public void abortedUploadIsDiscardedAndCannotBeResumed() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        try (OutputStream output = store.openOutputStream("/interrupted.bin", 0L)) {
            output.write(bytes("partial"));
        }
        store.abortUpload("/interrupted.bin");

        Assert.assertNull(store.stat("/interrupted.bin"));
        Assert.assertFalse(store.isUploadIncomplete("/interrupted.bin"));
        Assert.assertTrue(delegate.list("/").stream()
                .noneMatch(item -> item.absolutePath().contains(".gboardftp-")));
        assertOutputRejected(store, "/interrupted.bin", 1L, "not supported");
    }

    @Test
    public void interruptedOverwritePreservesTheOldFinal() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/archive.zip", bytes("old-final"), 1L);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        try (OutputStream output = store.openOutputStream("/archive.zip", 0L)) {
            output.write(bytes("partial-new"));
        }
        store.abortUpload("/archive.zip");

        Assert.assertEquals("old-final", read(store, "/archive.zip"));
        Assert.assertEquals(1, delegate.list("/").size());
    }

    @Test
    public void nonZeroUploadOffsetIsAlwaysRejected() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/archive.zip", bytes("existing"), 1L);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        assertOutputRejected(store, "/archive.zip", 1L, "not supported");
        Assert.assertEquals("existing", read(store, "/archive.zip"));
    }

    @Test
    public void duplicateWriterForTheSamePathIsRejected() throws Exception {
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(
                new InMemoryLanFtpDocumentStore());
        OutputStream first = store.openOutputStream("/same.bin", 0L);
        try {
            assertOutputRejected(store, "/same.bin", 0L, "already being uploaded");
        } finally {
            first.close();
            store.abortUpload("/same.bin");
        }
    }

    @Test
    public void completedUploadSafelyReplacesExistingFinal() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/final.bin", bytes("old"), 1L);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        try (OutputStream output = store.openOutputStream("/final.bin", 0L)) {
            output.write(bytes("replacement"));
        }
        store.completeUpload("/final.bin");

        Assert.assertEquals("replacement", read(store, "/final.bin"));
        Assert.assertTrue(delegate.list("/").stream()
                .noneMatch(item -> item.absolutePath().contains(".gboardftp-")));
    }

    @Test
    public void failedPromotionRestoresExistingFinal() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/final.bin", bytes("old"), 1L);
        FailingPromotionStore failing = new FailingPromotionStore(delegate);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(failing);

        try (OutputStream output = store.openOutputStream("/final.bin", 0L)) {
            output.write(bytes("replacement"));
        }

        IOException failure = Assert.assertThrows(
                IOException.class, () -> store.completeUpload("/final.bin"));
        Assert.assertTrue(failure.getMessage().contains("old target was restored"));
        Assert.assertEquals("old", read(store, "/final.bin"));
    }

    @Test
    public void reservedArtifactsAreHiddenAndCannotBeAddressed() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/.gboardftp-orphan.part", bytes("hidden"), 1L);
        delegate.putFile("/visible.txt", bytes("visible"), 1L);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        Assert.assertEquals(List.of("/visible.txt"), store.list("/").stream()
                .map(LanFtpDocument::absolutePath).toList());
        IOException failure = Assert.assertThrows(IOException.class,
                () -> store.stat("/.gboardftp-orphan.part"));
        Assert.assertTrue(failure.getMessage().contains("reserved staging name"));
    }

    @Test
    public void completedTemporaryNameCanReplaceAnExistingFile() throws Exception {
        InMemoryLanFtpDocumentStore delegate = new InMemoryLanFtpDocumentStore();
        delegate.putFile("/final.bin", bytes("old"), 1L);
        delegate.putFile("/final.uploading", bytes("replacement"), 2L);
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(delegate);

        Assert.assertTrue(store.move("/final.uploading", "/final.bin"));
        Assert.assertEquals("replacement", read(store, "/final.bin"));
        Assert.assertNull(store.stat("/final.uploading"));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(LanFtpDocumentStore store, String path) throws IOException {
        try (InputStream input = store.openInputStream(path, 0L)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertOutputRejected(LanFtpDocumentStore store, String path,
            long offset, String expectedMessage) {
        IOException failure = Assert.assertThrows(
                IOException.class, () -> store.openOutputStream(path, offset));
        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains(expectedMessage));
    }

    private static final class FailingPromotionStore implements LanFtpDocumentStore {
        private final LanFtpDocumentStore delegate;
        private boolean failPromotion = true;

        private FailingPromotionStore(LanFtpDocumentStore delegate) {
            this.delegate = delegate;
        }

        @Override public LanFtpDocument stat(String path) throws IOException {
            return delegate.stat(path);
        }

        @Override public List<LanFtpDocument> list(String path) throws IOException {
            return delegate.list(path);
        }

        @Override public boolean createDirectory(String path) throws IOException {
            return delegate.createDirectory(path);
        }

        @Override public boolean delete(String path) throws IOException {
            return delegate.delete(path);
        }

        @Override public boolean move(String source, String destination) throws IOException {
            if (failPromotion && source.endsWith(".part") && "/final.bin".equals(destination)) {
                failPromotion = false;
                return false;
            }
            return delegate.move(source, destination);
        }

        @Override public boolean setLastModified(String path, long lastModified)
                throws IOException {
            return delegate.setLastModified(path, lastModified);
        }

        @Override public InputStream openInputStream(String path, long offset)
                throws IOException {
            return delegate.openInputStream(path, offset);
        }

        @Override public OutputStream openOutputStream(String path, long offset)
                throws IOException {
            return delegate.openOutputStream(path, offset);
        }

        @Override public boolean supportsRandomAccess() {
            return delegate.supportsRandomAccess();
        }
    }
}
