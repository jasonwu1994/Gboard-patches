package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Gateway for settings that run in Gboard's own process. */
public final class GboardDataBackupInProcessGateway implements GboardDataBackupGateway {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gboard-data-backup");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Supplier<GboardDataBackupBinding> binding;

    public GboardDataBackupInProcessGateway(Supplier<GboardDataBackupBinding> binding) {
        this.binding = binding;
    }

    @Override
    public void inspect(Context context, Callback<GboardDataBackupResults.Inspection> callback) {
        run(callback, () -> GboardDataBackupManager.inspect(context, requireBinding()));
    }

    @Override
    public void export(Context context, Set<String> selectedIds,
            Callback<ExportedArchive> callback) {
        run(callback, () -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            GboardDataBackupResults.Export result = GboardDataBackupManager.export(context,
                    requireBinding(), output, selectedIds);
            return new ExportedArchive(output.toByteArray(), result.getEntryCount(),
                    result.getFailures());
        });
    }

    @Override
    public void restore(Context context, byte[] archive, Set<String> selectedIds,
            Callback<GboardDataBackupResults.Restore> callback) {
        run(callback, () -> GboardDataBackupManager.restore(context, requireBinding(),
                new ByteArrayInputStream(archive), selectedIds));
    }

    private GboardDataBackupBinding requireBinding() {
        GboardDataBackupBinding resolved = binding == null ? null : binding.get();
        if (resolved == null) {
            throw new IllegalStateException("Gboard backup framework is unavailable");
        }
        return resolved;
    }

    private interface Work<T> {
        T run() throws Exception;
    }

    private static <T> void run(Callback<T> callback, Work<T> work) {
        EXECUTOR.execute(() -> {
            T value = null;
            String error = null;
            try {
                value = work.run();
            } catch (Throwable failure) {
                error = GboardDataBackupManager.describe(failure);
            }
            T finalValue = value;
            String finalError = error;
            MAIN.post(() -> callback.onResult(finalValue, finalError));
        });
    }
}
