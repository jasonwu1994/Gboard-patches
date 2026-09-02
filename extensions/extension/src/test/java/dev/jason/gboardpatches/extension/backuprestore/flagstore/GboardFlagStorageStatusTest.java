package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GboardFlagStorageStatusTest {
    @Test
    public void persistedFilesSelectTheBackendWithoutVersionKnowledge() {
        assertEquals(GboardFlagStorageStatus.Backend.JETPACK_PROTO,
                GboardFlagStorageStatus.fromPresence(true, false, false).getBackend());
        assertEquals(GboardFlagStorageStatus.Backend.LEGACY_SHARED_PREFERENCES,
                GboardFlagStorageStatus.fromPresence(false, true, false).getBackend());
        assertEquals(GboardFlagStorageStatus.Backend.JETPACK_PROTO_WITH_LEGACY_FILES,
                GboardFlagStorageStatus.fromPresence(true, true, true).getBackend());
        assertEquals(GboardFlagStorageStatus.Backend.NOT_CREATED,
                GboardFlagStorageStatus.fromPresence(false, false, false).getBackend());
    }

    @Test
    public void persistedFileModificationTimesAreReportedWithoutGuessingRuntimeOwnership() {
        GboardFlagStorageStatus status = GboardFlagStorageStatus.fromPresence(
                true, true, true, 1000L, 2000L, 3000L);

        assertEquals(1000L, status.getProtoStoreLastModified());
        assertEquals(2000L, status.getLegacyOverrideLastModified());
        assertEquals(3000L, status.getLegacyValueLastModified());
    }
}
