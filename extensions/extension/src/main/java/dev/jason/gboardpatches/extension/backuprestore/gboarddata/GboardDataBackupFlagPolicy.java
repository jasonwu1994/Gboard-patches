package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import java.util.Set;

/**
 * Gboard registers some backup providers only when a flag is on. Two of them are off by default
 * outside Pixel devices, so Chinese learned words and expression images would never be exported
 * or restored. Forcing these flags on makes every provider available to the data backup.
 */
public final class GboardDataBackupFlagPolicy {
    /** Latin learned words and blocked suggestions; already on by default. */
    public static final String DELIGHT5_BACKUP_FLAG = "enable_backup_delight5_personalized_data";
    /** Chinese (HMM) learned words; off by default. */
    public static final String HMM_BACKUP_FLAG = "enable_hmm_user_history_backup";
    /** Expression images; off by default. */
    public static final String IMAGE_FILE_BACKUP_FLAG = "enable_image_file_database_backup";

    private static final Set<String> FLAGS =
            Set.of(DELIGHT5_BACKUP_FLAG, HMM_BACKUP_FLAG, IMAGE_FILE_BACKUP_FLAG);

    private GboardDataBackupFlagPolicy() {
    }

    public static boolean isTargetFlagName(String flagName) {
        return flagName != null && FLAGS.contains(flagName);
    }

    /** Returns {@code TRUE} for a target Boolean flag and leaves every other value unchanged. */
    public static Object apply(String flagName, Object currentResult) {
        return isTargetFlagName(flagName) && currentResult instanceof Boolean
                ? Boolean.TRUE : currentResult;
    }
}
