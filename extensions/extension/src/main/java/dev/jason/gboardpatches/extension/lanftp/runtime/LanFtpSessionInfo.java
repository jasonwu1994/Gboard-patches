package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.Objects;

public final class LanFtpSessionInfo {
    static final String ATTRIBUTE_COMMAND = "lanftp.command";
    static final String ATTRIBUTE_PATH = "lanftp.path";
    static final String ATTRIBUTE_DIRECTION = "lanftp.direction";
    static final String ATTRIBUTE_BYTES = "lanftp.bytes";
    static final String ATTRIBUTE_TOTAL_BYTES = "lanftp.total_bytes";
    static final String ATTRIBUTE_BYTES_PER_SECOND = "lanftp.bytes_per_second";
    static final String ATTRIBUTE_SPEED_SAMPLE_NANOS = "lanftp.speed_sample_nanos";

    private final String id;
    private final String peer;
    private final long loginTimeMillis;
    private final long lastActivityMillis;
    private final String command;
    private final String path;
    private final String direction;
    private final long bytesTransferred;
    private final long totalBytes;
    private final long bytesPerSecond;

    public LanFtpSessionInfo(String id, String peer, long loginTimeMillis,
            long lastActivityMillis, String command, String path, String direction,
            long bytesTransferred, long totalBytes, long bytesPerSecond) {
        this.id = id == null ? "" : id;
        this.peer = peer == null ? "unknown" : peer;
        this.loginTimeMillis = Math.max(0L, loginTimeMillis);
        this.lastActivityMillis = Math.max(0L, lastActivityMillis);
        this.command = command == null ? "IDLE" : command;
        this.path = path == null ? "" : path;
        this.direction = direction == null ? "idle" : direction;
        this.bytesTransferred = Math.max(0L, bytesTransferred);
        this.totalBytes = Math.max(-1L, totalBytes);
        this.bytesPerSecond = Math.max(0L, bytesPerSecond);
    }

    public LanFtpSessionInfo(String id, String peer) {
        this(id, peer, 0L, 0L, "IDLE", "", "idle", 0L, -1L, 0L);
    }

    public LanFtpSessionInfo(String id, String peer, long loginTimeMillis,
            long lastActivityMillis, String command, String path, String direction,
            long bytesTransferred) {
        this(id, peer, loginTimeMillis, lastActivityMillis, command, path, direction,
                bytesTransferred, -1L, 0L);
    }

    public LanFtpSessionInfo(String id, String peer, long loginTimeMillis,
            long lastActivityMillis, String command, String path, String direction,
            long bytesTransferred, long totalBytes) {
        this(id, peer, loginTimeMillis, lastActivityMillis, command, path, direction,
                bytesTransferred, totalBytes, 0L);
    }

    public String id() {
        return id;
    }

    public String peer() {
        return peer;
    }

    public long loginTimeMillis() {
        return loginTimeMillis;
    }

    public long lastActivityMillis() {
        return lastActivityMillis;
    }

    public String command() {
        return command;
    }

    public String path() {
        return path;
    }

    public String direction() {
        return direction;
    }

    public long bytesTransferred() {
        return bytesTransferred;
    }

    public long totalBytes() {
        return totalBytes;
    }

    public long bytesPerSecond() {
        return bytesPerSecond;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpSessionInfo that)) {
            return false;
        }
        return loginTimeMillis == that.loginTimeMillis
                && lastActivityMillis == that.lastActivityMillis
                && bytesTransferred == that.bytesTransferred
                && totalBytes == that.totalBytes
                && bytesPerSecond == that.bytesPerSecond
                && Objects.equals(id, that.id)
                && Objects.equals(peer, that.peer)
                && Objects.equals(command, that.command)
                && Objects.equals(path, that.path)
                && Objects.equals(direction, that.direction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, peer, loginTimeMillis, lastActivityMillis, command, path,
                direction, bytesTransferred, totalBytes, bytesPerSecond);
    }

    @Override
    public String toString() {
        return "LanFtpSessionInfo[id=" + id + ", peer=" + peer + ", loginTimeMillis="
                + loginTimeMillis + ", lastActivityMillis=" + lastActivityMillis
                + ", command=" + command + ", path=" + path + ", direction=" + direction
                + ", bytesTransferred=" + bytesTransferred + ", totalBytes=" + totalBytes
                + ", bytesPerSecond=" + bytesPerSecond + "]";
    }

    public LanFtpSessionInfo sanitized() {
        return new LanFtpSessionInfo(
                clean(id),
                clean(peer),
                loginTimeMillis,
                lastActivityMillis,
                clean(command),
                path.isBlank() ? "" : fileName(),
                clean(direction),
                bytesTransferred,
                totalBytes,
                bytesPerSecond);
    }

    public boolean isTransferActive() {
        return "upload".equals(direction) || "download".equals(direction);
    }

    public String fileName() {
        String normalized = path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        String safeName = clean(name).replace('\n', ' ').replace('\r', ' ');
        return safeName.isBlank() ? "file" : safeName;
    }

    public int progressPercent() {
        if (totalBytes < 0L) {
            return -1;
        }
        if (totalBytes == 0L) {
            return 100;
        }
        long completed = Math.min(bytesTransferred, totalBytes);
        return (int) ((completed / (double) totalBytes) * 100.0d);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 256 ? trimmed : trimmed.substring(0, 256);
    }
}
