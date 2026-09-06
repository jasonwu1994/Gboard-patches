package dev.jason.gboardpatches.extension.lanftp;

import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.User;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpEngine;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;

public final class LanFtpEngineIntegrationTest {
    private static final Pattern PASV = Pattern.compile(
            ".*\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\).*");
    private static final Pattern EPSV = Pattern.compile(
            ".*\\(\\|\\|\\|(\\d+)\\|\\).*");
    @Test
    public void passiveOnlyServerAuthenticatesAndAcceptsLongRestartOffsets() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        LanFtpServerConfig config = new LanFtpServerConfig(
                controlPort,
                passivePort,
                passivePort,
                4,
                300,
                true,
                "gboard",
                "abcdef",
                "content://tree");
        LanFtpEngine engine = new LanFtpEngine(
                config, "127.0.0.1", user -> new RootOnlyFileSystemView());

        try {
            engine.start();
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.greeting().startsWith("220"));
                Assert.assertTrue(client.command("USER gboard").startsWith("331"));
                Assert.assertTrue(client.command(
                        "PASS abcdef").startsWith("230"));
                Assert.assertTrue(client.command("REST 4294967296").startsWith("350"));
                Assert.assertTrue(client.command("REST -1").startsWith("501"));
                Assert.assertTrue(client.command(
                        "REST 999999999999999999999").startsWith("501"));
                Assert.assertTrue(client.command("PORT 127,0,0,1,200,1").startsWith("502"));
                Assert.assertTrue(client.command("EPRT |1|127.0.0.1|51201|")
                        .startsWith("502"));
                Assert.assertTrue(client.command("PASV").startsWith("227"));
                Assert.assertTrue(client.command("EPSV").startsWith("229"));
                Assert.assertEquals(1, engine.connectedClientCount());
                Assert.assertTrue(client.command("QUIT").startsWith("221"));
            }
        } finally {
            engine.stop();
        }

        Assert.assertTrue(engine.isStopped());
        try (ServerSocket rebound = new ServerSocket(controlPort)) {
            Assert.assertEquals(controlPort, rebound.getLocalPort());
        }
    }

    @Test
    public void ipv6ListenerUsesExtendedPassiveDataConnection() throws Exception {
        InetAddress loopback = InetAddress.getByName("::1");
        int controlPort = freePort(loopback);
        int passivePort;
        do {
            passivePort = freePort(loopback);
        } while (passivePort == controlPort);
        LanFtpEngine engine = new LanFtpEngine(
                config(controlPort, passivePort),
                loopback.getHostAddress(),
                user -> new RootOnlyFileSystemView());

        try {
            engine.start();
            try (FtpClient client = FtpClient.connect(loopback, controlPort)) {
                Assert.assertTrue(client.command("USER gboard").startsWith("331"));
                Assert.assertTrue(client.command("PASS abcdef").startsWith("230"));
                Assert.assertTrue(client.command("PASV").startsWith("522"));
                String passiveReply = client.command("EPSV");
                Matcher matcher = EPSV.matcher(passiveReply);
                Assert.assertTrue(passiveReply, matcher.matches());
                int dataPort = Integer.parseInt(matcher.group(1));
                try (Socket data = new Socket()) {
                    data.connect(new InetSocketAddress(loopback, dataPort));
                    String result = client.commandWithPossiblePreliminary("LIST");
                    data.getInputStream().readAllBytes();
                    Assert.assertTrue(result, result.matches("(?s).*226.*"));
                }
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void anonymousProtocolLoginRequiresTheExplicitConfigFlag() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        LanFtpServerConfig config = new LanFtpServerConfig(
                controlPort,
                passivePort,
                passivePort,
                4,
                300,
                true,
                true,
                "gboard",
                "abcdef",
                "file:///sdcard");
        LanFtpEngine engine = new LanFtpEngine(
                config, "127.0.0.1", user -> new RootOnlyFileSystemView());

        try {
            engine.start();
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.command("USER anonymous").startsWith("331"));
                Assert.assertTrue(client.command("PASS ").startsWith("230"));
            }
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.command("USER anonymous").startsWith("331"));
                Assert.assertTrue(client.command("PASS guest@example.invalid").startsWith("230"));
            }
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.command("USER gboard").startsWith("331"));
                Assert.assertTrue(client.command("PASS wrong-password").startsWith("530"));
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void passiveDataConnectionRejectsDifferentPeerAddress() throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        LanFtpEngine engine = new LanFtpEngine(
                config(controlPort, passivePort),
                "127.0.0.1",
                user -> new RootOnlyFileSystemView());
        try {
            engine.start();
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.command("USER gboard").startsWith("331"));
                Assert.assertTrue(client.command(
                        "PASS abcdef").startsWith("230"));
                String passiveReply = client.command("PASV");
                Matcher matcher = PASV.matcher(passiveReply);
                Assert.assertTrue(passiveReply, matcher.matches());
                int dataPort = Integer.parseInt(matcher.group(5)) * 256
                        + Integer.parseInt(matcher.group(6));
                try (Socket data = new Socket()) {
                    data.bind(new InetSocketAddress("127.0.0.2", 0));
                    data.connect(new InetSocketAddress("127.0.0.1", dataPort));
                    String result = client.commandWithPossiblePreliminary("LIST");
                    Assert.assertTrue(result,
                            result.matches("(?s).*(425|426|450|550|551).*"));
                }
                passiveReply = client.command("PASV");
                matcher = PASV.matcher(passiveReply);
                Assert.assertTrue(passiveReply, matcher.matches());
                dataPort = Integer.parseInt(matcher.group(5)) * 256
                        + Integer.parseInt(matcher.group(6));
                try (Socket data = new Socket("127.0.0.1", dataPort)) {
                    String result = client.commandWithPossiblePreliminary("LIST");
                    data.getInputStream().readAllBytes();
                    Assert.assertTrue(result, result.matches("(?s).*226.*"));
                }
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void commandParserContainsMalformedSessionsAndAcceptsFragmentedCommands()
            throws Exception {
        int controlPort = freePort();
        int passivePort = freePort();
        LanFtpEngine engine = new LanFtpEngine(
                config(controlPort, passivePort),
                "127.0.0.1",
                user -> new RootOnlyFileSystemView());
        try {
            engine.start();
            try (FtpClient client = FtpClient.connect(controlPort)) {
                Assert.assertTrue(client.fragmentedCommand("US", "ER gbo", "ard\r", "\n")
                        .startsWith("331"));
                Assert.assertTrue(client.fragmentedCommand(
                        "PASS abc", "def", "\r\n")
                        .startsWith("230"));
                Assert.assertTrue(client.command("ABOR").matches("(?s)22[56].*"));
                Assert.assertTrue(client.command("REST +1").startsWith("350"));
                for (String invalidRest : List.of("REST", "REST 0x10", "REST 1 2")) {
                    Assert.assertTrue(invalidRest,
                            client.command(invalidRest).startsWith("501"));
                }
            }

            sendMalformedLine(controlPort,
                    new byte[]{'N', 'O', 'O', 'P', ' ', (byte) 0xc3, 0x28, '\r', '\n'});
            sendMalformedLine(controlPort,
                    ("NOOP " + "x".repeat(16_384) + "\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            Random fuzz = new Random(0x484558465450L);
            for (int caseIndex = 0; caseIndex < 24; caseIndex++) {
                byte[] command = new byte[1 + fuzz.nextInt(512) + 2];
                for (int index = 0; index < command.length - 2; index++) {
                    int value = fuzz.nextInt(256);
                    command[index] = (byte) (value == '\r' || value == '\n' ? 'X' : value);
                }
                command[command.length - 2] = '\r';
                command[command.length - 1] = '\n';
                sendMalformedLine(controlPort, command);
            }

            try (FtpClient healthy = FtpClient.connect(controlPort)) {
                Assert.assertTrue(healthy.command("LIST").startsWith("530"));
                Assert.assertTrue(healthy.command("RETR /x").startsWith("530"));
                Assert.assertTrue(healthy.command("USER gboard").startsWith("331"));
                Assert.assertTrue(healthy.command(
                        "PASS abcdef").startsWith("230"));
                Assert.assertTrue(healthy.command("NOOP").startsWith("200"));
            }
        } finally {
            engine.stop();
        }
    }

    private static LanFtpServerConfig config(int controlPort, int passivePort) {
        return new LanFtpServerConfig(
                controlPort,
                passivePort,
                passivePort,
                4,
                300,
                true,
                "gboard",
                "abcdef",
                "content://tree");
    }

    private static void sendMalformedLine(int port, byte[] line) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(250);
            readLine(socket.getInputStream());
            socket.getOutputStream().write(line);
            socket.getOutputStream().flush();
            try {
                readLine(socket.getInputStream());
            } catch (SocketTimeoutException ignored) {
                // A malformed session may be closed silently; engine survival is asserted next.
            }
        }
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = input.read()) >= 0 && value != '\n') {
            if (value != '\r') {
                line.append((char) value);
            }
        }
        return line.toString();
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int freePort(InetAddress address) throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(address, 0));
            return socket.getLocalPort();
        }
    }

    private static final class FtpClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private final String greeting;

        private FtpClient(Socket socket) throws IOException {
            this.socket = socket;
            reader = new BufferedReader(new java.io.InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            greeting = readReply();
        }

        static FtpClient connect(int port) throws IOException {
            return connect(InetAddress.getByName("127.0.0.1"), port);
        }

        static FtpClient connect(InetAddress address, int port) throws IOException {
            Socket socket = new Socket(address, port);
            socket.setSoTimeout(5_000);
            return new FtpClient(socket);
        }

        String greeting() {
            return greeting;
        }

        String command(String command) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            return readReply();
        }

        String fragmentedCommand(String... fragments) throws IOException {
            for (String fragment : fragments) {
                writer.write(fragment);
                writer.flush();
            }
            return readReply();
        }

        String commandWithPossiblePreliminary(String command) throws IOException {
            writer.write(command);
            writer.write("\r\n");
            writer.flush();
            String first = readReply();
            return first.startsWith("1") ? first + "\n" + readReply() : first;
        }

        private String readReply() throws IOException {
            String first = reader.readLine();
            if (first == null || first.length() < 4 || first.charAt(3) != '-') {
                return first == null ? "" : first;
            }
            String code = first.substring(0, 3);
            String line;
            do {
                line = reader.readLine();
            } while (line != null && !line.startsWith(code + " "));
            return first + "\n" + (line == null ? "" : line);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class RootOnlyFileSystemView implements FileSystemView {
        private final FtpFile root = new RootFile();

        @Override public FtpFile getHomeDirectory() { return root; }
        @Override public FtpFile getWorkingDirectory() { return root; }
        @Override public boolean changeWorkingDirectory(String dir) { return "/".equals(dir); }
        @Override public FtpFile getFile(String file) { return root; }
        @Override public boolean isRandomAccessible() { return true; }
        @Override public void dispose() { }
    }

    private static final class RootFile implements FtpFile {
        @Override public String getAbsolutePath() { return "/"; }
        @Override public String getName() { return ""; }
        @Override public boolean isHidden() { return false; }
        @Override public boolean isDirectory() { return true; }
        @Override public boolean isFile() { return false; }
        @Override public boolean doesExist() { return true; }
        @Override public boolean isReadable() { return true; }
        @Override public boolean isWritable() { return false; }
        @Override public boolean isRemovable() { return false; }
        @Override public String getOwnerName() { return "ftpuser"; }
        @Override public String getGroupName() { return "ftp"; }
        @Override public int getLinkCount() { return 1; }
        @Override public long getLastModified() { return 0; }
        @Override public boolean setLastModified(long time) { return false; }
        @Override public long getSize() { return 0; }
        @Override public Object getPhysicalFile() { return null; }
        @Override public boolean mkdir() { return false; }
        @Override public boolean delete() { return false; }
        @Override public boolean move(FtpFile destination) { return false; }
        @Override public List<? extends FtpFile> listFiles() { return Collections.emptyList(); }
        @Override public OutputStream createOutputStream(long offset) throws IOException {
            throw new IOException("read-only");
        }
        @Override public InputStream createInputStream(long offset) throws IOException {
            throw new IOException("directory");
        }
    }
}
