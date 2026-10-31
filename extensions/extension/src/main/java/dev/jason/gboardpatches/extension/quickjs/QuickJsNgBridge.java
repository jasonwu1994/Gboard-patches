package dev.jason.gboardpatches.extension.quickjs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeSettings;

public final class QuickJsNgBridge {
    private static final int DEFAULT_HTTP_RESPONSE_LIMIT_BYTES =
            GboardTopRowSwipeSettings.DEFAULT_RESPONSE_BODY_LIMIT_BYTES;
    private static final int DEFAULT_MEMORY_LIMIT_BYTES =
            GboardTopRowSwipeSettings.DEFAULT_MEMORY_LIMIT_BYTES;
    private static final int DEFAULT_MAX_STACK_BYTES =
            GboardTopRowSwipeSettings.DEFAULT_MAX_STACK_BYTES;
    private static final String DEFAULT_ACCEPT_HEADER = "*/*";
    private static final String DEFAULT_USER_AGENT = "GboardPatches-QuickJS/1";
    private static final String DEFAULT_TEXT_BODY_CONTENT_TYPE = "text/plain; charset=utf-8";

    private QuickJsNgBridge() {
    }

    public static NativeEvalResult nativeEvaluate(String script, long timeoutMs) {
        return nativeEvaluate(script, timeoutMs,
                DEFAULT_MEMORY_LIMIT_BYTES,
                DEFAULT_MAX_STACK_BYTES,
                DEFAULT_HTTP_RESPONSE_LIMIT_BYTES);
    }

    public static NativeEvalResult nativeEvaluate(String script, long timeoutMs,
            long memoryLimitBytes, long maxStackBytes, int responseBodyLimitBytes) {
        QuickJsNgLibraryLoader.load();
        return nativeEvaluateLoaded(script, timeoutMs, memoryLimitBytes, maxStackBytes,
                responseBodyLimitBytes);
    }

    private static native NativeEvalResult nativeEvaluateLoaded(String script, long timeoutMs,
            long memoryLimitBytes, long maxStackBytes, int responseBodyLimitBytes);

    public static String httpGetForScript(String url, long timeoutMs) throws IOException {
        return httpGetForScript(url, timeoutMs, DEFAULT_HTTP_RESPONSE_LIMIT_BYTES);
    }

    public static String httpGetForScript(String url, long timeoutMs, int responseBodyLimitBytes)
            throws IOException {
        return httpRequestForScript("GET", url, (Map<String, String>) null, null, timeoutMs,
                responseBodyLimitBytes);
    }

    public static String httpPostForScript(String url, String body, long timeoutMs)
            throws IOException {
        return httpPostForScript(url, body, timeoutMs, DEFAULT_HTTP_RESPONSE_LIMIT_BYTES);
    }

    public static String httpPostForScript(String url, String body, long timeoutMs,
            int responseBodyLimitBytes) throws IOException {
        return httpRequestForScript("POST",
                url,
                null,
                body != null ? body : "",
                timeoutMs,
                responseBodyLimitBytes);
    }

    public static String httpRequestForScript(String method,
            String url,
            Map<String, String> headers,
            String body,
            long timeoutMs) throws IOException {
        return httpRequestForScript(method, url, headers, body, timeoutMs,
                DEFAULT_HTTP_RESPONSE_LIMIT_BYTES);
    }

    public static String httpRequestForScript(String method,
            String url,
            Map<String, String> headers,
            String body,
            long timeoutMs,
            int responseBodyLimitBytes) throws IOException {
        return httpRequestForScript(method, url, normalizedHeaders(headers), body, timeoutMs,
                sanitizeResponseBodyLimitBytes(responseBodyLimitBytes));
    }

    public static String httpRequestForScript(String method,
            String url,
            String[] headerNames,
            String[] headerValues,
            String body,
            long timeoutMs) throws IOException {
        return httpRequestForScript(method, url, headerNames, headerValues, body, timeoutMs,
                DEFAULT_HTTP_RESPONSE_LIMIT_BYTES);
    }

    public static String httpRequestForScript(String method,
            String url,
            String[] headerNames,
            String[] headerValues,
            String body,
            long timeoutMs,
            int responseBodyLimitBytes) throws IOException {
        return httpRequestForScript(method, url,
                normalizedHeaders(headersFromArrays(headerNames, headerValues)),
                body,
                timeoutMs,
                sanitizeResponseBodyLimitBytes(responseBodyLimitBytes));
    }

    private static String httpRequestForScript(String method,
            String url,
            LinkedHashMap<String, String> headers,
            String body,
            long timeoutMs,
            int responseBodyLimitBytes) throws IOException {
        String normalizedMethod = normalizedMethod(method);
        URL parsedUrl = validatedHttpUrl(url);
        if ("PATCH".equals(normalizedMethod)) {
            return rawHttpRequestForScript(normalizedMethod, parsedUrl, headers, body, timeoutMs,
                    responseBodyLimitBytes);
        }
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        try {
            int timeout = timeoutMillis(timeoutMs);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(normalizedMethod);
            connection.setRequestProperty("Accept", DEFAULT_ACCEPT_HEADER);
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT);

            boolean hasBody = body != null;
            if (hasBody && !containsHeader(headers, "Content-Type")) {
                connection.setRequestProperty("Content-Type", DEFAULT_TEXT_BODY_CONTENT_TYPE);
            }
            applyHeaders(connection, headers);

            if (hasBody) {
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bodyBytes.length);
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(bodyBytes);
                }
            }

            int statusCode = connection.getResponseCode();
            String response = readResponseBody(connection, statusCode, responseBodyLimitBytes);
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP " + statusCode + ": " + response);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String normalizedMethod(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("HTTP method must not be empty");
        }
        String method = value.trim().toUpperCase(Locale.ROOT);
        return switch (method) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> method;
            default -> throw new IOException("Unsupported HTTP method: " + method);
        };
    }

    private static URL validatedHttpUrl(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            throw new IOException("HTTP URL must not be empty");
        }
        URL url = new URL(value);
        String protocol = url.getProtocol() != null
                ? url.getProtocol().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IOException("Unsupported HTTP URL scheme: " + protocol);
        }
        return url;
    }

    private static int timeoutMillis(long timeoutMs) throws IOException {
        if (timeoutMs <= 0L) {
            throw new IOException("HTTP timeout must be positive");
        }
        return timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
    }

    private static LinkedHashMap<String, String> headersFromArrays(String[] headerNames,
            String[] headerValues) throws IOException {
        if (headerNames == null && headerValues == null) {
            return null;
        }
        if (headerNames == null || headerValues == null
                || headerNames.length != headerValues.length) {
            throw new IOException("HTTP headers must use matching name/value arrays");
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        for (int index = 0; index < headerNames.length; index++) {
            headers.put(headerNames[index], headerValues[index]);
        }
        return headers;
    }

    private static LinkedHashMap<String, String> normalizedHeaders(Map<String, String> headers)
            throws IOException {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.trim().isEmpty()) {
                throw new IOException("HTTP header name must not be empty");
            }
            String normalizedName = name.trim();
            String value = entry.getValue() != null ? entry.getValue() : "";
            validateHeaderName(normalizedName);
            validateHeaderValue(value);
            normalized.put(normalizedName, value);
        }
        return normalized;
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return false;
        }
        for (String headerName : headers.keySet()) {
            if (name.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private static void applyHeaders(HttpURLConnection connection, Map<String, String> headers) {
        if (connection == null || headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    private static String rawHttpRequestForScript(String method,
            URL url,
            Map<String, String> headers,
            String body,
            long timeoutMs,
            int responseBodyLimitBytes) throws IOException {
        int timeout = timeoutMillis(timeoutMs);
        byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : null;
        try (Socket socket = openSocket(url, timeout)) {
            writeRawHttpRequest(socket.getOutputStream(), method, url, headers, bodyBytes);
            return readRawHttpResponse(socket.getInputStream(), responseBodyLimitBytes);
        }
    }

    private static Socket openSocket(URL url, int timeoutMillis) throws IOException {
        String protocol = url.getProtocol() != null
                ? url.getProtocol().toLowerCase(Locale.ROOT) : "";
        int port = url.getPort() >= 0 ? url.getPort()
                : ("https".equals(protocol) ? 443 : 80);
        if ("https".equals(protocol)) {
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(url.getHost(), port), timeoutMillis);
            plainSocket.setSoTimeout(timeoutMillis);
            SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(
                    plainSocket,
                    url.getHost(),
                    port,
                    true);
            sslSocket.setSoTimeout(timeoutMillis);
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(sslParameters);
            sslSocket.startHandshake();
            return sslSocket;
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(url.getHost(), port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        return socket;
    }

    private static void validateHeaderName(String name) throws IOException {
        if (name == null || name.isEmpty()) {
            throw new IOException("Invalid HTTP header name");
        }
        for (int index = 0; index < name.length(); index++) {
            if (!isHeaderTokenCharacter(name.charAt(index))) {
                throw new IOException("Invalid HTTP header name");
            }
        }
    }

    private static void validateHeaderValue(String value) throws IOException {
        if (containsInvalidHeaderCharacters(value)) {
            throw new IOException("Invalid HTTP header value");
        }
    }

    private static boolean containsInvalidHeaderCharacters(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch < 0x20 || ch == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeaderTokenCharacter(char ch) {
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        if (ch >= 'A' && ch <= 'Z') {
            return true;
        }
        if (ch >= 'a' && ch <= 'z') {
            return true;
        }
        return switch (ch) {
            case '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|',
                    '~' -> true;
            default -> false;
        };
    }

    private static void writeRawHttpRequest(OutputStream rawOutput,
            String method,
            URL url,
            Map<String, String> headers,
            byte[] bodyBytes) throws IOException {
        BufferedOutputStream output = new BufferedOutputStream(rawOutput);
        String path = url.getFile();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<String, String>();
        requestHeaders.put("Host", hostHeaderValue(url));
        requestHeaders.put("Accept", DEFAULT_ACCEPT_HEADER);
        requestHeaders.put("User-Agent", DEFAULT_USER_AGENT);
        requestHeaders.put("Connection", "close");
        if (bodyBytes != null) {
            if (!containsHeader(headers, "Content-Type")) {
                requestHeaders.put("Content-Type", DEFAULT_TEXT_BODY_CONTENT_TYPE);
            }
            requestHeaders.put("Content-Length", Integer.toString(bodyBytes.length));
        }
        if (headers != null) {
            requestHeaders.putAll(headers);
        }

        output.write((method + " " + path + " HTTP/1.1\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
            output.write((header.getKey() + ": " + header.getValue() + "\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
        }
        output.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
        if (bodyBytes != null) {
            output.write(bodyBytes);
        }
        output.flush();
    }

    private static String hostHeaderValue(URL url) {
        if (url == null || url.getHost() == null) {
            return "";
        }
        int port = url.getPort();
        if (port < 0 || port == url.getDefaultPort()) {
            return url.getHost();
        }
        return url.getHost() + ":" + port;
    }

    private static String readRawHttpResponse(InputStream rawInput, int responseBodyLimitBytes)
            throws IOException {
        BufferedInputStream input = new BufferedInputStream(rawInput);
        String statusLine = readAsciiLine(input);
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IOException("HTTP response missing status line");
        }
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length < 2) {
            throw new IOException("Malformed HTTP status line: " + statusLine);
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException exception) {
            throw new IOException("Malformed HTTP status code: " + statusLine, exception);
        }

        LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
        String line;
        while ((line = readAsciiLine(input)) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0) {
                continue;
            }
            headers.put(line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT),
                    line.substring(colonIndex + 1).trim());
        }

        byte[] bodyBytes = readResponseBody(input, headers, responseBodyLimitBytes);
        String responseBody = new String(bodyBytes, StandardCharsets.UTF_8);
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode + ": " + responseBody);
        }
        return responseBody;
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) != -1) {
            if (current == '\r') {
                int next = input.read();
                if (next == '\n') {
                    return output.toString("ISO-8859-1");
                }
                if (next != -1) {
                    output.write(current);
                    output.write(next);
                }
                continue;
            }
            if (current == '\n') {
                return output.toString("ISO-8859-1");
            }
            output.write(current);
        }
        if (output.size() == 0) {
            return null;
        }
        return output.toString("ISO-8859-1");
    }

    private static byte[] readResponseBody(InputStream input, Map<String, String> headers,
            int responseBodyLimitBytes) throws IOException {
        String transferEncoding = headerValue(headers, "transfer-encoding");
        if (transferEncoding != null
                && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedBody(input, responseBodyLimitBytes);
        }
        String contentLength = headerValue(headers, "content-length");
        if (contentLength != null) {
            try {
                return readFixedLengthBody(input, Integer.parseInt(contentLength),
                        responseBodyLimitBytes);
            } catch (NumberFormatException exception) {
                throw new IOException("Malformed HTTP Content-Length: " + contentLength,
                        exception);
            }
        }
        return readUntilEof(input, responseBodyLimitBytes);
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty() || name == null) {
            return null;
        }
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    private static byte[] readFixedLengthBody(InputStream input, int contentLength,
            int responseBodyLimitBytes) throws IOException {
        if (contentLength < 0) {
            throw new IOException("HTTP Content-Length must not be negative");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(contentLength, responseBodyLimitBytes));
        byte[] buffer = new byte[4096];
        int remaining = contentLength;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("HTTP response ended before Content-Length bytes arrived");
            }
            writeLimited(output, buffer, read, responseBodyLimitBytes);
            remaining -= read;
        }
        return output.toByteArray();
    }

    private static byte[] readChunkedBody(InputStream input, int responseBodyLimitBytes)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readAsciiLine(input);
            if (sizeLine == null) {
                throw new IOException("HTTP chunked response ended unexpectedly");
            }
            int separatorIndex = sizeLine.indexOf(';');
            String rawSize = separatorIndex >= 0
                    ? sizeLine.substring(0, separatorIndex) : sizeLine;
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(rawSize.trim(), 16);
            } catch (NumberFormatException exception) {
                throw new IOException("Malformed HTTP chunk size: " + sizeLine, exception);
            }
            if (chunkSize == 0) {
                while (true) {
                    String trailer = readAsciiLine(input);
                    if (trailer == null || trailer.isEmpty()) {
                        return output.toByteArray();
                    }
                }
            }
            byte[] chunk = readFixedLengthBody(input, chunkSize, responseBodyLimitBytes);
            writeLimited(output, chunk, chunk.length, responseBodyLimitBytes);
            String lineBreak = readAsciiLine(input);
            if (lineBreak == null) {
                throw new IOException("HTTP chunked response missing chunk terminator");
            }
        }
    }

    private static byte[] readUntilEof(InputStream input, int responseBodyLimitBytes)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            writeLimited(output, buffer, read, responseBodyLimitBytes);
        }
        return output.toByteArray();
    }

    private static void writeLimited(ByteArrayOutputStream output, byte[] bytes, int count,
            int responseBodyLimitBytes) throws IOException {
        if (count <= 0) {
            return;
        }
        if (output.size() + count > responseBodyLimitBytes) {
            throw new IOException("HTTP response exceeded "
                    + responseBodyLimitBytes + " bytes");
        }
        output.write(bytes, 0, count);
    }

    private static String readResponseBody(HttpURLConnection connection, int statusCode,
            int responseBodyLimitBytes) throws IOException {
        InputStream inputStream = statusCode >= 400
                ? connection.getErrorStream() : connection.getInputStream();
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > responseBodyLimitBytes) {
                    throw new IOException("HTTP response exceeded "
                            + responseBodyLimitBytes + " bytes");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static int sanitizeResponseBodyLimitBytes(int responseBodyLimitBytes) {
        if (responseBodyLimitBytes < GboardTopRowSwipeSettings.MIN_RESPONSE_BODY_LIMIT_BYTES) {
            return GboardTopRowSwipeSettings.MIN_RESPONSE_BODY_LIMIT_BYTES;
        }
        return responseBodyLimitBytes;
    }

    public static final class NativeEvalResult {
        public final boolean success;
        final byte[] valueUtf8;
        final byte[] errorUtf8;
        public final boolean timedOut;

        NativeEvalResult(boolean success, byte[] valueUtf8, byte[] errorUtf8,
                boolean timedOut) {
            this.success = success;
            this.valueUtf8 = valueUtf8;
            this.errorUtf8 = errorUtf8;
            this.timedOut = timedOut;
        }

        public String valueString() {
            return valueUtf8 != null ? new String(valueUtf8, StandardCharsets.UTF_8) : null;
        }

        public String errorString() {
            return errorUtf8 != null ? new String(errorUtf8, StandardCharsets.UTF_8) : null;
        }
    }
}

