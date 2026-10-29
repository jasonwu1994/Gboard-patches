package dev.jason.gboardpatches.extension.splitkeyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.UserManager;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardSplitKeyboardEligibilityRuntimeTest {
    private SharedPreferences preferences;
    private Context context;
    private Context previousRuntimeContext;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        previousRuntimeContext = ReflectionHelpers.getStaticField(
                GboardFlagRuntimeContext.class, "applicationContext");
        ReflectionHelpers.setStaticField(GboardFlagRuntimeContext.class, "applicationContext", context);
        shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        preferences = context.getSharedPreferences(GboardPatchesSettings.PREF_FILE, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        GboardSplitKeyboardSettings.ensureDefault(preferences);
    }

    @After public void restoreRuntimeContext() {
        shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        ReflectionHelpers.setStaticField(
                GboardFlagRuntimeContext.class, "applicationContext", previousRuntimeContext);
    }

    @Test public void lockedUserKeepsStockEligibilityAndUnlockRecovers() {
        Map<String, ?> settings = new HashMap<>(preferences.getAll());
        shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(false);
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(true));
        shadowOf(context.getSystemService(UserManager.class)).setUserUnlocked(true);
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        assertEquals(settings, preferences.getAll());
    }

    @Test public void unavailablePreferenceStorageKeepsStockEligibility() {
        Context unavailable = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                throw new IllegalStateException("Credential storage unavailable");
            }
        };
        ReflectionHelpers.setStaticField(
                GboardFlagRuntimeContext.class, "applicationContext", unavailable);
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(true));
    }

    @Test public void preferenceReadFailureIsCaughtAndStockTrueSkipsReading() {
        int[] reads = {0};
        SharedPreferences unreadable = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> {
                    reads[0]++;
                    throw new IllegalStateException("Preference read failed after acquisition");
                });
        Context failing = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return unreadable;
            }
        };
        ReflectionHelpers.setStaticField(GboardFlagRuntimeContext.class, "applicationContext", failing);
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(true));
        assertEquals(0, reads[0]);
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        assertEquals(1, reads[0]);
        assertFalse(GboardSplitKeyboardRuntime.applyWidthEligibility(false, false, false, 407));
    }

    @Test public void malformedMasterSettingKeepsStockEligibilityWithoutChangingOtherSettings() {
        preferences.edit().putInt(GboardSplitKeyboardSettings.PREF_KEY_ENABLED, 42).commit();
        Map<String, ?> settings = new HashMap<>(preferences.getAll());
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(true));
        assertEquals(settings, preferences.getAll());
    }

    @Test public void manualSelectionSurvivesReopenAndRecreationWithoutAnotherPreferenceStore() {
        Map<String, ?> settings = new HashMap<>(preferences.getAll());
        NativeMode controller = new NativeMode();
        controller.saved = 4; // Stock manual action persists the user's choice.
        controller.reopen();
        assertEquals(4, controller.current);
        NativeMode recreated = new NativeMode();
        recreated.saved = controller.saved;
        recreated.reopen();
        assertEquals(4, recreated.current);
        assertEquals(settings, preferences.getAll());
    }

    @Test public void normalMergedFloatingAndOneHandedSelectionsAreNotForcedToSplit() {
        for (int mode : new int[]{1, 2, 3}) {
            NativeMode controller = new NativeMode();
            controller.saved = mode;
            controller.reopen();
            assertEquals(mode, controller.current);
        }
        NativeMode controller = new NativeMode();
        controller.saved = 4;
        controller.reopen();
        assertEquals(4, controller.current);
        controller.saved = 1; // Stock merge updates its own saved mode.
        controller.reopen();
        assertEquals(1, controller.current);
    }

    @Test public void deviceAdmissionIsCapturedOnceForTheWholeNativeEvaluation() {
        boolean admitted = GboardSplitKeyboardRuntime.applyDeviceEligibility(false);
        assertTrue(admitted);
        GboardSplitKeyboardSettings.writeEnabled(preferences, false);
        assertTrue(GboardSplitKeyboardRuntime.applyWidthEligibility(false, false, admitted, 407));
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
        NativeMode controller = new NativeMode();
        controller.saved = 4;
        controller.reopen();
        assertEquals(1, controller.current);
    }

    @Test public void supportedDevicesKeepTheirStockWidthFitDecision() {
        NativeMode controller = new NativeMode();
        controller.saved = 4;
        controller.stockDevice = true;
        controller.reopen();
        assertEquals(1, controller.current);
        controller.stockWidth = true;
        controller.reopen();
        assertEquals(4, controller.current);
    }

    @Test public void groupControllerSimulatorAndNonpositiveWidthsKeepNativeFallback() {
        for (int gate = 0; gate < 5; gate++) {
            NativeMode controller = new NativeMode();
            controller.saved = 4;
            controller.group = gate != 0;
            controller.controller = gate != 1;
            controller.simulator = gate == 2;
            controller.width = gate == 3 ? 0 : gate == 4 ? -1 : 407;
            controller.reopen();
            assertEquals(1, controller.current);
            assertEquals(4, controller.saved);
        }
    }

    @Test public void nativeWidthTrueAndUnadmittedDeviceResultsArePreserved() {
        assertTrue(GboardSplitKeyboardRuntime.applyWidthEligibility(true, true, true, 800));
        assertFalse(GboardSplitKeyboardRuntime.applyWidthEligibility(false, false, false, 407));
        assertFalse(GboardSplitKeyboardRuntime.applyWidthEligibility(false, true, true, 407));
        GboardSplitKeyboardSettings.writeEnabled(preferences, false);
        assertTrue(GboardSplitKeyboardRuntime.applyDeviceEligibility(true));
        assertFalse(GboardSplitKeyboardRuntime.applyDeviceEligibility(false));
    }

    private static final class NativeMode {
        int saved = 1, current = 1, width = 407;
        boolean group = true, controller = true, simulator, stockDevice, stockWidth;
        void reopen() {
            current = saved;
            if (current == 4 && !eligible()) current = 1;
        }
        boolean eligible() {
            if (!group) return false;
            boolean originalDevice = stockDevice;
            boolean admittedDevice = GboardSplitKeyboardRuntime.applyDeviceEligibility(originalDevice);
            return admittedDevice && controller && GboardSplitKeyboardRuntime.applyWidthEligibility(
                    stockWidth, originalDevice, admittedDevice, width) && !simulator;
        }
    }
}
