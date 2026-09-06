package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.LongConsumer;

import org.apache.ftpserver.impl.FtpIoSession;

/** Fixed-buffer streaming progress bridge from data commands to session management state. */
final class LanFtpTransferProgress {
    static final String ATTRIBUTE_CALLBACK = "lanftp.progress.callback";
    private static final int TRANSFER_BUFFER_BYTES = 256 * 1024;
    private static final long PROGRESS_INTERVAL_BYTES = TRANSFER_BUFFER_BYTES;

    private LanFtpTransferProgress() {
    }

    static OutputStream output(FtpIoSession session, OutputStream delegate) {
        return new BufferedOutputStream(delegate, TRANSFER_BUFFER_BYTES) {
            private final ProgressCounter progress = new ProgressCounter(session);

            @Override public void write(int value) throws IOException {
                super.write(value);
                progress.add(1L);
            }

            @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                super.write(bytes, offset, length);
                progress.add(length);
            }

            @Override public void flush() throws IOException {
                super.flush();
                progress.finish();
            }
        };
    }

    static InputStream input(FtpIoSession session, InputStream delegate) {
        return new BufferedInputStream(delegate, TRANSFER_BUFFER_BYTES) {
            private final ProgressCounter progress = new ProgressCounter(session);

            @Override public int read() throws IOException {
                int value = super.read();
                if (value >= 0) {
                    progress.add(1L);
                } else {
                    progress.finish();
                }
                return value;
            }

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count = super.read(bytes, offset, length);
                if (count > 0) {
                    progress.add(count);
                } else if (count < 0) {
                    progress.finish();
                }
                return count;
            }
        };
    }

    private static final class ProgressCounter {
        private final FtpIoSession session;
        private long transferred;
        private long lastPublished;

        private ProgressCounter(FtpIoSession session) {
            this.session = session;
        }

        private void add(long count) {
            transferred += count;
            if (transferred - lastPublished >= PROGRESS_INTERVAL_BYTES) {
                publish(session, transferred);
                lastPublished = transferred;
            }
        }

        private void finish() {
            if (transferred != lastPublished) {
                publish(session, transferred);
                lastPublished = transferred;
            }
        }
    }

    private static void publish(FtpIoSession session, long bytes) {
        session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES, Math.max(0L, bytes));
        Object callback = session.getAttribute(ATTRIBUTE_CALLBACK);
        if (callback instanceof LongConsumer consumer) {
            consumer.accept(bytes);
        }
    }
}
