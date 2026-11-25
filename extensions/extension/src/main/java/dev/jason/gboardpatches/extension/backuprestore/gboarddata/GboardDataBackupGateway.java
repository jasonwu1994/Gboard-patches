package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Settings-side access to {@link GboardDataBackupManager}. A host whose settings run inside
 * Gboard uses {@link GboardDataBackupInProcessGateway}; a host whose settings run in another
 * process supplies its own transport. Callbacks are delivered on the main thread.
 */
public interface GboardDataBackupGateway {
    interface Callback<T> {
        /** Exactly one of {@code value} and {@code error} is non-null. */
        void onResult(T value, String error);
    }

    void inspect(Context context, Callback<GboardDataBackupResults.Inspection> callback);

    /** {@code selectedIds == null} exports everything. */
    void export(Context context, Set<String> selectedIds, Callback<ExportedArchive> callback);

    void restore(Context context, byte[] archive, Set<String> selectedIds,
            Callback<GboardDataBackupResults.Restore> callback);

    final class ExportedArchive {
        private final byte[] data;
        private final int entryCount;
        private final List<String> failures;

        public ExportedArchive(byte[] data, int entryCount, List<String> failures) {
            this.data = data;
            this.entryCount = entryCount;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        public byte[] getData() { return data; }
        public int getEntryCount() { return entryCount; }
        public List<String> getFailures() { return failures; }
    }
}
