package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.Objects;

public final class LanFtpServerConfig {
    public static final int DEFAULT_CONTROL_PORT = 2121;
    public static final int DEFAULT_PASSIVE_PORT_START = 50000;
    public static final int DEFAULT_PASSIVE_PORT_END = 50009;
    public static final int DEFAULT_MAX_SESSIONS = 4;
    public static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 5 * 60;

    private final int controlPort;
    private final int passivePortStart;
    private final int passivePortEnd;
    private final int maxSessions;
    private final int idleTimeoutSeconds;
    private final boolean readOnly;
    private final boolean allowAnonymous;
    private final String username;
    private final String password;
    private final String rootTreeUri;

    public LanFtpServerConfig(int controlPort, int passivePortStart, int passivePortEnd,
            int maxSessions, int idleTimeoutSeconds, boolean readOnly, boolean allowAnonymous,
            String username, String password, String rootTreeUri) {
        LanFtpPortPolicy.requireValid(controlPort, passivePortStart, passivePortEnd);
        if (maxSessions < 1 || maxSessions > 16) {
            throw new IllegalArgumentException("maxSessions must be between 1 and 16");
        }
        if (idleTimeoutSeconds < 30 || idleTimeoutSeconds > 6 * 60 * 60) {
            throw new IllegalArgumentException("idleTimeoutSeconds is outside the safe range");
        }
        username = requireText(username, "username");
        if (!LanFtpCredentialPolicy.USERNAME.equals(username)) {
            throw new IllegalArgumentException("username must be gboard");
        }
        password = requireText(password, "password");
        if (!LanFtpCredentialPolicy.isValidPassword(password)) {
            throw new IllegalArgumentException(
                    "password must contain exactly six lowercase letters");
        }
        rootTreeUri = requireText(rootTreeUri, "rootTreeUri");
        this.controlPort = controlPort;
        this.passivePortStart = passivePortStart;
        this.passivePortEnd = passivePortEnd;
        this.maxSessions = maxSessions;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.readOnly = readOnly;
        this.allowAnonymous = allowAnonymous;
        this.username = username;
        this.password = password;
        this.rootTreeUri = rootTreeUri;
    }

    public LanFtpServerConfig(int controlPort, int passivePortStart, int passivePortEnd,
            int maxSessions, int idleTimeoutSeconds, boolean readOnly, String username,
            String password, String rootTreeUri) {
        this(controlPort, passivePortStart, passivePortEnd, maxSessions, idleTimeoutSeconds,
                readOnly, false, username, password, rootTreeUri);
    }

    public static LanFtpServerConfig defaults(String username, String password,
            String rootTreeUri) {
        return new LanFtpServerConfig(
                DEFAULT_CONTROL_PORT,
                DEFAULT_PASSIVE_PORT_START,
                DEFAULT_PASSIVE_PORT_END,
                DEFAULT_MAX_SESSIONS,
                DEFAULT_IDLE_TIMEOUT_SECONDS,
                false,
                true,
                username,
                password,
                rootTreeUri);
    }

    public int controlPort() {
        return controlPort;
    }

    public int passivePortStart() {
        return passivePortStart;
    }

    public int passivePortEnd() {
        return passivePortEnd;
    }

    public int maxSessions() {
        return maxSessions;
    }

    public int idleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean allowAnonymous() {
        return allowAnonymous;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String rootTreeUri() {
        return rootTreeUri;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpServerConfig that)) {
            return false;
        }
        return controlPort == that.controlPort
                && passivePortStart == that.passivePortStart
                && passivePortEnd == that.passivePortEnd
                && maxSessions == that.maxSessions
                && idleTimeoutSeconds == that.idleTimeoutSeconds
                && readOnly == that.readOnly
                && allowAnonymous == that.allowAnonymous
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password)
                && Objects.equals(rootTreeUri, that.rootTreeUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(controlPort, passivePortStart, passivePortEnd, maxSessions,
                idleTimeoutSeconds, readOnly, allowAnonymous, username, password, rootTreeUri);
    }

    @Override
    public String toString() {
        return "LanFtpServerConfig[controlPort=" + controlPort + ", passivePortStart="
                + passivePortStart + ", passivePortEnd=" + passivePortEnd
                + ", maxSessions=" + maxSessions + ", idleTimeoutSeconds="
                + idleTimeoutSeconds + ", readOnly=" + readOnly + ", allowAnonymous="
                + allowAnonymous + ", username=" + username + ", password=<redacted>"
                + ", rootTreeUri=" + rootTreeUri + "]";
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " is empty");
        }
        return normalized;
    }
}
