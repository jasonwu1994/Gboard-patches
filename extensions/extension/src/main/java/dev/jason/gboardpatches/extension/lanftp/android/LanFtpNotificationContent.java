package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.Locale;
import java.util.Objects;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

/** Pure notification presentation derived from a portable runtime session snapshot. */
final class LanFtpNotificationContent {
    private final String text;
    private final int progress;
    private final boolean indeterminate;

    LanFtpNotificationContent(String text, int progress, boolean indeterminate) {
        this.text = text;
        this.progress = progress;
        this.indeterminate = indeterminate;
    }

    String text() {
        return text;
    }

    int progress() {
        return progress;
    }

    boolean indeterminate() {
        return indeterminate;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanFtpNotificationContent that)) {
            return false;
        }
        return progress == that.progress
                && indeterminate == that.indeterminate
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, progress, indeterminate);
    }

    @Override
    public String toString() {
        return "LanFtpNotificationContent[text=" + text + ", progress=" + progress
                + ", indeterminate=" + indeterminate + "]";
    }

    static LanFtpNotificationContent from(
            String idleText, LanFtpSessionInfo activeTransfer) {
        if (activeTransfer == null || !activeTransfer.isTransferActive()) {
            return new LanFtpNotificationContent(idleText, 0, false);
        }
        String arrow = "upload".equals(activeTransfer.direction()) ? "↑" : "↓";
        String transferred = formatBytes(activeTransfer.bytesTransferred());
        String speed = formatBytes(activeTransfer.bytesPerSecond()) + "/s";
        int percent = activeTransfer.progressPercent();
        if (percent < 0) {
            return new LanFtpNotificationContent(
                    arrow + " " + activeTransfer.fileName() + " • " + transferred
                            + " • " + speed,
                    0,
                    true);
        }
        return new LanFtpNotificationContent(
                arrow + " " + activeTransfer.fileName()
                        + " • " + transferred + " / "
                        + formatBytes(activeTransfer.totalBytes())
                        + " • " + percent + "% • " + speed,
                percent,
                false);
    }

    private static String formatBytes(long bytes) {
        long safeBytes = Math.max(0L, bytes);
        if (safeBytes < 1024L) {
            return safeBytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB", "PiB", "EiB"};
        double value = safeBytes;
        int unit = -1;
        do {
            value /= 1024.0d;
            unit++;
        } while (value >= 1024.0d && unit < units.length - 1);
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
