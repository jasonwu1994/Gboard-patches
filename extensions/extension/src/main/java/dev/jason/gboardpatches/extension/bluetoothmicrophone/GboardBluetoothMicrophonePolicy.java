package dev.jason.gboardpatches.extension.bluetoothmicrophone;

public final class GboardBluetoothMicrophonePolicy {
    public static final String BLUETOOTH_MICROPHONE_FLAG =
            "enable_use_bluetooth_setting";

    private GboardBluetoothMicrophonePolicy() {
    }

    public static Object maybeForce(String flagName, Object stockResult, boolean enabled) {
        if (!enabled
                || !BLUETOOTH_MICROPHONE_FLAG.equals(flagName)
                || !(stockResult instanceof Boolean)
                || Boolean.TRUE.equals(stockResult)) {
            return stockResult;
        }
        return Boolean.TRUE;
    }
}
