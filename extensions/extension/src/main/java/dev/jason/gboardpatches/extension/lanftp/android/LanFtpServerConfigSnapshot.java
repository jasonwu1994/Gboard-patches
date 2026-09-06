package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.Objects;

/** Immutable configuration value crossing the LAN FTP controller seam. */
public final class LanFtpServerConfigSnapshot {
    public static final String SHARED_STORAGE_ROOT_URI = "file:///sdcard";

    private final boolean enabled;
    private final int controlPort;
    private final String rootTreeUri;
    private final boolean readOnly;
    private final String username;
    private final String password;
    private final long passwordRevision;
    private final boolean allowAnonymous;
    private final int passivePortStart;
    private final int passivePortEnd;
    private final int maxSessions;
    private final long idleTimeoutMs;

    public LanFtpServerConfigSnapshot(boolean enabled, int controlPort, String rootTreeUri,
            boolean readOnly, String username, String password, long passwordRevision,
            boolean allowAnonymous, int passivePortStart, int passivePortEnd, int maxSessions,
            long idleTimeoutMs) {
        this.enabled = enabled;
        this.controlPort = controlPort;
        this.rootTreeUri = rootTreeUri == null ? "" : rootTreeUri;
        this.readOnly = readOnly;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.passwordRevision = Math.max(0L, passwordRevision);
        this.allowAnonymous = allowAnonymous;
        this.passivePortStart = passivePortStart;
        this.passivePortEnd = passivePortEnd;
        this.maxSessions = maxSessions;
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public boolean enabled() {
        return enabled;
    }

    public int controlPort() {
        return controlPort;
    }

    public String rootTreeUri() {
        return rootTreeUri;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public long passwordRevision() {
        return passwordRevision;
    }

    public boolean allowAnonymous() {
        return allowAnonymous;
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

    public long idleTimeoutMs() {
        return idleTimeoutMs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpServerConfigSnapshot that)) {
            return false;
        }
        return enabled == that.enabled
                && controlPort == that.controlPort
                && readOnly == that.readOnly
                && passwordRevision == that.passwordRevision
                && allowAnonymous == that.allowAnonymous
                && passivePortStart == that.passivePortStart
                && passivePortEnd == that.passivePortEnd
                && maxSessions == that.maxSessions
                && idleTimeoutMs == that.idleTimeoutMs
                && Objects.equals(rootTreeUri, that.rootTreeUri)
                && Objects.equals(username, that.username)
                && Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, controlPort, rootTreeUri, readOnly, username, password,
                passwordRevision, allowAnonymous, passivePortStart, passivePortEnd,
                maxSessions, idleTimeoutMs);
    }

    @Override
    public String toString() {
        return "LanFtpServerConfigSnapshot[enabled=" + enabled + ", controlPort="
                + controlPort + ", rootTreeUri=" + rootTreeUri + ", readOnly=" + readOnly
                + ", username=" + username + ", password=<redacted>"
                + ", passwordRevision=" + passwordRevision + ", allowAnonymous="
                + allowAnonymous + ", passivePortStart=" + passivePortStart
                + ", passivePortEnd=" + passivePortEnd + ", maxSessions=" + maxSessions
                + ", idleTimeoutMs=" + idleTimeoutMs + "]";
    }

    public LanFtpServerConfigSnapshot withEnabled(boolean value) {
        return copy(value, controlPort, rootTreeUri, readOnly, password, passwordRevision,
                allowAnonymous, passivePortStart, passivePortEnd);
    }

    public LanFtpServerConfigSnapshot withControlPort(int value) {
        return copy(enabled, value, rootTreeUri, readOnly, password, passwordRevision,
                allowAnonymous, passivePortStart, passivePortEnd);
    }

    public LanFtpServerConfigSnapshot withPassivePortRange(int start, int end) {
        return copy(enabled, controlPort, rootTreeUri, readOnly, password, passwordRevision,
                allowAnonymous, start, end);
    }

    public LanFtpServerConfigSnapshot withRootTreeUri(String value) {
        return copy(enabled, controlPort, value, readOnly, password, passwordRevision,
                allowAnonymous, passivePortStart, passivePortEnd);
    }

    public LanFtpServerConfigSnapshot withReadOnly(boolean value) {
        return copy(enabled, controlPort, rootTreeUri, value, password, passwordRevision,
                allowAnonymous, passivePortStart, passivePortEnd);
    }

    public LanFtpServerConfigSnapshot withAllowAnonymous(boolean value) {
        return copy(enabled, controlPort, rootTreeUri, readOnly, password, passwordRevision,
                value, passivePortStart, passivePortEnd);
    }

    public boolean isSharedStorageRoot() {
        return SHARED_STORAGE_ROOT_URI.equals(rootTreeUri);
    }

    LanFtpServerConfigSnapshot withPassword(String value, long revision) {
        return copy(enabled, controlPort, rootTreeUri, readOnly, value, revision,
                allowAnonymous, passivePortStart, passivePortEnd);
    }

    private LanFtpServerConfigSnapshot copy(boolean nextEnabled, int nextControlPort,
            String nextRoot, boolean nextReadOnly, String nextPassword, long nextRevision,
            boolean nextAnonymous, int nextPassiveStart, int nextPassiveEnd) {
        return new LanFtpServerConfigSnapshot(nextEnabled, nextControlPort, nextRoot,
                nextReadOnly, username, nextPassword, nextRevision, nextAnonymous,
                nextPassiveStart, nextPassiveEnd, maxSessions, idleTimeoutMs);
    }
}
