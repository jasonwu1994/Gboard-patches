package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

/** Patched-APK adapter between lifecycle delegates, preferences, and portable runtime. */
public final class GboardCalculatorLifecycleRuntime {
    // Kept local so the feature-scoped RVE never links against shared settings classes.
    private static final String PREF_FILE = "gboard_patches_settings";
    private static final String PREF_KEY_ENABLED = "pref_simple_calculator_enabled";
    private static final boolean DEFAULT_ENABLED = false;

    private GboardCalculatorLifecycleRuntime() {
    }

    public static void onInputViewStarted(Object receiver) {
        boolean enabled = GboardCalculatorSafety.booleanValue(
                () -> readEnabled(receiver), false);
        GboardCalculatorSafety.run(() -> GboardCalculatorRuntime.onInputViewStarted(
                receiver, enabled));
    }

    public static void onSelectionUpdated(Object receiver, View inputView,
            int selectionStart, int selectionEnd) {
        GboardCalculatorSafety.run(() -> GboardCalculatorRuntime.onSelectionUpdated(
                receiver, inputView, selectionStart, selectionEnd));
    }

    public static void onInputWindowHidden() {
        GboardCalculatorSafety.run(GboardCalculatorRuntime::onInputWindowHidden);
    }

    private static boolean readEnabled(Object receiver) {
        if (!(receiver instanceof Context context)) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        SharedPreferences preferences = lookupContext.getSharedPreferences(
                PREF_FILE, Context.MODE_PRIVATE);
        Object raw = preferences.getAll().get(PREF_KEY_ENABLED);
        if (raw instanceof Boolean value) {
            return value.booleanValue();
        }
        if (raw instanceof String value) {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return DEFAULT_ENABLED;
    }
}
