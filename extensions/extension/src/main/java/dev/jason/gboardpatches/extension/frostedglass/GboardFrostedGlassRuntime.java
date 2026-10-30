package dev.jason.gboardpatches.extension.frostedglass;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.util.Log;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.IdentityHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

/** Android public-API runtime for a bounded IME background blur. */
public final class GboardFrostedGlassRuntime {
    private static final String TAG = "GboardPatchesBlur";
    private static final String KEYBOARD_ROOT_TAG = ".keyboard-base-area.keyboard-outline";
    // Floating mode uses the same semantic root without the docked outline suffix.
    private static final String FLOATING_KEYBOARD_ROOT_TAG = ".keyboard-base-area";
    // A decor layout callback fires for every layout pass (e.g. each candidate update),
    // not only for geometry changes. Settings are refreshed on show/configure and at most
    // this often from layout, so a change made while the keyboard stays open still lands.
    private static final long SETTINGS_REFRESH_MS = 2000L;
    private static final long FIRST_FALLBACK_MS = 48L;
    private static final long SECOND_FALLBACK_MS = 144L;
    // Since Android 11 the IME gets onWindowHidden when its hide animation starts, so the
    // window keeps drawing while it slides out. Keep the glass until that animation ends.
    private static final long HIDE_RESTORE_MS = 500L;
    private static final int TARGET_SURFACE = 1;
    private static final int TARGET_KEY = 2;
    private static final int TARGET_ACTION_KEY = 4;
    private static final Map<Window, State> STATES = new WeakHashMap<>();
    private static final Map<Window, Boolean> CONFIGURED_EXCLUSIONS = new WeakHashMap<>();
    private static final AtomicBoolean HIDDEN_API_READY = new AtomicBoolean();
    private static volatile WeakReference<InputMethodService> activeService =
            new WeakReference<>(null);

    private GboardFrostedGlassRuntime() {
    }

    public static void onConfigureWindow(Object receiver, boolean fullscreen,
            boolean candidatesOnly) {
        try {
            if (!(receiver instanceof InputMethodService service)) {
                return;
            }
            activeService = new WeakReference<>(service);
            if (!isUserUnlocked(service)) {
                return;
            }
            Window window = windowOf(service);
            if (window == null) {
                return;
            }
            synchronized (STATES) {
                CONFIGURED_EXCLUSIONS.put(window, fullscreen || candidatesOnly);
            }
            if (!GboardFrostedGlassSettingsRuntime.isEnabled()
                    || fullscreen || candidatesOnly) {
                disable(window);
                return;
            }
            ensureHiddenApiAccess();
            if (canUseBackgroundBlur(service)) scheduleApply(window, service);
            else prepareBeforeDraw(window, service);
        } catch (Throwable ignored) {
            // Lifecycle entry points must fail closed and never crash the IME.
        }
    }

    public static void afterConfigureWindow(Window window, boolean fullscreen,
            boolean candidatesOnly) {
        try {
            if (window == null) {
                return;
            }
            InputMethodService service = activeService.get();
            Context context = service != null ? service : contextOf(window);
            if (!isUserUnlocked(context)) {
                return;
            }
            synchronized (STATES) {
                CONFIGURED_EXCLUSIONS.put(window, fullscreen || candidatesOnly);
            }
            if (!GboardFrostedGlassSettingsRuntime.isEnabled()
                    || fullscreen || candidatesOnly || context == null) {
                disable(window);
                return;
            }
            ensureHiddenApiAccess();
            if (canUseBackgroundBlur(context)) scheduleApply(window, context);
            else prepareBeforeDraw(window, context);
        } catch (Throwable ignored) {
            // Lifecycle entry points must fail closed and never crash the IME.
        }
    }

    public static void onWindowShown(Object receiver) {
        try {
            if (!(receiver instanceof InputMethodService service)) {
                return;
            }
            activeService = new WeakReference<>(service);
            if (!isUserUnlocked(service)) {
                return;
            }
            Window window = windowOf(service);
            if (window == null) {
                return;
            }
            if (!GboardFrostedGlassSettingsRuntime.isEnabled()
                    || isSurfaceExcluded(window, service)) {
                disable(window);
                return;
            }
            ensureHiddenApiAccess();
            if (canUseBackgroundBlur(service)) scheduleApply(window, service);
            else prepareBeforeDraw(window, service);
        } catch (Throwable ignored) {
            // Lifecycle entry points must fail closed and never crash the IME.
        }
    }

    public static void onWindowHidden(Object receiver) {
        try {
            if (!(receiver instanceof InputMethodService service)) {
                activeService = new WeakReference<>(null);
                return;
            }
            if (!isUserUnlocked(service)) {
                activeService = new WeakReference<>(null);
                return;
            }
            Window window = windowOf(service);
            if (window != null) {
                hideAfterAnimation(window);
            }
            activeService = new WeakReference<>(null);
        } catch (Throwable ignored) {
            activeService = new WeakReference<>(null);
        }
    }

    /**
     * Stops all per-frame work at once, but restores the stock theme only after the hide
     * animation; restoring now would flash the opaque theme while the keyboard slides out.
     */
    private static void hideAfterAnimation(Window window) {
        State state;
        synchronized (STATES) {
            state = STATES.get(window);
        }
        View decor = window.getDecorView();
        long delay = hideRestoreDelayMs();
        if (state == null || decor == null || delay <= 0L) {
            disable(window);
            return;
        }
        ++state.layoutGeneration;
        state.hiding = true;
        cleanupStep(() -> cancelFallback(state));
        cleanupStep(() -> detachObservers(state));
        cancelHideRestore(state);
        Runnable restore = () -> {
            state.hideRestore = null;
            if (state.hiding && isCurrent(window, state)) disable(window);
        };
        state.hideRestore = restore;
        state.hideRestoreHost = decor;
        decor.postDelayed(restore, delay);
    }

    private static long hideRestoreDelayMs() {
        float scale = 1f;
        try {
            scale = android.animation.ValueAnimator.getDurationScale();
        } catch (Throwable ignored) {
            // Use the unscaled duration.
        }
        return scale <= 0f ? 0L : Math.round(HIDE_RESTORE_MS * scale);
    }

    private static void cancelHideRestore(State state) {
        if (state.hideRestore != null && state.hideRestoreHost != null) {
            state.hideRestoreHost.removeCallbacks(state.hideRestore);
        }
        state.hideRestore = null;
    }

    /** A show during the hide animation keeps the existing glass; no restore/reapply flash. */
    private static void resumeFromHide(State state) {
        if (!state.hiding) return;
        cancelHideRestore(state);
        state.hiding = false;
        state.structureDirty = true;
    }

    private static void detachObservers(State state) {
        if (state.preDrawTree != null && state.preDrawTree.isAlive())
            state.preDrawTree.removeOnPreDrawListener(state.preDrawObserver);
        if (state.observedDecor != null && state.preDrawObserver != null) {
            ViewTreeObserver currentTree = state.observedDecor.getViewTreeObserver();
            if (currentTree.isAlive()) currentTree.removeOnPreDrawListener(state.preDrawObserver);
        }
        if (state.decorLayoutObserverInstalled && state.observedDecor != null)
            state.observedDecor.removeOnLayoutChangeListener(state.decorLayoutObserver);
        state.preDrawTree = null;
        state.preDrawObserver = null;
        state.decorLayoutObserverInstalled = false;
        state.decorLayoutObserver = null;
    }

    static boolean isUserUnlocked(Context context) {
        return GboardFlagRuntimeContext.isUserUnlocked(context);
    }

    private static void reapplyAfterLayout(Window window, Context context,
            State state, long layoutGeneration) {
        if (!isCurrent(window, state) || state.layoutGeneration != layoutGeneration
                || !isUserUnlocked(context)) {
            return;
        }
        try {
            verifyAndApply(window, context, state);
        } catch (Throwable ignored) {
            // Delayed theme reapplication must remain fail closed.
        }
    }

    /** One coalesced fallback per state, only while the keyboard is not prepared yet. */
    private static void scheduleFallback(View host, Window window, Context context,
            State state, long generation) {
        cancelFallback(state);
        Runnable second = () -> {
            state.fallback = null;
            if (!state.ready) reapplyAfterLayout(window, context, state, generation);
        };
        Runnable first = () -> {
            state.fallback = null;
            if (state.ready) return;
            reapplyAfterLayout(window, context, state, generation);
            if (!state.ready && isCurrent(window, state) && state.layoutGeneration == generation) {
                state.fallback = second;
                host.postDelayed(second, SECOND_FALLBACK_MS - FIRST_FALLBACK_MS);
            }
        };
        state.fallback = first;
        state.fallbackHost = host;
        host.postDelayed(first, FIRST_FALLBACK_MS);
    }

    private static void cancelFallback(State state) {
        if (state.fallback != null && state.fallbackHost != null) {
            state.fallbackHost.removeCallbacks(state.fallback);
        }
        state.fallback = null;
    }

    private static void installDecorLayoutObserver(Window window,
            Context context, View decor, State state) {
        installPreDrawObserver(window, context, decor, state);
        if (state.decorLayoutObserverInstalled) {
            return;
        }
        View.OnLayoutChangeListener listener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            try {
                if (!isCurrent(window, state) || state.hiding || !isUserUnlocked(context)) {
                    return;
                }
                long generation = ++state.layoutGeneration;
                // Any view added or shown is laid out before it can draw, so this is the
                // structure signal. The pre-draw of this same traversal applies the result.
                state.structureDirty = true;
                if (SystemClock.uptimeMillis() - state.settingsLoadedAt >= SETTINGS_REFRESH_MS) {
                    state.settingsLoaded = false;
                }
                // An observer obtained before attach dies when merged; keep pre-draw alive.
                if (state.preDrawTree != view.getViewTreeObserver()) {
                    installPreDrawObserver(window, context, view, state);
                }
                if (!state.ready) scheduleFallback(view, window, context, state, generation);
            } catch (Throwable ignored) {
                // OEM/old-platform observer failures must not escape a layout traversal.
                cleanupStep(() -> disable(window));
            }
        };
        decor.addOnLayoutChangeListener(listener);
        state.observedDecor = decor;
        state.decorLayoutObserverInstalled = true;
        state.decorLayoutObserver = listener;
    }

    private static boolean isSurfaceExcluded(Window window, Context context) {
        synchronized (STATES) {
            return Boolean.TRUE.equals(CONFIGURED_EXCLUSIONS.get(window)) || (context instanceof InputMethodService service && service.isFullscreenMode());
        }
    }

    private static void installPreDrawObserver(Window window, Context context,
            View decor, State state) {
        ViewTreeObserver tree = decor.getViewTreeObserver();
        if (state.preDrawTree == tree) return;
        if (state.preDrawTree != null && state.preDrawTree.isAlive()) {
            state.preDrawTree.removeOnPreDrawListener(state.preDrawObserver);
        }
        // An observer obtained before attach is merged into the attached tree and dies.
        // Remove its transferred listener before registering on that new tree.
        if (state.preDrawObserver != null) tree.removeOnPreDrawListener(state.preDrawObserver);
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            try {
                // The single per-frame unlock query; a locked frame skips without disposing.
                if (isCurrent(window, state) && !state.hiding && isUserUnlocked(context)) {
                    applyUnlocked(window, context, state);
                }
            } catch (Throwable ignored) {
                cleanupStep(() -> disable(window));
            }
            // Never delay showing a keyboard while waiting for an OEM compositor.
            return true;
        };
        tree.addOnPreDrawListener(listener);
        state.preDrawTree = tree;
        state.preDrawObserver = listener;
    }

    private static boolean canUseBackgroundBlur(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null) {
            return false;
        }
        try {
            WindowManager manager = context.getSystemService(WindowManager.class);
            return manager != null && manager.isCrossWindowBlurEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void scheduleApply(Window window, Context context) {
        try {
            State state = stateFor(window);
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            View decor = window.getDecorView();
            if (decor == null) {
                disable(window);
                return;
            }
            constrainKeyboardSurface(decor, state);
            prepareBeforeDraw(window, context);
        } catch (Throwable ignored) {
            disable(window);
        }
    }

    private static void prepareBeforeDraw(Window window, Context context) {
        View decor = window.getDecorView();
        if (decor == null) return;
        State state = stateFor(window);
        resumeFromHide(state);
        state.settingsLoaded = false;
        state.structureDirty = true;
        installDecorLayoutObserver(window, context, decor, state);
        verifyAndApply(window, context, state);
    }

    private static void verifyAndApply(Window window, Context context, State state) {
        if (!isCurrent(window, state)) return;
        if (!isUserUnlocked(context)) {
            disable(window);
            return;
        }
        applyUnlocked(window, context, state);
    }

    /** Caller has just checked {@link #isUserUnlocked}; this avoids a second query per frame. */
    private static void applyUnlocked(Window window, Context context, State state) {
        try {
            if (!isCurrent(window, state) || state.hiding) return;
            if (isSurfaceExcluded(window, context)) {
                disable(window);
                return;
            }
            // A draw callback never reloads CE/remote preferences. Show/configure and a
            // throttled layout refresh invalidate this snapshot; an unavailable setting
            // disables only this session.
            if (!state.settingsLoaded) {
                if (!GboardFrostedGlassSettingsRuntime.isEnabled()) {
                    disable(window);
                    return;
                }
                state.radiusPx = GboardFrostedGlassSettingsRuntime.blurRadiusPx();
                state.customOpacity = GboardFrostedGlassSettingsRuntime.customOpacityPercent();
                state.customKeyOpacity = GboardFrostedGlassSettingsRuntime.customKeyOpacity();
                state.navigationEnabled = GboardFrostedGlassSettingsRuntime.isSeamlessNavBarEnabled();
                state.settingsLoaded = true;
                state.settingsLoadedAt = SystemClock.uptimeMillis();
            }
            state.ready = false;
            ++state.pass;
            int radiusPx = state.radiusPx;
            int customOpacity = state.customOpacity;
            View decor = window.getDecorView();
            // The first window layout can precede InputView inflation. Keep the observer
            // alive until the semantic root arrives instead of disposing this session.
            View root = resolveKeyboardRoot(decor, state);
            if (root == null || !root.isAttachedToWindow()) return;
            if (state.windowBackgroundChanged && state.windowBlurRoot == root) {
                GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(root, root.getBackground());
                if (canUseBackgroundBlur(context) && isBoundedSurface(window)
                        && canUseWindowShape(window, root, shape, state.location)) {
                    applyWindowBlur(window, root, shape, radiusPx, customOpacity, state);
                    state.ready = true;
                    return;
                }
                window.setBackgroundBlurRadius(0);
                state.windowBlurApplied = false;
                window.setBackgroundDrawable(state.originalWindowBackground);
                state.windowBackgroundChanged = false;
            }
            // Prefer the keyboard-local compositor surface even for bounded windows: a
            // Window outline cannot express asymmetric corners or exclude the nav area.
            if (applyMiuiLocalBlur(window, decor, root, radiusPx, customOpacity, state)) {
                state.ready = true;
                return;
            }
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(root, root.getBackground());
            if (!canUseBackgroundBlur(context)
                    || state.localBlurDrawable != null || state.localRenderNode != null
                    || !isBoundedSurface(window) || !canUseWindowShape(window, root, shape, state.location)) {
                disable(window);
                return;
            }
            applyWindowBlur(window, root, shape, radiusPx, customOpacity, state);
            state.ready = true;
        } catch (Throwable ignored) {
            disable(window);
        }
    }

    private static void applyWindowBlur(Window window, View root, GboardFrostedGlassShape shape,
            int radiusPx, int customOpacity, State state) {
        if (!state.windowBackgroundChanged) {
            state.originalWindowBackground = window.getDecorView().getBackground();
        }
        applyThemeOpacity(window.getDecorView(), root, customOpacity, state);
        if (!state.windowBackgroundChanged || !shape.sameGeometry(state.windowShape)) {
            window.setBackgroundDrawable(transparentBackground(shape));
        }
        if (!state.windowBackgroundChanged || state.windowRadiusPx != radiusPx) {
            window.setBackgroundBlurRadius(radiusPx);
        }
        state.windowBackgroundChanged = true;
        state.windowBlurApplied = true;
        state.windowBlurRoot = root;
        state.windowShape = shape;
        state.windowRadiusPx = radiusPx;
        state.applied = true;
    }

    static boolean isBoundedSurface(int surfaceHeight, int displayHeight) {
        return surfaceHeight > 0 && displayHeight > 0
                && surfaceHeight < Math.round(displayHeight * 0.80f);
    }

    private static boolean isBoundedSurface(Window window) {
        View decor = window.getDecorView();
        if (decor == null || decor.getHeight() <= 0) {
            return false;
        }
        DisplayMetrics metrics = new DisplayMetrics();
        try {
            window.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        } catch (Throwable ignored) {
            metrics.heightPixels = decor.getRootView().getHeight();
        }
        return isBoundedSurface(decor.getHeight(), metrics.heightPixels);
    }

    private static State stateFor(Window window) {
        synchronized (STATES) {
            State state = STATES.get(window);
            if (state == null) {
                state = new State(window);
                STATES.put(window, state);
            }
            return state;
        }
    }

    /** Host-test entry: resolves the root itself, then runs one normal apply pass. */
    private static boolean applyMiuiLocalBlur(Window window, View decor, int radiusPx,
            int customOpacity, State state) {
        ++state.pass;
        return applyMiuiLocalBlur(window, decor, resolveKeyboardRoot(decor, state),
                radiusPx, customOpacity, state);
    }

    private static boolean applyMiuiLocalBlur(Window window, View decor, View root, int radiusPx,
            int customOpacity, State state) {
        boolean applied = applyKeyboardLocalBlur(window, decor, root, radiusPx, customOpacity, state);
        boolean navigationApplied = false;
        if (applied && state.navigationEnabled) {
            Drawable theme = state.localBlurDrawable != null
                    ? state.localOriginalBackground : root == null ? null : root.getBackground();
            resolveNavigation(root, theme, state);
            navigationApplied = state.navigation.update(root, state.navInput, state.navTint, radiusPx);
            if (!navigationApplied && state.navInput != null && NavigationSurface.supportsTint(state.navTint)
                    && !"strip backdrop unavailable".equals(state.navReason)) {
                state.navReason = "strip backdrop unavailable";
                Log.i(TAG, "navigation kept stock: " + state.navReason);
            }
        } else {
            state.navigation.clear();
            if (applied && !"disabled by setting".equals(state.navReason)) {
                state.navReason = "disabled by setting";
                Log.i(TAG, "navigation kept stock: disabled by setting");
            }
        }
        // Only join contours after the navigation backdrop actually exists. Reconfigure
        // on ownership transitions, including clone/backend failure after an earlier join.
        if (state.navigationJoined != navigationApplied) {
            state.navigationJoined = navigationApplied;
            if (applied) {
                applied = applyKeyboardLocalBlur(window, decor, root, radiusPx, customOpacity, state);
                if (!applied) {
                    state.navigationJoined = navigationApplied = false;
                    state.navigation.clear();
                }
            }
        }
        if (state.settingsLoaded) {
            boolean wantGlass = applied && navigationApplied && root != null
                    && KEYBOARD_ROOT_TAG.equals(root.getTag());
            // Window navigation styling is owned per transition. Gboard's own nav-color
            // refresh is re-styled by the rra.f hook; layouts (configuration/inset changes)
            // re-assert it here. A stable frame performs no Window reads or writes.
            if (wantGlass) {
                if (!Boolean.TRUE.equals(state.seamlessGlass) || state.structurePass == state.pass) {
                    if (!GboardSeamlessNavBarRuntime.onFrostedSurfaceApplied(window)) {
                        // Keep the panel blur, but do not retry an unsupported Window every draw.
                        // A later show/configure/layout refresh may admit navigation again.
                        state.navigationEnabled = state.navigationJoined = false;
                        state.navigation.clear();
                        state.seamlessGlass = Boolean.FALSE;
                        GboardSeamlessNavBarRuntime.restore(window);
                        return applyKeyboardLocalBlur(window, decor, root, radiusPx, customOpacity, state);
                    }
                    state.seamlessGlass = Boolean.TRUE;
                }
            } else if (!Boolean.FALSE.equals(state.seamlessGlass)) {
                GboardSeamlessNavBarRuntime.restore(window);
                state.seamlessGlass = Boolean.FALSE;
            }
        }
        return applied;
    }

    /** Resolves the navigation admission and tint once per apply pass. */
    private static void resolveNavigation(View root, Drawable original, State state) {
        if (state.navPass == state.pass && state.navRoot == root && state.navOriginal == original) return;
        state.navPass = state.pass;
        state.navRoot = root;
        state.navOriginal = original;
        state.navInput = state.navigationEnabled
                ? NavigationSurface.navigationInput(root, state.location, state.inputLocation) : null;
        state.navTint = state.navInput == null ? null : navigationTint(root, original, state);
        String reason = !state.navigationEnabled ? "disabled by setting"
                : state.navInput == null ? NavigationSurface.rejectReason
                : !NavigationSurface.supportsTint(state.navTint) ? "unsupported tint " + describe(state.navTint)
                : null;
        if (!java.util.Objects.equals(reason, state.navReason)) {
            state.navReason = reason;
            Log.i(TAG, reason == null ? "navigation admitted" : "navigation kept stock: " + reason);
        }
    }

    private static String describe(Drawable drawable) {
        if (drawable == null) return "null";
        if (drawable instanceof GradientDrawable gradient) {
            return "GradientDrawable shape=" + gradient.getShape() + " color="
                    + (gradient.getColor() != null) + " state=" + (drawable.getConstantState() != null);
        }
        return drawable.getClass().getName();
    }

    private static Drawable navigationTheme(View root, State state) {
        // Same answer as a depth-first search of root for the first visible body area with a
        // background, served from the discovered surface targets (kept in DFS order).
        if (root == null || root.getVisibility() != View.VISIBLE) return null;
        for (int i = 0; i < state.targets.size(); i++) {
            Target target = state.targets.get(i);
            if ((target.kinds & TARGET_SURFACE) == 0
                    || !target.tag.contains(".keyboard-body-area")) continue;
            View view = target.view.get();
            if (view == null || view.getBackground() == null) continue;
            if (visibleWithin(view, root)) return view.getBackground();
        }
        return null;
    }

    private static boolean visibleWithin(View view, View root) {
        View current = view;
        while (current != null) {
            if (current.getVisibility() != View.VISIBLE) return false;
            if (current == root) return true;
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    private static Drawable navigationTint(View root, Drawable theme, State state) {
        // Official color themes may intentionally give the outline/header a different
        // shade. Continue the key area's actual palette and its live color filter.
        Drawable body = navigationTheme(root, state);
        return opacityTarget(body != null ? body : theme);
    }

    private static boolean applyKeyboardLocalBlur(Window window, View decor, View keyboardRoot,
            int radiusPx, int customOpacity, State state) {
        try {
            if (keyboardRoot == null) return false;
            // Surface targets (and the navigation body tint) come from one discovery.
            ensureTargets(decor, keyboardRoot, state);
            // A theme can replace the background on the same View. Never use our own
            // composite as a new theme source or stack another blur layer on it.
            if (state.localBlurDrawable != null
                    && (state.localBlurView != keyboardRoot
                    || keyboardRoot.getBackground() != state.localCombinedBackground)) {
                restoreLocalBackground(state);
            }
            Drawable original = state.localBlurView == keyboardRoot
                    && state.localBlurDrawable != null
                    ? state.localOriginalBackground : keyboardRoot.getBackground();
            GboardFrostedGlassShape shape = GboardFrostedGlassShape.resolve(keyboardRoot, original);
            if (shape == null) return false;
            // The lower edge is internal when the glass continues into the nav area.
            // Use a session-owned shape; never rewrite the theme or its saved radii.
            boolean joined = false;
            if (state.navigationEnabled && state.navigationJoined) {
                resolveNavigation(keyboardRoot, original, state);
                joined = state.navInput != null && NavigationSurface.supportsTint(state.navTint)
                        && shape.bounds.left == 0 && shape.bounds.top == 0
                        && shape.bounds.right == keyboardRoot.getWidth()
                        && shape.bounds.bottom == keyboardRoot.getHeight()
                        && (shape.bottomLeft > 0 || shape.bottomRight > 0);
            }
            if (joined) shape = shape.withoutBottomCorners();
            applyThemeOpacity(decor, keyboardRoot, customOpacity, state);
            if (state.localRenderNode != null && state.localRenderRoot == keyboardRoot
                    && state.localRadiusPx == radiusPx && shape.sameGeometry(state.localShape)) {
                return state.applied;
            }
            if (state.localBlurView == keyboardRoot && state.localBlurDrawable != null) {
                if (state.localRadiusPx != radiusPx || !shape.sameGeometry(state.localShape)) {
                    configureBlurDrawable(state.localBlurDrawable, radiusPx, shape);
                    ((ThemeBlurBackground) state.localCombinedBackground).updateBounds(
                            shape, keyboardRoot.getWidth(), keyboardRoot.getHeight(), joined);
                    state.localRadiusPx = radiusPx;
                    state.localShape = shape;
                    keyboardRoot.invalidate();
                    keyboardRoot.invalidateOutline();
                }
                return true;
            }
            Object blurDrawable = null;
            try {
                blurDrawable = createBackgroundBlurDrawable(keyboardRoot, radiusPx, shape);
            } catch (ReflectiveOperationException unavailable) {
                // OEM RenderNode is a separate fallback, not a reason to invent geometry.
            }
            if (blurDrawable != null) {
                // A previous backend may have covered a larger surface. Do not leave it
                // underneath a newly available keyboard-local drawable.
                if (state.localRenderNode != null || state.windowBackgroundChanged) {
                    clearRenderNode(state);
                    window.setBackgroundBlurRadius(0);
                    state.windowBlurApplied = false;
                    if (state.windowBackgroundChanged) {
                        window.setBackgroundDrawable(state.originalWindowBackground);
                        state.windowBackgroundChanged = false;
                    }
                }
                ThemeBlurBackground combined = new ThemeBlurBackground(original, (Drawable) blurDrawable);
                combined.updateBounds(shape, keyboardRoot.getWidth(), keyboardRoot.getHeight(), joined);
                keyboardRoot.setBackground(combined);
                keyboardRoot.invalidate();
                state.localBlurView = keyboardRoot;
                state.localBlurDrawable = blurDrawable;
                state.localOriginalBackground = original;
                state.localCombinedBackground = combined;
                state.localRadiusPx = radiusPx;
                state.localShape = shape;
                state.applied = true;
                Log.i(TAG, "applied local radius=" + radiusPx + " corners="
                        + shape.topLeft + "," + shape.topRight + ","
                        + shape.bottomLeft + "," + shape.bottomRight);
                return true;
            }
            View rootDecor = keyboardRoot.getRootView();
            if (rootDecor == null) {
                return false;
            }
            Field renderNodeField = View.class.getDeclaredField("mRenderNode");
            renderNodeField.setAccessible(true);
            Object renderNode = renderNodeField.get(rootDecor);
            if (renderNode == null) {
                return false;
            }
            Class<?> renderNodeClass = renderNode.getClass();
            Method setMode = renderNodeClass.getDeclaredMethod(
                    "setBackgroundBlurMode", int.class);
            Method setRadius = renderNodeClass.getDeclaredMethod(
                    "setBackgroundBlurRadius", int.class);
            Method setScale = renderNodeClass.getDeclaredMethod(
                    "setBackgroundBlurScaleRatio", float.class);
            Method setPath = renderNodeClass.getDeclaredMethod(
                    "setMiBackgroundBlurPath", Path.class);
            setMode.setAccessible(true);
            setRadius.setAccessible(true);
            setScale.setAccessible(true);
            setPath.setAccessible(true);
            int[] keyboardLocation = new int[2];
            int[] decorLocation = new int[2];
            keyboardRoot.getLocationInWindow(keyboardLocation);
            rootDecor.getLocationInWindow(decorLocation);
            float left = keyboardLocation[0] - decorLocation[0];
            float top = keyboardLocation[1] - decorLocation[1];
            Path blurPath = shape.path(left, top);
            // Save before the first mutation so even a partially failing OEM call is cleaned up.
            state.localBlurView = rootDecor;
            state.localRenderNode = renderNode;
            boolean modeApplied = Boolean.TRUE.equals(setMode.invoke(renderNode, 1));
            boolean radiusApplied = Boolean.TRUE.equals(setRadius.invoke(renderNode, radiusPx));
            setScale.invoke(renderNode, 0.5f);
            setPath.invoke(renderNode, blurPath);
            try {
                Method enhance = renderNodeClass.getDeclaredMethod(
                        "setMiBackgroundBlurEnhanceFlag", int.class);
                enhance.setAccessible(true);
                enhance.invoke(renderNode, 1);
            } catch (Throwable ignored) {
                // Enhancement is optional; mode + radius own the effect.
            }
            // Xiaomi's RenderNode path only becomes a backdrop sample when the Window
            // compositor blur is enabled as well. The path above clips it to the keyboard root.
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(radiusPx);
            state.windowBlurApplied = true;
            rootDecor.invalidate();
            state.localBlurView = rootDecor;
            state.localRenderNode = renderNode;
            state.applied = modeApplied || radiusApplied;
            if (state.applied) {
                state.localRenderRoot = keyboardRoot;
                state.localRadiusPx = radiusPx;
                state.localShape = shape;
            }
            return state.applied;
        } catch (Throwable throwable) {
            Log.w(TAG, "clipped blur unavailable: " + throwable.getClass().getSimpleName(),
                    throwable);
            return false;
        }
    }

    private static Object createBackgroundBlurDrawable(View target, int radiusPx,
            GboardFrostedGlassShape shape)
            throws ReflectiveOperationException {
        Method getViewRoot = View.class.getDeclaredMethod("getViewRootImpl");
        getViewRoot.setAccessible(true);
        Object viewRoot = getViewRoot.invoke(target);
        if (viewRoot == null) {
            return null;
        }
        Method create = viewRoot.getClass().getDeclaredMethod("createBackgroundBlurDrawable");
        create.setAccessible(true);
        Object drawable = create.invoke(viewRoot);
        if (!(drawable instanceof Drawable)) {
            return null;
        }
        configureBlurDrawable(drawable, radiusPx, shape);
        Method setVisible = drawable.getClass().getDeclaredMethod(
                "setVisible", boolean.class, boolean.class);
        setVisible.setAccessible(true);
        setVisible.invoke(drawable, true, false);
        return drawable;
    }

    private static void configureBlurDrawable(Object drawable, int radiusPx,
            GboardFrostedGlassShape shape) throws ReflectiveOperationException {
        Method setRadius = drawable.getClass().getDeclaredMethod("setBlurRadius", int.class);
        setRadius.setAccessible(true);
        setRadius.invoke(drawable, radiusPx);
        try {
            Method setCorners = drawable.getClass().getDeclaredMethod("setCornerRadius",
                    float.class, float.class, float.class, float.class);
            setCorners.setAccessible(true);
            setCorners.invoke(drawable, shape.topLeft, shape.topRight,
                    shape.bottomLeft, shape.bottomRight);
        } catch (NoSuchMethodException unavailable) {
            if (!shape.isUniform()) throw unavailable;
            Method setCorner = drawable.getClass().getDeclaredMethod("setCornerRadius", float.class);
            setCorner.setAccessible(true);
            setCorner.invoke(drawable, shape.topLeft);
        }
    }

    private static View findKeyboardRoot(View decor) {
        View root = findUsableViewWithTag(decor, KEYBOARD_ROOT_TAG);
        return root != null ? root : findUsableViewWithTag(decor, FLOATING_KEYBOARD_ROOT_TAG);
    }

    /**
     * Reuses the last root while the structure is unchanged. A docked/floating switch,
     * reinflate or new root is laid out (structureDirty) or detaches the old root; a tag
     * that appears later without layout is still found because a miss searches every pass.
     */
    private static View resolveKeyboardRoot(View decor, State state) {
        View cached = state.rootCache;
        if (!state.structureDirty && cached != null && state.rootCacheTag != null
                && state.rootCacheTag.equals(cached.getTag())
                && cached.isAttachedToWindow() && isUsableRoot(cached)
                && cached.getRootView() == decor) {
            return cached;
        }
        View root = findKeyboardRoot(decor);
        state.rootCache = root;
        state.rootCacheTag = root == null ? null : root.getTag();
        return root;
    }

    private static boolean canUseWindowShape(Window window, View root,
            GboardFrostedGlassShape shape) {
        return canUseWindowShape(window, root, shape, new int[2]);
    }

    private static boolean canUseWindowShape(Window window, View root,
            GboardFrostedGlassShape shape, int[] location) {
        if (root == null || shape == null || !shape.isUniform()) return false;
        View decor = window.getDecorView();
        root.getLocationInWindow(location);
        return location[0] == 0 && location[1] == 0
                && shape.bounds.left == 0 && shape.bounds.top == 0
                && shape.bounds.right == decor.getWidth() && shape.bounds.bottom == decor.getHeight();
    }

    /** Preserve the theme outline, except at a validated internal navigation join. */
    private static final class ThemeBlurBackground extends LayerDrawable {
        private final Drawable theme;
        private GboardFrostedGlassShape joinedShape;

        ThemeBlurBackground(Drawable theme, Drawable blur) {
            super(theme == null ? new Drawable[]{blur} : new Drawable[]{theme, blur});
            this.theme = theme;
        }

        void updateBounds(GboardFrostedGlassShape shape, int width, int height, boolean joined) {
            joinedShape = joined ? shape : null;
            setLayerInset(getNumberOfLayers() - 1, shape.bounds.left, shape.bounds.top,
                    width - shape.bounds.right, height - shape.bounds.bottom);
        }

        @Override public void getOutline(Outline outline) {
            if (joinedShape != null) outline.setPath(joinedShape.path(0, 0));
            else if (theme != null) theme.getOutline(outline);
            else outline.setEmpty();
        }
    }

    private static View findUsableViewWithTag(View view, String expectedTag) {
        if (view == null) {
            return null;
        }
        Object tag = view.getTag();
        if (expectedTag.equals(tag) && isUsableRoot(view)) {
            return view;
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findUsableViewWithTag(group.getChildAt(index), expectedTag);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static boolean isUsableRoot(View view) {
        return view.getVisibility() == View.VISIBLE
                && view.getWidth() > 0
                && view.getHeight() > 0
                && view.isShown();
    }

    /** Host-test entry: one pass over {@code scope}, which is both search scope and root. */
    private static void applyThemeOpacity(View scope, int customOpacity, State state) {
        ++state.pass;
        applyThemeOpacity(scope, scope, customOpacity, state);
    }

    /**
     * Applies the theme/key opacity to the discovered targets only. Full discovery runs
     * when the structure may have changed; a draw-only frame touches O(targets) views.
     */
    private static void applyThemeOpacity(View decor, View root, int customOpacity, State state) {
        if (decor == null) return;
        ensureTargets(decor, root, state);
        if (state.customKeyOpacity < 0) restoreKeyOpacity(state);
        for (int i = 0; i < state.targets.size(); i++) {
            Target target = state.targets.get(i);
            View view = target.view.get();
            if (view == null || !target.tag.equals(view.getTag())
                    || (view != decor && view.getParent() == null)) {
                // Removed or re-tagged without a layout pass: rediscover once in this pass.
                if (state.structurePass != state.pass) {
                    discoverTargets(decor, root, state);
                    i = -1;
                }
                continue;
            }
            if ((target.kinds & TARGET_KEY) != 0 && state.customKeyOpacity >= 0) {
                applyKeyOpacity(view, view.getBackground(), state);
            }
            // The action-key fill can be an image separate from its foreground icon.
            if ((target.kinds & TARGET_ACTION_KEY) != 0 && state.customKeyOpacity >= 0) {
                applyKeyOpacity(view, view.getBackground(), state);
                if (view instanceof android.widget.ImageView image)
                    applyKeyOpacity(view, image.getDrawable(), state);
            }
            if ((target.kinds & TARGET_SURFACE) != 0) {
                applySurfaceOpacity(view, customOpacity, state);
            }
        }
    }

    private static void applySurfaceOpacity(View view, int customOpacity, State state) {
        Drawable background = view == state.localBlurView && state.localBlurDrawable != null
                && view.getBackground() == state.localCombinedBackground
                ? state.localOriginalBackground : view.getBackground();
        background = opacityTarget(background);
        if (background == null) return;
        SurfaceAlpha saved = state.originalSurfaceAlphas.get(background);
        if (saved == null) {
            saved = new SurfaceAlpha(background.getAlpha());
            state.originalSurfaceAlphas.put(background, saved);
        }
        int alpha = customOpacity >= 0 ? customOpacity : saved.original;
        int current = background.getAlpha();
        if (saved.requested != alpha || saved.observed != current) {
            if (current != alpha) setSurfaceAlpha(background, alpha);
            saved.requested = alpha;
            saved.observed = background.getAlpha();
            if (saved.observed != current) view.invalidate();
        }
    }

    /** Runs discovery at most once per pass, and only when the structure may have changed. */
    private static void ensureTargets(View decor, View root, State state) {
        if (state.targetsPass == state.pass) return;
        if (state.structureDirty || state.targetsRoot != root
                || (root instanceof android.view.ViewGroup group
                        && group.getChildCount() != state.targetsRootChildCount)) {
            discoverTargets(decor, root, state);
        }
        state.targetsPass = state.pass;
    }

    private static void discoverTargets(View decor, View root, State state) {
        state.targets.clear();
        collectTargets(decor, state.targets);
        state.targetsRoot = root;
        state.targetsRootChildCount = root instanceof android.view.ViewGroup group
                ? group.getChildCount() : -1;
        state.structureDirty = false;
        state.targetsPass = state.pass;
        state.structurePass = state.pass;
        pruneKeyAlphas(state);
    }

    private static void collectTargets(View view, List<Target> out) {
        if (view == null) return;
        if (view.getTag() instanceof String tag) {
            int kinds = targetKinds(tag);
            if (kinds != 0) out.add(new Target(view, tag, kinds));
        }
        if (view instanceof android.view.ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                collectTargets(group.getChildAt(index), out);
            }
        }
    }

    static int targetKinds(String tag) {
        int kinds = 0;
        if (tag.equals(".keytop") || tag.startsWith(".keytop.")
                || tag.equals(".space_bar") || tag.startsWith(".space_bar.")) {
            kinds |= TARGET_KEY;
        }
        if (tag.equals(".background-icon.for-action-key")
                || tag.startsWith(".background-icon.for-action-key.")) {
            kinds |= TARGET_ACTION_KEY;
        }
        if (isThemeSurfaceTag(tag)) kinds |= TARGET_SURFACE;
        return kinds;
    }

    /** Restores key fills whose views were replaced, so the session map does not grow. */
    private static void pruneKeyAlphas(State state) {
        if (state.originalKeyAlphas.isEmpty()) return;
        Map<Drawable, Boolean> live = new IdentityHashMap<>();
        for (Target target : state.targets) {
            if ((target.kinds & (TARGET_KEY | TARGET_ACTION_KEY)) == 0) continue;
            View view = target.view.get();
            if (view == null) continue;
            Drawable background = opacityTarget(view.getBackground());
            if (background != null) live.put(background, Boolean.TRUE);
            if (view instanceof android.widget.ImageView image) {
                Drawable drawable = opacityTarget(image.getDrawable());
                if (drawable != null) live.put(drawable, Boolean.TRUE);
            }
        }
        var entries = state.originalKeyAlphas.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Drawable, SurfaceAlpha> entry = entries.next();
            if (live.containsKey(entry.getKey())) continue;
            try { setSurfaceAlpha(entry.getKey(), entry.getValue().original); }
            catch (Throwable ignored) { /* A detached drawable must not block pruning. */ }
            entries.remove();
        }
    }

    private static final class Target {
        final WeakReference<View> view;
        final String tag;
        final int kinds;

        Target(View view, String tag, int kinds) {
            this.view = new WeakReference<>(view);
            this.tag = tag;
            this.kinds = kinds;
        }
    }

    private static void applyKeyOpacity(View view, Drawable source, State state) {
        Drawable target = opacityTarget(source);
        if (target == null) return;
        SurfaceAlpha saved = state.originalKeyAlphas.get(target);
        int current = target.getAlpha();
        if (saved == null) {
            // Transparent placeholders are not visible keycaps; do not create a fill.
            if (current == 0) return;
            target.mutate();
            saved = new SurfaceAlpha(current);
            state.originalKeyAlphas.put(target, saved);
        }
        int alpha = state.customKeyOpacity;
        if (saved.requested != alpha || saved.observed != current) {
            if (current != alpha) setSurfaceAlpha(target, alpha);
            saved.requested = alpha;
            saved.observed = target.getAlpha();
            if (saved.observed != current) view.invalidate();
        }
    }

    private static void restoreKeyOpacity(State state) {
        restoreAlphas(state.originalKeyAlphas);
    }

    private static void restoreAlphas(Map<Drawable, SurfaceAlpha> alphas) {
        if (alphas.isEmpty()) return;
        for (Map.Entry<Drawable, SurfaceAlpha> entry : alphas.entrySet()) {
            try { setSurfaceAlpha(entry.getKey(), entry.getValue().original); }
            catch (Throwable ignored) { /* A bad theme drawable must not strand the other surfaces. */ }
        }
        alphas.clear();
    }

    private static Drawable opacityTarget(Drawable drawable) {
        // Some stock wrappers intentionally reject setAlpha; their public child still
        // supports it. Keep the wrapper installed so its outline/state/shape remain stock.
        for (int depth = 0; depth < 16 && drawable instanceof DrawableWrapper wrapper; depth++) {
            drawable = wrapper.getDrawable();
        }
        return drawable;
    }

    private static void setSurfaceAlpha(Drawable drawable, int alpha) {
        if (drawable instanceof ColorDrawable color) {
            // ColorDrawable.setAlpha multiplies its base color alpha and can read back
            // 76 for an opaque color requested at 77. Override only the alpha channel;
            // this also makes restoring a translucent original exact, without RGB drift.
            color.setColor((color.getColor() & 0x00ffffff) | (alpha << 24));
        } else {
            drawable.setAlpha(alpha);
        }
    }

    private static final class SurfaceAlpha {
        final int original;
        int requested = -1;
        int observed;

        SurfaceAlpha(int original) {
            this.original = original;
            observed = original;
        }
    }

    private static boolean isThemeSurfaceTag(String tag) {
        return tag.equals(".keyboard-base-area")
                || tag.equals(".keyboard-base-area.keyboard-outline")
                || tag.contains(".keyboard-header-area")
                || tag.contains(".keyboard-body-area")
                || tag.startsWith(".candidates-area");
    }

    private static void restoreLocalBackground(State state) {
        restoreKeyOpacity(state);
        state.navigation.clear();
        state.navigationJoined = false;
        restoreAlphas(state.originalSurfaceAlphas);
        if (state.localBlurView != null && state.localBlurDrawable != null) {
            cleanupStep(() -> {
                if (state.localBlurView.getBackground() == state.localCombinedBackground)
                    state.localBlurView.setBackground(state.localOriginalBackground);
            });
            cleanupStep(() -> state.localBlurView.invalidate());
            // Drop the compositor region now instead of waiting for positionLost on a later frame.
            Object blur = state.localBlurDrawable;
            if (blur instanceof Drawable drawable) cleanupStep(() -> drawable.setVisible(false, false));
        }
        state.localBlurView = null;
        state.localBlurDrawable = null;
        state.localOriginalBackground = null;
        state.localCombinedBackground = null;
        state.localShape = null;
    }

    private static Drawable transparentBackground(GboardFrostedGlassShape shape) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(shape.topLeft);
        return background;
    }

    private static boolean isCurrent(Window window, State state) {
        synchronized (STATES) {
            return STATES.get(window) == state;
        }
    }

    private static void clearRenderNode(State state) throws ReflectiveOperationException {
        if (state.localRenderNode == null) return;
        Class<?> type = state.localRenderNode.getClass();
        Method radius = type.getDeclaredMethod("setBackgroundBlurRadius", int.class);
        Method mode = type.getDeclaredMethod("setBackgroundBlurMode", int.class);
        radius.setAccessible(true);
        mode.setAccessible(true);
        radius.invoke(state.localRenderNode, 0);
        mode.invoke(state.localRenderNode, 0);
        state.localRenderNode = null;
        state.localRenderRoot = null;
        if (state.localBlurView != null) state.localBlurView.invalidate();
    }

    private static void disable(Window window) {
        State state;
        synchronized (STATES) {
            state = STATES.remove(window);
        }
        if (state == null) {
            cleanupStep(() -> GboardSeamlessNavBarRuntime.restore(window));
            return;
        }
        ++state.layoutGeneration;
        state.hiding = false;
        cleanupStep(() -> cancelHideRestore(state));
        cleanupStep(() -> cancelFallback(state));
        cleanupStep(() -> detachObservers(state));
        clearBlurEffects(window, state);
        cleanupStep(() -> restoreKeyboardLayout(state));
        cleanupStep(() -> window.setLayout(state.originalWidth, state.originalHeight));
        cleanupStep(() -> window.setGravity(state.originalGravity));
        cleanupStep(() -> GboardSeamlessNavBarRuntime.restore(window));
        Log.i(TAG, "cleared");
    }

    private static void clearBlurEffects(Window window, State state) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && state.windowBlurApplied) {
            cleanupStep(() -> window.setBackgroundBlurRadius(0));
            state.windowBlurApplied = false;
        }
        if (state.localRenderNode != null) {
            cleanupStep(() -> {
                try {
                    clearRenderNode(state);
                } catch (ReflectiveOperationException unavailable) {
                    throw new IllegalStateException(unavailable);
                }
            });
            state.localRenderNode = null;
            state.localRenderRoot = null;
        }
        cleanupStep(() -> restoreLocalBackground(state));
        if (state.windowBackgroundChanged) {
            cleanupStep(() -> {
                window.setBackgroundDrawable(state.originalWindowBackground);
                View decor = window.getDecorView();
                if (decor != null) decor.setBackground(state.originalWindowBackground);
            });
            state.windowBackgroundChanged = false;
        }
        state.applied = false;
    }

    private static void cleanupStep(Runnable step) {
        try { step.run(); }
        catch (Throwable ignored) { /* Cleanup must not escape a draw or lifecycle callback. */ }
    }

    private static void ensureHiddenApiAccess() {
        if (HIDDEN_API_READY.get()) {
            return;
        }
        try {
            HiddenApiBypass.addHiddenApiExemptions("L");
            HIDDEN_API_READY.set(true);
            Log.i(TAG, "hidden API access enabled");
        } catch (Throwable failure) {
            Log.w(TAG, "hidden API access unavailable: "
                    + failure.getClass().getSimpleName());
        }
    }

    private static void constrainKeyboardSurface(View decor, State state) {
        View root = findVisibleViewWithTag(decor, KEYBOARD_ROOT_TAG);
        if (root == null) {
            root = findVisibleViewWithTag(decor, FLOATING_KEYBOARD_ROOT_TAG);
        }
        if (root == null) {
            Log.w(TAG, "keyboard surface tag not found");
            return;
        }
        boolean changed = setWrapContentBottom(root, state);
        View parent = root.getParent() instanceof View ? (View) root.getParent() : null;
        while (parent != null && parent != decor) {
            if ("com.google.android.libraries.inputmethod.inputview.InputView"
                    .equals(parent.getClass().getName())) {
                changed |= setWrapContentBottom(parent, state);
                break;
            }
            parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
        }
        View inputArea = decor.findViewById(android.R.id.inputArea);
        if (inputArea != null) changed |= setWrapContentBottom(inputArea, state);
        // setLayoutParams already requests layout for a changed View. An unchanged
        // configure/show must not start another decor layout pass.
        if (changed) root.requestLayout();
    }

    /** Returns whether the View's params were changed. */
    private static boolean setWrapContentBottom(View view, State state) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) return false;
        if (!state.originalLayouts.containsKey(view)) {
            state.originalLayouts.put(view, new LayoutSnapshot(view));
        }
        boolean changed = params.height != ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (params instanceof android.widget.FrameLayout.LayoutParams frame) {
            changed |= frame.gravity != Gravity.BOTTOM;
            frame.gravity = Gravity.BOTTOM;
        }
        if (changed) view.setLayoutParams(params);
        return changed;
    }

    private static View findVisibleViewWithTag(View view, String expectedTag) {
        if (view == null) return null;
        if (expectedTag.equals(view.getTag()) && view.getVisibility() == View.VISIBLE) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findVisibleViewWithTag(group.getChildAt(i), expectedTag);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Context contextOf(Window window) {
        try {
            Context context = window.getContext();
            while (context instanceof ContextWrapper wrapper && context != wrapper.getBaseContext()) {
                if (context instanceof InputMethodService) {
                    return context;
                }
                context = wrapper.getBaseContext();
            }
            return context;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Window windowOf(InputMethodService service) {
        try {
            Dialog dialog = service.getWindow();
            return dialog == null ? null : dialog.getWindow();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** InputView.onDraw in the admitted 18.5.4 target paints only its nav padding. */
    private static final class NavigationSurface {
        private View input;
        private boolean originalWillNotDraw;
        private Drawable source;
        private Drawable tint;
        private Drawable blur;
        private LayerDrawable surface;
        private final Rect bounds = new Rect();
        private final Rect nextBounds = new Rect();
        private int radius;
        private android.content.res.ColorStateList color;
        private int[] drawableState;

        static boolean supportsTint(Drawable theme) {
            return theme != null && theme.getConstantState() != null
                    && (theme instanceof ColorDrawable || theme instanceof GradientDrawable gradient
                    && gradient.getShape() == GradientDrawable.RECTANGLE && gradient.getColor() != null);
        }

        /** {@code target} is this pass's {@link #navigationInput} result for {@code root}. */
        boolean update(View root, View target, Drawable theme, int radiusPx) {
            try {
                if (target == null || !supportsTint(theme)) {
                    clear();
                    return false;
                }
                android.content.res.ColorStateList nextColor = theme instanceof GradientDrawable g
                        ? g.getColor() : null;
                nextBounds.set(0, target.getHeight() - target.getPaddingBottom(),
                        target.getWidth(), target.getHeight());
                if (input != target || source != theme) {
                    clear();
                    Drawable nextTint = theme.getConstantState().newDrawable().mutate();
                    if (nextTint instanceof GradientDrawable g) {
                        g.setCornerRadii(null);
                        g.setCornerRadius(0);
                    }
                    Drawable nextBlur = (Drawable) createBackgroundBlurDrawable(target,
                            radiusPx, GboardFrostedGlassShape.rectangle(nextBounds));
                    if (nextBlur == null) return false;
                    input = target;
                    originalWillNotDraw = target.willNotDraw();
                    source = theme;
                    tint = nextTint;
                    blur = nextBlur;
                    // BackgroundBlurDrawable cuts a transparent hole for the compositor.
                    // Draw the tint after that hole, as the keyboard's child surfaces do.
                    surface = new LayerDrawable(new Drawable[]{blur, tint});
                    surface.setBounds(nextBounds);
                    bounds.set(nextBounds);
                    radius = radiusPx;
                    target.getOverlay().add(surface);
                }
                // InputView.a() can re-enable its stock opaque nav paint after a
                // palette/inset refresh. Reassert ownership before each glass draw.
                if (!target.willNotDraw()) target.setWillNotDraw(true);
                if (theme instanceof GradientDrawable && !java.util.Objects.equals(color, nextColor)) {
                    ((GradientDrawable) tint).setColor(nextColor);
                    color = nextColor;
                } else if (theme instanceof ColorDrawable c
                        && ((ColorDrawable) tint).getColor() != c.getColor()) {
                    ((ColorDrawable) tint).setColor(c.getColor());
                }
                if (tint.getAlpha() != theme.getAlpha()) setSurfaceAlpha(tint, theme.getAlpha());
                // ConstantState does not retain the live filter applied by Gboard's
                // theme renderer; base RGB alone can differ from the visible panel.
                if (tint.getColorFilter() != theme.getColorFilter()) tint.setColorFilter(theme.getColorFilter());
                if (tint.isStateful()) {
                    // View returns the same array until its drawable state is refreshed.
                    int[] nextState = root.getDrawableState();
                    if (nextState != drawableState) {
                        tint.setState(nextState);
                        drawableState = nextState;
                    }
                }
                if (!bounds.equals(nextBounds)) {
                    surface.setBounds(nextBounds);
                    bounds.set(nextBounds);
                }
                if (radius != radiusPx) {
                    configureBlurDrawable(blur, radiusPx,
                            GboardFrostedGlassShape.rectangle(nextBounds));
                    radius = radiusPx;
                }
                return true;
            } catch (Throwable ignored) {
                // If this backend cannot blur a separate strip, leave stock navigation intact.
                clear();
                return false;
            }
        }

        /** Why the last {@link #navigationInput} call returned null (UI thread only). */
        static String rejectReason;

        private static View navigationInput(View root, int[] rootLocation, int[] inputLocation) {
            View input = navigationInputOrNull(root, rootLocation, inputLocation);
            if (input != null) rejectReason = null;
            return input;
        }

        private static View navigationInputOrNull(View root, int[] rootLocation, int[] inputLocation) {
            if (root == null || !KEYBOARD_ROOT_TAG.equals(root.getTag())) {
                rejectReason = "not the docked keyboard root";
                return null;
            }
            android.view.ViewParent parent = root.getParent();
            while (parent instanceof View view) {
                if (view.getClass().getName().equals(
                        "com.google.android.libraries.inputmethod.inputview.InputView")) {
                    int padding = view.getPaddingBottom();
                    if (padding <= 0 || padding >= view.getHeight() || view.getBackground() != null
                            || view.getWidth() != root.getWidth()) {
                        rejectReason = "InputView padding=" + padding + " height=" + view.getHeight()
                                + " background=" + (view.getBackground() != null)
                                + " width=" + view.getWidth() + "/" + root.getWidth();
                        return null;
                    }
                    root.getLocationInWindow(rootLocation);
                    view.getLocationInWindow(inputLocation);
                    if (rootLocation[0] != inputLocation[0]
                            || rootLocation[1] - inputLocation[1] + root.getHeight()
                            != view.getHeight() - padding) {
                        rejectReason = "root not adjacent: rootY=" + rootLocation[1]
                                + " inputY=" + inputLocation[1] + " rootH=" + root.getHeight()
                                + " inputH=" + view.getHeight() + " padding=" + padding;
                        return null;
                    }
                    // Do not place an overlay over OEM navigation controls inside this View.
                    android.view.ViewGroup group = (android.view.ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        if (child.getVisibility() == View.VISIBLE && child.getHeight() > 0
                                && child.getBottom() > view.getHeight() - padding) {
                            rejectReason = "child in nav padding: " + child.getClass().getName()
                                    + " bottom=" + child.getBottom();
                            return null;
                        }
                    }
                    return view;
                }
                parent = view.getParent();
            }
            rejectReason = "no InputView ancestor";
            return null;
        }

        void clear() {
            View target = input;
            LayerDrawable oldSurface = surface;
            Drawable oldBlur = blur;
            boolean restoreWillNotDraw = originalWillNotDraw;
            input = null;
            source = null;
            tint = null;
            blur = null;
            surface = null;
            color = null;
            drawableState = null;
            if (target != null) {
                cleanupStep(() -> {
                    if (oldSurface != null) target.getOverlay().remove(oldSurface);
                });
                cleanupStep(() -> target.setWillNotDraw(restoreWillNotDraw));
                cleanupStep(target::invalidate);
            }
            if (oldBlur != null) cleanupStep(() -> oldBlur.setVisible(false, false));
        }
    }

    private static final class State {
        final NavigationSurface navigation = new NavigationSurface();
        final Drawable originalBackground;
        final int originalWidth;
        final int originalHeight;
        final int originalGravity;
        boolean applied;
        boolean settingsLoaded;
        boolean navigationEnabled = true;
        boolean navigationJoined;
        int radiusPx;
        int customOpacity;
        int customKeyOpacity = -1;
        int localRadiusPx;
        GboardFrostedGlassShape localShape;
        GboardFrostedGlassShape windowShape;
        View windowBlurRoot;
        int windowRadiusPx;
        View localRenderRoot;
        ViewTreeObserver preDrawTree;
        ViewTreeObserver.OnPreDrawListener preDrawObserver;
        View localBlurView;
        Object localRenderNode;
        Object localBlurDrawable;
        Drawable localOriginalBackground;
        Drawable localCombinedBackground;
        boolean windowBackgroundChanged;
        boolean windowBlurApplied;
        Drawable originalWindowBackground;
        final Map<View, LayoutSnapshot> originalLayouts = new IdentityHashMap<>();
        View observedDecor;
        final Map<Drawable, SurfaceAlpha> originalSurfaceAlphas = new IdentityHashMap<>();
        final Map<Drawable, SurfaceAlpha> originalKeyAlphas = new IdentityHashMap<>();
        long layoutGeneration;
        boolean decorLayoutObserverInstalled;
        View.OnLayoutChangeListener decorLayoutObserver;
        long settingsLoadedAt;
        // Set by the last apply pass; a layout schedules fallbacks only while not ready.
        boolean ready;
        Runnable fallback;
        View fallbackHost;
        // Apply-pass bookkeeping: each pre-draw/show/layout fallback is one pass.
        long pass;
        boolean structureDirty = true;
        long structurePass = -1;
        long targetsPass = -1;
        final List<Target> targets = new ArrayList<>();
        View targetsRoot;
        int targetsRootChildCount = -1;
        View rootCache;
        Object rootCacheTag;
        long navPass = -1;
        View navRoot;
        Drawable navOriginal;
        View navInput;
        Drawable navTint;
        // null: not yet reconciled with the Window navigation styling in this session.
        Boolean seamlessGlass;
        String navReason = "";
        boolean hiding;
        Runnable hideRestore;
        View hideRestoreHost;
        final int[] location = new int[2];
        final int[] inputLocation = new int[2];

        State(Window window) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            originalBackground = window.getDecorView() == null
                    ? null : window.getDecorView().getBackground();
            originalWidth = attributes == null
                    ? WindowManager.LayoutParams.MATCH_PARENT : attributes.width;
            originalHeight = attributes == null
                    ? WindowManager.LayoutParams.MATCH_PARENT : attributes.height;
            originalGravity = attributes == null ? Gravity.NO_GRAVITY : attributes.gravity;
        }
    }

    private static void restoreKeyboardLayout(State state) {
        for (Map.Entry<View, LayoutSnapshot> entry : state.originalLayouts.entrySet()) {
            View view = entry.getKey();
            LayoutSnapshot snapshot = entry.getValue();
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) continue;
            params.height = snapshot.height;
            if (params instanceof android.widget.FrameLayout.LayoutParams frame
                    && snapshot.frameGravity != null) {
                frame.gravity = snapshot.frameGravity;
            }
            view.setLayoutParams(params);
        }
        state.originalLayouts.clear();
    }

    private static final class LayoutSnapshot {
        final int height;
        final Integer frameGravity;

        LayoutSnapshot(View view) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            height = params == null ? ViewGroup.LayoutParams.WRAP_CONTENT : params.height;
            frameGravity = params instanceof android.widget.FrameLayout.LayoutParams frame
                    ? frame.gravity : null;
        }
    }

}
