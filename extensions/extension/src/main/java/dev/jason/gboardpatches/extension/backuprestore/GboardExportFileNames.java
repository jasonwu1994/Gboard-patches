package dev.jason.gboardpatches.extension.backuprestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Timestamp and import-name policy for files crossing the settings-shell document seam. */
public final class GboardExportFileNames {
    private static final String TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss";

    private GboardExportFileNames() {
    }

    public static String timestamped(String canonicalName) {
        return timestamped(canonicalName, new Date());
    }

    static String timestamped(String canonicalName, Date date) {
        if (canonicalName == null || canonicalName.isBlank() || date == null) {
            throw new IllegalArgumentException("Canonical filename and date are required");
        }
        int dot = canonicalName.lastIndexOf('.');
        String stem = dot > 0 ? canonicalName.substring(0, dot) : canonicalName;
        String extension = dot > 0 ? canonicalName.substring(dot) : "";
        String timestamp = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.ROOT).format(date);
        return stem + "_" + timestamp + extension;
    }

    public static String canonicalImportedName(String displayName, String... canonicalNames) {
        if (displayName == null || canonicalNames == null) {
            return null;
        }
        String leaf = displayName.replace('\\', '/');
        int slash = leaf.lastIndexOf('/');
        if (slash >= 0) {
            leaf = leaf.substring(slash + 1);
        }
        for (String canonical : canonicalNames) {
            if (canonical == null || canonical.isBlank()) {
                continue;
            }
            if (canonical.equals(leaf)) {
                return canonical;
            }
            int dot = canonical.lastIndexOf('.');
            String stem = dot > 0 ? canonical.substring(0, dot) : canonical;
            String extension = dot > 0 ? canonical.substring(dot) : "";
            if (leaf.matches(java.util.regex.Pattern.quote(stem)
                    + "_[0-9]{8}_[0-9]{6}"
                    + java.util.regex.Pattern.quote(extension))) {
                return canonical;
            }
        }
        return null;
    }
}
