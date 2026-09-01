package dev.jason.gboardpatches.extension.backuprestore.flagstore;

/** Version-neutral classification and metadata for Gboard's persisted flag-store files. */
public final class GboardFlagStorageStatus {
    public enum Backend {
        LOADING,
        JETPACK_PROTO,
        JETPACK_PROTO_WITH_LEGACY_FILES,
        LEGACY_SHARED_PREFERENCES,
        NOT_CREATED,
        UNAVAILABLE
    }

    private final Backend backend;
    private final boolean protoStorePresent;
    private final boolean legacyOverridePresent;
    private final boolean legacyValuePresent;
    private final long protoStoreLastModified;
    private final long legacyOverrideLastModified;
    private final long legacyValueLastModified;

    public GboardFlagStorageStatus(Backend backend, boolean protoStorePresent,
            boolean legacyOverridePresent, boolean legacyValuePresent,
            long protoStoreLastModified, long legacyOverrideLastModified,
            long legacyValueLastModified) {
        this.backend = backend == null ? Backend.UNAVAILABLE : backend;
        this.protoStorePresent = protoStorePresent;
        this.legacyOverridePresent = legacyOverridePresent;
        this.legacyValuePresent = legacyValuePresent;
        this.protoStoreLastModified = protoStorePresent ? protoStoreLastModified : 0L;
        this.legacyOverrideLastModified = legacyOverridePresent
                ? legacyOverrideLastModified : 0L;
        this.legacyValueLastModified = legacyValuePresent ? legacyValueLastModified : 0L;
    }

    public static GboardFlagStorageStatus loading() {
        return new GboardFlagStorageStatus(
                Backend.LOADING, false, false, false, 0L, 0L, 0L);
    }

    public static GboardFlagStorageStatus unavailable() {
        return new GboardFlagStorageStatus(
                Backend.UNAVAILABLE, false, false, false, 0L, 0L, 0L);
    }

    public static GboardFlagStorageStatus fromPresence(boolean protoStorePresent,
            boolean legacyOverridePresent, boolean legacyValuePresent) {
        return fromPresence(protoStorePresent, legacyOverridePresent, legacyValuePresent,
                0L, 0L, 0L);
    }

    public static GboardFlagStorageStatus fromPresence(boolean protoStorePresent,
            boolean legacyOverridePresent, boolean legacyValuePresent,
            long protoStoreLastModified, long legacyOverrideLastModified,
            long legacyValueLastModified) {
        boolean legacyPresent = legacyOverridePresent || legacyValuePresent;
        Backend backend;
        if (protoStorePresent && legacyPresent) {
            backend = Backend.JETPACK_PROTO_WITH_LEGACY_FILES;
        } else if (protoStorePresent) {
            backend = Backend.JETPACK_PROTO;
        } else if (legacyPresent) {
            backend = Backend.LEGACY_SHARED_PREFERENCES;
        } else {
            backend = Backend.NOT_CREATED;
        }
        return new GboardFlagStorageStatus(backend, protoStorePresent,
                legacyOverridePresent, legacyValuePresent, protoStoreLastModified,
                legacyOverrideLastModified, legacyValueLastModified);
    }

    public Backend getBackend() { return backend; }
    public boolean isProtoStorePresent() { return protoStorePresent; }
    public boolean isLegacyOverridePresent() { return legacyOverridePresent; }
    public boolean isLegacyValuePresent() { return legacyValuePresent; }
    public long getProtoStoreLastModified() { return protoStoreLastModified; }
    public long getLegacyOverrideLastModified() { return legacyOverrideLastModified; }
    public long getLegacyValueLastModified() { return legacyValueLastModified; }
}
