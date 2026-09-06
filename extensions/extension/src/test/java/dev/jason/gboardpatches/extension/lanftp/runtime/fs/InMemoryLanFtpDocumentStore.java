package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

final class InMemoryLanFtpDocumentStore implements LanFtpDocumentStore {
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private boolean randomAccess = true;
    private long lastInputOffset = -1L;
    private long lastOutputOffset = -1L;
    private final AtomicInteger largestInputReadLength = new AtomicInteger();
    private final AtomicInteger largestOutputWriteLength = new AtomicInteger();
    private volatile long inputReadDelayNanos;
    private volatile boolean failNextOutputWriteWithError;

    InMemoryLanFtpDocumentStore() {
        nodes.put("/", new Node(true, new byte[0], 0L, 0L));
    }

    void putFile(String path, byte[] content, long lastModified) {
        nodes.put(path, new Node(false, content.clone(), content.length, lastModified));
    }

    void putFileMetadata(String path, long size, long lastModified) {
        nodes.put(path, new Node(false, new byte[0], size, lastModified));
    }

    void putDirectory(String path, long lastModified) {
        nodes.put(path, new Node(true, new byte[0], 0L, lastModified));
    }

    void setRandomAccess(boolean randomAccess) {
        this.randomAccess = randomAccess;
    }

    long lastInputOffset() {
        return lastInputOffset;
    }

    long lastOutputOffset() {
        return lastOutputOffset;
    }

    int largestInputReadLength() {
        return largestInputReadLength.get();
    }

    int largestOutputWriteLength() {
        return largestOutputWriteLength.get();
    }

    void setInputReadDelayMillis(long delayMillis) {
        inputReadDelayNanos = Math.max(0L, delayMillis) * 1_000_000L;
    }

    void failNextOutputWriteWithError() {
        failNextOutputWriteWithError = true;
    }

    @Override
    public LanFtpDocument stat(String absolutePath) {
        Node node = nodes.get(absolutePath);
        return node == null ? null : document(absolutePath, node);
    }

    @Override
    public List<LanFtpDocument> list(String absolutePath) {
        List<LanFtpDocument> result = new ArrayList<>();
        String prefix = "/".equals(absolutePath) ? "/" : absolutePath + "/";
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            String candidate = entry.getKey();
            if (!candidate.equals(absolutePath) && candidate.startsWith(prefix)
                    && candidate.indexOf('/', prefix.length()) < 0) {
                result.add(document(candidate, entry.getValue()));
            }
        }
        return result;
    }

    @Override
    public boolean createDirectory(String absolutePath) {
        if (nodes.containsKey(absolutePath) || !isWritableParent(absolutePath)) {
            return false;
        }
        nodes.put(absolutePath, new Node(true, new byte[0], 0L, 0L));
        return true;
    }

    @Override
    public boolean delete(String absolutePath) {
        if ("/".equals(absolutePath) || !nodes.containsKey(absolutePath)) {
            return false;
        }
        String prefix = absolutePath + "/";
        nodes.keySet().removeIf(path -> path.equals(absolutePath) || path.startsWith(prefix));
        return true;
    }

    @Override
    public boolean move(String sourcePath, String destinationPath) {
        if ("/".equals(sourcePath) || !nodes.containsKey(sourcePath)
                || nodes.containsKey(destinationPath) || !isWritableParent(destinationPath)) {
            return false;
        }
        Map<String, Node> moved = new LinkedHashMap<>();
        String prefix = sourcePath + "/";
        for (Map.Entry<String, Node> entry : new ArrayList<>(nodes.entrySet())) {
            if (entry.getKey().equals(sourcePath) || entry.getKey().startsWith(prefix)) {
                moved.put(destinationPath + entry.getKey().substring(sourcePath.length()),
                        entry.getValue());
                nodes.remove(entry.getKey());
            }
        }
        nodes.putAll(moved);
        return true;
    }

    @Override
    public boolean setLastModified(String absolutePath, long lastModified) {
        return false;
    }

    @Override
    public InputStream openInputStream(String absolutePath, long offset) throws IOException {
        checkOffset(offset);
        lastInputOffset = offset;
        Node node = nodes.get(absolutePath);
        if (node == null || node.directory) {
            throw new IOException("Not a file");
        }
        ByteArrayInputStream input = new ByteArrayInputStream(node.content) {
            @Override public synchronized int read(byte[] bytes, int start, int length) {
                largestInputReadLength.accumulateAndGet(length, Math::max);
                LockSupport.parkNanos(inputReadDelayNanos);
                return super.read(bytes, start, length);
            }
        };
        input.skip(Math.min(offset, node.content.length));
        return input;
    }

    @Override
    public OutputStream openOutputStream(String absolutePath, long offset) throws IOException {
        checkOffset(offset);
        lastOutputOffset = offset;
        Node existing = nodes.get(absolutePath);
        if (offset > 0L && (existing == null || existing.directory)) {
            throw new IOException("Resume target does not exist");
        }
        if (offset > Integer.MAX_VALUE) {
            if (existing != null && existing.size >= offset) {
                return OutputStream.nullOutputStream();
            }
            throw new IOException("Offset exceeds the existing test document");
        }
        byte[] initial = offset == 0L || existing == null
                ? new byte[0]
                : java.util.Arrays.copyOf(existing.content, (int) offset);
        return new OutputStream() {
            private byte[] content = initial;
            private int position = (int) offset;
            private boolean closed;

            @Override
            public void write(int value) throws IOException {
                ensureOpen();
                failOutputWriteIfRequested();
                ensureCapacity(position + 1);
                content[position++] = (byte) value;
            }

            @Override
            public void write(byte[] bytes, int start, int length) throws IOException {
                ensureOpen();
                failOutputWriteIfRequested();
                largestOutputWriteLength.accumulateAndGet(length, Math::max);
                ensureCapacity(position + length);
                System.arraycopy(bytes, start, content, position, length);
                position += length;
            }

            @Override
            public void close() {
                if (!closed) {
                    byte[] completed = java.util.Arrays.copyOf(content, position);
                    nodes.put(absolutePath,
                            new Node(false, completed, completed.length, 0L));
                    closed = true;
                }
            }

            private void ensureCapacity(int required) {
                if (required > content.length) {
                    int grown = Math.max(required, Math.max(16, content.length * 2));
                    content = java.util.Arrays.copyOf(content, grown);
                }
            }

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream is closed");
                }
            }

            private void failOutputWriteIfRequested() {
                if (failNextOutputWriteWithError) {
                    failNextOutputWriteWithError = false;
                    throw new AssertionError("Injected test storage failure");
                }
            }
        };
    }

    @Override
    public boolean supportsRandomAccess() {
        return randomAccess;
    }

    private static LanFtpDocument document(String path, Node node) {
        return new LanFtpDocument(
                path,
                node.directory,
                node.directory ? 0L : node.size,
                node.lastModified,
                true,
                true,
                !"/".equals(path));
    }

    private boolean isWritableParent(String absolutePath) {
        int separator = absolutePath.lastIndexOf('/');
        String parent = separator <= 0 ? "/" : absolutePath.substring(0, separator);
        Node parentNode = nodes.get(parent);
        return parentNode != null && parentNode.directory;
    }

    private void checkOffset(long offset) throws IOException {
        if (offset < 0L) {
            throw new IOException("Negative transfer offset");
        }
        if (offset > 0L && !randomAccess) {
            throw new IOException("Document provider does not support seek");
        }
    }

    private static final class Node {
        private final boolean directory;
        private final byte[] content;
        private final long size;
        private final long lastModified;

        private Node(boolean directory, byte[] content, long size, long lastModified) {
            this.directory = directory;
            this.content = content;
            this.size = size;
            this.lastModified = lastModified;
        }
    }
}
