package dev.jason.gboardpatches.extension.accessibilitylayout;

public final class GboardAccessibilityLayoutPolicy {
    public static final String PK_SIMULATOR_FLAG = "enable_pk_simulator";

    private GboardAccessibilityLayoutPolicy() {
    }

    public static Object maybeForce(String flagName, Object stockResult, boolean enabled) {
        if (!enabled
                || !PK_SIMULATOR_FLAG.equals(flagName)
                || !Boolean.FALSE.equals(stockResult)) {
            return stockResult;
        }
        return Boolean.TRUE;
    }
}
