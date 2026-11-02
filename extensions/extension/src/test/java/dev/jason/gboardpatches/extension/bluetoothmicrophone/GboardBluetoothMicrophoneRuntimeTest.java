package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import org.junit.Assert;
import org.junit.Test;

public final class GboardBluetoothMicrophoneRuntimeTest {
    @Test
    public void readsExactFlagNameWithPerReceiverClassCaching() throws Throwable {
        Assert.assertEquals(
                "enable_use_bluetooth_setting",
                GboardBluetoothMicrophoneRuntime.readFlagName(
                        new FirstReceiver("enable_use_bluetooth_setting")));
        Assert.assertEquals(
                "unrelated",
                GboardBluetoothMicrophoneRuntime.readFlagName(
                        new SecondReceiver("unrelated")));
        Assert.assertNull(GboardBluetoothMicrophoneRuntime.readFlagName(
                new WrongTypeReceiver(Integer.valueOf(1))));
    }

    @Test
    public void seamForcesOnlyEnabledExactBooleanFalse() {
        TestSharedPreferences enabledPreferences = new TestSharedPreferences();
        enabledPreferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                Boolean.TRUE);
        TestSharedPreferences disabledPreferences = new TestSharedPreferences();
        disabledPreferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                Boolean.FALSE);
        TestSharedPreferences malformedPreferences = new TestSharedPreferences();
        malformedPreferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                Integer.valueOf(1));

        Assert.assertEquals(Boolean.TRUE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("enable_use_bluetooth_setting"),
                        Boolean.FALSE,
                        enabledPreferences));
        Assert.assertSame(Boolean.FALSE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("enable_use_bluetooth_setting"),
                        Boolean.FALSE,
                        disabledPreferences));
        Assert.assertSame(Boolean.FALSE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("enable_use_bluetooth_setting"),
                        Boolean.FALSE,
                        malformedPreferences));
        Assert.assertSame(Boolean.FALSE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("unrelated"),
                        Boolean.FALSE,
                        enabledPreferences));
        Assert.assertSame(Boolean.FALSE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new WrongTypeReceiver(Integer.valueOf(1)),
                        Boolean.FALSE,
                        enabledPreferences));
        Assert.assertNull(GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                null,
                null,
                enabledPreferences));
    }

    @Test
    public void irrelevantResultsDoNotReadPreferencesOnTheFlagGetterHotPath() {
        TestSharedPreferences preferences = new TestSharedPreferences();

        Assert.assertSame(Boolean.FALSE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("unrelated"),
                        Boolean.FALSE,
                        preferences));
        Assert.assertSame(Boolean.TRUE,
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("enable_use_bluetooth_setting"),
                        Boolean.TRUE,
                        preferences));
        Assert.assertEquals("false",
                GboardBluetoothMicrophoneRuntime.applyOverriddenFlagValue(
                        new FirstReceiver("enable_use_bluetooth_setting"),
                        "false",
                        preferences));

        Assert.assertEquals(0, preferences.getAllCalls);
    }

    private static final class FirstReceiver {
        private final String a;

        private FirstReceiver(String flagName) {
            a = flagName;
        }
    }

    private static final class SecondReceiver {
        private final String a;

        private SecondReceiver(String flagName) {
            a = flagName;
        }
    }

    private static final class WrongTypeReceiver {
        private final Integer a;

        private WrongTypeReceiver(Integer value) {
            a = value;
        }
    }
}
