package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.Context;

/** Stable lifecycle entry for a version adapter or a Morphe manifest provider. */
public final class GboardFlagStoreColdStartInitializer {
    private GboardFlagStoreColdStartInitializer() {
    }

    public static void initialize(Context context) {
        GboardFlagStoreManager.applyPendingAtColdStart(context);
    }
}
