package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

/** Local-file backend rooted at one canonical directory such as {@code /sdcard}. */
public final class LocalLanFtpDocumentStore implements LanFtpDocumentStore {
    private final File root;
    private final String rootPath;
    private final String rootPrefix;
    private final boolean readOnly;
    private final int maxDirectoryEntries;
    private final DirectoryStreamOpener directoryStreamOpener;

    public LocalLanFtpDocumentStore(File rootDirectory, boolean readOnly) throws IOException {
        this(rootDirectory, readOnly, LanFtpDocumentStore.MAX_DIRECTORY_ENTRIES);
    }

    LocalLanFtpDocumentStore(File rootDirectory, boolean readOnly, int maxDirectoryEntries)
            throws IOException {
        this(rootDirectory, readOnly, maxDirectoryEntries, Files::newDirectoryStream);
    }

    LocalLanFtpDocumentStore(File rootDirectory, boolean readOnly, int maxDirectoryEntries,
            DirectoryStreamOpener directoryStreamOpener) throws IOException {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        if (maxDirectoryEntries < 1) {
            throw new IllegalArgumentException("maxDirectoryEntries must be positive");
        }
        root = rootDirectory.getCanonicalFile();
        if (!root.isDirectory()) {
            throw new IOException("Local FTP root is not a directory: " + root);
        }
        rootPath = root.getPath();
        rootPrefix = rootPath.endsWith(File.separator)
                ? rootPath
                : rootPath + File.separator;
        this.readOnly = readOnly;
        this.maxDirectoryEntries = maxDirectoryEntries;
        this.directoryStreamOpener = Objects.requireNonNull(
                directoryStreamOpener, "directoryStreamOpener");
    }

    @Override
    public LanFtpDocument stat(String absolutePath) throws IOException {
        Resolved resolved = resolve(absolutePath);
        return resolved.file.exists() ? metadata(resolved) : null;
    }

    @Override
    public List<LanFtpDocument> list(String absolutePath) throws IOException {
        Resolved directory = resolve(absolutePath);
        if (!directory.file.exists() || !directory.file.isDirectory()) {
            return List.of();
        }
        List<Path> children = new ArrayList<>(Math.min(maxDirectoryEntries, 64));
        try (DirectoryStream<Path> stream =
                directoryStreamOpener.open(directory.file.toPath())) {
            for (Path child : stream) {
                if (children.size() >= maxDirectoryEntries) {
                    throw new IOException("Directory entry limit exceeded for "
                            + directory.ftpPath);
                }
                children.add(child);
            }
        } catch (AccessDeniedException | SecurityException opaque) {
            // Android intentionally keeps a few existing shared-storage directories opaque.
            return List.of();
        } catch (DirectoryIteratorException exception) {
            if (exception.getCause() instanceof AccessDeniedException) {
                return List.of();
            }
            throw new IOException("Unable to list local FTP directory: "
                    + directory.ftpPath, exception.getCause());
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<LanFtpDocument> result = new ArrayList<>(children.size());
        for (Path child : children) {
            String childPath = "/".equals(directory.ftpPath)
                    ? "/" + child.getFileName()
                    : directory.ftpPath + "/" + child.getFileName();
            try {
                Resolved resolvedChild = resolve(childPath);
                if (resolvedChild.file.exists()) {
                    result.add(metadata(resolvedChild));
                }
            } catch (IOException ignored) {
                // Symlinks and names rejected by the FTP path policy are never exposed.
            }
        }
        return result;
    }

    @Override
    public boolean createDirectory(String absolutePath) throws IOException {
        requireWritable();
        Resolved target = resolve(absolutePath);
        if (target.isRoot() || target.file.exists()) {
            return false;
        }
        File parent = target.file.getParentFile();
        return parent != null && parent.isDirectory() && parent.canWrite() && target.file.mkdir();
    }

    @Override
    public boolean delete(String absolutePath) throws IOException {
        requireWritable();
        Resolved target = resolve(absolutePath);
        return !target.isRoot() && target.file.exists() && target.file.delete();
    }

    @Override
    public boolean move(String sourcePath, String destinationPath) throws IOException {
        requireWritable();
        Resolved source = resolve(sourcePath);
        Resolved destination = resolve(destinationPath);
        if (source.isRoot() || destination.isRoot() || !source.file.exists()
                || destination.file.exists()) {
            return false;
        }
        if (source.file.isDirectory()
                && isSameOrDescendant(destination.file.getPath(), source.file.getPath())) {
            return false;
        }
        File destinationParent = destination.file.getParentFile();
        return destinationParent != null
                && destinationParent.isDirectory()
                && destinationParent.canWrite()
                && source.file.renameTo(destination.file);
    }

    @Override
    public boolean setLastModified(String absolutePath, long lastModified) throws IOException {
        requireWritable();
        Resolved target = resolve(absolutePath);
        return target.file.exists() && target.file.setLastModified(lastModified);
    }

    @Override
    public InputStream openInputStream(String absolutePath, long offset) throws IOException {
        requireValidOffset(offset);
        Resolved target = resolve(absolutePath);
        if (!target.file.isFile() || !target.file.canRead()) {
            throw new IOException("Local FTP path is not a readable file: " + target.ftpPath);
        }
        long length = target.file.length();
        if (offset > length) {
            throw new IOException("FTP restart offset exceeds file size: " + target.ftpPath);
        }
        FileInputStream input = new FileInputStream(target.file);
        try {
            input.getChannel().position(offset);
            return input;
        } catch (IOException exception) {
            try {
                input.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public OutputStream openOutputStream(String absolutePath, long offset) throws IOException {
        requireWritable();
        requireValidOffset(offset);
        Resolved target = resolve(absolutePath);
        if (target.isRoot() || target.file.isDirectory()) {
            throw new IOException("Local FTP path is not a writable file: " + target.ftpPath);
        }
        File parent = target.file.getParentFile();
        if (parent == null || !parent.isDirectory() || !parent.canWrite()) {
            throw new IOException("Local FTP parent is not writable: " + target.ftpPath);
        }
        if (offset == 0L) {
            return new FileOutputStream(target.file, false);
        }
        if (!target.file.isFile() || !target.file.canWrite()) {
            throw new IOException("Cannot resume a missing local FTP file: " + target.ftpPath);
        }
        long length = target.file.length();
        if (offset > length) {
            throw new IOException("FTP restart offset exceeds file size: " + target.ftpPath);
        }
        RandomAccessFile output = new RandomAccessFile(target.file, "rw");
        try {
            output.setLength(offset);
            output.seek(offset);
            return new RandomAccessOutputStream(output);
        } catch (IOException exception) {
            try {
                output.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    @Override
    public boolean supportsRandomAccess() {
        return true;
    }

    private Resolved resolve(String absolutePath) throws IOException {
        String ftpPath = canonicalFtpPath(absolutePath);
        File current = root;
        if (!"/".equals(ftpPath)) {
            String[] segments = ftpPath.substring(1).split("/");
            for (String segment : segments) {
                File absolute = new File(current, segment).getAbsoluteFile();
                File canonical = absolute.getCanonicalFile();
                requireContained(canonical);
                if (!samePath(absolute.getPath(), canonical.getPath())) {
                    throw new IOException("Local FTP path contains a symbolic link: " + ftpPath);
                }
                current = canonical;
            }
        }
        requireContained(current);
        return new Resolved(ftpPath, current);
    }

    private LanFtpDocument metadata(Resolved resolved) {
        boolean rootDocument = resolved.isRoot();
        File parent = resolved.file.getParentFile();
        boolean removable = !readOnly
                && !rootDocument
                && parent != null
                && parent.canWrite();
        return new LanFtpDocument(
                resolved.ftpPath,
                resolved.file.isDirectory(),
                resolved.file.isDirectory() ? 0L : resolved.file.length(),
                resolved.file.lastModified(),
                resolved.file.canRead(),
                !readOnly && resolved.file.canWrite(),
                removable);
    }

    private String canonicalFtpPath(String absolutePath) throws IOException {
        if (absolutePath == null || !absolutePath.startsWith("/")) {
            throw new IOException("Local FTP path must be absolute");
        }
        try {
            return LanFtpPath.resolve("/", absolutePath);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Local FTP path escapes the export root", exception);
        }
    }

    private void requireContained(File candidate) throws IOException {
        String candidatePath = candidate.getPath();
        if (!samePath(candidatePath, rootPath)
                && !startsWithPath(candidatePath, rootPrefix)) {
            throw new IOException("Local FTP path escapes the export root");
        }
    }

    private void requireWritable() throws IOException {
        if (readOnly) {
            throw new IOException("FTP export is read-only");
        }
    }

    private static void requireValidOffset(long offset) throws IOException {
        if (offset < 0L) {
            throw new IOException("FTP transfer offset cannot be negative");
        }
    }

    private static boolean startsWithPath(String value, String prefix) {
        return File.separatorChar == '\\'
                ? value.regionMatches(true, 0, prefix, 0, prefix.length())
                : value.startsWith(prefix);
    }

    private static boolean samePath(String first, String second) {
        return File.separatorChar == '\\'
                ? first.equalsIgnoreCase(second)
                : first.equals(second);
    }

    private static boolean isSameOrDescendant(String candidate, String directory) {
        String prefix = directory.endsWith(File.separator)
                ? directory
                : directory + File.separator;
        return samePath(candidate, directory) || startsWithPath(candidate, prefix);
    }

    private static final class Resolved {
        private final String ftpPath;
        private final File file;

        private Resolved(String ftpPath, File file) {
            this.ftpPath = ftpPath;
            this.file = file;
        }

        private boolean isRoot() {
            return "/".equals(ftpPath);
        }
    }

    @FunctionalInterface
    interface DirectoryStreamOpener {
        DirectoryStream<Path> open(Path directory) throws IOException;
    }

    private static final class RandomAccessOutputStream extends OutputStream {
        private final RandomAccessFile output;

        private RandomAccessOutputStream(RandomAccessFile output) {
            this.output = output;
        }

        @Override
        public void write(int value) throws IOException {
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            output.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            output.close();
        }
    }
}
