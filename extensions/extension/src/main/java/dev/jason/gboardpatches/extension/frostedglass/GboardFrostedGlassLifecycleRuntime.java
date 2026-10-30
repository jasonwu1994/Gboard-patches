package dev.jason.gboardpatches.extension.frostedglass;

import android.view.Window;

/** Thin lifecycle adapter; Gboard version bindings stay in the patch module. */
public final class GboardFrostedGlassLifecycleRuntime {
    private GboardFrostedGlassLifecycleRuntime() {}
    public static void afterConfigureWindow(Window window, boolean fullscreen, boolean candidatesOnly) {
        GboardFrostedGlassRuntime.afterConfigureWindow(window, fullscreen, candidatesOnly);
    }
    public static void onInputViewStarted(Object receiver) {
        GboardFrostedGlassRuntime.onWindowShown(receiver);
    }
    public static void onInputWindowHidden(Object receiver) {
        GboardFrostedGlassRuntime.onWindowHidden(receiver);
    }
    /** Entry of the 18.5.4 nav-bar styling method; returns the model to apply. */
    public static Object onApplyNavBarEntry(Window window, Object owner, Object params) {
        return GboardSeamlessNavBarRuntime.onApplyNavBarEntry(window, owner, params);
    }
    /** Before each return of the nav-bar styling method. */
    public static void onApplyNavBarExit(Object params) {
        GboardSeamlessNavBarRuntime.onApplyNavBarExit(params);
    }
}
