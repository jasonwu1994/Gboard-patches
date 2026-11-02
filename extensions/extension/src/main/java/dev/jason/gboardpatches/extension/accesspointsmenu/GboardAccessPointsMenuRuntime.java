package dev.jason.gboardpatches.extension.accesspointsmenu;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.flagsettings.GboardFlagRuntimeContext;

public final class GboardAccessPointsMenuRuntime {
    public static final int LEGACY_BODY = 0x7f0b070c;
    public static final int MODERN_BODY = 0x7f0b070d;
    public static final int LEGACY_LAYOUT = 0x7f0e062e;
    public static final int MODERN_KEYBOARD_LAYOUT = 0x7f0e01aa;
    private static final Map<View, Snapshot> PENDING = new WeakHashMap<>();
    private static final Map<View, Boolean> LEGACY_POPUPS = new WeakHashMap<>();

    private GboardAccessPointsMenuRuntime() { }

    public static int selectBody(int stock) {
        return selectBody(stock, preferences());
    }

    static int selectBody(int stock, SharedPreferences preferences) {
        Boolean modern = readModernOrNull(preferences);
        return modern == null || (stock != LEGACY_BODY && stock != MODERN_BODY)
                ? stock : modern ? MODERN_BODY : LEGACY_BODY;
    }

    public static int selectKeyboardLayout(int stock) {
        return selectKeyboardLayout(stock, preferences());
    }

    static int selectKeyboardLayout(int stock, SharedPreferences preferences) {
        Boolean modern = readModernOrNull(preferences);
        return modern == null || (stock != LEGACY_LAYOUT && stock != MODERN_KEYBOARD_LAYOUT)
                ? stock : modern ? MODERN_KEYBOARD_LAYOUT : LEGACY_LAYOUT;
    }

    static Boolean readModernOrNull(SharedPreferences preferences) {
        if (preferences == null) return null;
        try {
            String key = GboardAccessPointsMenuSettings.PREF_KEY_ENABLED;
            if (!preferences.contains(key)) return GboardAccessPointsMenuSettings.DEFAULT_ENABLED;
            Object raw = preferences.getAll().get(key);
            if (raw instanceof Boolean) return (Boolean) raw;
            if (raw instanceof String && "true".equalsIgnoreCase((String) raw)) return true;
            if (raw instanceof String && "false".equalsIgnoreCase((String) raw)) return false;
            return null;
        } catch (Throwable ignored) { return null; }
    }

    private static SharedPreferences preferences() {
        try { return GboardFlagRuntimeContext.preferencesOrNull(); }
        catch (Throwable ignored) { return null; }
    }

    public static boolean wantsLegacy() {
        return Boolean.FALSE.equals(readModernOrNull(preferences()));
    }

    public static void prepareDrag(View popup, ImageView replacement) {
        prepareDrag(popup, replacement, readModernOrNull(preferences()));
    }

    static void prepareDrag(View popup, ImageView replacement, Boolean modern) {
        if (!Boolean.FALSE.equals(modern) || popup == null || replacement == null) return;
        View candidate = popup.findViewById(0x7f0b007f);
        if (!(candidate instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) candidate;
        if (!(frame.findViewById(0x7f0b03c2) instanceof ImageView)) return;
        try {
            int width, height, padding;
            TypedArray dimensions = popup.getContext().obtainStyledAttributes(
                    new int[] {0x7f040018, 0x7f040015, 0x7f040014});
            try {
                if (!dimensions.hasValue(0) || !dimensions.hasValue(1) || !dimensions.hasValue(2)) return;
                width = dimensions.getDimensionPixelSize(0, 0);
                height = dimensions.getDimensionPixelSize(1, 0);
                padding = dimensions.getDimensionPixelSize(2, -1);
            } finally { dimensions.recycle(); }
            Drawable background = popup.getContext().getDrawable(0x7f080233);
            applyLegacyPresentation(popup, replacement, frame, width, height, padding, background);
        } catch (Throwable ignored) { }
    }

    static void applyLegacyPresentation(View popup, ImageView replacement, FrameLayout frame,
            int width, int height, int padding, Drawable background) {
        if (width <= 0 || height <= 0 || padding < 0) return;
        Snapshot snapshot = null;
        try {
            replacement.setId(0x7f0b03c2);
            replacement.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            replacement.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            replacement.setBackground(background);
            FrameLayout.LayoutParams iconLayout = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.CENTER);
            FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(
                    width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
            snapshot = new Snapshot(frame);
            frame.removeAllViews();
            frame.setLayoutParams(frameLayout);
            frame.setBackgroundColor(Color.TRANSPARENT);
            frame.setPadding(padding, 0, padding, 0);
            frame.addView(replacement, iconLayout);
            frame.setVisibility(View.GONE);
            synchronized (PENDING) { PENDING.put(popup, snapshot); }
        } catch (Throwable ignored) {
            if (snapshot != null) snapshot.restore();
        }
    }

    public static void completeDrag(View popup) {
        synchronized (PENDING) {
            if (PENDING.remove(popup) != null) LEGACY_POPUPS.put(popup, Boolean.TRUE);
        }
    }

    public static void rollbackDrag(View popup) {
        synchronized (PENDING) {
            Snapshot snapshot = PENDING.remove(popup);
            LEGACY_POPUPS.remove(popup);
            if (snapshot != null) snapshot.restore();
        }
    }

    public static boolean dragMode(View popup, boolean stock) {
        synchronized (PENDING) { return LEGACY_POPUPS.containsKey(popup) ? false : stock; }
    }

    public static float dragScale(View popup, float stock) {
        synchronized (PENDING) { return LEGACY_POPUPS.containsKey(popup) ? 1.3f : stock; }
    }

    private static final class Snapshot {
        final FrameLayout frame;
        final List<View> children = new ArrayList<>();
        final List<ViewGroup.LayoutParams> layouts = new ArrayList<>();
        final Drawable background;
        final ViewGroup.LayoutParams layout;
        final int left, top, right, bottom, visibility;
        Snapshot(FrameLayout frame) {
            this.frame = frame;
            for (int i = 0; i < frame.getChildCount(); i++) {
                View child = frame.getChildAt(i);
                children.add(child); layouts.add(child.getLayoutParams());
            }
            background = frame.getBackground(); layout = frame.getLayoutParams();
            left = frame.getPaddingLeft(); top = frame.getPaddingTop();
            right = frame.getPaddingRight(); bottom = frame.getPaddingBottom();
            visibility = frame.getVisibility();
        }
        void restore() {
            frame.removeAllViews();
            for (int i = 0; i < children.size(); i++) frame.addView(children.get(i), layouts.get(i));
            frame.setBackground(background); frame.setLayoutParams(layout);
            frame.setPadding(left, top, right, bottom); frame.setVisibility(visibility);
        }
    }

    public static Object applyOverriddenFlagValue(String flagName, Object stockResult) {
        try {
            if (!GboardAccessPointsMenuPolicy.MENU_REDESIGN_FLAG.equals(flagName)
                    || !(stockResult instanceof Boolean)) {
                return stockResult;
            }
            SharedPreferences preferences = GboardFlagRuntimeContext.preferencesOrNull();
            return preferences == null
                    ? stockResult
                    : applyOverriddenFlagValue(flagName, stockResult, preferences);
        } catch (Throwable ignored) {
            return stockResult;
        }
    }

    static Object applyOverriddenFlagValue(
            String flagName,
            Object stockResult,
            SharedPreferences preferences) {
        return GboardAccessPointsMenuPolicy.mirrorSwitch(
                flagName,
                stockResult,
                GboardAccessPointsMenuSettings.readEnabled(preferences));
    }
}
