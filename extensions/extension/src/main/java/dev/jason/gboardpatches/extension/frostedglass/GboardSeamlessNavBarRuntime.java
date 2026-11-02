package dev.jason.gboardpatches.extension.frostedglass;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.WeakHashMap;
import androidx.annotation.RequiresApi;
import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

/**
 * Seamless navigation bar integration for Gboard 18.5.4, folded into Frosted Glass.
 * Window navigation styling is taken over only while a frosted nav-strip surface exists.
 * Never changes IME geometry.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class GboardSeamlessNavBarRuntime {
    private static final int EDGE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    private static final Map<Window, State> WINDOWS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);
    // rra.f reuses its Window register before returning; carry the entry Window to the exit.
    private static final ThreadLocal<Window> PENDING = new ThreadLocal<>();
    private static volatile Params cachedParams;

    private GboardSeamlessNavBarRuntime() {}

    public static void validateParams(Class<?> type) throws ReflectiveOperationException { paramsFor(type); }
    public static boolean isReplaying() { return REPLAYING.get(); }

    /** Patched entry of {@code rra.f(Window, rqz)}; returns the model the stock body applies. */
    static Object onApplyNavBarEntry(Window window, Object owner, Object params) {
        if (REPLAYING.get()) return params;
        PENDING.set(window);
        try {
            if (window == null || params == null
                    || !GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) return params;
            if (!GboardFrostedGlassSettingsRuntime.isSeamlessNavBarEnabled()) {
                restore(window);
                return params;
            }
            return beforeApplyNavBar(window, owner, applyMethod(owner, params), params);
        } catch (Throwable ignored) {
            return params;
        }
    }

    /** Patched exit of {@code rra.f}; {@code params} is the model the stock body applied. */
    static void onApplyNavBarExit(Object params) {
        if (REPLAYING.get()) return;
        Window window = PENDING.get();
        PENDING.remove();
        if (window == null || params == null) return;
        try {
            if (!GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) return;
            if (GboardFrostedGlassSettingsRuntime.isSeamlessNavBarEnabled()) afterApplyNavBar(window, params);
            else restore(window);
        } catch (Throwable ignored) {
            // Styling never changes a stock result.
        }
    }

    /** The patched void (Window, model) method on the owner, used to replay stock paint. */
    static Method applyMethod(Object owner, Object params) {
        if (owner == null || params == null) return null;
        Method found = null;
        for (Method method : owner.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || types.length != 2 || types[0] != Window.class
                    || types[1] != params.getClass()) continue;
            if (found != null) return null; // Ambiguous: never replay a guessed method.
            found = method;
        }
        if (found != null) found.setAccessible(true);
        return found;
    }

    public static Object beforeApplyNavBar(Window window, Object owner, Method method, Object original) {
        if (window == null || original == null
                || !GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) return original;
        try {
            Params fields = paramsFor(original.getClass());
            State state = stateFor(window);
            state.owner = new WeakReference<>(owner);
            state.method = method;
            state.original = original;
            // Remember the stock model for a later successful glass surface, but do
            // not sample colors or acquire Window styling while stock owns the area.
            if (!state.glass) return original;
            int nav = fields.nav.getInt(original);
            int divider = fields.divider.getInt(original);
            boolean light = fields.light.getBoolean(original);
            if (state.glass) {
                nav = divider = Color.TRANSPARENT;
                light = dayIcons(window, light);
            }
            if (nav == fields.nav.getInt(original) && divider == fields.divider.getInt(original)
                    && light == fields.light.getBoolean(original)) return original;
            // rqz is immutable and may be retained by stock code. Replace the call argument;
            // never overwrite its final fields or poison a cached stock theme model.
            return fields.constructor.newInstance(nav, divider, fields.body.get(original),
                    fields.header.get(original), light);
        } catch (Throwable ignored) {
            return original;
        }
    }

    public static void afterApplyNavBar(Window window, Object params) {
        if (window == null || params == null
                || !GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) return;
        try {
            Params fields = paramsFor(params.getClass());
            State state = stateFor(window);
            if (!state.glass) return;
            applyWindow(window, state, fields.nav.getInt(params), fields.divider.getInt(params),
                    fields.light.getBoolean(params));
        } catch (Throwable ignored) {
            restore(window);
        }
    }

    /** Called only after a real keyboard-local glass backend has succeeded. */
    public static boolean onFrostedSurfaceApplied(Window window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        if (window == null || !GboardFlagRuntimeContext.isUserUnlocked(window.getContext())) return false;
        try {
            State state = stateFor(window);
            boolean starting = !state.glass;
            if (starting) state.captureWindow(window);
            state.glass = true;
            if (starting && state.original != null && state.method != null) {
                Object owner = state.owner == null ? null : state.owner.get();
                if (owner != null) {
                    Object params = beforeApplyNavBar(window, owner, state.method, state.original);
                    REPLAYING.set(true);
                    try { state.method.invoke(owner, window, params); }
                    catch (ReflectiveOperationException ignored) { /* Retain the local glass overlay. */ }
                    finally { REPLAYING.remove(); }
                }
            }
            applyWindow(window, state, Color.TRANSPARENT, Color.TRANSPARENT,
                    dayIcons(window, stockLightIcons(state)));
            return true;
        } catch (Throwable ignored) {
            restore(window);
            return false;
        }
    }

    private static boolean stockLightIcons(State state) {
        try {
            if (state.original != null) return paramsFor(state.original.getClass()).light.getBoolean(state.original);
        } catch (Throwable ignored) { /* Use the captured Window appearance. */ }
        return state.stockLightIcons;
    }

    private static boolean dayIcons(Window window, boolean stock) {
        return (window.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES ? stock : true;
    }

    private static void applyWindow(Window window, State state, int nav, int divider, boolean light) {
        if ((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) == 0) {
            state.addedDrawsBackground = true;
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (window.getNavigationBarColor() != nav) window.setNavigationBarColor(nav);
        if (window.getNavigationBarDividerColor() != divider) window.setNavigationBarDividerColor(divider);
        if (window.isNavigationBarContrastEnforced()) window.setNavigationBarContrastEnforced(false);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (state.glass && Build.VERSION.SDK_INT < 36) {
            state.addedUiFlags |= EDGE_FLAGS & ~flags;
            if ((flags & EDGE_FLAGS) != EDGE_FLAGS) decor.setSystemUiVisibility(flags | EDGE_FLAGS);
        } else if (state.addedUiFlags != 0) {
            decor.setSystemUiVisibility(flags & ~state.addedUiFlags);
            state.addedUiFlags = 0;
        }
        WindowInsetsController controller = window.getInsetsController();
        int mask = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        int appearance = light ? mask : 0;
        if (controller != null && (controller.getSystemBarsAppearance() & mask) != appearance)
            controller.setSystemBarsAppearance(appearance, mask);
    }

    public static void restore(Window window) {
        State state;
        synchronized (WINDOWS) { state = WINDOWS.remove(window); }
        if (state == null || !state.glass) return;
        try {
            Object owner = state.owner == null ? null : state.owner.get();
            if (owner != null && state.method != null && state.original != null) {
                REPLAYING.set(true);
                try { state.method.invoke(owner, window, state.original); }
                finally { REPLAYING.remove(); }
            }
        } catch (Throwable ignored) {
            // Stock input paint restoration is best effort; Window cleanup still runs.
        }
        int nav = state.nav, divider = state.divider;
        try {
            if (state.original != null) {
                Params fields = paramsFor(state.original.getClass());
                nav = fields.nav.getInt(state.original);
                divider = fields.divider.getInt(state.original);
            }
        } catch (Throwable ignored) { /* Retain the original Window colors. */ }
        int navColor = nav, dividerColor = divider;
        cleanupStep(() -> window.setNavigationBarColor(navColor));
        cleanupStep(() -> window.setNavigationBarDividerColor(dividerColor));
        cleanupStep(() -> window.setNavigationBarContrastEnforced(state.contrast));
        cleanupStep(() -> {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~state.addedUiFlags);
        });
        if (state.addedDrawsBackground) cleanupStep(() ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS));
        cleanupStep(() -> {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    stockLightIcons(state) ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
    }

    private static void cleanupStep(Runnable step) {
        try { step.run(); }
        catch (Throwable ignored) { /* One unsupported Window operation must not abort cleanup. */ }
    }

    private static Params paramsFor(Class<?> type) throws ReflectiveOperationException {
        Params cached = cachedParams;
        if (cached != null && cached.type == type) return cached;
        Params created = new Params(type);
        cachedParams = created;
        return created;
    }

    private static State stateFor(Window window) {
        synchronized (WINDOWS) {
            State state = WINDOWS.get(window);
            if (state == null) { state = new State(); WINDOWS.put(window, state); }
            return state;
        }
    }

    private static final class Params {
        final Class<?> type;
        final Field nav, divider, body, header, light;
        final Constructor<?> constructor;
        Params(Class<?> type) throws ReflectiveOperationException {
            this.type = type;
            nav = field(type, "a", int.class);
            divider = field(type, "b", int.class);
            body = type.getDeclaredField("c");
            header = field(type, "d", body.getType());
            light = field(type, "e", boolean.class);
            if (!Modifier.isFinal(body.getModifiers()) || Modifier.isStatic(body.getModifiers()))
                throw new NoSuchFieldException("c must be a final instance field");
            body.setAccessible(true);
            Method present = body.getType().getMethod("isPresent");
            if (present.getReturnType() != boolean.class) throw new NoSuchMethodException("isPresent");
            constructor = type.getDeclaredConstructor(int.class, int.class, body.getType(), body.getType(), boolean.class);
            constructor.setAccessible(true);
        }
        private static Field field(Class<?> type, String name, Class<?> valueType) throws ReflectiveOperationException {
            Field field = type.getDeclaredField(name);
            if (field.getType() != valueType || Modifier.isStatic(field.getModifiers())
                    || !Modifier.isFinal(field.getModifiers())) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static final class State {
        int nav, divider;
        boolean contrast, stockLightIcons;
        boolean glass, addedDrawsBackground;
        int addedUiFlags;
        WeakReference<Object> owner;
        Method method;
        Object original;
        void captureWindow(Window window) {
            nav = window.getNavigationBarColor();
            divider = window.getNavigationBarDividerColor();
            contrast = window.isNavigationBarContrastEnforced();
            WindowInsetsController controller = window.getInsetsController();
            int appearance = controller == null ? 0 : controller.getSystemBarsAppearance();
            stockLightIcons = (appearance & WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0;
        }
    }
}
