package dev.jason.gboardpatches.extension.splitkeyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

@RunWith(RobolectricTestRunner.class)
public final class GboardSplitKeyboardRuntimeTest {
    private SharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(
                GboardPatchesSettings.PREF_FILE,
                Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        GboardSplitKeyboardSettings.ensureDefault(preferences);
    }

    @Test
    public void lifecycleUsesStockEligibilityAndNonPersistingModeSwitch() {
        FakeModeController controller = new FakeModeController();
        controller.stockEligible = true;
        controller.currentMode = 1;
        GboardSplitKeyboardSettings.writeAutoSplitEnabled(preferences, true);

        GboardSplitKeyboardRuntime.afterModeEnvironmentChanged(controller);

        Assert.assertEquals(1, controller.switchCalls);
        Assert.assertEquals(GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE,
                controller.requestedMode);
        Assert.assertFalse(controller.persistRequested);
        Assert.assertFalse(controller.temporaryRequested);
    }

    @Test
    public void lifecycleDoesNothingWhenAutoSplitIsOffOrModeIsAlreadySplit() {
        FakeModeController controller = new FakeModeController();
        controller.stockEligible = true;
        controller.currentMode = 1;

        GboardSplitKeyboardRuntime.afterModeEnvironmentChanged(controller);
        Assert.assertEquals(0, controller.switchCalls);

        GboardSplitKeyboardSettings.writeAutoSplitEnabled(preferences, true);
        controller.currentMode = GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE;
        GboardSplitKeyboardRuntime.afterModeEnvironmentChanged(controller);
        Assert.assertEquals(0, controller.switchCalls);
    }

    @Test
    public void splitActionUsesStockRestoreOnlyWhenMasterIsOnAndAlreadySplit() {
        FakeModeController controller = new FakeModeController();
        controller.currentMode = GboardSplitKeyboardPolicy.SPLIT_KEYBOARD_MODE;

        Assert.assertTrue(GboardSplitKeyboardRuntime.interceptSplitAction(controller));
        Assert.assertEquals(1, controller.restoreCalls);

        GboardSplitKeyboardSettings.writeEnabled(preferences, false);
        Assert.assertFalse(GboardSplitKeyboardRuntime.interceptSplitAction(controller));
        Assert.assertEquals(1, controller.restoreCalls);
    }

    private static final class FakeModeController {
        boolean stockEligible;
        int currentMode;
        int switchCalls;
        int restoreCalls;
        int requestedMode = -1;
        boolean persistRequested = true;
        boolean temporaryRequested = true;

        @SuppressWarnings("unused")
        private boolean au() {
            return stockEligible;
        }

        @SuppressWarnings("unused")
        public int G() {
            return currentMode;
        }

        @SuppressWarnings("unused")
        public boolean ag(int mode, boolean persist, boolean temporary) {
            switchCalls++;
            requestedMode = mode;
            persistRequested = persist;
            temporaryRequested = temporary;
            currentMode = mode;
            return true;
        }

        @SuppressWarnings("unused")
        public void O() {
            restoreCalls++;
            currentMode = 1;
        }
    }
}
