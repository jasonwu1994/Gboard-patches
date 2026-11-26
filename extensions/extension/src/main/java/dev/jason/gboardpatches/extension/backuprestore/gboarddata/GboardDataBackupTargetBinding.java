package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

/**
 * Names of Gboard's file backup framework in the supported Gboard version. Every other member is
 * derived from method signatures by {@link GboardDataBackupBinding}.
 */
public final class GboardDataBackupTargetBinding {
    /** {@code FileBackupRestoreHelper}: static boolean restore(Context, Map). */
    static final String DISPATCHER_CLASS = "nga";
    /** {@code BackupHelper}: static ImmutableMap providers(Context, Class). */
    static final String REGISTRY_CLASS = "nfv";
    /** Backup data provider interface. */
    static final String PROVIDER_CLASS = "ngd";
    static final String PROVIDER_CATEGORY_METHOD = "a";
    static final String PROVIDER_ID_METHOD = "b";

    static final GboardDataBackupBinding.Names NAMES = new GboardDataBackupBinding.Names(
            DISPATCHER_CLASS, REGISTRY_CLASS, PROVIDER_CLASS, PROVIDER_CATEGORY_METHOD,
            PROVIDER_ID_METHOD);

    private static volatile GboardDataBackupBinding resolved;

    private GboardDataBackupTargetBinding() {
    }

    /** Resolves the binding against Gboard's own class loader; null when it does not match. */
    public static GboardDataBackupBinding resolveOrNull() {
        GboardDataBackupBinding current = resolved;
        if (current != null) {
            return current;
        }
        synchronized (GboardDataBackupTargetBinding.class) {
            if (resolved == null) {
                try {
                    resolved = GboardDataBackupBinding.resolve(
                            GboardDataBackupTargetBinding.class.getClassLoader(), NAMES);
                } catch (Throwable ignored) {
                    // An unmatched Gboard build shows the section as unavailable.
                    return null;
                }
            }
            return resolved;
        }
    }

    /** Gateway for the settings activity, which runs inside Gboard's own process. */
    public static GboardDataBackupGateway gateway() {
        return new GboardDataBackupInProcessGateway(GboardDataBackupTargetBinding::resolveOrNull);
    }
}
