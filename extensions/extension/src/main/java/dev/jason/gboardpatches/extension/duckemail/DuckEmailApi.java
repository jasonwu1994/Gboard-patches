package dev.jason.gboardpatches.extension.duckemail;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Direct DDG Email Protection client. Never logs URLs, passphrases or credentials. */
public final class DuckEmailApi {
    private static final String BASE = "https://quack.duckduckgo.com/api/";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Version/4.0 Chrome/109.0.5414.86 "
            + "Mobile DuckDuckGo/5 Safari/537.36";

    interface Transport {
        Response request(String method, String url, String token) throws Exception;
    }
    static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }
    static final class Account {
        private final String username, token;
        Account(String username, String token) { this.username = username; this.token = token; }
        String username() { return username; }
        String token() { return token; }
    }
    static final class ApiException extends IOException {
        final int status;
        ApiException(int status) { super("DuckDuckGo request failed"); this.status = status; }
    }

    private final Transport transport;
    public DuckEmailApi() { this(DuckEmailApi::request); }
    DuckEmailApi(Transport transport) { this.transport = transport; }

    static String username(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("@duck.com")) value = value.substring(0, value.length() - 9);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Enter a Duck address");
        }
        return value;
    }

    static String passphrase(String input) {
        String value = input == null ? "" : input.trim().replace('+', ' ')
                .replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException("Enter the emailed passphrase");
        }
        return value;
    }

    void sendCode(String user) throws Exception {
        call("GET", "auth/loginlink?user=" + encode(username(user)), null);
    }

    Account signIn(String user, String code) throws Exception {
        String expected = username(user);
        JSONObject login = new JSONObject(call("GET", "auth/login?otp="
                + encode(passphrase(code)) + "&user=" + encode(expected), null));
        String temporary = required(login, "token");
        JSONObject profile = new JSONObject(call("GET", "email/dashboard", temporary))
                .getJSONObject("user");
        String actual = username(required(profile, "username"));
        if (!expected.equals(actual)) throw new IOException("Duck account did not match");
        return new Account(actual, required(profile, "access_token"));
    }

    String generate(String token) throws Exception {
        String address = required(new JSONObject(call("POST", "email/addresses", token)),
                "address").toLowerCase(Locale.ROOT);
        // DDG normally returns the local part, but accept a full Duck address too.
        return username(address) + "@duck.com";
    }

    private String call(String method, String path, String token) throws Exception {
        Response response = transport.request(method, BASE + path, token);
        if (response.status < 200 || response.status >= 300) {
            throw new ApiException(response.status);
        }
        return response.body;
    }

    private static String required(JSONObject object, String key) throws Exception {
        String value = object.getString(key).trim();
        if (value.isEmpty() || value.equals("null")) throw new IOException("Missing DDG value");
        return value;
    }

    private static String encode(String text) {
        try { return URLEncoder.encode(text, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    private static Response request(String method, String url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(0);
                connection.getOutputStream().close();
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return new Response(status, "");
            try (InputStream stream = connection.getInputStream();
                    ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int size;
                while ((size = stream.read(buffer)) != -1) {
                    if (body.size() + size > 65536) throw new IOException("DDG response too large");
                    body.write(buffer, 0, size);
                }
                return new Response(status, new String(body.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }
}
