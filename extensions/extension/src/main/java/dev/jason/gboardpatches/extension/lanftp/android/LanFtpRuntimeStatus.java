package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

/** Single sanitized value type shared by the service registry, provider and UI controller. */
public final class LanFtpRuntimeStatus {
    public final boolean active;
    public final String status;
    public final String endpoint;
    public final List<LanFtpSessionInfo> clients;
    public final int transferCount;
    public final boolean resumeSupported;
    public final long passwordRevision;
    public final boolean startupFailed;

    public LanFtpRuntimeStatus(boolean active, String status, String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount) {
        this(active, status, endpoint, clients, transferCount, true);
    }

    public LanFtpRuntimeStatus(boolean active, String status, String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount, boolean resumeSupported) {
        this(active, status, endpoint, clients, transferCount, resumeSupported, 0L, false);
    }

    public LanFtpRuntimeStatus(boolean active, String status, String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount, boolean resumeSupported,
            long passwordRevision, boolean startupFailed) {
        String safeStatus = clean(status);
        String safeEndpoint = clean(endpoint);
        boolean validActive = active && !safeStatus.isEmpty()
                && safeEndpoint.startsWith("ftp://");
        this.active = validActive;
        this.status = active && !validActive
                ? "Unavailable"
                : (safeStatus.isEmpty() ? (validActive ? "Running" : "Stopped") : safeStatus);
        this.endpoint = validActive ? safeEndpoint : "";
        this.clients = sanitizeClients(clients);
        this.transferCount = Math.max(0, transferCount);
        this.resumeSupported = resumeSupported;
        this.passwordRevision = Math.max(0L, passwordRevision);
        this.startupFailed = !validActive && startupFailed;
    }

    public int clientCount() {
        return clients.size();
    }

    public static LanFtpRuntimeStatus stopped() {
        return new LanFtpRuntimeStatus(false, "Stopped", "", List.of(), 0);
    }

    public static LanFtpRuntimeStatus unavailable() {
        return new LanFtpRuntimeStatus(false, "Unavailable", "", List.of(), 0);
    }

    public static LanFtpRuntimeStatus starting() {
        return new LanFtpRuntimeStatus(false, "Starting", "", List.of(), 0);
    }

    public static LanFtpRuntimeStatus stopping() {
        return new LanFtpRuntimeStatus(false, "Stopping", "", List.of(), 0);
    }

    public static LanFtpRuntimeStatus failed(String reason) {
        return new LanFtpRuntimeStatus(false,
                reason == null ? "Failed" : reason, "", List.of(), 0, true, 0L, true);
    }

    public static LanFtpRuntimeStatus running(String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount) {
        return running(endpoint, clients, transferCount, true);
    }

    public static LanFtpRuntimeStatus running(String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount, boolean resumeSupported) {
        return running(endpoint, clients, transferCount, resumeSupported, 0L);
    }

    public static LanFtpRuntimeStatus running(String endpoint,
            List<LanFtpSessionInfo> clients, int transferCount, boolean resumeSupported,
            long passwordRevision) {
        return new LanFtpRuntimeStatus(
                true, "Running", endpoint, clients, transferCount, resumeSupported,
                passwordRevision, false);
    }

    private static List<LanFtpSessionInfo> sanitizeClients(List<LanFtpSessionInfo> clients) {
        if (clients == null || clients.isEmpty()) {
            return List.of();
        }
        List<LanFtpSessionInfo> result = new ArrayList<>();
        for (LanFtpSessionInfo client : clients) {
            if (client != null && !client.id().isBlank() && result.size() < 16) {
                result.add(client.sanitized());
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpRuntimeStatus that)) {
            return false;
        }
        return active == that.active
                && transferCount == that.transferCount
                && resumeSupported == that.resumeSupported
                && passwordRevision == that.passwordRevision
                && startupFailed == that.startupFailed
                && Objects.equals(status, that.status)
                && Objects.equals(endpoint, that.endpoint)
                && Objects.equals(clients, that.clients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(active, status, endpoint, clients, transferCount,
                resumeSupported, passwordRevision, startupFailed);
    }
}
