package dev.jason.gboardpatches.extension.webclipboard;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class ClipboardSyncLoopbackIngressClient {
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final String STATUS_PATH = "/status";
    private static final String INGRESS_PATH = "/phone-clipboard";
    private static final String LOOPBACK_INGRESS_TOKEN_HEADER = "X-Loopback-Ingress-Token";
    private static final int DEFAULT_TIMEOUT_MS = 750;
    private static final int LOOPBACK_CHALLENGE_BYTES = 16;
    private static final SecureRandom LOOPBACK_CHALLENGE_RANDOM = new SecureRandom();

    private ClipboardSyncLoopbackIngressClient() {
    }

    public static boolean submitPhoneClipboard(String text, int port, String loopbackIngressToken) {
        return submitPhoneClipboard(text, port, loopbackIngressToken, DEFAULT_TIMEOUT_MS);
    }

    public static boolean isExpectedPortal(int port, String loopbackIngressToken) {
        return isExpectedPortal(port, loopbackIngressToken, DEFAULT_TIMEOUT_MS);
    }

    public static boolean isExpectedPortal(int port, String loopbackIngressToken, int timeoutMs) {
        String safeToken = loopbackIngressToken == null ? "" : loopbackIngressToken;
        if (safeToken.isEmpty()
                || safeToken.indexOf('\r') >= 0
                || safeToken.indexOf('\n') >= 0) {
            return false;
        }
        return verifyExpectedPortal(
                WebClipboardPreferences.sanitizePort(port),
                safeToken,
                Math.max(100, timeoutMs));
    }

    static boolean submitPhoneClipboard(String text, int port, String loopbackIngressToken,
            int timeoutMs) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String safeToken = loopbackIngressToken == null ? "" : loopbackIngressToken;
        if (safeToken.isEmpty()
                || safeToken.indexOf('\r') >= 0
                || safeToken.indexOf('\n') >= 0) {
            return false;
        }
        int safePort = WebClipboardPreferences.sanitizePort(port);
        int safeTimeoutMs = Math.max(100, timeoutMs);
        try {
            if (!isExpectedPortal(safePort, safeToken, safeTimeoutMs)) {
                return false;
            }
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(LOOPBACK_HOST, safePort), safeTimeoutMs);
                socket.setSoTimeout(safeTimeoutMs);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                writer.write("POST " + INGRESS_PATH + " HTTP/1.1\r\n");
                writer.write("Host: " + LOOPBACK_HOST + ":" + safePort + "\r\n");
                writer.write("Content-Type: application/json; charset=utf-8\r\n");
                if (!safeToken.isEmpty()) {
                    writer.write(LOOPBACK_INGRESS_TOKEN_HEADER + ": " + safeToken + "\r\n");
                }
                writer.write("Content-Length: " + body.length + "\r\n");
                writer.write("Connection: close\r\n");
                writer.write("\r\n");
                writer.flush();
                socket.getOutputStream().write(body);
                socket.getOutputStream().flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.UTF_8));
                String statusLine = reader.readLine();
                return statusLine != null && statusLine.contains(" 200 ");
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean verifyExpectedPortal(int port, String loopbackIngressToken,
            int timeoutMs) {
        String challenge = nextChallenge();
        String path = STATUS_PATH + "?" + ClipboardSyncLoopbackAuth.CHALLENGE_QUERY
                + "=" + challenge;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(LOOPBACK_HOST, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write("GET " + path + " HTTP/1.1\r\n");
            writer.write("Host: " + LOOPBACK_HOST + ":" + port + "\r\n");
            writer.write("Accept: application/json\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            HttpResponse response = readHttpResponse(reader);
            if (response.statusCode != 200) {
                return false;
            }
            JSONObject payload = new JSONObject(response.body);
            return ClipboardSyncLoopbackAuth.proofMatches(
                    loopbackIngressToken,
                    challenge,
                    payload.optString(ClipboardSyncLoopbackAuth.PROOF_FIELD, ""));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static HttpResponse readHttpResponse(BufferedReader reader) throws Exception {
        String statusLine = reader.readLine();
        int statusCode = parseStatusCode(statusLine);
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // Drain response headers.
        }
        StringBuilder body = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            body.append(line).append('\n');
        }
        return new HttpResponse(statusCode, body.toString());
    }

    private static int parseStatusCode(String statusLine) {
        if (statusLine == null) {
            return 0;
        }
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String nextChallenge() {
        byte[] bytes = new byte[LOOPBACK_CHALLENGE_BYTES];
        LOOPBACK_CHALLENGE_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0xF, 16));
            builder.append(Character.forDigit(current & 0xF, 16));
        }
        return builder.toString();
    }

    private static final class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }
}

