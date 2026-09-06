package dev.jason.gboardpatches.extension.lanftp.runtime;

/** Single source of truth for control/passive FTP port validation. */
public final class LanFtpPortPolicy {
    private LanFtpPortPolicy() {
    }

    public static boolean isValidPort(int port) {
        return port >= 1024 && port <= 65535;
    }

    public static boolean isValidRange(int controlPort, int start, int end) {
        return violation(controlPort, start, end) == null;
    }

    public static void requireValid(int controlPort, int start, int end) {
        String violation = violation(controlPort, start, end);
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }
    }

    private static String violation(int controlPort, int start, int end) {
        if (!isValidPort(controlPort)) {
            return "controlPort must be between 1024 and 65535";
        }
        if (!isValidPort(start) || !isValidPort(end)) {
            return "passive ports must be between 1024 and 65535";
        }
        if (start > end) {
            return "passive port range is reversed";
        }
        if ((long) end - start + 1L > 32L) {
            return "passive port range is limited to 32 ports";
        }
        if (controlPort >= start && controlPort <= end) {
            return "control port overlaps passive range";
        }
        return null;
    }
}
