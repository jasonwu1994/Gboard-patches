package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpLargeStreamingTest {
    private static final long ONE_GIB = 1_073_741_824L;

    @Test
    public void simulatedOneGiBDownloadUsesFixedSizeBufferAndLongOffsets() throws Exception {
        AtomicInteger largestReadRequest = new AtomicInteger();
        LanFtpFile file = new LanFtpFile(
                new SimulatedLargeStore(ONE_GIB, largestReadRequest),
                "/large.bin",
                "ftpuser",
                true);

        byte[] buffer = new byte[64 * 1024];
        long transferred = 0L;
        try (InputStream input = file.createInputStream(0L)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                transferred += count;
            }
        }

        Assert.assertEquals(ONE_GIB, transferred);
        Assert.assertTrue(largestReadRequest.get() <= buffer.length);
        try (InputStream eof = file.createInputStream(ONE_GIB)) {
            Assert.assertEquals(-1, eof.read(buffer));
        }
        assertOffsetRejected(file, ONE_GIB + 1L);
    }

    private static void assertOffsetRejected(LanFtpFile file, long offset) throws Exception {
        try {
            file.createInputStream(offset);
            Assert.fail("Expected offset beyond EOF to be rejected");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("offset"));
        }
    }

    private static final class SimulatedLargeStore implements LanFtpDocumentStore {
        private final long size;
        private final AtomicInteger largestReadRequest;

        private SimulatedLargeStore(long size, AtomicInteger largestReadRequest) {
            this.size = size;
            this.largestReadRequest = largestReadRequest;
        }

        @Override public LanFtpDocument stat(String path) {
            if ("/".equals(path)) {
                return new LanFtpDocument("/", true, 0L, 0L, true, false, false);
            }
            if ("/large.bin".equals(path)) {
                return new LanFtpDocument(path, false, size, 0L, true, false, false);
            }
            return null;
        }

        @Override public List<LanFtpDocument> list(String path) {
            return Collections.emptyList();
        }

        @Override public InputStream openInputStream(String path, long offset)
                throws IOException {
            if (offset < 0L || offset > size) {
                throw new IOException("FTP restart offset exceeds file size");
            }
            return new InputStream() {
                private long remaining = size - offset;

                @Override public int read() {
                    if (remaining <= 0L) {
                        return -1;
                    }
                    remaining--;
                    return 0;
                }

                @Override public int read(byte[] bytes, int start, int length) {
                    if (remaining <= 0L) {
                        return -1;
                    }
                    largestReadRequest.accumulateAndGet(length, Math::max);
                    int count = (int) Math.min(remaining, length);
                    remaining -= count;
                    return count;
                }
            };
        }

        @Override public boolean createDirectory(String path) { return false; }
        @Override public boolean delete(String path) { return false; }
        @Override public boolean move(String source, String destination) { return false; }
        @Override public boolean setLastModified(String path, long value) { return false; }
        @Override public OutputStream openOutputStream(String path, long offset)
                throws IOException { throw new IOException("read-only"); }
        @Override public boolean supportsRandomAccess() { return true; }
    }
}
