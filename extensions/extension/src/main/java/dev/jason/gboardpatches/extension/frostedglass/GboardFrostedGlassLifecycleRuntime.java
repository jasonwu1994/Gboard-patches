package dev.jason.gboardpatches.extension.frostedglass;

import android.os.Build;
import android.view.Window;

/** Thin lifecycle adapter; Gboard version bindings stay in the patch module. */
public final class GboardFrostedGlassLifecycleRuntime {
    private GboardFrostedGlassLifecycleRuntime() {}

    private static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void afterConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        if (!supported()) return;
        try {
            GboardFrostedGlassRuntime.afterConfigureWindow(window, fullscreen, candidatesOnly);
        } catch (Throwable ignored) { /* Stock window configuration stays in effect. */ }
    }
    public static void onInputViewStarted(Object receiver) {
        if (!supported()) return;
        try {
            GboardFrostedGlassRuntime.onWindowShown(receiver);
        } catch (Throwable ignored) { /* Never affect showing the IME. */ }
    }
    public static void onInputWindowHidden(Object receiver) {
        if (!supported()) return;
        try {
            GboardFrostedGlassRuntime.onWindowHidden(receiver);
        } catch (Throwable ignored) { /* Never affect hiding the IME. */ }
    }
    /** Entry of the 18.5.4 nav-bar styling method; returns the model to apply. */
    public static Object onApplyNavBarEntry(Window window, Object owner, Object params) {
        if (!supported()) return params;
        try {
            return GboardSeamlessNavBarRuntime.onApplyNavBarEntry(window, owner, params);
        } catch (Throwable ignored) {
            return params;
        }
    }
    /** Before each return of the nav-bar styling method. */
    public static void onApplyNavBarExit(Object params) {
        if (!supported()) return;
        try {
            GboardSeamlessNavBarRuntime.onApplyNavBarExit(params);
        } catch (Throwable ignored) { /* Stock navigation styling stays in effect. */ }
    }
}
