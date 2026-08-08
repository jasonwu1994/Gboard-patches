package dev.jason.gboardpatches.extension.webclipboard;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public final class ClipboardSyncWebPortal {
    public static final int DEFAULT_PORT = 8080;
    private static final String TAG = "GboardWebClipboard";
    private static final String LOG_PREFIX = "[gboard-clipboard-sync]";
    private static final long DUPLICATE_SUPPRESSION_WINDOW_MS = 5_000L;
    private static final long WEB_ECHO_SUPPRESSION_WINDOW_MS = 2 * 60_000L;
    private static final long CLIENT_HEARTBEAT_INTERVAL_MS = 1_000L;
    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final AtomicLong NEXT_PORTAL_INSTANCE_SEQUENCE = new AtomicLong(1L);

    private final int requestedPort;
    private final int requestedHttpsPort;
    private final ClipboardBridge bridge;
    private final WebAssets webAssets;
    private final SecurityConfig securityConfig;
    private final String portalInstanceId =
            "portal-" + System.currentTimeMillis()
                    + "-" + NEXT_PORTAL_INSTANCE_SEQUENCE.getAndIncrement();
    private final Object clientsLock = new Object();
    private final List<EventClient> eventClients = new ArrayList<>();
    private final Set<String> revokedClientIds = new LinkedHashSet<>();
    private final WebClipboardEchoSuppressor webEchoSuppressor =
            new WebClipboardEchoSuppressor(WEB_ECHO_SUPPRESSION_WINDOW_MS);

    private ServerSocket serverSocket;
    private ServerSocket httpsServerSocket;
    private Thread acceptThread;
    private Thread httpsAcceptThread;
    private Thread clientHeartbeatThread;
    private volatile boolean stopped = true;
    private volatile String latestPhoneClipboardText = "";
    private volatile long latestPhoneClipboardAtMs;
    private volatile String latestWebClipboardText = "";
    private volatile long latestWebClipboardAtMs;
    private long nextEventClientId = 1L;

    ClipboardSyncWebPortal(int port, ClipboardBridge bridge) {
        this(port, bridge, WebAssets.empty());
    }

    ClipboardSyncWebPortal(int port, ClipboardBridge bridge, WebAssets webAssets) {
        this(port, ClipboardSyncHttpsSocketFactory.defaultHttpsPortFor(port), bridge, webAssets);
    }

    ClipboardSyncWebPortal(int port, ClipboardBridge bridge, WebAssets webAssets,
            SecurityConfig securityConfig) {
        this(port, ClipboardSyncHttpsSocketFactory.defaultHttpsPortFor(port), bridge, webAssets,
                securityConfig);
    }

    ClipboardSyncWebPortal(int port, int httpsPort, ClipboardBridge bridge, WebAssets webAssets) {
        this(port, httpsPort, bridge, webAssets, SecurityConfig.disabled());
    }

    ClipboardSyncWebPortal(int port, int httpsPort, ClipboardBridge bridge, WebAssets webAssets,
            SecurityConfig securityConfig) {
        requestedPort = port;
        requestedHttpsPort = httpsPort;
        this.bridge = bridge;
        this.webAssets = webAssets == null ? WebAssets.empty() : webAssets;
        this.securityConfig = securityConfig == null ? SecurityConfig.disabled() : securityConfig;
    }

    interface ClipboardBridge {
        void applyDesktopClipboard(String text);
    }

    interface WebAssets {
        byte[] read(String path) throws IOException;

        static WebAssets from(Context context) {
            Context appContext = context == null || context.getApplicationContext() == null
                    ? context
                    : context.getApplicationContext();
            if (appContext == null) {
                return empty();
            }
            AssetManager assetManager = appContext.getAssets();
            return path -> {
                if (path == null || path.isBlank()) {
                    return null;
                }
                String assetPath = "web-clipboard/" + path;
                try (InputStream input = assetManager.open(assetPath)) {
                    return readAllBytes(input);
                }
            };
        }

        static WebAssets empty() {
            return path -> null;
        }
    }

    public static final class SecurityConfig {
        private final boolean pairingRequired;
        private final String pairingCode;
        private final String loopbackIngressToken;

        public SecurityConfig(boolean pairingRequired, String pairingCode) {
            this(pairingRequired, pairingCode,
                    WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN);
        }

        public SecurityConfig(boolean pairingRequired, String pairingCode,
                String loopbackIngressToken) {
            this.pairingRequired = pairingRequired;
            this.pairingCode = WebClipboardPreferences.sanitizePairingCode(pairingCode);
            this.loopbackIngressToken = loopbackIngressToken == null
                    ? WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN
                    : loopbackIngressToken;
        }

        static SecurityConfig disabled() {
            return new SecurityConfig(false, WebClipboardPreferences.DEFAULT_PAIRING_CODE,
                    WebClipboardPreferences.DEFAULT_LOOPBACK_INGRESS_TOKEN);
        }
    }

    public static final class ConnectedClientSnapshot {
        public final String id;
        public final String address;
        public final String label;
        public final String browser;
        public final String agent;
        public final long connectedAtMs;

        ConnectedClientSnapshot(String id, String address, String label, String browser,
                String agent, long connectedAtMs) {
            this.id = id;
            this.address = address;
            this.label = label;
            this.browser = browser;
            this.agent = agent;
            this.connectedAtMs = connectedAtMs;
        }
    }

    void start() throws Exception {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        synchronized (clientsLock) {
            revokedClientIds.clear();
        }
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(requestedPort));
        stopped = false;
        acceptThread = startAcceptThread(serverSocket, "ClipboardSyncWebPortalHttp");
        try {
            httpsServerSocket = ClipboardSyncHttpsSocketFactory.createServerSocket(
                    requestedHttpsPort);
            httpsAcceptThread = startAcceptThread(
                    httpsServerSocket,
                    "ClipboardSyncWebPortalHttps");
        } catch (Throwable throwable) {
            logInfo(LOG_PREFIX + " portal HTTPS unavailable: "
                    + throwable.getClass().getSimpleName());
            closeQuietly(httpsServerSocket);
            httpsServerSocket = null;
            httpsAcceptThread = null;
        }
        logInfo(LOG_PREFIX + " portal sockets ready"
                + " instance=" + portalInstanceId
                + ", httpPort=" + getPort()
                + ", httpsPort=" + getHttpsPort());
    }

    void stop() {
        logInfo(LOG_PREFIX + " portal stopping"
                + " instance=" + portalInstanceId
                + ", clients=" + eventClientCount());
        stopped = true;
        closeQuietly(serverSocket);
        closeQuietly(httpsServerSocket);
        Thread thread = acceptThread;
        if (thread != null) {
            thread.interrupt();
        }
        Thread heartbeatThread = clientHeartbeatThread;
        clientHeartbeatThread = null;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        Thread httpsThread = httpsAcceptThread;
        if (httpsThread != null) {
            httpsThread.interrupt();
        }
        List<EventClient> clientsToClose;
        synchronized (clientsLock) {
            clientsToClose = new ArrayList<>(eventClients);
            eventClients.clear();
        }
        for (EventClient client : clientsToClose) {
            client.close();
        }
    }

    int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? 0 : socket.getLocalPort();
    }

    int getHttpsPort() {
        ServerSocket socket = httpsServerSocket;
        return socket == null ? 0 : socket.getLocalPort();
    }

    public List<ConnectedClientSnapshot> getConnectedClients() {
        synchronized (clientsLock) {
            return Collections.unmodifiableList(buildClientSnapshotsLocked());
        }
    }

    String getPortalInstanceId() {
        return portalInstanceId;
    }

    public boolean kickClient(String id) {
        String clientId = sanitizeClientId(id);
        if (clientId.isBlank()) {
            return false;
        }
        List<EventClient> kickedClients = new ArrayList<>();
        synchronized (clientsLock) {
            Iterator<EventClient> iterator = eventClients.iterator();
            while (iterator.hasNext()) {
                EventClient client = iterator.next();
                if (!clientId.equals(client.id)) {
                    continue;
                }
                iterator.remove();
                kickedClients.add(client);
            }
            if (!kickedClients.isEmpty()) {
                revokedClientIds.add(clientId);
            }
        }
        if (!kickedClients.isEmpty()) {
            stopClientHeartbeatThreadIfIdle();
        }
        for (EventClient client : kickedClients) {
            writeClientFrame(client,
                    buildSseFrame("kicked", "{\"ok\":true,\"reason\":\"manual\"}"),
                    "kicked");
            client.close();
        }
        if (!kickedClients.isEmpty()) {
            broadcastClientsEvent();
        }
        return !kickedClients.isEmpty();
    }

    void publishPhoneClipboard(String text) {
        if (!hasClipboardText(text)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (webEchoSuppressor.shouldSuppressClipboardEvent(text, nowMs)) {
            logInfo(LOG_PREFIX + " portal publish suppressed web echo"
                    + " len=" + text.length());
            return;
        }
        if (text.equals(latestPhoneClipboardText)
                && nowMs - latestPhoneClipboardAtMs <= DUPLICATE_SUPPRESSION_WINDOW_MS) {
            logInfo(LOG_PREFIX + " portal publish suppressed duplicate"
                    + " len=" + text.length());
            return;
        }
        latestPhoneClipboardText = text;
        latestPhoneClipboardAtMs = nowMs;
        broadcastClipboardEvent(text, latestPhoneClipboardAtMs, "phone", "Phone", null);
    }

    private long publishWebClipboard(String text, String senderLabel, String clientId) {
        if (!hasClipboardText(text)) {
            return 0L;
        }
        latestWebClipboardText = text;
        latestWebClipboardAtMs = System.currentTimeMillis();
        webEchoSuppressor.markWebApplied(text, latestWebClipboardAtMs);
        broadcastClipboardEvent(text, latestWebClipboardAtMs, "web", senderLabel, clientId);
        return latestWebClipboardAtMs;
    }

    private void broadcastClipboardEvent(String text, long updatedAtMs, String source,
            String senderLabel, String clientId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text);
            payload.put("updatedAtMs", updatedAtMs);
            payload.put("source", source);
            if (senderLabel != null && !senderLabel.isBlank()) {
                payload.put("senderLabel", senderLabel);
            }
            if (clientId != null && !clientId.isBlank()) {
                payload.put("clientId", clientId);
            }
        } catch (Throwable ignored) {
            return;
        }
        String frame = "event: clipboard\r\n"
                + "data: " + payload.toString() + "\r\n\r\n";
        List<EventClient> clients = snapshotEventClients();
        logInfo(LOG_PREFIX + " portal published clipboard"
                + " instance=" + portalInstanceId
                + " source=" + source
                + " len=" + text.length()
                + ", clients=" + clients.size());
        List<EventClient> failedClients = writeFrameToClients(clients, frame, "clipboard");
        removeFailedEventClients(failedClients);
    }

    private void broadcastClientsEvent() {
        ClientsEventSnapshot snapshot = snapshotClientsEvent();
        List<EventClient> failedClients = writeFrameToClients(
                snapshot.clients,
                snapshot.frame,
                "clients");
        removeFailedEventClients(failedClients);
    }

    private JSONObject buildClientsPayloadLocked() {
        JSONObject payload = new JSONObject();
        JSONArray clients = new JSONArray();
        try {
            for (ConnectedClientSnapshot client : buildClientSnapshotsLocked()) {
                JSONObject item = new JSONObject();
                item.put("id", client.id);
                item.put("address", client.address);
                item.put("label", client.label);
                if (client.browser != null && !client.browser.isBlank()) {
                    item.put("browser", client.browser);
                }
                if (client.agent != null && !client.agent.isBlank()) {
                    item.put("agent", client.agent);
                }
                item.put("connectedAtMs", client.connectedAtMs);
                clients.put(item);
            }
            payload.put("clients", clients);
        } catch (Throwable ignored) {
            JSONObject emptyPayload = new JSONObject();
            try {
                emptyPayload.put("clients", new JSONArray());
            } catch (Throwable ignoredAgain) {
                // Keep the empty object fallback.
            }
            return emptyPayload;
        }
        return payload;
    }

    private List<ConnectedClientSnapshot> buildClientSnapshotsLocked() {
        List<ConnectedClientSnapshot> snapshots = new ArrayList<>();
        Map<String, Integer> addressCounts = new HashMap<>();
        Map<String, Integer> emittedAddressCounts = new HashMap<>();
        for (EventClient client : eventClients) {
            addressCounts.put(client.address, addressCounts.getOrDefault(client.address, 0) + 1);
        }
        for (EventClient client : eventClients) {
            int index = emittedAddressCounts.getOrDefault(client.address, 0) + 1;
            emittedAddressCounts.put(client.address, index);
            String label = addressCounts.getOrDefault(client.address, 0) > 1
                    ? client.address + " #" + index
                    : client.address;
            snapshots.add(new ConnectedClientSnapshot(
                    client.id,
                    client.address,
                    label,
                    client.browser,
                    client.agent,
                    client.connectedAtMs));
        }
        return snapshots;
    }

    private List<EventClient> snapshotEventClients() {
        synchronized (clientsLock) {
            return new ArrayList<>(eventClients);
        }
    }

    private ClientsEventSnapshot snapshotClientsEvent() {
        synchronized (clientsLock) {
            return new ClientsEventSnapshot(
                    buildSseFrame("clients", buildClientsPayloadLocked().toString()),
                    new ArrayList<>(eventClients));
        }
    }

    private List<EventClient> writeFrameToClients(List<EventClient> clients, String frame,
            String frameType) {
        if (clients == null || clients.isEmpty()) {
            return Collections.emptyList();
        }
        List<EventClient> failedClients = new ArrayList<>();
        for (EventClient client : clients) {
            if (!writeClientFrame(client, frame, frameType)) {
                failedClients.add(client);
            }
        }
        return failedClients;
    }

    private void removeFailedEventClients(List<EventClient> failedClients) {
        if (failedClients == null || failedClients.isEmpty()) {
            return;
        }
        List<EventClient> removedClients = new ArrayList<>();
        synchronized (clientsLock) {
            for (EventClient client : failedClients) {
                if (eventClients.remove(client)) {
                    removedClients.add(client);
                }
            }
        }
        stopClientHeartbeatThreadIfIdle();
        for (EventClient client : removedClients) {
            client.close();
        }
    }

    private String buildSseFrame(String event, String data) {
        return "event: " + event + "\r\n"
                + "data: " + data + "\r\n\r\n";
    }

    private Thread startAcceptThread(ServerSocket listenSocket, String name) {
        Thread thread = new Thread(() -> runAcceptLoop(listenSocket), name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private Thread startClientHeartbeatThread() {
        Thread thread = new Thread(this::runClientHeartbeatLoop, "ClipboardSyncWebPortalHeartbeat");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void runClientHeartbeatLoop() {
        Thread currentThread = Thread.currentThread();
        try {
            while (!stopped) {
                sleepQuietly(CLIENT_HEARTBEAT_INTERVAL_MS);
                if (stopped) {
                    return;
                }
                List<EventClient> clients = snapshotEventClients();
                if (clients.isEmpty()) {
                    stopClientHeartbeatThreadIfIdle();
                    return;
                }
                List<EventClient> failedClients = writeFrameToClients(
                        clients,
                        ": heartbeat\r\n\r\n",
                        "heartbeat");
                removeFailedEventClients(failedClients);
                if (eventClientCount() == 0) {
                    return;
                }
                if (!failedClients.isEmpty()) {
                    broadcastClientsEvent();
                }
            }
        } finally {
            clearHeartbeatThreadIfCurrent(currentThread);
        }
    }

    private void runAcceptLoop(ServerSocket listenSocket) {
        while (!stopped) {
            try {
                Socket socket = listenSocket.accept();
                Thread thread = new Thread(() -> handleClient(socket), "ClipboardSyncWebPortalClient");
                thread.setDaemon(true);
                thread.start();
            } catch (Throwable ignored) {
                if (!stopped) {
                    sleepQuietly(100L);
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(10_000);
            InputStream input = socket.getInputStream();
            String requestLine = readHttpLine(input);
            if (requestLine == null || requestLine.isBlank()) {
                closeQuietly(socket);
                return;
            }
            Request request = readRequest(requestLine, input);
            if (request.bodyTooLarge) {
                writeResponse(socket, 413, "Payload Too Large",
                        "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"payload_too_large\"}");
                return;
            }
            if (requiresActiveClientSession(request)
                    && isRevokedClientId(requestClientId(request))) {
                writeResponse(socket, 401, "Unauthorized", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"client_revoked\"}");
                return;
            }
            if ("GET".equals(request.method) && "/".equals(request.path)) {
                writeAssetResponse(socket, "index.html", "text/html; charset=utf-8",
                        INDEX_HTML.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("GET".equals(request.method) && request.path.startsWith("/assets/")) {
                writeAssetResponse(socket, request.path.substring(1),
                        contentTypeForPath(request.path), null);
                return;
            }
            if ("GET".equals(request.method) && isRootAssetRequest(request.path)) {
                writeAssetResponse(socket, request.path.substring(1),
                        contentTypeForPath(request.path), null);
                return;
            }
            if ("GET".equals(request.method) && "/status".equals(request.path)) {
                writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                        buildStatusJson(request, socket));
                return;
            }
            if (requiresPairing(request, socket) && !isAuthorized(request)) {
                writeResponse(socket, 401, "Unauthorized", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"pairing_required\"}");
                return;
            }
            if ("GET".equals(request.method) && "/events".equals(request.path)) {
                attachEventClient(socket, request.header("user-agent"), request.query("clientId"));
                return;
            }
            if ("GET".equals(request.method) && "/state".equals(request.path)) {
                writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                        buildLatestStateJson());
                return;
            }
            if ("GET".equals(request.method) && "/clients".equals(request.path)) {
                synchronized (clientsLock) {
                    writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                            buildClientsPayloadLocked().toString());
                }
                return;
            }
            if ("POST".equals(request.method) && "/clipboard".equals(request.path)) {
                handleClipboardPost(socket, request.body);
                return;
            }
            if ("POST".equals(request.method) && "/clients/kick".equals(request.path)) {
                handleKickClientPost(socket, request.body);
                return;
            }
            if ("POST".equals(request.method) && "/phone-clipboard".equals(request.path)) {
                handlePhoneClipboardIngressPost(socket, request);
                return;
            }
            writeResponse(socket, 404, "Not Found", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"not_found\"}");
        } catch (Throwable ignored) {
            closeQuietly(socket);
        }
    }

    private Request readRequest(String requestLine, InputStream input) throws Exception {
        String[] parts = requestLine.split(" ");
        Request request = new Request();
        request.method = parts.length > 0 ? parts[0].toUpperCase(Locale.US) : "";
        request.path = parts.length > 1 ? parts[1] : "/";
        request.headers = new HashMap<>();
        request.query = new HashMap<>();
        int queryIndex = request.path.indexOf('?');
        if (queryIndex >= 0) {
            request.query = parseQuery(request.path.substring(queryIndex + 1));
            request.path = request.path.substring(0, queryIndex);
        }
        int contentLength = 0;
        String line;
        while ((line = readHttpLine(input)) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!name.isBlank()) {
                request.headers.put(name.toLowerCase(Locale.US), value);
            }
            if ("content-length".equalsIgnoreCase(name)) {
                contentLength = parsePositiveInt(value);
            }
        }
        if (contentLength > 0) {
            if (contentLength > MAX_REQUEST_BODY_BYTES) {
                request.bodyTooLarge = true;
                request.body = "";
                return request;
            }
            byte[] bytes = new byte[contentLength];
            int read = 0;
            while (read < bytes.length) {
                int count = input.read(bytes, read, bytes.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            request.body = new String(bytes, 0, read, StandardCharsets.UTF_8);
        } else {
            request.body = "";
        }
        return request;
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            if (!key.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    private boolean requiresPairing(Request request, Socket socket) {
        if (!securityConfig.pairingRequired) {
            return false;
        }
        if ("POST".equals(request.method) && "/phone-clipboard".equals(request.path)
                && socket != null
                && socket.getInetAddress() != null
                && socket.getInetAddress().isLoopbackAddress()) {
            return false;
        }
        return "/events".equals(request.path)
                || "/state".equals(request.path)
                || "/status".equals(request.path)
                || "/clipboard".equals(request.path)
                || "/clients".equals(request.path)
                || "/clients/kick".equals(request.path)
                || "/phone-clipboard".equals(request.path);
    }

    private boolean isAuthorized(Request request) {
        if (!securityConfig.pairingRequired) {
            return true;
        }
        String suppliedCode = request.query("code");
        if (suppliedCode.isBlank()) {
            suppliedCode = request.query("pairingCode");
        }
        if (suppliedCode.isBlank()) {
            suppliedCode = request.header("x-pairing-code");
        }
        if (suppliedCode.isBlank()) {
            suppliedCode = extractField(request.body, "code");
        }
        if (suppliedCode.isBlank()) {
            suppliedCode = extractField(request.body, "pairingCode");
        }
        if (suppliedCode.isBlank()) {
            return false;
        }
        return securityConfig.pairingCode.equals(
                WebClipboardPreferences.sanitizePairingCode(suppliedCode));
    }

    private boolean isLoopbackIngressAuthorized(Request request) {
        String expectedToken = securityConfig.loopbackIngressToken;
        if (expectedToken == null || expectedToken.isEmpty()) {
            return false;
        }
        String suppliedToken = request == null ? "" : request.header("x-loopback-ingress-token");
        if (suppliedToken.isBlank()) {
            suppliedToken = extractField(request == null ? "" : request.body,
                    "loopbackIngressToken");
        }
        if (suppliedToken.isBlank()) {
            suppliedToken = extractField(request == null ? "" : request.body, "token");
        }
        return constantTimeEquals(expectedToken, suppliedToken);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = supplied.getBytes(StandardCharsets.UTF_8);
        int difference = expectedBytes.length ^ suppliedBytes.length;
        int maxLength = Math.max(expectedBytes.length, suppliedBytes.length);
        for (int index = 0; index < maxLength; index++) {
            byte expectedByte = index < expectedBytes.length ? expectedBytes[index] : 0;
            byte suppliedByte = index < suppliedBytes.length ? suppliedBytes[index] : 0;
            difference |= expectedByte ^ suppliedByte;
        }
        return difference == 0;
    }

    private String readHttpLine(InputStream input) throws Exception {
        StringBuilder builder = new StringBuilder();
        int current;
        boolean sawAny = false;
        while ((current = input.read()) >= 0) {
            sawAny = true;
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                builder.append((char) current);
            }
            if (builder.length() > 8192) {
                break;
            }
        }
        if (!sawAny && builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    private void handleClipboardPost(Socket socket, String body) throws Exception {
        String text = extractText(body);
        if (!hasClipboardText(text)) {
            writeResponse(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"empty_text\"}");
            return;
        }
        String clientId = sanitizeClientId(extractClientId(body));
        String messageId = sanitizeMessageId(extractField(body, "messageId"));
        try {
            bridge.applyDesktopClipboard(text);
        } catch (Throwable throwable) {
            writeResponse(socket, 500, "Internal Server Error", "application/json; charset=utf-8",
                    buildAckJson(false, messageId, clientId, "failed", 0L, "apply_failed"));
            return;
        }
        long appliedAtMs = publishWebClipboard(text, senderLabelFor(socket), clientId);
        String ackJson = buildAckJson(true, messageId, clientId, "applied", appliedAtMs, "");
        broadcastAckEvent(ackJson);
        writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                ackJson);
    }

    private void handleKickClientPost(Socket socket, String body) throws Exception {
        String clientId = extractField(body, "id");
        boolean kicked = kickClient(clientId);
        JSONObject payload = new JSONObject();
        payload.put("ok", true);
        payload.put("kicked", kicked);
        payload.put("id", sanitizeClientId(clientId));
        writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                payload.toString());
    }

    private void handlePhoneClipboardIngressPost(Socket socket, Request request) throws Exception {
        if (socket == null || socket.getInetAddress() == null
                || !socket.getInetAddress().isLoopbackAddress()) {
            writeResponse(socket, 403, "Forbidden", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"loopback_required\"}");
            return;
        }
        if (!isLoopbackIngressAuthorized(request)) {
            writeResponse(socket, 403, "Forbidden", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"invalid_loopback_token\"}");
            return;
        }
        String text = extractText(request.body);
        if (!hasClipboardText(text)) {
            writeResponse(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"empty_text\"}");
            return;
        }
        publishPhoneClipboard(text);
        writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                "{\"ok\":true}");
    }

    private String buildLatestStateJson() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("text", latestPhoneClipboardText == null ? "" : latestPhoneClipboardText);
            payload.put("updatedAtMs", Math.max(0L, latestPhoneClipboardAtMs));
        } catch (Throwable ignored) {
            return "{\"text\":\"\",\"updatedAtMs\":0}";
        }
        return payload.toString();
    }

    private boolean requiresActiveClientSession(Request request) {
        if (request == null) {
            return false;
        }
        return "/status".equals(request.path)
                || "/events".equals(request.path)
                || "/state".equals(request.path)
                || "/clipboard".equals(request.path)
                || "/clients".equals(request.path)
                || "/clients/kick".equals(request.path);
    }

    private String buildStatusJson(Request request, Socket socket) {
        JSONObject payload = new JSONObject();
        boolean authorized = isAuthorized(request);
        synchronized (clientsLock) {
            try {
                payload.put("ok", true);
                payload.put("pairingRequired", securityConfig.pairingRequired);
                payload.put("pairingVerified", authorized);
                payload.put("codeLength", 4);
                payload.put("httpPort", getPort());
                payload.put("httpsPort", getHttpsPort());
                payload.put("clients",
                        authorized || !securityConfig.pairingRequired
                                ? buildClientsPayloadLocked().optJSONArray("clients")
                                : new JSONArray());
                String loopbackProof = loopbackProofForStatusRequest(request, socket);
                if (!loopbackProof.isEmpty()) {
                    payload.put(ClipboardSyncLoopbackAuth.PROOF_FIELD, loopbackProof);
                }
            } catch (Throwable ignored) {
                return "{\"ok\":false,\"pairingRequired\":true,\"pairingVerified\":false,\"codeLength\":4,\"clients\":[]}";
            }
        }
        return payload.toString();
    }

    private String loopbackProofForStatusRequest(Request request, Socket socket) {
        if (request == null || socket == null || socket.getInetAddress() == null
                || !socket.getInetAddress().isLoopbackAddress()) {
            return "";
        }
        return ClipboardSyncLoopbackAuth.proof(
                securityConfig.loopbackIngressToken,
                request.query(ClipboardSyncLoopbackAuth.CHALLENGE_QUERY));
    }

    private String buildAckJson(boolean ok, String messageId, String clientId, String status,
            long appliedAtMs, String error) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("ok", ok);
            payload.put("status", status);
            if (messageId != null && !messageId.isBlank()) {
                payload.put("messageId", messageId);
            }
            if (clientId != null && !clientId.isBlank()) {
                payload.put("clientId", clientId);
            }
            if (appliedAtMs > 0L) {
                payload.put("appliedAtMs", appliedAtMs);
            }
            if (error != null && !error.isBlank()) {
                payload.put("error", error);
            }
        } catch (Throwable ignored) {
            return ok ? "{\"ok\":true,\"status\":\"applied\"}"
                    : "{\"ok\":false,\"status\":\"failed\"}";
        }
        return payload.toString();
    }

    private void broadcastAckEvent(String ackJson) {
        String frame = buildSseFrame("ack", ackJson);
        List<EventClient> clients = snapshotEventClients();
        List<EventClient> failedClients = writeFrameToClients(clients, frame, "ack");
        removeFailedEventClients(failedClients);
    }

    private String extractText(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(body);
            return json.optString("text", "");
        } catch (Throwable ignored) {
            return decodeFormText(body);
        }
    }

    private String extractClientId(String body) {
        return extractField(body, "clientId");
    }

    private String requestClientId(Request request) {
        if (request == null) {
            return "";
        }
        String queryClientId = sanitizeClientId(request.query("clientId"));
        if (!queryClientId.isBlank()) {
            return queryClientId;
        }
        return sanitizeClientId(extractClientId(request.body));
    }

    private String extractField(String body, String fieldName) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(body);
            return json.optString(fieldName, "");
        } catch (Throwable ignored) {
            return decodeFormField(body, fieldName);
        }
    }

    private String decodeFormText(String body) {
        return decodeFormField(body, "text");
    }

    private String decodeFormField(String body, String fieldName) {
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = pair.substring(0, separator);
            if (!fieldName.equals(key)) {
                continue;
            }
            return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return "";
    }

    private String sanitizeClientId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < normalized.length() && builder.length() < 96; index++) {
            char current = normalized.charAt(index);
            if ((current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '-'
                    || current == '_'
                    || current == '.'
                    || current == ':') {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String sanitizeMessageId(String value) {
        return sanitizeClientId(value);
    }

    private boolean isRevokedClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        synchronized (clientsLock) {
            return revokedClientIds.contains(clientId);
        }
    }

    private String senderLabelFor(Socket socket) {
        if (socket == null || socket.getInetAddress() == null) {
            return "unknown";
        }
        String hostAddress = socket.getInetAddress().getHostAddress();
        return hostAddress == null || hostAddress.isBlank() ? "unknown" : hostAddress;
    }

    private String browserLabelFor(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Browser";
        }
        String normalized = userAgent.toLowerCase(Locale.US);
        if (normalized.contains("edg/") || normalized.contains("edge/")) {
            return "Edge";
        }
        if (normalized.contains("firefox/")) {
            return "Firefox";
        }
        if (normalized.contains("opr/") || normalized.contains("opera/")) {
            return "Opera";
        }
        if (normalized.contains("samsungbrowser/")) {
            return "Samsung Internet";
        }
        if (normalized.contains("chrome/") || normalized.contains("crios/")) {
            return "Chrome";
        }
        if (normalized.contains("android") && normalized.contains("version/")
                && normalized.contains("safari/")) {
            return "Android Browser";
        }
        if (normalized.contains("safari/") && !normalized.contains("chrome/")) {
            return "Safari";
        }
        return "Browser";
    }

    private boolean isRootAssetRequest(String path) {
        return "/favicon.svg".equals(path) || "/icons.svg".equals(path);
    }

    private void writeAssetResponse(Socket socket, String assetPath, String contentType,
            byte[] fallbackBody) throws Exception {
        byte[] bytes = readAsset(assetPath);
        if (bytes == null) {
            if (fallbackBody == null) {
                writeResponse(socket, 404, "Not Found", "application/json; charset=utf-8",
                        "{\"ok\":false,\"error\":\"asset_not_found\"}");
                return;
            }
            bytes = fallbackBody;
        }
        writeResponse(socket, 200, "OK", contentType, bytes);
    }

    private byte[] readAsset(String assetPath) {
        try {
            return webAssets.read(assetPath);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void attachEventClient(Socket socket, String userAgent, String requestedClientId)
            throws Exception {
        socket.setSoTimeout(0);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        writer.write("HTTP/1.1 200 OK\r\n");
        writer.write("Content-Type: text/event-stream; charset=utf-8\r\n");
        writer.write("Cache-Control: no-cache, no-transform\r\n");
        writer.write("X-Accel-Buffering: no\r\n");
        writer.write("Connection: keep-alive\r\n");
        writer.write("\r\n");
        writer.flush();
        String clientId;
        synchronized (clientsLock) {
            clientId = sanitizeClientId(requestedClientId);
            if (clientId.isBlank()) {
                clientId = "web-" + nextEventClientId++;
            }
        }
        EventClient client = new EventClient(
                clientId,
                senderLabelFor(socket),
                browserLabelFor(userAgent),
                userAgent == null ? "" : userAgent,
                System.currentTimeMillis(),
                socket,
                writer);
        if (!writeClientFrame(client, ": connected\r\nretry: 1500\r\n\r\n", "connected")) {
            client.close();
            return;
        }
        if (!writeClientFrame(client,
                buildSseFrame("meta", buildPortalMetaJson(client).toString()),
                "meta")) {
            client.close();
            return;
        }
        List<EventClient> replacedClients;
        int clientCount;
        synchronized (clientsLock) {
            replacedClients = removeEventClientsByIdLocked(client.id);
            eventClients.add(client);
            clientCount = eventClients.size();
            ensureClientHeartbeatThreadLocked();
            logInfo(LOG_PREFIX + " portal event client attached"
                    + " instance=" + portalInstanceId
                    + ", clientId=" + client.id
                    + ", clients=" + clientCount);
        }
        for (EventClient replacedClient : replacedClients) {
            replacedClient.close();
        }
        broadcastClientsEvent();
    }

    private void ensureClientHeartbeatThreadLocked() {
        Thread existingHeartbeatThread = clientHeartbeatThread;
        if (existingHeartbeatThread != null && existingHeartbeatThread.isAlive()) {
            return;
        }
        clientHeartbeatThread = startClientHeartbeatThread();
    }

    private void stopClientHeartbeatThreadIfIdle() {
        Thread threadToInterrupt = null;
        synchronized (clientsLock) {
            if (!eventClients.isEmpty()) {
                return;
            }
            Thread existingHeartbeatThread = clientHeartbeatThread;
            if (existingHeartbeatThread == null) {
                return;
            }
            clientHeartbeatThread = null;
            if (existingHeartbeatThread != Thread.currentThread()) {
                threadToInterrupt = existingHeartbeatThread;
            }
        }
        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }
    }

    private void clearHeartbeatThreadIfCurrent(Thread currentThread) {
        synchronized (clientsLock) {
            if (clientHeartbeatThread == currentThread) {
                clientHeartbeatThread = null;
            }
        }
    }

    private List<EventClient> removeEventClientsByIdLocked(String clientId) {
        List<EventClient> removedClients = new ArrayList<>();
        if (clientId == null || clientId.isBlank()) {
            return removedClients;
        }
        Iterator<EventClient> iterator = eventClients.iterator();
        while (iterator.hasNext()) {
            EventClient existingClient = iterator.next();
            if (!clientId.equals(existingClient.id)) {
                continue;
            }
            iterator.remove();
            removedClients.add(existingClient);
        }
        return removedClients;
    }

    private JSONObject buildPortalMetaJson(EventClient client) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("portalInstanceId", portalInstanceId);
            payload.put("clientId", client.id);
            payload.put("connectedAtMs", client.connectedAtMs);
            int httpPort = getPort();
            if (httpPort > 0) {
                payload.put("httpPort", httpPort);
            }
            int httpsPort = getHttpsPort();
            if (httpsPort > 0) {
                payload.put("httpsPort", httpsPort);
            }
        } catch (Throwable ignored) {
            // Keep the partial payload when metadata fields fail.
        }
        return payload;
    }

    private boolean writeClientFrame(EventClient client, String frame, String frameType) {
        if (client.write(frame)) {
            return true;
        }
        logInfo(LOG_PREFIX + " portal client write failed"
                + " instance=" + portalInstanceId
                + ", clientId=" + client.id
                + ", frameType=" + frameType
                + ", error=" + client.lastWriteErrorClass());
        return false;
    }

    private int eventClientCount() {
        synchronized (clientsLock) {
            return eventClients.size();
        }
    }

    private void writeResponse(Socket socket, int status, String reason, String contentType,
            String body) throws Exception {
        writeResponse(socket, status, reason, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private void writeResponse(Socket socket, int status, String reason, String contentType,
            byte[] bytes) throws Exception {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                socket.getOutputStream(), StandardCharsets.UTF_8));
        writer.write("HTTP/1.1 " + status + " " + reason + "\r\n");
        writer.write("Content-Type: " + contentType + "\r\n");
        writer.write("Content-Length: " + bytes.length + "\r\n");
        writer.write("Cache-Control: no-store\r\n");
        writer.write("Pragma: no-cache\r\n");
        writer.write("Expires: 0\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        writer.flush();
        socket.getOutputStream().write(bytes);
        socket.getOutputStream().flush();
        closeQuietly(socket);
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String contentTypeForPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.US);
        if (normalized.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (normalized.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (normalized.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (normalized.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        if (normalized.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".jpg") || normalized.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean hasClipboardText(String text) {
        return text != null && !text.isEmpty();
    }

    private static void sleepQuietly(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    private static void logInfo(String message) {
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
            // android.util.Log is unavailable in local JVM unit tests.
        }
    }

    private static final class Request {
        String method;
        String path;
        String body;
        Map<String, String> headers;
        Map<String, String> query;
        boolean bodyTooLarge;

        String header(String name) {
            if (headers == null || name == null) {
                return "";
            }
            String value = headers.get(name.toLowerCase(Locale.US));
            return value == null ? "" : value;
        }

        String query(String name) {
            if (query == null || name == null) {
                return "";
            }
            String value = query.get(name);
            return value == null ? "" : value;
        }
    }

    private static final class ClientsEventSnapshot {
        final String frame;
        final List<EventClient> clients;

        ClientsEventSnapshot(String frame, List<EventClient> clients) {
            this.frame = frame;
            this.clients = clients;
        }
    }

    private static final class EventClient {
        private final String id;
        private final String address;
        private final String browser;
        private final String agent;
        private final long connectedAtMs;
        private final Socket socket;
        private final BufferedWriter writer;
        private final Object writeLock = new Object();
        private volatile String lastWriteErrorClass = "";

        EventClient(String id, String address, String browser, String agent, long connectedAtMs,
                Socket socket, BufferedWriter writer) {
            this.id = id;
            this.address = address;
            this.browser = browser;
            this.agent = agent;
            this.connectedAtMs = connectedAtMs;
            this.socket = socket;
            this.writer = writer;
        }

        boolean write(String frame) {
            synchronized (writeLock) {
                try {
                    writer.write(frame);
                    writer.flush();
                    lastWriteErrorClass = "";
                    return true;
                } catch (Throwable throwable) {
                    lastWriteErrorClass = throwable.getClass().getSimpleName();
                    return false;
                }
            }
        }

        String lastWriteErrorClass() {
            return lastWriteErrorClass.isBlank() ? "unknown" : lastWriteErrorClass;
        }

        void close() {
            synchronized (writeLock) {
                closeQuietly(writer);
                closeQuietly(socket);
            }
        }
    }

    private static final String INDEX_HTML = """
<!doctype html>
<html lang="zh-Hant">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Clipboard</title>
  <style>
    :root {
      color-scheme: light;
      --ink: rgba(19, 31, 45, .92);
      --muted: rgba(31, 49, 68, .68);
      --line: rgba(255, 255, 255, .46);
      --glass: rgba(238, 250, 255, .48);
      --glass-strong: rgba(255, 255, 255, .66);
      --blue: #147eea;
      --blue-soft: rgba(20, 126, 234, .15);
      --red: #e23d60;
      --green: #29a676;
      font-family: ui-rounded, "SF Pro Rounded", "SF Pro Display", "Avenir Next",
        "Noto Sans TC", "Microsoft JhengHei", sans-serif;
    }

    * { box-sizing: border-box; }
    body {
      min-height: 100dvh;
      margin: 0;
      display: grid;
      place-items: center;
      color: var(--ink);
      background:
        radial-gradient(circle at 24% 20%, rgba(181, 243, 255, .92), transparent 34%),
        radial-gradient(circle at 70% 76%, rgba(105, 139, 255, .58), transparent 34%),
        linear-gradient(135deg, #edf8ff 0%, #84cff8 46%, #789dea 100%);
      overflow-x: hidden;
    }

    body::before {
      content: "";
      position: fixed;
      inset: -20%;
      background:
        radial-gradient(circle at 10% 80%, rgba(162, 250, 214, .44), transparent 26%),
        radial-gradient(circle at 92% 22%, rgba(255, 255, 255, .56), transparent 20%);
      filter: blur(18px);
      animation: drift 12s ease-in-out infinite alternate;
    }

    main {
      position: relative;
      width: min(920px, calc(100vw - 28px));
      min-height: min(760px, calc(100dvh - 28px));
      padding: 28px;
      border: 1px solid rgba(255, 255, 255, .48);
      border-radius: 42px;
      background: linear-gradient(145deg, rgba(177, 227, 250, .72), rgba(126, 169, 231, .64));
      box-shadow: 0 30px 80px rgba(29, 60, 107, .34), inset 0 1px 0 rgba(255, 255, 255, .58);
      backdrop-filter: blur(30px) saturate(1.28);
      -webkit-backdrop-filter: blur(30px) saturate(1.28);
      overflow: hidden;
    }

    .topbar, .grid, .dock { position: relative; z-index: 1; }
    .topbar {
      display: flex;
      justify-content: space-between;
      gap: 18px;
      align-items: center;
      margin-bottom: 24px;
    }
    .eyebrow {
      margin: 0 0 6px;
      color: var(--muted);
      font-size: 13px;
      font-weight: 800;
      letter-spacing: .18em;
      text-transform: uppercase;
    }
    h1 {
      margin: 0;
      font-size: clamp(30px, 5vw, 52px);
      line-height: .95;
      letter-spacing: -.06em;
    }
    .status {
      display: flex;
      align-items: center;
      gap: 10px;
      min-height: 44px;
      padding: 10px 14px;
      border-radius: 999px;
      background: rgba(255, 255, 255, .42);
      border: 1px solid var(--line);
      font-weight: 800;
      box-shadow: inset 0 1px 0 rgba(255,255,255,.42);
    }
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 999px;
      background: var(--red);
      box-shadow: 0 0 0 6px rgba(226, 61, 96, .13);
    }
    .dot.online {
      background: var(--green);
      box-shadow: 0 0 0 6px rgba(41, 166, 118, .14);
    }

    .grid {
      display: grid;
      grid-template-columns: 1.05fr .95fr;
      gap: 22px;
    }
    .card {
      min-height: 210px;
      padding: 24px;
      border-radius: 30px;
      background: linear-gradient(180deg, var(--glass-strong), var(--glass));
      border: 1px solid var(--line);
      box-shadow: 0 18px 44px rgba(72, 121, 170, .16), inset 0 1px 0 rgba(255,255,255,.66);
      backdrop-filter: blur(22px) saturate(1.2);
      -webkit-backdrop-filter: blur(22px) saturate(1.2);
      animation: rise .52s ease-out both;
    }
    .card:nth-child(2) { animation-delay: .06s; }
    .card:nth-child(3) { animation-delay: .12s; }
    .card:nth-child(4) { animation-delay: .18s; }
    .card.large { grid-row: span 2; }
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
    }
    .title {
      font-size: 22px;
      font-weight: 900;
      letter-spacing: -.03em;
    }
    .metric {
      color: var(--blue);
      font-size: 22px;
      font-weight: 900;
    }
    textarea, .clip-view {
      width: 100%;
      min-height: 214px;
      padding: 16px;
      resize: vertical;
      border: 1px solid rgba(255, 255, 255, .58);
      border-radius: 22px;
      outline: none;
      color: var(--ink);
      background: rgba(255, 255, 255, .38);
      box-shadow: inset 0 1px 18px rgba(86, 145, 191, .14);
      font: 600 16px/1.55 ui-rounded, "SF Pro Rounded", "Noto Sans TC", sans-serif;
    }
    textarea:focus {
      border-color: rgba(20, 126, 234, .55);
      box-shadow: 0 0 0 5px var(--blue-soft), inset 0 1px 18px rgba(86, 145, 191, .14);
    }
    .clip-view {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .hint {
      margin: 12px 0 0;
      color: var(--muted);
      font-size: 14px;
      line-height: 1.45;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      margin-top: 14px;
    }
    button {
      min-height: 44px;
      padding: 0 16px;
      border: 0;
      border-radius: 16px;
      color: white;
      background: linear-gradient(180deg, #299bff, #0976dc);
      box-shadow: 0 10px 20px rgba(12, 109, 210, .24), inset 0 1px 0 rgba(255,255,255,.38);
      font-weight: 900;
      cursor: pointer;
      transition: transform .18s ease, filter .18s ease;
    }
    button.secondary {
      color: var(--ink);
      background: rgba(255,255,255,.55);
    }
    button:active { transform: scale(.97); }
    .dock {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      margin: 28px -28px -28px;
      padding: 20px 32px;
      color: rgba(255,255,255,.95);
      background: rgba(53, 82, 122, .36);
      border-top: 1px solid rgba(255,255,255,.28);
      font-size: 18px;
      font-weight: 900;
      text-shadow: 0 1px 10px rgba(20, 45, 75, .32);
    }
    .dock small { font-size: 13px; opacity: .78; }

    @keyframes rise {
      from { opacity: 0; transform: translateY(14px) scale(.985); }
      to { opacity: 1; transform: none; }
    }
    @keyframes drift {
      from { transform: translate3d(-2%, -1%, 0) rotate(-2deg); }
      to { transform: translate3d(2%, 1%, 0) rotate(2deg); }
    }
    @media (max-width: 760px) {
      main { padding: 18px; border-radius: 30px; }
      .topbar { align-items: flex-start; flex-direction: column; }
      .grid { grid-template-columns: 1fr; }
      .card.large { grid-row: auto; }
      .dock { margin: 18px -18px -18px; padding: 18px; flex-direction: column; align-items: flex-start; }
    }
    @media (prefers-reduced-motion: reduce) {
      *, body::before { animation: none !important; transition: none !important; }
    }
  </style>
</head>
<body>
  <main>
    <section class="topbar">
      <div>
        <p class="eyebrow">Zero install clipboard</p>
        <h1>Gboard Clipboard Glass</h1>
      </div>
      <div class="status" aria-live="polite"><span id="dot" class="dot"></span><span id="status">Connecting</span></div>
    </section>

    <section class="grid">
      <article class="card large">
        <div class="card-header">
          <div class="title">Desktop → Phone</div>
          <div class="metric" id="desktopCount">0</div>
        </div>
        <textarea id="desktopText" placeholder="在這裡 Ctrl+V 貼上 Windows / macOS / Linux 剪貼簿，會直接送到手機。"></textarea>
        <div class="actions">
          <button id="sendButton">Send to phone</button>
          <button class="secondary" id="pasteButton">Paste from browser</button>
        </div>
        <p class="hint">HTTP 模式下瀏覽器可能不允許自動讀剪貼簿；手動 Ctrl+V 永遠可用。</p>
      </article>

      <article class="card">
        <div class="card-header">
          <div class="title">Phone → Web</div>
          <div class="metric" id="phoneCount">0</div>
        </div>
        <div id="phoneText" class="clip-view">等待手機剪貼簿...</div>
        <div class="actions">
          <button id="copyButton">Copy on desktop</button>
        </div>
      </article>

      <article class="card">
        <div class="card-header">
          <div class="title">Live Link</div>
          <div class="metric">LAN</div>
        </div>
        <p class="hint">保持這個分頁開啟。手機複製文字後會透過 Server-Sent Events 即時顯示在這裡。</p>
        <p class="hint" id="lastUpdated">No clipboard received yet.</p>
      </article>
    </section>

    <section class="dock">
      <span>頁面開著就可用，不需要安裝桌面 APP</span>
      <small id="endpoint"></small>
    </section>
  </main>
  <script>
    const desktopText = document.getElementById("desktopText");
    const phoneText = document.getElementById("phoneText");
    const desktopCount = document.getElementById("desktopCount");
    const phoneCount = document.getElementById("phoneCount");
    const statusText = document.getElementById("status");
    const dot = document.getElementById("dot");
    const lastUpdated = document.getElementById("lastUpdated");
    const endpoint = document.getElementById("endpoint");
    let lastPhoneText = "";
    let lastPhoneUpdatedAtMs = 0;

    endpoint.textContent = location.host;

    function setOnline(online, label) {
      statusText.textContent = label;
      dot.classList.toggle("online", online);
    }

    function applyPhoneClipboard(text, updatedAtMs) {
      const nextText = text || "";
      const nextUpdatedAtMs = Number(updatedAtMs || 0);
      if (nextUpdatedAtMs > 0 && nextUpdatedAtMs < lastPhoneUpdatedAtMs) {
        return;
      }
      if (nextUpdatedAtMs === lastPhoneUpdatedAtMs && nextText === lastPhoneText) {
        return;
      }
      lastPhoneText = nextText;
      lastPhoneUpdatedAtMs = nextUpdatedAtMs;
      phoneText.textContent = lastPhoneText || "空剪貼簿";
      phoneCount.textContent = String(lastPhoneText.length);
      lastUpdated.textContent = nextUpdatedAtMs > 0
        ? "Updated " + new Date(nextUpdatedAtMs).toLocaleTimeString()
        : "Updated " + new Date().toLocaleTimeString();
      setOnline(true, "Phone copied");
    }

    async function pollLatestClipboard() {
      try {
        const response = await fetch("/state", { cache: "no-store" });
        if (!response.ok) return;
        const payload = await response.json();
        applyPhoneClipboard(payload.text, payload.updatedAtMs);
      } catch {
        // Keep SSE as the primary path; polling is best effort fallback.
      }
    }

    async function sendToPhone() {
      const text = desktopText.value;
      if (text.length === 0) return;
      const response = await fetch("/clipboard", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text })
      });
      if (!response.ok) throw new Error("send failed");
      desktopCount.textContent = String(text.length);
      setOnline(true, "Sent");
    }

    document.getElementById("sendButton").addEventListener("click", () => {
      sendToPhone().catch(() => setOnline(false, "Send failed"));
    });
    desktopText.addEventListener("paste", () => {
      setTimeout(() => sendToPhone().catch(() => setOnline(false, "Send failed")), 20);
    });
    document.getElementById("pasteButton").addEventListener("click", async () => {
      try {
        desktopText.value = await navigator.clipboard.readText();
        await sendToPhone();
      } catch {
        desktopText.focus();
        setOnline(false, "Use Ctrl+V");
      }
    });
    document.getElementById("copyButton").addEventListener("click", async () => {
      if (!lastPhoneText) return;
      try {
        await navigator.clipboard.writeText(lastPhoneText);
        setOnline(true, "Copied");
      } catch {
        const range = document.createRange();
        range.selectNodeContents(phoneText);
        const selection = getSelection();
        selection.removeAllRanges();
        selection.addRange(range);
        setOnline(false, "Press Ctrl+C");
      }
    });

    const events = new EventSource("/events");
    events.onopen = () => setOnline(true, "Connected");
    events.onerror = () => {
      setOnline(false, "Reconnecting");
      void pollLatestClipboard();
    };
    events.addEventListener("clipboard", event => {
      const payload = JSON.parse(event.data);
      applyPhoneClipboard(payload.text, payload.updatedAtMs);
    });
    void pollLatestClipboard();
    setInterval(() => { void pollLatestClipboard(); }, 1500);
  </script>
</body>
</html>
""";
}

