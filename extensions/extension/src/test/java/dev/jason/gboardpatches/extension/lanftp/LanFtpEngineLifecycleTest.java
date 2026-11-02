package dev.jason.gboardpatches.extension.lanftp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpEngine;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;

public final class LanFtpEngineLifecycleTest {
    @Test
    public void stopClosesActiveControlSessionAndTerminatesOwnedSessionExecutor()
            throws Exception {
        int controlPort;
        int passivePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            controlPort = socket.getLocalPort();
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            passivePort = socket.getLocalPort();
        }
        LanFtpEngine engine = new LanFtpEngine(
                new LanFtpServerConfig(
                        controlPort, passivePort, passivePort, 4, 300, true,
                        "gboard", "abcdef", "content://tree"),
                "127.0.0.1",
                user -> null);
        Field executorField = LanFtpEngine.class.getDeclaredField("sessionExecutor");
        executorField.setAccessible(true);
        engine.start();
        try (Socket client = new Socket("127.0.0.1", controlPort)) {
            client.setSoTimeout(2_000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            Assert.assertTrue(reader.readLine().startsWith("220"));
            ThreadPoolExecutor sessionExecutor =
                    (ThreadPoolExecutor) executorField.get(engine);
            Assert.assertNotNull(sessionExecutor);

            engine.stop();

            Assert.assertTrue(sessionExecutor.isShutdown());
            Assert.assertTrue(sessionExecutor.awaitTermination(1L, TimeUnit.SECONDS));
            Assert.assertTrue(sessionExecutor.isTerminated());
            try {
                Assert.assertEquals(-1, client.getInputStream().read());
            } catch (java.net.SocketException expectedClosedSocket) {
                // A reset is also a valid fail-closed shutdown signal.
            }
        } finally {
            engine.stop();
        }
    }

    @Test
    public void concurrentStartStopAndRepeatedStopReleaseControlPort() throws Exception {
        int controlPort;
        int passivePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            controlPort = socket.getLocalPort();
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            passivePort = socket.getLocalPort();
        }
        LanFtpEngine engine = new LanFtpEngine(
                new LanFtpServerConfig(
                        controlPort, passivePort, passivePort, 4, 300, true,
                        "gboard", "abcdef", "content://tree"),
                "127.0.0.1",
                user -> null);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> operations = new ArrayList<>();
            for (int index = 0; index < 40; index++) {
                operations.add(() -> { engine.start(); return null; });
                operations.add(() -> { engine.stop(); return null; });
            }
            List<Future<Void>> results = workers.invokeAll(operations);
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            workers.shutdownNow();
            engine.stop();
            engine.stop();
        }
        Assert.assertTrue(engine.isStopped());
        try (ServerSocket rebound = new ServerSocket(controlPort)) {
            Assert.assertEquals(controlPort, rebound.getLocalPort());
        }
    }

    @Test
    public void quiesceImmediatelyClosesListenerAndRejectsLateStartForThatGeneration()
            throws Exception {
        int controlPort;
        int passivePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            controlPort = socket.getLocalPort();
        }
        try (ServerSocket socket = new ServerSocket(0)) {
            passivePort = socket.getLocalPort();
        }
        LanFtpEngine engine = new LanFtpEngine(
                new LanFtpServerConfig(
                        controlPort, passivePort, passivePort, 4, 300, true,
                        "gboard", "abcdef", "content://tree"),
                "127.0.0.1",
                user -> null);
        engine.start();
        try (Socket client = new Socket("127.0.0.1", controlPort)) {
            client.setSoTimeout(2_000);
            Assert.assertTrue(new BufferedReader(new InputStreamReader(
                    client.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))
                    .readLine().startsWith("220"));

            engine.quiesce();

            try {
                Assert.assertEquals(-1, client.getInputStream().read());
            } catch (java.net.SocketException expectedClosedSocket) {
                // A reset is also a valid immediate quiesce signal.
            }
            try (ServerSocket rebound = new ServerSocket(controlPort)) {
                Assert.assertEquals(controlPort, rebound.getLocalPort());
            }
            try {
                engine.start();
                Assert.fail("a quiesced engine generation must not reopen its listener");
            } catch (org.apache.ftpserver.ftplet.FtpException expected) {
                Assert.assertTrue(expected.getMessage().contains("quiesced"));
            }
        } finally {
            engine.stop();
        }
    }
}
