package dev.jason.gboardpatches.extension.lanftp.config;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpServerConfigSnapshot;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPortPolicy;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpCredentialPolicy;

/** Persistent user settings and conservative implementation defaults for the LAN FTP server. */
public final class LanFtpPreferences {
    public static final String PREF_FILE = "lan_ftp_settings";
    public static final String PREF_KEY_ENABLED = "pref_lan_ftp_enabled";
    public static final String PREF_KEY_CONTROL_PORT = "pref_lan_ftp_control_port";
    public static final String PREF_KEY_PASSIVE_PORT_START = "pref_lan_ftp_passive_port_start";
    public static final String PREF_KEY_PASSIVE_PORT_END = "pref_lan_ftp_passive_port_end";
    public static final String PREF_KEY_ROOT_TREE_URI = "pref_lan_ftp_root_tree_uri";
    public static final String PREF_KEY_READ_ONLY = "pref_lan_ftp_read_only";
    public static final String PREF_KEY_PASSWORD = "pref_lan_ftp_password";
    public static final String PREF_KEY_PASSWORD_REVISION = "pref_lan_ftp_password_revision";
    private static final String REMOVED_PREF_KEY_CREDENTIAL_GENERATION =
            "pref_lan_ftp_credential_generation";
    private static final String REMOVED_PREF_KEY_METADATA_AUTH_KEY =
            "pref_lan_ftp_metadata_auth_key";
    public static final String PREF_KEY_ALLOW_ANONYMOUS = "pref_lan_ftp_allow_anonymous";
    private static final String PREF_KEY_DEDICATED_STORAGE_READY =
            "pref_lan_ftp_dedicated_storage_ready";
    private static final Set<String> FTP_PREFERENCE_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    PREF_KEY_ENABLED,
                    PREF_KEY_CONTROL_PORT,
                    PREF_KEY_PASSIVE_PORT_START,
                    PREF_KEY_PASSIVE_PORT_END,
                    PREF_KEY_ROOT_TREE_URI,
                    PREF_KEY_READ_ONLY,
                    PREF_KEY_PASSWORD,
                    PREF_KEY_PASSWORD_REVISION,
                    PREF_KEY_ALLOW_ANONYMOUS)));
    private static final Set<String> LEGACY_FTP_PREFERENCE_KEYS;

    static {
        LinkedHashSet<String> keys = new LinkedHashSet<>(FTP_PREFERENCE_KEYS);
        keys.add(REMOVED_PREF_KEY_CREDENTIAL_GENERATION);
        keys.add(REMOVED_PREF_KEY_METADATA_AUTH_KEY);
        LEGACY_FTP_PREFERENCE_KEYS = Collections.unmodifiableSet(keys);
    }

    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_CONTROL_PORT = 2121;
    public static final String SHARED_STORAGE_ROOT_URI = "file:///sdcard";
    public static final String DEFAULT_ROOT_TREE_URI = SHARED_STORAGE_ROOT_URI;
    public static final boolean DEFAULT_READ_ONLY = false;
    public static final boolean DEFAULT_ALLOW_ANONYMOUS = true;
    public static final String DEFAULT_USERNAME = LanFtpCredentialPolicy.USERNAME;
    public static final int DEFAULT_PASSIVE_PORT_START = 50000;
    public static final int DEFAULT_PASSIVE_PORT_END = 50009;
    public static final int DEFAULT_MAX_SESSIONS = 4;
    public static final int DEFAULT_CONTROL_SESSION_IDLE_TIMEOUT_SECONDS = 5 * 60;
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 15L * 60_000L;

    private LanFtpPreferences() {
    }

    public static SharedPreferences preferences(Context context) {
        SharedPreferences dedicatedPreferences = null;
        try {
            Context appContext = applicationContext(context);
            if (appContext == null) {
                return null;
            }
            SharedPreferences legacyPreferences = appContext.getSharedPreferences(
                    GboardPatchesSettings.PREF_FILE, Context.MODE_PRIVATE);
            dedicatedPreferences = appContext.getSharedPreferences(
                    PREF_FILE, Context.MODE_PRIVATE);
            migrateLegacyPreferences(legacyPreferences, dedicatedPreferences);
        } catch (Throwable ignored) {
            // Dedicated storage remains the only writable credential destination.
        }
        ensureDefaults(dedicatedPreferences);
        return dedicatedPreferences;
    }

    static Set<String> ftpPreferenceKeys() {
        return FTP_PREFERENCE_KEYS;
    }

    private static void migrateLegacyPreferences(
            SharedPreferences legacyPreferences, SharedPreferences dedicatedPreferences) {
        if (dedicatedPreferences == null) {
            return;
        }
        if (legacyPreferences == null || legacyPreferences == dedicatedPreferences) {
            return;
        }
        try {
            if (!dedicatedPreferences.getBoolean(PREF_KEY_DEDICATED_STORAGE_READY, false)) {
                Map<String, ?> legacyValues = legacyPreferences.getAll();
                SharedPreferences.Editor target = dedicatedPreferences.edit();
                if (legacyValues != null) {
                    for (String key : FTP_PREFERENCE_KEYS) {
                        if (legacyValues.containsKey(key)) {
                            copyPreferenceValue(target, key, legacyValues.get(key));
                        }
                    }
                }
                target.putBoolean(PREF_KEY_DEDICATED_STORAGE_READY, true);
                if (!target.commit()) {
                    return;
                }
            }
            removeLegacyFtpPreferences(legacyPreferences);
        } catch (Throwable ignored) {
            // Never fall back to writing credentials into the backup-eligible legacy store.
        }
    }

    private static boolean removeLegacyFtpPreferences(SharedPreferences legacyPreferences) {
        try {
            SharedPreferences.Editor editor = legacyPreferences.edit();
            for (String key : LEGACY_FTP_PREFERENCE_KEYS) {
                editor.remove(key);
            }
            return editor.commit();
        } catch (Throwable ignored) {
            // The committed dedicated copy remains authoritative; cleanup retries next access.
            return false;
        }
    }

    private static void copyPreferenceValue(
            SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean booleanValue) {
            editor.putBoolean(key, booleanValue);
        } else if (value instanceof Number numberValue) {
            editor.putInt(key, numberValue.intValue());
        } else if (value instanceof String stringValue) {
            editor.putString(key, stringValue);
        }
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            return null;
        }
        Context appContext = context.getApplicationContext();
        return appContext == null ? context : appContext;
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        try {
            Map<String, ?> values = preferences.getAll();
            SharedPreferences.Editor editor = preferences.edit();
            boolean changed = false;
            if (values.containsKey(REMOVED_PREF_KEY_CREDENTIAL_GENERATION)) {
                editor.remove(REMOVED_PREF_KEY_CREDENTIAL_GENERATION);
                changed = true;
            }
            if (values.containsKey(REMOVED_PREF_KEY_METADATA_AUTH_KEY)) {
                editor.remove(REMOVED_PREF_KEY_METADATA_AUTH_KEY);
                changed = true;
            }
            if (!(values.get(PREF_KEY_ENABLED) instanceof Boolean)) {
                editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
                changed = true;
            }
            if (!(values.get(PREF_KEY_CONTROL_PORT) instanceof Number)) {
                editor.putInt(PREF_KEY_CONTROL_PORT, DEFAULT_CONTROL_PORT);
                changed = true;
            }
            if (!(values.get(PREF_KEY_PASSIVE_PORT_START) instanceof Number)) {
                editor.putInt(PREF_KEY_PASSIVE_PORT_START, DEFAULT_PASSIVE_PORT_START);
                changed = true;
            }
            if (!(values.get(PREF_KEY_PASSIVE_PORT_END) instanceof Number)) {
                editor.putInt(PREF_KEY_PASSIVE_PORT_END, DEFAULT_PASSIVE_PORT_END);
                changed = true;
            }
            Object rootValue = values.get(PREF_KEY_ROOT_TREE_URI);
            if (!(rootValue instanceof String) || ((String) rootValue).trim().isEmpty()) {
                editor.putString(PREF_KEY_ROOT_TREE_URI, DEFAULT_ROOT_TREE_URI);
                changed = true;
            }
            if (!(values.get(PREF_KEY_READ_ONLY) instanceof Boolean)) {
                editor.putBoolean(PREF_KEY_READ_ONLY, DEFAULT_READ_ONLY);
                changed = true;
            }
            if (!(values.get(PREF_KEY_ALLOW_ANONYMOUS) instanceof Boolean)) {
                editor.putBoolean(PREF_KEY_ALLOW_ANONYMOUS, DEFAULT_ALLOW_ANONYMOUS);
                changed = true;
            }
            boolean passwordInvalid = !LanFtpCredentialPolicy.isValidPassword(
                    asString(values.get(PREF_KEY_PASSWORD)));
            if (passwordInvalid) {
                editor.putString(PREF_KEY_PASSWORD, nextPassword());
                editor.putLong(PREF_KEY_PASSWORD_REVISION,
                        nextRevision(asLong(values.get(PREF_KEY_PASSWORD_REVISION), 0L)));
                changed = true;
            } else if (!(values.get(PREF_KEY_PASSWORD_REVISION) instanceof Number)) {
                editor.putLong(PREF_KEY_PASSWORD_REVISION, 1L);
                changed = true;
            }
            if (changed) {
                editor.commit();
            }
        } catch (Throwable ignored) {
            // Callers read safe defaults if persistence is unavailable.
        }
    }

    public static Snapshot read(SharedPreferences preferences) {
        ensureDefaults(preferences);
        if (preferences == null) {
            return fallbackSnapshot();
        }
        try {
            Map<String, ?> values = preferences.getAll();
            int port = sanitizePort(asInt(values.get(PREF_KEY_CONTROL_PORT), DEFAULT_CONTROL_PORT));
            int passiveStart = asInt(
                    values.get(PREF_KEY_PASSIVE_PORT_START), DEFAULT_PASSIVE_PORT_START);
            int passiveEnd = asInt(
                    values.get(PREF_KEY_PASSIVE_PORT_END), DEFAULT_PASSIVE_PORT_END);
            if (!LanFtpPortPolicy.isValidRange(port, passiveStart, passiveEnd)) {
                passiveStart = DEFAULT_PASSIVE_PORT_START;
                passiveEnd = DEFAULT_PASSIVE_PORT_END;
                if (!LanFtpPortPolicy.isValidRange(port, passiveStart, passiveEnd)) {
                    port = DEFAULT_CONTROL_PORT;
                }
            }
            String persistedRoot = asString(values.get(PREF_KEY_ROOT_TREE_URI));
            boolean validRoot = isSupportedRoot(persistedRoot);
            String rootTreeUri = sanitizeRootTreeUri(persistedRoot);
            String username = DEFAULT_USERNAME;
            String password = sanitizePassword(asString(values.get(PREF_KEY_PASSWORD)));
            boolean allowAnonymous = asBoolean(
                    values.get(PREF_KEY_ALLOW_ANONYMOUS), DEFAULT_ALLOW_ANONYMOUS);
            boolean configured = validRoot
                    && !password.isEmpty()
                    && LanFtpPortPolicy.isValidPort(port);
            boolean enabled = configured
                    && asBoolean(values.get(PREF_KEY_ENABLED), DEFAULT_ENABLED);
            boolean readOnly = !validRoot
                    || asBoolean(values.get(PREF_KEY_READ_ONLY), DEFAULT_READ_ONLY);
            return new Snapshot(
                    enabled,
                    port,
                    rootTreeUri,
                    readOnly,
                    username,
                    password,
                    Math.max(1L, asLong(values.get(PREF_KEY_PASSWORD_REVISION), 1L)),
                    allowAnonymous,
                    passiveStart,
                    passiveEnd,
                    DEFAULT_MAX_SESSIONS,
                    DEFAULT_IDLE_TIMEOUT_MS);
        } catch (Throwable ignored) {
            return fallbackSnapshot();
        }
    }

    public static boolean setEnabled(SharedPreferences preferences, boolean enabled) {
        Snapshot snapshot = read(preferences);
        boolean safeEnabled = enabled && !snapshot.rootTreeUri.isEmpty()
                && LanFtpCredentialPolicy.isValidPassword(snapshot.password);
        return commitBoolean(preferences, PREF_KEY_ENABLED, safeEnabled);
    }

    public static boolean write(SharedPreferences preferences,
            LanFtpServerConfigSnapshot requested) {
        if (preferences == null || requested == null
                || !isSupportedRoot(requested.rootTreeUri())
                || !LanFtpCredentialPolicy.isValidPassword(requested.password())
                || !LanFtpPortPolicy.isValidRange(requested.controlPort(),
                        requested.passivePortStart(), requested.passivePortEnd())) {
            return false;
        }
        Snapshot current = read(preferences);
        if (!current.password.equals(requested.password())
                || current.passwordRevision != requested.passwordRevision()) {
            return false;
        }
        boolean safeEnabled = requested.enabled() && !requested.rootTreeUri().isEmpty();
        try {
            return preferences.edit()
                    .putBoolean(PREF_KEY_ENABLED, safeEnabled)
                    .putInt(PREF_KEY_CONTROL_PORT, requested.controlPort())
                    .putInt(PREF_KEY_PASSIVE_PORT_START, requested.passivePortStart())
                    .putInt(PREF_KEY_PASSIVE_PORT_END, requested.passivePortEnd())
                    .putString(PREF_KEY_ROOT_TREE_URI,
                            sanitizeRootTreeUri(requested.rootTreeUri()))
                    .putBoolean(PREF_KEY_READ_ONLY, requested.readOnly())
                    .putBoolean(PREF_KEY_ALLOW_ANONYMOUS, requested.allowAnonymous())
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setControlPort(SharedPreferences preferences, int port) {
        Snapshot snapshot = read(preferences);
        return LanFtpPortPolicy.isValidRange(
                port, snapshot.passivePortStart, snapshot.passivePortEnd)
                && commitInt(preferences, PREF_KEY_CONTROL_PORT, port);
    }

    public static boolean setPassivePortRange(SharedPreferences preferences, int start, int end) {
        Snapshot snapshot = read(preferences);
        if (!LanFtpPortPolicy.isValidRange(snapshot.controlPort, start, end)) {
            return false;
        }
        try {
            return preferences != null && preferences.edit()
                    .putInt(PREF_KEY_PASSIVE_PORT_START, start)
                    .putInt(PREF_KEY_PASSIVE_PORT_END, end)
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setRootTreeUri(SharedPreferences preferences, String uri) {
        if (!isSupportedRoot(uri)) {
            return false;
        }
        String safeUri = sanitizeRootTreeUri(uri);
        try {
            return preferences != null && preferences.edit()
                    .putString(PREF_KEY_ROOT_TREE_URI, safeUri)
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean setReadOnly(SharedPreferences preferences, boolean readOnly) {
        return commitBoolean(preferences, PREF_KEY_READ_ONLY, readOnly);
    }

    public static boolean setAllowAnonymous(SharedPreferences preferences,
            boolean allowAnonymous) {
        return commitBoolean(preferences, PREF_KEY_ALLOW_ANONYMOUS, allowAnonymous);
    }

    public static boolean setPassword(SharedPreferences preferences, String password) {
        String safePassword = LanFtpCredentialPolicy.normalizePassword(password);
        if (safePassword.isEmpty()) {
            return false;
        }
        try {
            Snapshot current = read(preferences);
            if (safePassword.equals(current.password)) {
                return true;
            }
            return preferences != null && preferences.edit()
                    .putString(PREF_KEY_PASSWORD, safePassword)
                    .putLong(PREF_KEY_PASSWORD_REVISION,
                            nextRevision(current.passwordRevision))
                    .commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String regeneratePassword(SharedPreferences preferences) {
        String previous = read(preferences).password;
        String password;
        do {
            password = LanFtpCredentialPolicy.newPassword();
        } while (password.equals(previous));
        try {
            if (preferences != null
                    && preferences.edit()
                    .putString(PREF_KEY_PASSWORD, password)
                    .putLong(PREF_KEY_PASSWORD_REVISION,
                            nextRevision(read(preferences).passwordRevision))
                    .commit()) {
                return password;
            }
        } catch (Throwable ignored) {
            // Empty indicates that the credential was not persisted.
        }
        return "";
    }

    public static int sanitizePort(int port) {
        return LanFtpPortPolicy.isValidPort(port) ? port : DEFAULT_CONTROL_PORT;
    }

    public static String sanitizeRootTreeUri(String value) {
        if (value == null) {
            return DEFAULT_ROOT_TREE_URI;
        }
        String trimmed = value.trim();
        if (SHARED_STORAGE_ROOT_URI.equals(trimmed)) {
            return SHARED_STORAGE_ROOT_URI;
        }
        return trimmed.startsWith("content://") ? trimmed : DEFAULT_ROOT_TREE_URI;
    }

    public static boolean isSharedStorageRoot(String value) {
        return SHARED_STORAGE_ROOT_URI.equals(value == null ? "" : value.trim());
    }

    public static boolean isSupportedRoot(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return SHARED_STORAGE_ROOT_URI.equals(trimmed) || trimmed.startsWith("content://");
    }

    private static String sanitizePassword(String value) {
        return LanFtpCredentialPolicy.normalizePassword(value);
    }

    private static String nextPassword() {
        return LanFtpCredentialPolicy.newPassword();
    }

    private static boolean commitBoolean(SharedPreferences preferences, String key,
            boolean value) {
        try {
            return preferences != null && preferences.edit().putBoolean(key, value).commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean commitInt(SharedPreferences preferences, String key, int value) {
        try {
            return preferences != null && preferences.edit().putInt(key, value).commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean asBoolean(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static long asLong(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static long nextRevision(long revision) {
        return revision == Long.MAX_VALUE ? 1L : Math.max(1L, revision + 1L);
    }

    private static Snapshot fallbackSnapshot() {
        return new Snapshot(
                DEFAULT_ENABLED,
                DEFAULT_CONTROL_PORT,
                DEFAULT_ROOT_TREE_URI,
                DEFAULT_READ_ONLY,
                DEFAULT_USERNAME,
                "",
                0L,
                DEFAULT_ALLOW_ANONYMOUS,
                DEFAULT_PASSIVE_PORT_START,
                DEFAULT_PASSIVE_PORT_END,
                DEFAULT_MAX_SESSIONS,
                DEFAULT_IDLE_TIMEOUT_MS);
    }

    public static final class Snapshot {
        public final boolean enabled;
        public final int controlPort;
        public final String rootTreeUri;
        public final boolean readOnly;
        public final String username;
        public final String password;
        public final long passwordRevision;
        public final boolean allowAnonymous;
        public final int passivePortStart;
        public final int passivePortEnd;
        public final int maxSessions;
        public final long idleTimeoutMs;

        public Snapshot(boolean enabled, int controlPort, String rootTreeUri,
                boolean readOnly, String username, String password,
                int passivePortStart, int passivePortEnd, int maxSessions,
                long idleTimeoutMs) {
            this(enabled, controlPort, rootTreeUri, readOnly, username, password,
                    1L,
                    DEFAULT_ALLOW_ANONYMOUS, passivePortStart, passivePortEnd,
                    maxSessions, idleTimeoutMs);
        }

        public Snapshot(boolean enabled, int controlPort, String rootTreeUri,
                boolean readOnly, String username, String password, boolean allowAnonymous,
                int passivePortStart, int passivePortEnd, int maxSessions,
                long idleTimeoutMs) {
            this(enabled, controlPort, rootTreeUri, readOnly, username, password, 1L,
                    allowAnonymous,
                    passivePortStart, passivePortEnd, maxSessions, idleTimeoutMs);
        }

        public Snapshot(boolean enabled, int controlPort, String rootTreeUri,
                boolean readOnly, String username, String password, long passwordRevision,
                boolean allowAnonymous, int passivePortStart, int passivePortEnd, int maxSessions,
                long idleTimeoutMs) {
            this.enabled = enabled;
            this.controlPort = controlPort;
            this.rootTreeUri = rootTreeUri == null ? "" : rootTreeUri;
            this.readOnly = readOnly;
            this.username = username == null ? DEFAULT_USERNAME : username;
            this.password = password == null ? "" : password;
            this.passwordRevision = Math.max(0L, passwordRevision);
            this.allowAnonymous = allowAnonymous;
            this.passivePortStart = passivePortStart;
            this.passivePortEnd = passivePortEnd;
            this.maxSessions = maxSessions;
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public LanFtpServerConfigSnapshot toConfigSnapshot() {
            return new LanFtpServerConfigSnapshot(enabled, controlPort, rootTreeUri, readOnly,
                    username, password, passwordRevision, allowAnonymous, passivePortStart,
                    passivePortEnd, maxSessions, idleTimeoutMs);
        }
    }
}
