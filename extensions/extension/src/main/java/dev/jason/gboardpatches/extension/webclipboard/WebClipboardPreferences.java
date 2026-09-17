package dev.jason.gboardpatches.extension.webclipboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

public final class WebClipboardPreferences {
    public static final String PREF_FILE = "gboard_web_clipboard";
    public static final String PREF_KEY_ENABLED = "pref_web_clipboard_enabled";
    public static final String PREF_KEY_PORT = "pref_web_clipboard_port";
    public static final String PREF_KEY_RUNTIME_ACTIVE = "pref_web_clipboard_runtime_active";
    public static final String PREF_KEY_PAIRING_REQUIRED =
            "pref_web_clipboard_pairing_required";
    public static final String PREF_KEY_PAIRING_CODE = "pref_web_clipboard_pairing_code";
    public static final String PREF_KEY_LOOPBACK_INGRESS_TOKEN =
            "pref_web_clipboard_loopback_ingress_token";
    public static final String PREF_KEY_INJECT_SYSTEM_COLORS =
            "pref_web_clipboard_inject_system_colors";

    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_PORT = 8080;
    public static final boolean DEFAULT_RUNTIME_ACTIVE = false;
    public static final boolean DEFAULT_PAIRING_REQUIRED = true;
    public static final boolean DEFAULT_INJECT_SYSTEM_COLORS = true;
    public static final String DEFAULT_PAIRING_CODE = "0000";
    public static final String DEFAULT_LOOPBACK_INGRESS_TOKEN = "";

    private static final int LOOPBACK_INGRESS_TOKEN_BYTES = 32;
    private static final int MIN_LOOPBACK_INGRESS_TOKEN_LENGTH = 32;
    private static final SecureRandom PAIRING_CODE_RANDOM = new SecureRandom();
    private static final SecureRandom LOOPBACK_INGRESS_TOKEN_RANDOM = new SecureRandom();

    private WebClipboardPreferences() {
    }

    public static SharedPreferences preferences(Context context) {
        Context lookupContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        SharedPreferences preferences = lookupContext.getSharedPreferences(
                PREF_FILE,
                Context.MODE_PRIVATE);
        ensureDefaults(preferences);
        return preferences;
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_PORT)) {
            editor = ensureEditor(editor, preferences);
            editor.putInt(PREF_KEY_PORT, DEFAULT_PORT);
        }
        if (!preferences.contains(PREF_KEY_RUNTIME_ACTIVE)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_RUNTIME_ACTIVE, DEFAULT_RUNTIME_ACTIVE);
        }
        if (!preferences.contains(PREF_KEY_PAIRING_REQUIRED)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_PAIRING_REQUIRED, DEFAULT_PAIRING_REQUIRED);
        }
        if (!preferences.contains(PREF_KEY_INJECT_SYSTEM_COLORS)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_INJECT_SYSTEM_COLORS, DEFAULT_INJECT_SYSTEM_COLORS);
        }

        Object rawPairingCode = preferences.getAll().get(PREF_KEY_PAIRING_CODE);
        String pairingCode = getPairingCode(preferences);
        if (!(rawPairingCode instanceof String)
                && DEFAULT_PAIRING_REQUIRED
                && DEFAULT_PAIRING_CODE.equals(pairingCode)) {
            pairingCode = nextPairingCode();
        }
        if (!(rawPairingCode instanceof String) || !pairingCode.equals(rawPairingCode)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_PAIRING_CODE, pairingCode);
        }

        Object rawLoopbackToken = preferences.getAll().get(PREF_KEY_LOOPBACK_INGRESS_TOKEN);
        String loopbackIngressToken =
                rawLoopbackToken instanceof String token && isUsableLoopbackIngressToken(token)
                        ? token
                        : nextLoopbackIngressToken();
        if (!(rawLoopbackToken instanceof String)
                || !loopbackIngressToken.equals(rawLoopbackToken)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_LOOPBACK_INGRESS_TOKEN, loopbackIngressToken);
        }

        if (editor != null) {
            editor.commit();
        }
    }

    public static boolean isEnabled(SharedPreferences preferences) {
        ensureDefaults(preferences);
        return readEnabled(preferences);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        Boolean explicitEnabledState = readExplicitEnabledState(preferences);
        if (explicitEnabledState != null) {
            return explicitEnabledState;
        }
        try {
            return preferences.getBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
        } catch (Throwable ignored) {
            return DEFAULT_ENABLED;
        }
    }

    public static Boolean readExplicitEnabledState(SharedPreferences preferences) {
        if (preferences == null) {
            return null;
        }
        return readBooleanObject(preferences.getAll().get(PREF_KEY_ENABLED));
    }

    public static void setEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .commit();
    }

    public static int getPort(SharedPreferences preferences) {
        ensureDefaults(preferences);
        return readPort(preferences);
    }

    public static int readPort(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_PORT;
        }
        Object rawValue = preferences.getAll().get(PREF_KEY_PORT);
        return sanitizePort(readIntPreference(rawValue, DEFAULT_PORT));
    }

    public static void setPort(SharedPreferences preferences, int port) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putInt(PREF_KEY_PORT, sanitizePort(port)).commit();
    }

    public static boolean isRuntimeActive(SharedPreferences preferences) {
        ensureDefaults(preferences);
        return readRuntimeActive(preferences);
    }

    public static boolean readRuntimeActive(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_RUNTIME_ACTIVE;
        }
        Object rawValue = preferences.getAll().get(PREF_KEY_RUNTIME_ACTIVE);
        Boolean value = readBooleanObject(rawValue);
        return value != null ? value.booleanValue() : DEFAULT_RUNTIME_ACTIVE;
    }

    public static void setRuntimeActive(SharedPreferences preferences, boolean runtimeActive) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(PREF_KEY_RUNTIME_ACTIVE, runtimeActive).commit();
    }

    public static boolean isPairingRequired(SharedPreferences preferences) {
        ensureDefaults(preferences);
        if (preferences == null) {
            return DEFAULT_PAIRING_REQUIRED;
        }
        Object rawValue = preferences.getAll().get(PREF_KEY_PAIRING_REQUIRED);
        Boolean value = readBooleanObject(rawValue);
        return value != null ? value.booleanValue() : DEFAULT_PAIRING_REQUIRED;
    }

    public static void setPairingRequired(SharedPreferences preferences, boolean required) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(PREF_KEY_PAIRING_REQUIRED, required).commit();
    }

    public static boolean isInjectSystemColors(SharedPreferences preferences) {
        ensureDefaults(preferences);
        if (preferences == null) {
            return DEFAULT_INJECT_SYSTEM_COLORS;
        }
        Object rawValue = preferences.getAll().get(PREF_KEY_INJECT_SYSTEM_COLORS);
        Boolean value = readBooleanObject(rawValue);
        return value != null ? value.booleanValue() : DEFAULT_INJECT_SYSTEM_COLORS;
    }

    public static void setInjectSystemColors(SharedPreferences preferences, boolean inject) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putBoolean(PREF_KEY_INJECT_SYSTEM_COLORS, inject).commit();
    }

    public static String getPairingCode(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_PAIRING_CODE;
        }
        Object rawValue = preferences.getAll().get(PREF_KEY_PAIRING_CODE);
        String rawCode = rawValue instanceof String ? (String) rawValue : DEFAULT_PAIRING_CODE;
        return sanitizePairingCode(rawCode);
    }

    public static void setPairingCode(SharedPreferences preferences, String code) {
        if (preferences == null) {
            return;
        }
        preferences.edit().putString(PREF_KEY_PAIRING_CODE, sanitizePairingCode(code)).commit();
    }

    public static String regeneratePairingCode(SharedPreferences preferences) {
        String nextCode = nextPairingCode();
        setPairingCode(preferences, nextCode);
        return nextCode;
    }

    public static String getLoopbackIngressToken(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_LOOPBACK_INGRESS_TOKEN;
        }
        Object rawToken = preferences.getAll().get(PREF_KEY_LOOPBACK_INGRESS_TOKEN);
        if (rawToken instanceof String token && isUsableLoopbackIngressToken(token)) {
            return token;
        }
        return DEFAULT_LOOPBACK_INGRESS_TOKEN;
    }

    public static String readCustomCss(Context context) {
        File dir = context.getExternalFilesDir("web-clipboard");
        if (dir == null) {
            return "";
        }
        File file = new File(dir, "custom.css");
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (Throwable ignored) {
            return "";
        }
        return content.toString().trim();
    }

    public static void writeCustomCss(Context context, String content) {
        File dir = context.getExternalFilesDir("web-clipboard");
        if (dir == null) {
            return;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        File file = new File(dir, "custom.css");
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(text);
        } catch (Throwable ignored) {
            // Best effort.
        }
    }

    public static void setLoopbackIngressToken(SharedPreferences preferences, String token) {
        if (preferences == null) {
            return;
        }
        String sanitized = isUsableLoopbackIngressToken(token)
                ? token
                : nextLoopbackIngressToken();
        preferences.edit().putString(PREF_KEY_LOOPBACK_INGRESS_TOKEN, sanitized).commit();
    }

    public static int sanitizePort(int port) {
        return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
    }

    public static String sanitizePairingCode(String code) {
        if (code == null) {
            return DEFAULT_PAIRING_CODE;
        }
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < code.length(); index++) {
            char current = code.charAt(index);
            if (current >= '0' && current <= '9') {
                digits.append(current);
            }
        }
        if (digits.length() == 0) {
            return DEFAULT_PAIRING_CODE;
        }
        while (digits.length() < 4) {
            digits.insert(0, '0');
        }
        if (digits.length() > 4) {
            return digits.substring(digits.length() - 4);
        }
        return digits.toString();
    }

    private static SharedPreferences.Editor ensureEditor(SharedPreferences.Editor editor,
            SharedPreferences preferences) {
        return editor != null ? editor : preferences.edit();
    }

    private static Boolean readBooleanObject(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (rawValue instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return null;
    }

    private static int readIntPreference(Object rawValue, int defaultValue) {
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        if (rawValue instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String nextPairingCode() {
        return String.format(Locale.US, "%04d", PAIRING_CODE_RANDOM.nextInt(10_000));
    }

    private static String nextLoopbackIngressToken() {
        byte[] bytes = new byte[LOOPBACK_INGRESS_TOKEN_BYTES];
        LOOPBACK_INGRESS_TOKEN_RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(LOOPBACK_INGRESS_TOKEN_BYTES * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0xF, 16));
            builder.append(Character.forDigit(current & 0xF, 16));
        }
        return builder.toString();
    }

    private static boolean isUsableLoopbackIngressToken(String token) {
        if (token == null || token.length() < MIN_LOOPBACK_INGRESS_TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            char current = token.charAt(index);
            if (current <= ' ' || current == '\u007f') {
                return false;
            }
        }
        return true;
    }
}
