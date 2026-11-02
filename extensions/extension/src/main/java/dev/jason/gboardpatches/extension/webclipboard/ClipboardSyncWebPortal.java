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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
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
    private static final long MAX_IMAGE_REQUEST_BODY_BYTES = 20 * 1024 * 1024L;
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
    private volatile String latestPhoneClipboardType = "text";
    private volatile String latestPhoneImageMime = "image/png";
    private volatile long latestPhoneImageAtMs;
    private volatile long latestPhoneImageVersion;
    private volatile long latestPhoneImageSize;
    private volatile String latestPhoneImageSource = "phone";
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

        default boolean applyDesktopImage(InputStream stream, String mimeType, long contentLength) {
            return false;
        }

        default InputStream openPhoneImageStream() {
            return null;
        }

        default long getPhoneImageLength() {
            return 0L;
        }

        default String getPhoneImageMimeType() {
            return "image/png";
        }

        default boolean hasPhoneImage() {
            return false;
        }

        default void recordPhoneImage(String mimeType, String uri) {
        }
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
        latestPhoneClipboardType = "text";
        latestPhoneClipboardAtMs = nowMs;
        broadcastClipboardEvent(text, latestPhoneClipboardAtMs, "phone", "Phone", null);
    }

    void publishPhoneImage(String mimeType, long sizeBytes) {
        long nowMs = System.currentTimeMillis();
        latestPhoneClipboardType = "image";
        latestPhoneImageSource = "phone";
        latestPhoneImageMime = mimeType != null ? mimeType : "image/png";
        latestPhoneImageSize = sizeBytes;
        latestPhoneImageAtMs = nowMs;
        latestPhoneImageVersion = nowMs;
        broadcastImageEvent(latestPhoneImageMime, latestPhoneImageSize, latestPhoneImageVersion,
                "phone", "Phone", null);
    }

    private void broadcastImageEvent(String mime, long sizeBytes, long version, String source,
            String senderLabel, String clientId) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("type", "image");
            payload.put("mime", mime);
            payload.put("size", sizeBytes);
            payload.put("version", version);
            payload.put("url", "/clipboard/image?v=" + version);
            payload.put("updatedAtMs", version);
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
        String frame = "event: image\r\n"
                + "data: " + payload.toString() + "\r\n\r\n";
        List<EventClient> clients = snapshotEventClients();
        logInfo(LOG_PREFIX + " portal published image"
                + " instance=" + portalInstanceId
                + " source=" + source
                + " mime=" + mime
                + " size=" + sizeBytes
                + ", clients=" + clients.size());
        List<EventClient> failedClients = writeFrameToClients(clients, frame, "image");
        removeFailedEventClients(failedClients);
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
            if ("GET".equals(request.method) && "/clipboard/image".equals(request.path)) {
                handleClipboardImageGet(socket);
                return;
            }
            if ("POST".equals(request.method) && "/clipboard/image".equals(request.path)) {
                handleClipboardImagePost(socket, request);
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
            boolean isImageUpload = "POST".equals(request.method) && "/clipboard/image".equals(request.path);
            long maxBytes = isImageUpload ? MAX_IMAGE_REQUEST_BODY_BYTES : MAX_REQUEST_BODY_BYTES;
            if (contentLength > maxBytes) {
                request.bodyTooLarge = true;
                request.body = "";
                return request;
            }
            request.contentLength = contentLength;
            if (isImageUpload) {
                request.bodyStream = new LimitedInputStream(input, contentLength);
                request.body = "";
            } else {
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
            }
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
            String key = decodeUtf8(pair.substring(0, separator));
            String value = decodeUtf8(pair.substring(separator + 1));
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
                || "/clipboard/image".equals(request.path)
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
        String suppliedToken = request == null ? "" : request.header("x-loopback-ingress-token");
        return ClipboardSyncLoopbackAuth.tokenMatches(expectedToken, suppliedToken);
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
        JSONObject ingressJson = null;
        try {
            ingressJson = new JSONObject(request.body);
        } catch (Throwable ignored) {
        }
        if (ingressJson != null && "image".equals(ingressJson.optString("type"))) {
            String rawMime = ingressJson.optString("mime", "image/png");
            String mime = sanitizeImageMime(rawMime);
            String uri = ingressJson.optString("uri");
            if (bridge != null) {
                bridge.recordPhoneImage(mime, uri);
            }
            long size = bridge != null ? bridge.getPhoneImageLength() : 0L;
            publishPhoneImage(mime, size);
            writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                    "{\"ok\":true}");
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

    private static boolean isWhitelistedImageMime(String mime) {
        if (mime == null) {
            return false;
        }
        String clean = mime.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
        return "image/png".equals(clean)
                || "image/jpeg".equals(clean)
                || "image/jpg".equals(clean)
                || "image/webp".equals(clean)
                || "image/gif".equals(clean);
    }

    private static String sanitizeImageMime(String rawMime) {
        if (rawMime == null) {
            return "image/png";
        }
        String clean = rawMime.replaceAll("[\\r\\n]", "").trim().toLowerCase(Locale.ROOT);
        if (isWhitelistedImageMime(clean)) {
            return clean;
        }
        return "image/png";
    }

    private void handleClipboardImageGet(Socket socket) throws Exception {
        if (bridge == null || !bridge.hasPhoneImage()) {
            writeResponse(socket, 404, "Not Found", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"no_image\"}");
            return;
        }
        InputStream stream = bridge.openPhoneImageStream();
        if (stream == null) {
            writeResponse(socket, 404, "Not Found", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"image_not_available\"}");
            return;
        }
        try {
            long length = bridge.getPhoneImageLength();
            String mime = sanitizeImageMime(bridge.getPhoneImageMimeType());
            OutputStream output = socket.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    output, StandardCharsets.UTF_8));
            writer.write("HTTP/1.1 200 OK\r\n");
            writer.write("Content-Type: " + mime + "\r\n");
            writer.write("X-Content-Type-Options: nosniff\r\n");
            if (length > 0) {
                writer.write("Content-Length: " + length + "\r\n");
            }
            writer.write("Cache-Control: no-cache\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();

            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            closeQuietly(stream);
        }
    }

    private void handleClipboardImagePost(Socket socket, Request request) throws Exception {
        if (request.bodyTooLarge) {
            writeResponse(socket, 413, "Payload Too Large", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"payload_too_large\"}");
            return;
        }
        if (request.bodyStream == null || request.contentLength <= 0) {
            writeResponse(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"empty_image\"}");
            return;
        }
        String rawContentType = request.header("content-type");
        int semi = rawContentType.indexOf(';');
        if (semi > 0) {
            rawContentType = rawContentType.substring(0, semi).trim();
        }
        if (!isWhitelistedImageMime(rawContentType)) {
            writeResponse(socket, 400, "Bad Request", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"unsupported_image_type\"}");
            return;
        }
        String contentType = sanitizeImageMime(rawContentType);
        boolean applied = bridge != null && bridge.applyDesktopImage(
                request.bodyStream, contentType, request.contentLength);
        if (!applied) {
            writeResponse(socket, 500, "Internal Server Error", "application/json; charset=utf-8",
                    "{\"ok\":false,\"error\":\"apply_failed\"}");
            return;
        }
        long now = System.currentTimeMillis();
        latestPhoneClipboardType = "image";
        latestPhoneImageSource = "web";
        latestPhoneImageMime = contentType;
        latestPhoneImageSize = request.contentLength;
        latestPhoneImageAtMs = now;
        latestPhoneImageVersion = now;
        broadcastImageEvent(contentType, request.contentLength, now, "web",
                senderLabelFor(socket), request.query("clientId"));
        writeResponse(socket, 200, "OK", "application/json; charset=utf-8",
                "{\"ok\":true,\"status\":\"applied\"}");
    }

    private String buildLatestStateJson() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("type", latestPhoneClipboardType);
            payload.put("text", latestPhoneClipboardText == null ? "" : latestPhoneClipboardText);
            payload.put("updatedAtMs", Math.max(0L, latestPhoneClipboardAtMs));
            if ("image".equals(latestPhoneClipboardType)) {
                payload.put("mime", latestPhoneImageMime);
                payload.put("size", latestPhoneImageSize);
                payload.put("version", latestPhoneImageVersion);
                payload.put("url", "/clipboard/image?v=" + latestPhoneImageVersion);
                payload.put("source", latestPhoneImageSource);
            }
        } catch (Throwable ignored) {
            return "{\"type\":\"text\",\"text\":\"\",\"updatedAtMs\":0}";
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
                || "/clipboard/image".equals(request.path)
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
            return decodeUtf8(pair.substring(separator + 1));
        }
        return "";
    }

    private static String decodeUtf8(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
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
        if ("index.html".equals(assetPath) && bytes.length > 0) {
            String html = new String(bytes, StandardCharsets.UTF_8);
            if (html.contains("</body>")) {
                html = html.replace("</body>", IMAGE_INJECTION_SNIPPET + "</body>");
                bytes = html.getBytes(StandardCharsets.UTF_8);
            }
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
        InputStream bodyStream;
        long contentLength;
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

    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        LimitedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = delegate.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int count = delegate.read(b, off, toRead);
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }

        @Override
        public void close() throws IOException {
            // Keep underlying socket stream alive
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

    private static final String IMAGE_INJECTION_SNIPPET = """
<style>
#gb-img-tray{position:fixed;bottom:20px;right:20px;z-index:99999;display:flex;flex-direction:column;align-items:flex-end;gap:10px;pointer-events:none;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif}
.gb-img-card{pointer-events:auto;background:rgba(24,24,27,0.92);color:#fafafa;border:1px solid rgba(255,255,255,0.14);border-radius:14px;box-shadow:0 10px 30px rgba(0,0,0,0.35);backdrop-filter:blur(16px);-webkit-backdrop-filter:blur(16px);padding:12px 14px;display:flex;align-items:center;gap:12px;max-width:340px;animation:gbIn .25s ease-out}
@media(prefers-color-scheme:light){.gb-img-card{background:rgba(255,255,255,0.95);color:#18181b;border:1px solid rgba(0,0,0,0.1);box-shadow:0 10px 30px rgba(0,0,0,0.12)}}
.gb-img-thumb{width:48px;height:48px;border-radius:8px;object-fit:cover;border:1px solid rgba(128,128,128,0.25);flex-shrink:0}
.gb-img-btn{background:#2563eb;color:#fff;border:none;border-radius:8px;padding:6px 12px;font-size:12px;font-weight:600;cursor:pointer;transition:background .15s}
.gb-img-btn:hover{background:#1d4ed8}
.gb-img-btn-sec{background:rgba(128,128,128,0.2);color:inherit}
.gb-img-btn-sec:hover{background:rgba(128,128,128,0.3)}
@keyframes gbIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:none}}
#gb-img-pending-host{margin-bottom:8px}
#gb-img-pending{display:flex;align-items:center;gap:10px;padding:8px 10px;border:1px solid var(--agent-chat-border);background:var(--agent-chat-secondary);border-radius:16px;animation:gbIn .2s ease-out}
#gb-img-pending .gb-img-thumb{width:44px;height:44px}
.gb-chat-img-row{display:flex;align-items:center;gap:10px}
.gb-chat-img-col{display:flex;flex-direction:column;gap:5px;align-items:flex-start;min-width:0}
.gb-chat-img-title{font-size:12px;font-weight:600;color:var(--agent-chat-text)}
.gb-chat-img-meta{font-size:11px;color:var(--agent-chat-muted);word-break:break-all}
.gb-chat-img-actions{display:flex;gap:6px;flex-wrap:wrap}
#gb-img-plus{display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;margin-right:auto;border-radius:9999px;border:1px solid var(--agent-chat-border);background:transparent;color:var(--agent-chat-text);cursor:pointer;transition:background .15s}
#gb-img-plus:hover{background:var(--agent-chat-secondary)}
</style>
<div id="gb-img-tray"></div>
<script>
(function(){
  var pendingFile=null,pendingUrl='',lastVer=0;
  function getCode(){
    try{
      var q=new URLSearchParams(location.search);
      return q.get('code')||q.get('pairingCode')||sessionStorage.getItem('gboard-web-clipboard.pairing-code.v1')||localStorage.getItem('gboard-web-clipboard.pairing-code.v1')||'';
    }catch(e){return '';}
  }
  function showToast(msg){
    var tray=document.getElementById('gb-img-tray');
    if(!tray)return;
    var t=document.createElement('div');
    t.className='gb-img-card';
    t.style.fontSize='13px';
    t.textContent=msg;
    tray.appendChild(t);
    setTimeout(function(){t.remove();},3000);
  }
  function chatList(){return document.querySelector('[data-testid="clipboard-thread-messages"]');}
  function scrollChat(){
    var vp=document.querySelector('[data-slot="scroll-area-viewport"]');
    if(!vp)return;
    try{vp.scrollTo({top:vp.scrollHeight,behavior:'smooth'});}catch(e){vp.scrollTop=vp.scrollHeight;}
  }
  function timeLabel(){try{return new Date().toLocaleTimeString();}catch(e){return '';}}
  function icon(inner,size){
    var svg=document.createElementNS('http://www.w3.org/2000/svg','svg');
    svg.setAttribute('viewBox','0 0 24 24');
    svg.setAttribute('width',size);
    svg.setAttribute('height',size);
    svg.setAttribute('fill','none');
    svg.setAttribute('stroke','currentColor');
    svg.setAttribute('stroke-width','2');
    svg.setAttribute('stroke-linecap','round');
    svg.setAttribute('stroke-linejoin','round');
    svg.setAttribute('aria-hidden','true');
    var parsed=new DOMParser().parseFromString('<svg xmlns="http://www.w3.org/2000/svg">'+inner+'</svg>','image/svg+xml');
    while(parsed.documentElement.firstChild){svg.appendChild(parsed.documentElement.firstChild);}
    return svg;
  }
  var ICON_MONITOR='<rect x="2" y="3" width="20" height="14" rx="2"></rect><path d="M8 21h8"></path><path d="M12 17v4"></path>';
  var ICON_PHONE='<rect x="5" y="2" width="14" height="20" rx="2"></rect><path d="M12 18h.01"></path>';
  var ICON_CHECK='<path d="M20 6 9 17l-5-5"></path>';
  function avatar(side){
    var wrap=document.createElement('div');
    wrap.className='row-start-1 mt-0.5 shrink-0 '+(side==='desktop'?'col-start-3':'col-start-1');
    var circle=document.createElement('div');
    circle.setAttribute('data-slot','avatar-fallback');
    circle.className='flex size-6 items-center justify-center rounded-full border border-[var(--agent-chat-border)] '+(side==='desktop'?'bg-background text-foreground dark:bg-[#242424]':'bg-[var(--agent-chat-secondary)] text-[var(--agent-chat-text)]');
    circle.appendChild(icon(side==='desktop'?ICON_MONITOR:ICON_PHONE,12));
    wrap.appendChild(circle);
    return wrap;
  }
  function statusDot(){
    var span=document.createElement('span');
    span.setAttribute('data-testid','message-status-indicator');
    span.setAttribute('role','img');
    span.setAttribute('aria-label','Sent');
    span.setAttribute('title','Sent');
    span.className='inline-flex size-5 items-center justify-center rounded-full border border-[var(--agent-chat-border)] bg-transparent text-emerald-600 dark:text-emerald-400';
    span.appendChild(icon(ICON_CHECK,12));
    return span;
  }
  function addChatMessage(side,source,aria,buildBody){
    var list=chatList();
    if(!list)return null;
    var wrap=document.createElement('div');
    wrap.className='flex w-full min-w-0 max-w-full '+(side==='desktop'?'justify-end':'justify-start');
    wrap.setAttribute('data-gb-image-message','true');
    var art=document.createElement('article');
    art.setAttribute('role','article');
    art.setAttribute('aria-label',aria);
    art.setAttribute('data-side',side);
    art.setAttribute('data-message-source',source);
    art.setAttribute('data-layout-variant',side==='desktop'?'desktop':'phone-default');
    art.className='relative grid min-w-0 w-fit grid-cols-[auto_minmax(0,1fr)_auto] items-start gap-x-2 gap-y-1.5 '+(side==='desktop'?'max-w-[min(36rem,82vw)]':'max-w-[min(42rem,92%)]');
    var meta=document.createElement('div');
    meta.setAttribute('data-testid','message-side-meta');
    meta.className='row-start-1 flex shrink-0 self-end pb-1 text-xs text-[var(--agent-chat-muted)] items-center gap-1.5 '+(side==='desktop'?'col-start-1 text-left':'col-start-3 justify-self-end text-right');
    if(side==='desktop')meta.appendChild(statusDot());
    var tm=document.createElement('span');
    tm.className='whitespace-nowrap';
    tm.textContent=timeLabel();
    meta.appendChild(tm);
    var col=document.createElement('div');
    col.className='col-start-2 row-start-1 flex min-w-0 max-w-full flex-col gap-1.5 '+(side==='desktop'?'items-end text-right':'items-start text-left');
    var holder=document.createElement('div');
    holder.className='min-w-0 max-w-full '+(side==='desktop'?'self-end':'self-start');
    var body=document.createElement('div');
    body.setAttribute('data-testid','message-body');
    body.className='min-w-0 w-fit max-w-full rounded-[16px] px-3.5 py-2 text-sm leading-[1.55] whitespace-pre-wrap break-words [overflow-wrap:anywhere] text-[var(--agent-chat-text)] '+(side==='desktop'?'text-left bg-[var(--agent-chat-secondary)]':'border border-[var(--agent-chat-border)] bg-[var(--agent-chat-tertiary)]');
    buildBody(body);
    holder.appendChild(body);
    col.appendChild(holder);
    art.appendChild(meta);
    art.appendChild(col);
    art.appendChild(avatar(side));
    wrap.appendChild(art);
    var end=document.querySelector('[data-testid="clipboard-thread-end"]');
    if(end&&end.parentNode===list){list.insertBefore(wrap,end);}else{list.appendChild(wrap);}
    scrollChat();
    return wrap;
  }
  function copyToClipboard(imgUrl,btn){
    (async function(){
      try{
        var r=await fetch(imgUrl);
        var b=await r.blob();
        var out=b;
        if(b.type!=='image/png'){
          var im=new Image();
          await new Promise(function(res,rej){im.onload=res;im.onerror=rej;im.src=URL.createObjectURL(b);});
          var cv=document.createElement('canvas');cv.width=im.naturalWidth;cv.height=im.naturalHeight;
          cv.getContext('2d').drawImage(im,0,0);
          out=await new Promise(function(res){cv.toBlob(res,'image/png');});
        }
        await navigator.clipboard.write([new ClipboardItem({[out.type]:out})]);
        var old=btn.textContent;
        btn.textContent='Copied!';
        setTimeout(function(){btn.textContent=old;},2000);
      }catch(err){showToast('Clipboard copy failed');}
    })();
  }
  function receiveChat(imgUrl,mime){
    return addChatMessage('phone','phone','Phone sent an image',function(body){
      var row=document.createElement('div');
      row.className='gb-chat-img-row';
      var im=document.createElement('img');
      im.className='gb-img-thumb';
      im.src=imgUrl;
      im.alt='Image received from phone';
      var col=document.createElement('div');
      col.className='gb-chat-img-col';
      var tit=document.createElement('div');
      tit.className='gb-chat-img-title';
      tit.textContent='Image received';
      var acts=document.createElement('div');
      acts.className='gb-chat-img-actions';
      var cp=document.createElement('button');
      cp.type='button';
      cp.className='gb-img-btn';
      cp.textContent='Copy';
      cp.onclick=function(){copyToClipboard(imgUrl,cp);};
      var dl=document.createElement('button');
      dl.type='button';
      dl.className='gb-img-btn gb-img-btn-sec';
      dl.textContent='Download';
      dl.onclick=function(){
        var a=document.createElement('a');
        a.href=imgUrl;
        a.download='phone_image_'+Date.now()+(mime&&mime.indexOf('png')>=0?'.png':'.jpg');
        a.click();
      };
      acts.appendChild(cp);
      acts.appendChild(dl);
      col.appendChild(tit);
      col.appendChild(acts);
      row.appendChild(im);
      row.appendChild(col);
      body.appendChild(row);
    });
  }
  function showImgCard(imgUrl,mime){
    var tray=document.getElementById('gb-img-tray');
    if(!tray)return;
    var old=document.getElementById('gb-card-latest');
    if(old)old.remove();
    var card=document.createElement('div');
    card.id='gb-card-latest';
    card.className='gb-img-card';
    var img=document.createElement('img');
    img.className='gb-img-thumb';
    img.src=imgUrl;
    var right=document.createElement('div');
    right.style.display='flex';
    right.style.flexDirection='column';
    right.style.gap='6px';
    var tit=document.createElement('div');
    tit.style.fontSize='12px';
    tit.style.fontWeight='600';
    tit.textContent='Phone Image';
    var row=document.createElement('div');
    row.style.display='flex';
    row.style.gap='6px';
    var cp=document.createElement('button');
    cp.className='gb-img-btn';
    cp.textContent='Copy';
    cp.onclick=function(){copyToClipboard(imgUrl,cp);};
    var dl=document.createElement('button');
    dl.className='gb-img-btn gb-img-btn-sec';
    dl.textContent='Download';
    dl.onclick=function(){
      var a=document.createElement('a');
      a.href=imgUrl;
      a.download='phone_image_'+Date.now()+(mime&&mime.indexOf('png')>=0?'.png':'.jpg');
      a.click();
    };
    var cl=document.createElement('button');
    cl.className='gb-img-btn gb-img-btn-sec';
    cl.textContent='✕';
    cl.onclick=function(){card.remove();};
    row.appendChild(cp);row.appendChild(dl);row.appendChild(cl);
    right.appendChild(tit);right.appendChild(row);
    card.appendChild(img);card.appendChild(right);
    tray.appendChild(card);
  }
  function handlePhoneImage(imgUrl,mime,version){
    if(version){
      if(version<=lastVer)return;
      lastVer=version;
    }
    if(!receiveChat(imgUrl,mime))showImgCard(imgUrl,mime);
  }
  function composerForm(){
    var ta=document.getElementById('chat-composer');
    return ta?ta.closest('form'):null;
  }
  function clearPending(){
    if(pendingUrl){try{URL.revokeObjectURL(pendingUrl);}catch(e){}pendingUrl='';}
    pendingFile=null;
    var h=document.getElementById('gb-img-pending-host');
    if(h){h.innerHTML='';h.style.display='none';}
  }
  function stageImg(file){
    if(!file)return;
    var host=document.getElementById('gb-img-pending-host');
    if(!host){uploadImg(file);return;}
    clearPending();
    pendingFile=file;
    pendingUrl=URL.createObjectURL(file);
    host.style.display='';
    var box=document.createElement('div');
    box.id='gb-img-pending';
    var im=document.createElement('img');
    im.className='gb-img-thumb';
    im.src=pendingUrl;
    im.alt='Selected image';
    var col=document.createElement('div');
    col.className='gb-chat-img-col';
    col.style.flex='1 1 auto';
    var tit=document.createElement('div');
    tit.className='gb-chat-img-title';
    tit.textContent='Ready to send';
    var meta=document.createElement('div');
    meta.className='gb-chat-img-meta';
    meta.textContent=(file.name||'Pasted image')+(file.size?' · '+Math.max(1,Math.round(file.size/1024))+' KB':'');
    col.appendChild(tit);
    col.appendChild(meta);
    var send=document.createElement('button');
    send.type='button';
    send.className='gb-img-btn';
    send.textContent='Send';
    send.onclick=function(){var f=pendingFile;clearPending();if(f)uploadImg(f);};
    var cancel=document.createElement('button');
    cancel.type='button';
    cancel.className='gb-img-btn gb-img-btn-sec';
    cancel.textContent='✕';
    cancel.onclick=clearPending;
    box.appendChild(im);
    box.appendChild(col);
    box.appendChild(send);
    box.appendChild(cancel);
    host.appendChild(box);
  }
  async function uploadImg(file){
    var code=getCode();
    var headers={'Content-Type':file.type||'image/png'};
    if(code)headers['x-pairing-code']=code;
    var url='/clipboard/image'+(code?'?code='+encodeURIComponent(code):'');
    var thumb=URL.createObjectURL(file);
    var label=file.name||'Pasted image';
    showToast('Sending image to phone...');
    try{
      var res=await fetch(url,{method:'POST',headers:headers,body:file});
      if(res.ok){
        var placed=addChatMessage('desktop','web','You sent an image '+label,function(body){
          var row=document.createElement('div');
          row.className='gb-chat-img-row';
          var im=document.createElement('img');
          im.className='gb-img-thumb';
          im.src=thumb;
          im.alt=label;
          im.onload=function(){try{URL.revokeObjectURL(thumb);}catch(e){}};
          var col=document.createElement('div');
          col.className='gb-chat-img-col';
          var tit=document.createElement('div');
          tit.className='gb-chat-img-title';
          tit.textContent='Image sent';
          var meta=document.createElement('div');
          meta.className='gb-chat-img-meta';
          meta.textContent=label+' · copied to phone';
          col.appendChild(tit);
          col.appendChild(meta);
          row.appendChild(im);
          row.appendChild(col);
          body.appendChild(row);
        });
        if(!placed)showToast('Image copied to phone!');
      }else{
        try{URL.revokeObjectURL(thumb);}catch(e){}
        showToast('Image upload failed ('+res.status+')');
      }
    }catch(e){
      try{URL.revokeObjectURL(thumb);}catch(e2){}
      showToast('Network error uploading image');
    }
  }
  function ensureComposerUi(){
    var ta=document.getElementById('chat-composer');
    if(!ta)return;
    var form=ta.closest('form');
    if(form&&!document.getElementById('gb-img-pending-host')){
      var host=document.createElement('div');
      host.id='gb-img-pending-host';
      host.style.display='none';
      form.insertBefore(host,form.firstChild);
    }
    var submit=form?form.querySelector('button[type="submit"]'):null;
    if(submit&&submit.parentNode&&!document.getElementById('gb-img-plus')){
      var plus=document.createElement('button');
      plus.type='button';
      plus.id='gb-img-plus';
      plus.setAttribute('aria-label','Attach image');
      plus.title='Attach image';
      plus.appendChild(icon('<path d="M12 5v14"></path><path d="M5 12h14"></path>',16));
      plus.onclick=function(){
        var fi=document.getElementById('gb-img-file');
        if(fi)fi.click();
      };
      submit.parentNode.insertBefore(plus,submit);
    }
    if(!document.getElementById('gb-img-file')){
      var fi=document.createElement('input');
      fi.type='file';
      fi.id='gb-img-file';
      fi.accept='image/*';
      fi.style.display='none';
      fi.onchange=function(){
        if(fi.files&&fi.files[0]){stageImg(fi.files[0]);fi.value='';}
      };
      document.body.appendChild(fi);
    }
  }
  window.addEventListener('paste',function(e){
    if(e.clipboardData&&e.clipboardData.items){
      for(var i=0;i<e.clipboardData.items.length;i++){
        var it=e.clipboardData.items[i];
        if(it.type&&it.type.indexOf('image/')===0){
          var f=it.getAsFile();
          if(f){
            if(e.preventDefault)e.preventDefault();
            stageImg(f);
            return;
          }
        }
      }
    }
  });
  ensureComposerUi();
  if(window.MutationObserver&&document.body){
    new MutationObserver(function(){ensureComposerUi();}).observe(document.body,{childList:true,subtree:true});
  }
  var OrigES=window.EventSource;
  if(OrigES){
    window.EventSource=function(){
      var es=new (Function.prototype.bind.apply(OrigES,[null].concat([].slice.call(arguments))))();
      es.addEventListener('image',function(e){
        try{
          var d=JSON.parse(e.data);
          if(d&&d.url&&d.source==='phone'){
            var c=getCode();
            handlePhoneImage(d.url+(c?'&code='+encodeURIComponent(c):''),d.mime,d.updatedAtMs||d.version||0);
          }
        }catch(_){}
      });
      return es;
    };
  }
  setInterval(async function(){
    try{
      var c=getCode();
      var r=await fetch('/state'+(c?'?code='+encodeURIComponent(c):''),{cache:'no-store'});
      if(!r.ok)return;
      var d=await r.json();
      if(d&&d.type==='image'&&d.source==='phone'){
        var ver=d.updatedAtMs||d.version||0;
        if(ver>lastVer){
          var u=(d.url||'/clipboard/image?v='+ver)+(c?'&code='+encodeURIComponent(c):'');
          handlePhoneImage(u,d.mime,ver);
        }
      }
    }catch(_){}
  },2000);
})();
</script>

""";

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
    .clip-image {
      max-width: 100%;
      max-height: 260px;
      object-fit: contain;
      border-radius: 18px;
      border: 1px solid rgba(255, 255, 255, .58);
      background: rgba(0, 0, 0, .05);
      display: none;
      margin-bottom: 8px;
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
        <textarea id="desktopText" placeholder="在這裡 Ctrl+V 貼上 Windows / macOS / Linux 剪貼簿（文字或圖片），會直接送到手機。"></textarea>
        <div class="actions">
          <button id="sendButton">Send to phone</button>
          <button class="secondary" id="pasteButton">Paste from browser</button>
          <input type="file" id="imageFileInput" accept="image/*" style="display:none">
          <button class="secondary" id="selectImageButton">Send image...</button>
        </div>
        <p class="hint">支援文字及圖片。可直接按 Ctrl+V 貼上截圖/圖片，或選取檔案傳送至手機。</p>
      </article>

      <article class="card">
        <div class="card-header">
          <div class="title">Phone → Web</div>
          <div class="metric" id="phoneCount">0</div>
        </div>
        <div id="phoneText" class="clip-view">等待手機剪貼簿...</div>
        <img id="phoneImage" class="clip-image" alt="Phone image clipboard">
        <div class="actions">
          <button id="copyButton">Copy on desktop</button>
          <button id="copyImageButton" style="display:none">Copy Image</button>
          <button id="downloadImageButton" class="secondary" style="display:none">Download Image</button>
        </div>
      </article>

      <article class="card">
        <div class="card-header">
          <div class="title">Live Link</div>
          <div class="metric">LAN</div>
        </div>
        <p class="hint">保持這個分頁開啟。手機複製文字或截圖後會透過 Server-Sent Events 即時顯示在這裡。</p>
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
    const phoneImage = document.getElementById("phoneImage");
    const desktopCount = document.getElementById("desktopCount");
    const phoneCount = document.getElementById("phoneCount");
    const statusText = document.getElementById("status");
    const dot = document.getElementById("dot");
    const lastUpdated = document.getElementById("lastUpdated");
    const endpoint = document.getElementById("endpoint");
    const copyButton = document.getElementById("copyButton");
    const copyImageButton = document.getElementById("copyImageButton");
    const downloadImageButton = document.getElementById("downloadImageButton");
    const imageFileInput = document.getElementById("imageFileInput");
    let lastPhoneText = "";
    let lastPhoneUpdatedAtMs = 0;
    let currentImagePayload = null;

    endpoint.textContent = location.host;

    function setOnline(online, label) {
      statusText.textContent = label;
      dot.classList.toggle("online", online);
    }

    function getPairingCode() {
      try {
        const params = new URLSearchParams(location.search);
        const code = params.get("code") || params.get("pairingCode");
        if (code) {
          sessionStorage.setItem("gboard_web_clipboard_code", code);
          return code;
        }
        return sessionStorage.getItem("gboard_web_clipboard_code") || "";
      } catch (_) {
        return "";
      }
    }

    function applyPhonePayload(payload) {
      if (!payload) return;
      const nextUpdatedAtMs = Number(payload.updatedAtMs || 0);
      if (nextUpdatedAtMs > 0 && nextUpdatedAtMs < lastPhoneUpdatedAtMs) {
        return;
      }
      lastPhoneUpdatedAtMs = nextUpdatedAtMs;
      lastUpdated.textContent = nextUpdatedAtMs > 0
        ? "Updated " + new Date(nextUpdatedAtMs).toLocaleTimeString()
        : "Updated " + new Date().toLocaleTimeString();

      if (payload.type === "image") {
        currentImagePayload = payload;
        phoneText.style.display = "none";
        copyButton.style.display = "none";
        phoneImage.style.display = "block";
        const code = getPairingCode();
        let imgUrl = payload.url;
        if (code) {
          imgUrl += (imgUrl.includes("?") ? "&" : "?") + "code=" + encodeURIComponent(code);
        }
        phoneImage.src = imgUrl;
        copyImageButton.style.display = "";
        downloadImageButton.style.display = "";
        phoneCount.textContent = "IMG";
        setOnline(true, "Image received");
      } else {
        currentImagePayload = null;
        phoneImage.style.display = "none";
        copyImageButton.style.display = "none";
        downloadImageButton.style.display = "none";
        phoneText.style.display = "";
        copyButton.style.display = "";
        lastPhoneText = payload.text || "";
        phoneText.textContent = lastPhoneText || "空剪貼簿";
        phoneCount.textContent = String(lastPhoneText.length);
        setOnline(true, "Phone copied");
      }
    }

    async function pollLatestClipboard() {
      try {
        const code = getPairingCode();
        const pollUrl = "/state" + (code ? "?code=" + encodeURIComponent(code) : "");
        const response = await fetch(pollUrl, { cache: "no-store" });
        if (!response.ok) return;
        const payload = await response.json();
        applyPhonePayload(payload);
      } catch {
        // Keep SSE as the primary path; polling is best effort fallback.
      }
    }

    async function sendToPhone() {
      const text = desktopText.value;
      if (text.length === 0) return;
      const code = getPairingCode();
      const sendUrl = "/clipboard" + (code ? "?code=" + encodeURIComponent(code) : "");
      const headers = { "Content-Type": "application/json" };
      if (code) {
        headers["x-pairing-code"] = code;
      }
      const response = await fetch(sendUrl, {
        method: "POST",
        headers: headers,
        body: JSON.stringify({ text })
      });
      if (!response.ok) throw new Error("send failed");
      desktopCount.textContent = String(text.length);
      setOnline(true, "Sent");
    }

    async function uploadImageToPhone(file) {
      if (!file) return;
      setOnline(true, "Sending image...");
      try {
        const code = getPairingCode();
        const headers = { "Content-Type": file.type || "image/png" };
        if (code) {
          headers["x-pairing-code"] = code;
        }
        const uploadUrl = "/clipboard/image" + (code ? "?code=" + encodeURIComponent(code) : "");
        const response = await fetch(uploadUrl, {
          method: "POST",
          headers: headers,
          body: file
        });
        if (!response.ok) throw new Error("upload failed");
        setOnline(true, "Image sent to phone");
        desktopCount.textContent = "IMG";
      } catch {
        setOnline(false, "Image send failed");
      }
    }

    document.getElementById("sendButton").addEventListener("click", () => {
      sendToPhone().catch(() => setOnline(false, "Send failed"));
    });
    desktopText.addEventListener("paste", e => {
      if (e.clipboardData && e.clipboardData.items) {
        for (const item of e.clipboardData.items) {
          if (item.type.startsWith("image/")) {
            const file = item.getAsFile();
            if (file) {
              e.preventDefault();
              uploadImageToPhone(file);
              return;
            }
          }
        }
      }
      setTimeout(() => sendToPhone().catch(() => setOnline(false, "Send failed")), 20);
    });

    window.addEventListener("paste", e => {
      if (e.clipboardData && e.clipboardData.items) {
        for (const item of e.clipboardData.items) {
          if (item.type.startsWith("image/")) {
            const file = item.getAsFile();
            if (file) {
              e.preventDefault();
              uploadImageToPhone(file);
              return;
            }
          }
        }
      }
    });

    document.getElementById("selectImageButton").addEventListener("click", () => {
      imageFileInput.click();
    });
    imageFileInput.addEventListener("change", () => {
      if (imageFileInput.files && imageFileInput.files[0]) {
        uploadImageToPhone(imageFileInput.files[0]);
      }
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

    copyImageButton.addEventListener("click", async () => {
      if (!currentImagePayload) return;
      try {
        setOnline(true, "Copying image...");
        const code = getPairingCode();
        let fetchUrl = currentImagePayload.url;
        if (code && !fetchUrl.includes("code=")) {
          fetchUrl += (fetchUrl.includes("?") ? "&" : "?") + "code=" + encodeURIComponent(code);
        }
        const res = await fetch(fetchUrl);
        const blob = await res.blob();
        let writeBlob = blob;
        if (blob.type !== "image/png") {
          const img = new Image();
          await new Promise((resolve, reject) => {
            img.onload = resolve;
            img.onerror = reject;
            img.src = URL.createObjectURL(blob);
          });
          const canvas = document.createElement("canvas");
          canvas.width = img.naturalWidth;
          canvas.height = img.naturalHeight;
          const ctx = canvas.getContext("2d");
          ctx.drawImage(img, 0, 0);
          writeBlob = await new Promise(resolve => canvas.toBlob(resolve, "image/png"));
        }
        await navigator.clipboard.write([
          new ClipboardItem({ [writeBlob.type]: writeBlob })
        ]);
        setOnline(true, "Image copied");
      } catch (err) {
        setOnline(false, "Copy failed - click Download");
      }
    });

    downloadImageButton.addEventListener("click", () => {
      if (!currentImagePayload) return;
      const code = getPairingCode();
      let downloadUrl = currentImagePayload.url;
      if (code && !downloadUrl.includes("code=")) {
        downloadUrl += (downloadUrl.includes("?") ? "&" : "?") + "code=" + encodeURIComponent(code);
      }
      const a = document.createElement("a");
      a.href = downloadUrl;
      const ext = (currentImagePayload.mime && currentImagePayload.mime.split("/")[1]) || "png";
      a.download = "phone_clipboard_image." + ext;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    });

    const code = getPairingCode();
    const events = new EventSource("/events" + (code ? "?code=" + encodeURIComponent(code) : ""));
    events.onopen = () => setOnline(true, "Connected");
    events.onerror = () => {
      setOnline(false, "Reconnecting");
      void pollLatestClipboard();
    };
    events.addEventListener("clipboard", event => {
      const payload = JSON.parse(event.data);
      applyPhonePayload({ type: "text", text: payload.text, updatedAtMs: payload.updatedAtMs });
    });
    events.addEventListener("image", event => {
      const payload = JSON.parse(event.data);
      applyPhonePayload(payload);
    });
    void pollLatestClipboard();
    setInterval(() => { void pollLatestClipboard(); }, 1500);
  </script>
</body>
</html>
""";
}

