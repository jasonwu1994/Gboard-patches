package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import org.junit.Assert;
import org.junit.Test;

public final class GboardBluetoothMicrophonePolicyTest {
    @Test
    public void forcesOnlyExactFalseBooleanWhenEnabled() {
        Assert.assertEquals(
                "enable_use_bluetooth_setting",
                GboardBluetoothMicrophonePolicy.BLUETOOTH_MICROPHONE_FLAG);
        Assert.assertEquals(Boolean.TRUE, GboardBluetoothMicrophonePolicy.maybeForce(
                "enable_use_bluetooth_setting", Boolean.FALSE, true));
        Assert.assertSame(Boolean.FALSE, GboardBluetoothMicrophonePolicy.maybeForce(
                "enable_use_bluetooth_setting", Boolean.FALSE, false));
        Assert.assertSame(Boolean.TRUE, GboardBluetoothMicrophonePolicy.maybeForce(
                "enable_use_bluetooth_setting", Boolean.TRUE, true));
        Assert.assertEquals("false", GboardBluetoothMicrophonePolicy.maybeForce(
                "enable_use_bluetooth_setting", "false", true));
        Assert.assertSame(Boolean.FALSE, GboardBluetoothMicrophonePolicy.maybeForce(
                "unrelated", Boolean.FALSE, true));
        Assert.assertNull(GboardBluetoothMicrophonePolicy.maybeForce(
                "enable_use_bluetooth_setting", null, true));
    }
}
