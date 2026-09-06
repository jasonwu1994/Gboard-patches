package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.List;
import java.util.Objects;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

/** Immutable aggregate returned by the LAN FTP controller. */
public final class LanFtpServerState {
    private final LanFtpServerConfigSnapshot config;
    private final boolean active;
    private final String status;
    private final String endpoint;
    private final List<LanFtpSessionInfo> clients;
    private final int transferCount;
    private final boolean resumeSupported;
    private final long livePasswordRevision;
    private final boolean localNetworkAccess;
    private final boolean allFilesAccess;

    public LanFtpServerState(LanFtpServerConfigSnapshot config, boolean active, String status,
            String endpoint, List<LanFtpSessionInfo> clients, int transferCount,
            boolean resumeSupported, long livePasswordRevision,
            boolean localNetworkAccess, boolean allFilesAccess) {
        this.config = config;
        this.active = active;
        this.status = status == null ? "Unavailable" : status;
        this.endpoint = endpoint == null ? "" : endpoint;
        this.clients = clients == null ? List.of() : List.copyOf(clients);
        this.transferCount = Math.max(0, transferCount);
        this.resumeSupported = resumeSupported;
        this.livePasswordRevision = Math.max(0L, livePasswordRevision);
        this.localNetworkAccess = localNetworkAccess;
        this.allFilesAccess = allFilesAccess;
    }

    public LanFtpServerConfigSnapshot config() {
        return config;
    }

    public boolean active() {
        return active;
    }

    public String status() {
        return status;
    }

    public String endpoint() {
        return endpoint;
    }

    public List<LanFtpSessionInfo> clients() {
        return clients;
    }

    public int transferCount() {
        return transferCount;
    }

    public boolean resumeSupported() {
        return resumeSupported;
    }

    public long livePasswordRevision() {
        return livePasswordRevision;
    }

    public boolean localNetworkAccess() {
        return localNetworkAccess;
    }

    public boolean allFilesAccess() {
        return allFilesAccess;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpServerState that)) {
            return false;
        }
        return active == that.active
                && transferCount == that.transferCount
                && resumeSupported == that.resumeSupported
                && livePasswordRevision == that.livePasswordRevision
                && localNetworkAccess == that.localNetworkAccess
                && allFilesAccess == that.allFilesAccess
                && Objects.equals(config, that.config)
                && Objects.equals(status, that.status)
                && Objects.equals(endpoint, that.endpoint)
                && Objects.equals(clients, that.clients);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config, active, status, endpoint, clients, transferCount,
                resumeSupported, livePasswordRevision, localNetworkAccess, allFilesAccess);
    }

    @Override
    public String toString() {
        return "LanFtpServerState[config=" + config + ", active=" + active + ", status="
                + status + ", endpoint=" + endpoint + ", clients=" + clients
                + ", transferCount=" + transferCount + ", resumeSupported="
                + resumeSupported
                + ", livePasswordRevision=" + livePasswordRevision
                + ", localNetworkAccess=" + localNetworkAccess + ", allFilesAccess="
                + allFilesAccess + "]";
    }

    public int clientCount() {
        return clients.size();
    }
}
