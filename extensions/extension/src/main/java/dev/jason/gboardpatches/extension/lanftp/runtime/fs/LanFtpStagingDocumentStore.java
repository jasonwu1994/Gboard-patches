package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

/**
 * Keeps an in-flight upload hidden until it can safely replace the final file.
 * Interrupted uploads are discarded immediately and cannot be resumed.
 */
public final class LanFtpStagingDocumentStore
        implements LanFtpDocumentStore, LanFtpUploadLifecycle {
    private static final String INTERNAL_PREFIX = ".gboardftp-";
    private static final String STAGING_SUFFIX = ".part";
    private static final String BACKUP_SUFFIX = ".backup";
    private static final String LEGACY_METADATA_SUFFIX = ".meta";
    private static final String LEGACY_MIGRATION_SUFFIX = ".meta.next";

    private final LanFtpDocumentStore delegate;
    private final Object writeLock = new Object();
    private final Set<String> activeWrites = new HashSet<>();

    public LanFtpStagingDocumentStore(LanFtpDocumentStore delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public LanFtpDocument stat(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        recoverInterruptedPromotion(path);
        return delegate.stat(path);
    }

    @Override
    public List<LanFtpDocument> list(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        List<LanFtpDocument> visible = new ArrayList<>();
        for (LanFtpDocument child : delegate.list(path)) {
            if (!isInternalPath(child.absolutePath())) {
                visible.add(child);
            }
        }
        return visible;
    }

    @Override
    public boolean createDirectory(String absolutePath) throws IOException {
        return delegate.createDirectory(logicalPath(absolutePath));
    }

    @Override
    public boolean delete(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        synchronized (writeLock) {
            if (activeWrites.contains(path)) {
                return false;
            }
            recoverInterruptedPromotionLocked(path);
            boolean deleted = delegate.delete(path);
            discardUploadArtifactsLocked(path);
            return deleted;
        }
    }

    @Override
    public boolean move(String sourcePath, String destinationPath) throws IOException {
        String source = logicalPath(sourcePath);
        String destination = logicalPath(destinationPath);
        synchronized (writeLock) {
            if (source.equals(destination)
                    || activeWrites.contains(source) || activeWrites.contains(destination)) {
                return false;
            }
            recoverInterruptedPromotionLocked(source);
            recoverInterruptedPromotionLocked(destination);
            LanFtpDocument sourceDocument = delegate.stat(source);
            LanFtpDocument destinationDocument = delegate.stat(destination);
            if (sourceDocument == null) {
                return false;
            }
            if (destinationDocument == null) {
                return delegate.move(source, destination);
            }
            if (sourceDocument.isDirectory() || destinationDocument.isDirectory()) {
                return false;
            }
            discardUploadArtifactsLocked(destination);
            replaceFileSafely(source, destination);
            return true;
        }
    }

    @Override
    public boolean setLastModified(String absolutePath, long lastModified) throws IOException {
        return delegate.setLastModified(logicalPath(absolutePath), lastModified);
    }

    @Override
    public InputStream openInputStream(String absolutePath, long offset) throws IOException {
        String path = logicalPath(absolutePath);
        recoverInterruptedPromotion(path);
        return delegate.openInputStream(path, offset);
    }

    @Override
    public OutputStream openOutputStream(String absolutePath, long offset) throws IOException {
        String path = logicalPath(absolutePath);
        if (offset != 0L) {
            throw new IOException(
                    "FTP upload resume is not supported; restart the upload from byte 0");
        }
        if ("/".equals(path)) {
            throw new IOException("Cannot upload to the FTP root");
        }
        synchronized (writeLock) {
            if (!activeWrites.add(path)) {
                throw new IOException("FTP path is already being uploaded: " + path);
            }
            try {
                recoverInterruptedPromotionLocked(path);
                discardUploadArtifactsLocked(path);
                OutputStream output = delegate.openOutputStream(stagingPath(path), 0L);
                return new FilterOutputStream(output) {
                    private boolean closed;

                    @Override
                    public void write(byte[] bytes, int writeOffset, int length)
                            throws IOException {
                        out.write(bytes, writeOffset, length);
                    }

                    @Override
                    public void close() throws IOException {
                        if (closed) {
                            return;
                        }
                        closed = true;
                        super.close();
                    }
                };
            } catch (Throwable throwable) {
                activeWrites.remove(path);
                if (throwable instanceof IOException exception) {
                    throw exception;
                }
                if (throwable instanceof RuntimeException exception) {
                    throw exception;
                }
                if (throwable instanceof Error error) {
                    throw error;
                }
                throw new IOException("Unexpected FTP upload setup failure", throwable);
            }
        }
    }

    @Override
    public boolean supportsRandomAccess() {
        return delegate.supportsRandomAccess();
    }

    @Override
    public void completeUpload(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        synchronized (writeLock) {
            if (!activeWrites.contains(path)) {
                throw new IOException("No active FTP upload exists for " + path);
            }
            try {
                String staging = stagingPath(path);
                LanFtpDocument staged = delegate.stat(staging);
                if (staged == null || staged.isDirectory()) {
                    throw new IOException("FTP staging document disappeared: " + path);
                }
                recoverInterruptedPromotionLocked(path);
                LanFtpDocument completed = delegate.stat(path);
                if (completed != null && completed.isDirectory()) {
                    throw new IOException("FTP upload target is a directory: " + path);
                }
                replaceFileSafely(staging, path);
                discardLegacyMetadataLocked(path);
            } finally {
                activeWrites.remove(path);
            }
        }
    }

    @Override
    public void abortUpload(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        synchronized (writeLock) {
            try {
                discardUploadArtifactsLocked(path);
                recoverInterruptedPromotionLocked(path);
            } finally {
                activeWrites.remove(path);
            }
        }
    }

    @Override
    public boolean isUploadIncomplete(String absolutePath) throws IOException {
        String path = logicalPath(absolutePath);
        synchronized (writeLock) {
            return activeWrites.contains(path);
        }
    }

    private void replaceFileSafely(String source, String destination) throws IOException {
        LanFtpDocument existing = delegate.stat(destination);
        if (existing == null) {
            if (!delegate.move(source, destination)) {
                throw new IOException("Unable to promote FTP document: " + destination);
            }
            return;
        }
        String backup = backupPath(destination);
        deleteIfPresent(backup, "Unable to clear stale FTP replacement backup");
        if (!delegate.move(destination, backup)) {
            throw new IOException("Document provider cannot safely replace FTP target: "
                    + destination);
        }
        if (!delegate.move(source, destination)) {
            boolean restored = delegate.move(backup, destination);
            throw new IOException("Unable to promote FTP replacement; old target "
                    + (restored ? "was restored: " : "requires recovery: ") + destination);
        }
        delegate.delete(backup);
    }

    private void recoverInterruptedPromotion(String path) throws IOException {
        synchronized (writeLock) {
            recoverInterruptedPromotionLocked(path);
        }
    }

    private void recoverInterruptedPromotionLocked(String path) throws IOException {
        String backup = backupPath(path);
        LanFtpDocument oldFinal = delegate.stat(backup);
        if (oldFinal == null) {
            return;
        }
        LanFtpDocument currentFinal = delegate.stat(path);
        if (currentFinal != null) {
            delegate.delete(backup);
            return;
        }
        if (!delegate.move(backup, path)) {
            throw new IOException("Unable to recover interrupted FTP replacement: " + path);
        }
    }

    private void discardUploadArtifactsLocked(String logicalPath) throws IOException {
        deleteIfPresent(stagingPath(logicalPath),
                "Unable to discard interrupted FTP upload");
        discardLegacyMetadataLocked(logicalPath);
    }

    private void discardLegacyMetadataLocked(String logicalPath) throws IOException {
        deleteIfPresent(internalPath(logicalPath, LEGACY_METADATA_SUFFIX),
                "Unable to discard legacy FTP upload metadata");
        deleteIfPresent(internalPath(logicalPath, LEGACY_MIGRATION_SUFFIX),
                "Unable to discard legacy FTP upload metadata");
    }

    private void deleteIfPresent(String path, String failureMessage) throws IOException {
        if (delegate.stat(path) != null && !delegate.delete(path)) {
            throw new IOException(failureMessage + ": " + path);
        }
    }

    private static String logicalPath(String absolutePath) throws IOException {
        final String path;
        try {
            path = LanFtpPath.resolve("/", absolutePath);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unsafe FTP path", exception);
        }
        if (isInternalPath(path)) {
            throw new IOException("FTP path uses a server-reserved staging name");
        }
        return path;
    }

    private static boolean isInternalPath(String path) {
        String name;
        try {
            name = LanFtpPath.name(path);
        } catch (IllegalArgumentException exception) {
            return true;
        }
        return name.startsWith(INTERNAL_PREFIX)
                && (name.endsWith(STAGING_SUFFIX)
                        || name.endsWith(BACKUP_SUFFIX)
                        || name.endsWith(LEGACY_METADATA_SUFFIX)
                        || name.endsWith(LEGACY_MIGRATION_SUFFIX));
    }

    private static String stagingPath(String logicalPath) {
        return internalPath(logicalPath, STAGING_SUFFIX);
    }

    private static String backupPath(String logicalPath) {
        return internalPath(logicalPath, BACKUP_SUFFIX);
    }

    private static String internalPath(String logicalPath, String suffix) {
        String name = INTERNAL_PREFIX + sha256(logicalPath) + suffix;
        String parent = LanFtpPath.parent(logicalPath);
        return "/".equals(parent) ? "/" + name : parent + "/" + name;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(Character.forDigit((item >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(item & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
