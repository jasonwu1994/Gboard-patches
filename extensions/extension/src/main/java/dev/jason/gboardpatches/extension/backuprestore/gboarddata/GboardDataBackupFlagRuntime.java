package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

/** Flag getter callback that makes every Gboard backup provider available. */
@SuppressWarnings("unused")
public final class GboardDataBackupFlagRuntime {
    private GboardDataBackupFlagRuntime() {
    }

    /** Returns the policy value, or the incoming composed result on any failure. */
    public static Object applyFlagValue(String name, Object original) {
        try {
            return GboardDataBackupFlagPolicy.apply(name, original);
        } catch (Throwable unavailable) {
            return original;
        }
    }
}
