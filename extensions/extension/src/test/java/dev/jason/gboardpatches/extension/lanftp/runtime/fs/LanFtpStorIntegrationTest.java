package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpEngine;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpActivityObserver;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

public final class LanFtpStorIntegrationTest {
    private static final Pattern PASV = Pattern.compile(
            ".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");

    @Test
    public void storRejectsRestartOffsetsWithoutOpeningADataTransfer() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        LanFtpEngine engine = new LanFtpEngine(
                new LanFtpServerConfig(
                        controlPort, passivePort, passivePort, 4, 300,
                        false, "gboard", "abcdef", "content://tree"),
                "127.0.0.1",
                new LanFtpFileSystemFactory(
                        new LanFtpStagingDocumentStore(
                                new InMemoryLanFtpDocumentStore()),
                        "gboard",
                        false));
        try {
            engine.start();
            try (Client client = Client.connect(controlPort)) {
                client.expect("USER gboard", "331");
                client.expect("PASS abcdef", "230");
                client.expect("REST 1", "350");
                client.expect("STOR /restart.bin", "550");
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void unexpectedWorkerThrowableReleasesStagingWriterForRetry() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        InMemoryLanFtpDocumentStore raw = new InMemoryLanFtpDocumentStore();
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(raw);
        LanFtpServerConfig config = new LanFtpServerConfig(
                controlPort,
                passivePort,
                passivePort,
                4,
                300,
                false,
                "gboard",
                "abcdef",
                "content://tree");
        LanFtpEngine engine = new LanFtpEngine(
                config,
                "127.0.0.1",
                new LanFtpFileSystemFactory(store, "gboard", false));

        try {
            engine.start();
            try (Client client = Client.connect(controlPort)) {
                client.expect("USER gboard", "331");
                client.expect("PASS abcdef", "230");
                raw.failNextOutputWriteWithError();
                client.expectUploadTaskFailure(
                        "/unexpected-error.bin", "broken".getBytes(StandardCharsets.UTF_8));
                client.upload(
                        "/unexpected-error.bin", "recovered".getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals("recovered", read(store, "/unexpected-error.bin"));
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void storPromotesBefore226RejectsUploadResumeAndDiscardsAborts() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        InMemoryLanFtpDocumentStore raw = new InMemoryLanFtpDocumentStore();
        LanFtpStagingDocumentStore store = new LanFtpStagingDocumentStore(raw);
        AtomicInteger currentTransfers = new AtomicInteger();
        AtomicInteger maximumTransfers = new AtomicInteger();
        AtomicInteger sessionActivityChanges = new AtomicInteger();
        AtomicLong maximumInFlightBytes = new AtomicLong();
        AtomicLong maximumInFlightSpeed = new AtomicLong();
        AtomicLong activeUploadTotal = new AtomicLong(Long.MIN_VALUE);
        AtomicLong activeDownloadTotal = new AtomicLong(Long.MIN_VALUE);
        AtomicReference<LanFtpEngine> engineReference = new AtomicReference<>();
        LanFtpServerConfig config = new LanFtpServerConfig(
                controlPort,
                passivePort,
                passivePort,
                4,
                300,
                false,
                "gboard",
                "abcdef",
                "content://tree");
        LanFtpEngine engine = new LanFtpEngine(
                config,
                "127.0.0.1",
                new LanFtpFileSystemFactory(store, "gboard", false),
                new LanFtpActivityObserver() {
                    @Override public void onSessionCountChanged(int count) { }
                    @Override public void onTransferCountChanged(int count) {
                        currentTransfers.set(count);
                        maximumTransfers.accumulateAndGet(count, Math::max);
                    }
                    @Override public void onSessionActivityChanged() {
                        sessionActivityChanges.incrementAndGet();
                        LanFtpEngine current = engineReference.get();
                        if (current != null && !current.sessionInfos().isEmpty()) {
                            LanFtpSessionInfo session = current.sessionInfos().get(0);
                            maximumInFlightBytes.accumulateAndGet(
                                    session.bytesTransferred(), Math::max);
                            maximumInFlightSpeed.accumulateAndGet(
                                    session.bytesPerSecond(), Math::max);
                            if ("upload".equals(session.direction())) {
                                activeUploadTotal.set(session.totalBytes());
                            } else if ("download".equals(session.direction())) {
                                activeDownloadTotal.set(session.totalBytes());
                            }
                        }
                    }
                });
        engineReference.set(engine);

        try {
            engine.start();
            try (Client client = Client.connect(controlPort)) {
                client.expect("USER gboard", "331");
                client.expect("PASS abcdef", "230");
                String features = client.command("FEAT");
                Assert.assertTrue(features, features.contains("REST STREAM"));
                Assert.assertFalse(features, features.contains("MODE Z"));
                Assert.assertFalse(features, features.contains("AUTH TLS"));
                client.expect("MODE Z", "504");
                client.expect("APPE /unsupported.bin", "502");
                byte[] defaultBinary = new byte[]{'A', '\r', '\n', 'B', '\n', 0, 'C'};
                client.upload("/default-type.bin", defaultBinary);
                Assert.assertArrayEquals(defaultBinary, readBytes(store, "/default-type.bin"));
                Assert.assertArrayEquals(defaultBinary, client.download("/default-type.bin"));
                client.upload("/large.bin", "complete".getBytes(StandardCharsets.UTF_8));
                Assert.assertEquals("complete", read(store, "/large.bin"));
                Assert.assertEquals(1, maximumTransfers.get());
                awaitZero(currentTransfers);
                Assert.assertEquals(0, currentTransfers.get());
                Assert.assertEquals("IDLE", engine.sessionInfos().get(0).command());
                Assert.assertEquals("idle", engine.sessionInfos().get(0).direction());
                Assert.assertEquals(8L, engine.sessionInfos().get(0).bytesTransferred());

                client.expect("REST 3", "350");
                client.expect("STOR /large.bin", "550");
                Assert.assertEquals("complete", read(store, "/large.bin"));
                Assert.assertArrayEquals("lete".getBytes(StandardCharsets.UTF_8),
                        client.download("/large.bin", 4L));
                client.expectDownloadRangeFailure("/large.bin", 9L);

                byte[] progressPayload = new byte[2 * 1024 * 1024];
                client.uploadSlowly("/progress.bin", progressPayload, 256 * 1024, 200L);
                Assert.assertTrue("FTP upload reached storage in undersized writes: "
                                + raw.largestOutputWriteLength(),
                        raw.largestOutputWriteLength() >= 256 * 1024);
                Assert.assertTrue("active upload bytes were never published",
                        maximumInFlightBytes.get() >= 1024L * 1024L);
                Assert.assertTrue("active upload speed was never published",
                        maximumInFlightSpeed.get() > 0L);
                Assert.assertEquals(-1L, activeUploadTotal.get());
                maximumInFlightBytes.set(0L);
                maximumInFlightSpeed.set(0L);
                raw.setInputReadDelayMillis(150L);
                try {
                    Assert.assertArrayEquals(progressPayload, client.download("/progress.bin"));
                } finally {
                    raw.setInputReadDelayMillis(0L);
                }
                Assert.assertTrue("FTP download read storage in undersized chunks: "
                                + raw.largestInputReadLength(),
                        raw.largestInputReadLength() >= 256 * 1024);
                Assert.assertTrue("active download bytes were never published",
                        maximumInFlightBytes.get() >= 1024L * 1024L);
                Assert.assertTrue("active download speed was never published",
                        maximumInFlightSpeed.get() > 0L);
                Assert.assertEquals(progressPayload.length, activeDownloadTotal.get());

                client.abortActiveUpload("/aborted.bin", new byte[256 * 1024]);
                awaitZero(currentTransfers);
                Assert.assertEquals(0, currentTransfers.get());
                assertDiscarded(store, raw, "/aborted.bin");

                client.abortAfterClosingUploadData(
                        "/winscp-cancel.bin", new byte[256 * 1024]);
                awaitZero(currentTransfers);
                Assert.assertEquals(0, currentTransfers.get());
                assertDiscarded(store, raw, "/winscp-cancel.bin");

                byte[] lineSensitive = new byte[]{'A', '\r', '\n', 'B', '\n', 0, 'C'};
                client.expect("TYPE A", "200");
                client.upload("/type-a.bin", lineSensitive);
                Assert.assertArrayEquals(lineSensitive, readBytes(store, "/type-a.bin"));
                Assert.assertArrayEquals(lineSensitive, client.download("/type-a.bin"));
                client.expect("QUIT", "221");
            }
        } finally {
            engine.stop();
        }
    }

    private static String read(LanFtpDocumentStore store, String path) throws Exception {
        try (java.io.InputStream input = store.openInputStream(path, 0L)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void awaitZero(AtomicInteger value) throws InterruptedException {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (value.get() != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }

    private static byte[] readBytes(LanFtpDocumentStore store, String path) throws Exception {
        try (java.io.InputStream input = store.openInputStream(path, 0L)) {
            return input.readAllBytes();
        }
    }

    private static void assertDiscarded(LanFtpDocumentStore store,
            LanFtpDocumentStore raw, String path)
            throws Exception {
        Assert.assertNull(store.stat(path));
        Assert.assertTrue(raw.list("/").stream()
                .noneMatch(item -> item.absolutePath().contains(".gboardftp-")));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static final class Client implements AutoCloseable {
        private final Socket control;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        private Client(Socket control) throws IOException {
            this.control = control;
            reader = new BufferedReader(new InputStreamReader(
                    control.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                    control.getOutputStream(), StandardCharsets.UTF_8));
            Assert.assertTrue(readReply().startsWith("220"));
        }

        static Client connect(int port) throws IOException {
            Socket socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            return new Client(socket);
        }

        void expect(String command, String code) throws IOException {
            String reply = command(command);
            Assert.assertTrue(reply, reply.startsWith(code));
        }

        void upload(String path, byte[] bytes) throws IOException {
            upload(path, bytes, 0L);
        }

        void upload(String path, byte[] bytes, long offset) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket data = new Socket("127.0.0.1", port)) {
                if (offset > 0L) {
                    expect("REST " + offset, "350");
                }
                writeCommand("STOR " + path);
                String preliminary = readReply();
                Assert.assertTrue(preliminary, preliminary.startsWith("150"));
                data.getOutputStream().write(bytes);
                data.shutdownOutput();
            }
            String completion = readReply();
            Assert.assertTrue(completion, completion.startsWith("226"));
        }

        void uploadSlowly(String path, byte[] bytes, int chunkBytes, long delayMillis)
                throws IOException, InterruptedException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket data = new Socket("127.0.0.1", port)) {
                writeCommand("STOR " + path);
                String preliminary = readReply();
                Assert.assertTrue(preliminary, preliminary.startsWith("150"));
                OutputStream output = data.getOutputStream();
                for (int offset = 0; offset < bytes.length; offset += chunkBytes) {
                    int length = Math.min(chunkBytes, bytes.length - offset);
                    output.write(bytes, offset, length);
                    output.flush();
                    Thread.sleep(delayMillis);
                }
                data.shutdownOutput();
            }
            String completion = readReply();
            Assert.assertTrue(completion, completion.startsWith("226"));
        }

        void expectUploadFailure(String path, long offset, String code) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            expect("REST " + offset, "350");
            try (Socket data = new Socket("127.0.0.1", port)) {
                writeCommand("STOR " + path);
                String preliminary = readReply();
                Assert.assertTrue(preliminary, preliminary.startsWith("150"));
            }
            String completion = readReply();
            Assert.assertTrue(completion, completion.startsWith(code));
        }

        void expectUploadTaskFailure(String path, byte[] bytes) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket data = new Socket("127.0.0.1", port)) {
                writeCommand("STOR " + path);
                String preliminary = readReply();
                Assert.assertTrue(preliminary, preliminary.startsWith("150"));
                try {
                    data.getOutputStream().write(bytes);
                    data.shutdownOutput();
                } catch (java.net.SocketException expected) {
                    // The contained worker failure may close/reset the data connection first.
                }
            }
            String completion = readReply();
            Assert.assertTrue(completion, completion.startsWith("451"));
        }

        byte[] download(String path) throws IOException {
            return download(path, 0L);
        }

        byte[] download(String path, long offset) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            byte[] bytes;
            try (Socket data = new Socket("127.0.0.1", port)) {
                if (offset > 0L) {
                    expect("REST " + offset, "350");
                }
                writeCommand("RETR " + path);
                String preliminary = readReply();
                Assert.assertTrue(preliminary, preliminary.startsWith("150"));
                bytes = data.getInputStream().readAllBytes();
            }
            String completion = readReply();
            Assert.assertTrue(completion, completion.startsWith("226"));
            return bytes;
        }

        void expectDownloadRangeFailure(String path, long offset) throws IOException {
            expect("PASV", "227");
            expect("REST " + offset, "350");
            expect("RETR " + path, "551");
        }

        void expectIncompleteDownloadRejected(String path) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                writeCommand("RETR " + path);
                String reply = readReply();
                Assert.assertTrue(reply, reply.startsWith("550"));
            }
        }

        void abortActiveUpload(String path, byte[] partial) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket data = new Socket("127.0.0.1", port)) {
                writeCommand("STOR " + path);
                Assert.assertTrue(readReply().startsWith("150"));
                data.getOutputStream().write(partial);
                data.getOutputStream().flush();
                writeCommand("ABOR");
                data.setSoTimeout(5_000);
                try {
                    Assert.assertEquals("ABOR must close the server data socket",
                            -1, data.getInputStream().read());
                } catch (java.net.SocketException expected) {
                    // A reset is also an authoritative server-side close.
                }
            }
            String first = readReply();
            String second = readReply();
            String replies = first + "\n" + second;
            Assert.assertTrue(replies, replies.contains("426"));
            Assert.assertTrue(replies, replies.contains("226"));
        }

        void abortAfterClosingUploadData(String path, byte[] partial) throws IOException {
            String passiveReply = command("PASV");
            Matcher matcher = PASV.matcher(passiveReply);
            Assert.assertTrue(passiveReply, matcher.matches());
            int port = Integer.parseInt(matcher.group(5)) * 256
                    + Integer.parseInt(matcher.group(6));
            try (Socket data = new Socket("127.0.0.1", port)) {
                writeCommand("STOR " + path);
                Assert.assertTrue(readReply().startsWith("150"));
                data.getOutputStream().write(partial);
                data.shutdownOutput();
            }
            writeCommand("ABOR");
            String first = readReply();
            String second = readReply();
            String replies = first + "\n" + second;
            Assert.assertTrue(replies, replies.contains("426"));
            Assert.assertTrue(replies, replies.contains("226"));
        }

        String command(String command) throws IOException {
            writeCommand(command);
            return readReply();
        }

        private void writeCommand(String command) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
        }

        private String readReply() throws IOException {
            String first = reader.readLine();
            if (first == null || first.length() < 4 || first.charAt(3) != '-') {
                return first == null ? "" : first;
            }
            String code = first.substring(0, 3);
            StringBuilder reply = new StringBuilder(first);
            String line;
            do {
                line = reader.readLine();
                if (line != null) {
                    reply.append('\n').append(line);
                }
            } while (line != null && !line.startsWith(code + " "));
            return reply.toString();
        }

        @Override
        public void close() throws IOException {
            control.close();
        }
    }
}
