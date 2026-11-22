package dev.jason.gboardpatches.extension.advancedvoice;

/**
 * Gboard treats airplane mode as "no network" for its S3 recognizer, even when Wi-Fi is on.
 * This policy lets S3 through only when Gboard's own network state shows a usable connection;
 * without one the stock answer stands.
 */
public final class GboardS3AirplaneModePolicy {
    private GboardS3AirplaneModePolicy() {
    }

    public static boolean shouldOverride(boolean stockResult, boolean airplaneModeOn,
            String networkStateName) {
        if (stockResult || !airplaneModeOn || networkStateName == null) {
            return false;
        }
        return "METERED".equals(networkStateName)
                || "NON_METERED".equals(networkStateName)
                || "PROXIED".equals(networkStateName);
    }
}
