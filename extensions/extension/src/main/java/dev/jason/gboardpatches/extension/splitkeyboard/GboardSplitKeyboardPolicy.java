package dev.jason.gboardpatches.extension.splitkeyboard;

import java.util.ArrayList;
import java.util.List;

public final class GboardSplitKeyboardPolicy {
    public static final String SPLIT_ACCESS_POINT = "split";
    public static final int ENABLED_ACCESS_POINT_STATE = 2;
    public static final int UNAVAILABLE_ACCESS_POINT_STATE = 5;
    public static final int SPLIT_KEYBOARD_MODE = 4;

    private GboardSplitKeyboardPolicy() {
    }

    public static boolean resolveDeviceEligibility(boolean stockEligible, boolean enabled) {
        return stockEligible || enabled;
    }

    public static boolean resolveWidthEligibility(boolean stockEligible,
            boolean stockDeviceEligible, boolean admittedDeviceEligible, int availableWidth) {
        return stockEligible || (!stockDeviceEligible && admittedDeviceEligible && availableWidth > 0);
    }

    public static int forceAccessPointState(
            String accessPointName, int stockState, boolean enabled) {
        return enabled
                && SPLIT_ACCESS_POINT.equals(accessPointName)
                && stockState == UNAVAILABLE_ACCESS_POINT_STATE
                ? ENABLED_ACCESS_POINT_STATE
                : stockState;
    }

    public static List<String> appendSplitAccessPoint(List<?> stockOrder, boolean enabled) {
        List<String> result = new ArrayList<>();
        if (stockOrder != null) {
            for (Object item : stockOrder) {
                if (item instanceof String && !result.contains(item)) {
                    result.add((String) item);
                }
            }
        }
        if (enabled && !result.contains(SPLIT_ACCESS_POINT)) {
            result.add(SPLIT_ACCESS_POINT);
        }
        return result;
    }

    public static boolean shouldSwitchToSplit(boolean splitKeyboardEnabled,
            boolean autoSplitEnabled, boolean stockEligible, int currentMode) {
        return splitKeyboardEnabled
                && autoSplitEnabled
                && stockEligible
                && currentMode != SPLIT_KEYBOARD_MODE;
    }

    public static boolean shouldRedirectSplitActionToRestore(boolean splitKeyboardEnabled,
            int currentMode) {
        return splitKeyboardEnabled && currentMode == SPLIT_KEYBOARD_MODE;
    }
}
