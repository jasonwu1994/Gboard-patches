package dev.jason.gboardpatches.extension.lanftp.settings;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Pure, version-neutral formatting for the LAN FTP settings UI. */
final class LanFtpSettingsDisplayFormatter {
    private static final String[] BINARY_UNIT_LABELS = {
            "B", "KB", "MB", "GB", "TB", "PB", "EB"
    };

    private LanFtpSettingsDisplayFormatter() {
    }

    static String formatBytes(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        double value = safeBytes;
        int unitIndex = 0;
        while (value >= 1024.0d && unitIndex < BINARY_UNIT_LABELS.length - 1) {
            value /= 1024.0d;
            unitIndex++;
        }
        if (unitIndex == 0) {
            return safeBytes + BINARY_UNIT_LABELS[unitIndex];
        }
        String pattern = value < 10.0d ? "0.##" : value < 100.0d ? "0.#" : "0";
        DecimalFormat formatter = new DecimalFormat(
                pattern, DecimalFormatSymbols.getInstance(Locale.US));
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(value) + BINARY_UNIT_LABELS[unitIndex];
    }

    static String formatTimestamp(long timestampMillis) {
        return formatTimestamp(timestampMillis, TimeZone.getDefault());
    }

    static String formatRate(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }

    static String formatTimestamp(long timestampMillis, TimeZone timeZone) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy/M/d HH:mm:ss", Locale.ROOT);
        formatter.setTimeZone(timeZone == null ? TimeZone.getDefault() : timeZone);
        return formatter.format(new Date(Math.max(0L, timestampMillis)));
    }
}
