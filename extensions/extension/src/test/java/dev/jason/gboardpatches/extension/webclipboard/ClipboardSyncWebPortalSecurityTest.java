package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public final class ClipboardSyncWebPortalSecurityTest {
    private static final String LOOPBACK_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String LEGACY_STATIC_TOKEN =
            "gboardpatches-web-clipboard-loopback-fallback-v1";

    @Test
    public void onlyConfiguredTokenHeaderCanPublishPhoneClipboard() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(false, "0000", LOOPBACK_TOKEN));
        portal.start();
        try {
            HttpResponse legacyResponse = request(portal.getPort(),
                    phoneClipboardRequest("legacy-forged", LEGACY_STATIC_TOKEN, ""));
            Assert.assertEquals(403, legacyResponse.statusCode);
            Assert.assertTrue(legacyResponse.body.contains("invalid_loopback_token"));

            String bodyCredential = "text=body-forged"
                    + "&loopbackIngressToken=" + LOOPBACK_TOKEN
                    + "&token=" + LOOPBACK_TOKEN;
            HttpResponse bodyCredentialResponse = request(portal.getPort(),
                    phoneClipboardRequest("", "", bodyCredential));
            Assert.assertEquals(403, bodyCredentialResponse.statusCode);
            Assert.assertTrue(bodyCredentialResponse.body.contains("invalid_loopback_token"));
            Assert.assertEquals("", latestPhoneClipboardText(portal));

            HttpResponse validResponse = request(portal.getPort(),
                    phoneClipboardRequest("trusted-phone", LOOPBACK_TOKEN, ""));
            Assert.assertEquals(200, validResponse.statusCode);
            Assert.assertEquals("trusted-phone", latestPhoneClipboardText(portal));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void statusProofUsesOnlyConfiguredPerInstallToken() throws Exception {
        String source = new String(
                Files.readAllBytes(Path.of(
                        "src/main/java/dev/jason/gboardpatches/extension/webclipboard/"
                                + "ClipboardSyncWebPortal.java")),
                StandardCharsets.UTF_8);
        String statusBody = source.substring(
                source.indexOf("private String buildStatusJson"),
                source.indexOf("private String buildAckJson"));
        String ingressAuthBody = source.substring(
                source.indexOf("private boolean isLoopbackIngressAuthorized"),
                source.indexOf("private String readHttpLine"));

        Assert.assertTrue(statusBody.contains("loopbackProofForStatusRequest"));
        Assert.assertFalse(statusBody.contains("fallbackLoopbackProofForStatusRequest"));
        Assert.assertFalse(statusBody.contains("loopbackFallbackProof"));
        Assert.assertTrue(ingressAuthBody.contains(
                "ClipboardSyncLoopbackAuth.tokenMatches(expectedToken, suppliedToken)"));
        Assert.assertFalse(ingressAuthBody.contains("extractField("));
    }

    @Test
    public void ingressClientRejectsLegacyStaticToken() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            Assert.assertFalse(ClipboardSyncLoopbackIngressClient.isExpectedPortal(
                    portal.getPort(), LEGACY_STATIC_TOKEN, 2_000));
            Assert.assertFalse(ClipboardSyncLoopbackIngressClient.submitPhoneClipboard(
                    "legacy-client-forged", portal.getPort(), LEGACY_STATIC_TOKEN, 2_000));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void clearEndpointResetsStateAndRequiresPairingWhenEnabled() throws Exception {
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                text -> { },
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            portal.publishPhoneClipboard("stale-phone-clip");
            HttpResponse unauthorized = request(portal.getPort(),
                    "POST /clear HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            Assert.assertEquals(401, unauthorized.statusCode);
            Assert.assertEquals("stale-phone-clip", latestPhoneClipboardText(portal));

            HttpResponse authorized = request(portal.getPort(),
                    "POST /clear?code=1234 HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
            Assert.assertEquals(200, authorized.statusCode);
            Assert.assertEquals("", latestPhoneClipboardText(portal));

            HttpResponse stateResponse = request(portal.getPort(),
                    "GET /state?code=1234 HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n");
            Assert.assertEquals(200, stateResponse.statusCode);
            Assert.assertTrue(stateResponse.body.contains("\"text\":\"\""));
            Assert.assertTrue(stateResponse.body.contains("\"updatedAtMs\":0"));
        } finally {
            portal.stop();
        }
    }

    @Test
    public void imageEndpointsEnforceAuthAndRejectMalformedUploads() throws Exception {
        byte[] samplePng = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
                0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00, 0x06,
                0x08, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        AtomicReference<byte[]> appliedBytes = new AtomicReference<>();
        ClipboardSyncWebPortal.ClipboardBridge bridge = new ClipboardSyncWebPortal.ClipboardBridge() {
            @Override
            public void applyDesktopClipboard(String text) {
            }

            @Override
            public void applyDesktopClipboardImage(byte[] imageBytes, String mimeType, String fileName) {
                appliedBytes.set(imageBytes);
            }
        };
        ClipboardSyncWebPortal portal = new ClipboardSyncWebPortal(
                0,
                0,
                bridge,
                ClipboardSyncWebPortal.WebAssets.empty(),
                new ClipboardSyncWebPortal.SecurityConfig(true, "1234", LOOPBACK_TOKEN));
        portal.start();
        try {
            HttpResponse overflow = request(portal.getPort(),
                    "POST /clipboard-image?code=1234 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Length: 999999999999\r\n"
                            + "Connection: close\r\n\r\n");
            Assert.assertEquals(413, overflow.statusCode);

            HttpResponse incomplete = request(portal.getPort(),
                    "POST /clipboard-image?code=1234 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Length: 32\r\n"
                            + "Connection: close\r\n\r\n"
                            + "short");
            Assert.assertEquals(400, incomplete.statusCode);
            Assert.assertTrue(incomplete.body.contains("incomplete_image"));

            HttpResponse unauth = requestBytes(portal.getPort(),
                    "POST /clipboard-image HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Length: " + samplePng.length + "\r\n"
                            + "Connection: close\r\n\r\n",
                    samplePng);
            Assert.assertEquals(401, unauth.statusCode);

            HttpResponse uploaded = requestBytes(portal.getPort(),
                    "POST /clipboard-image?code=1234&messageId=m-1 HTTP/1.1\r\n"
                            + "Host: 127.0.0.1\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Length: " + samplePng.length + "\r\n"
                            + "Connection: close\r\n\r\n",
                    samplePng);
            Assert.assertEquals(200, uploaded.statusCode);
            Assert.assertArrayEquals(samplePng, appliedBytes.get());
            Assert.assertTrue("Unexpected body: " + uploaded.body,
                    uploaded.body.contains("\"kind\":\"image\""));

            Assert.assertFalse(ClipboardSyncLoopbackIngressClient.submitPhoneClipboardImage(
                    samplePng, "image/png", "shot.png", portal.getPort(), LEGACY_STATIC_TOKEN, 2_000));
            Assert.assertTrue(ClipboardSyncLoopbackIngressClient.submitPhoneClipboardImage(
                    samplePng, "image/png", "shot.png", portal.getPort(), LOOPBACK_TOKEN, 2_000));
        } finally {
            portal.stop();
        }
    }

    private static String phoneClipboardRequest(String text, String token, String rawBody) {
        String body = rawBody == null || rawBody.isEmpty()
                ? "text=" + text
                : rawBody;
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder request = new StringBuilder()
                .append("POST /phone-clipboard HTTP/1.1\r\n")
                .append("Host: 127.0.0.1\r\n")
                .append("Content-Type: application/x-www-form-urlencoded; charset=utf-8\r\n");
        if (token != null && !token.isEmpty()) {
            request.append("X-Loopback-Ingress-Token: ").append(token).append("\r\n");
        }
        return request
                .append("Content-Length: ").append(bodyBytes.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("\r\n")
                .append(body)
                .toString();
    }

    private static HttpResponse request(int port, String rawRequest) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write(rawRequest);
            writer.flush();
            socket.shutdownOutput();

            String statusLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Drain response headers.
            }
            StringBuilder body = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
            return new HttpResponse(parseStatusCode(statusLine), body.toString());
        }
    }

    private static HttpResponse requestBytes(int port, String rawHeaders, byte[] bodyBytes)
            throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream output = socket.getOutputStream();
            output.write(rawHeaders.getBytes(StandardCharsets.UTF_8));
            if (bodyBytes != null && bodyBytes.length > 0) {
                output.write(bodyBytes);
            }
            output.flush();
            socket.shutdownOutput();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Drain response headers.
            }
            StringBuilder body = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
            return new HttpResponse(parseStatusCode(statusLine), body.toString());
        }
    }

    private static int parseStatusCode(String statusLine) {
        if (statusLine == null) {
            return 0;
        }
        String[] parts = statusLine.split(" ");
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    private static String latestPhoneClipboardText(ClipboardSyncWebPortal portal)
            throws Exception {
        Field field = ClipboardSyncWebPortal.class.getDeclaredField("latestPhoneClipboardText");
        field.setAccessible(true);
        return (String) field.get(portal);
    }

    private static final class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}
