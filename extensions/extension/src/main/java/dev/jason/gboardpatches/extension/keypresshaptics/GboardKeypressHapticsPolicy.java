package dev.jason.gboardpatches.extension.keypresshaptics;

public final class GboardKeypressHapticsPolicy {
    public static final String FLAG_NAME = "vibration_effect_min_sdk";

    private GboardKeypressHapticsPolicy() {}

    public static boolean isTarget(String name, Object value) {
        return FLAG_NAME.equals(name) && value instanceof Long && ((Long) value).longValue() == 1024L;
    }

    public static Object maybeOverride(String name, Object value, Boolean enabled) {
        return isTarget(name, value) && Boolean.TRUE.equals(enabled) ? Long.valueOf(30L) : value;
    }
}
