package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.util.Objects;

public final class LanFtpDocument {
    private final String absolutePath;
    private final boolean directory;
    private final long size;
    private final long lastModified;
    private final boolean readable;
    private final boolean writable;
    private final boolean removable;

    public LanFtpDocument(String absolutePath, boolean directory, long size, long lastModified,
            boolean readable, boolean writable, boolean removable) {
        this.absolutePath = Objects.requireNonNull(absolutePath, "absolutePath");
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
        this.readable = readable;
        this.writable = writable;
        this.removable = removable;
    }

    public String absolutePath() {
        return absolutePath;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long size() {
        return size;
    }

    public long lastModified() {
        return lastModified;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isRemovable() {
        return removable;
    }
}
