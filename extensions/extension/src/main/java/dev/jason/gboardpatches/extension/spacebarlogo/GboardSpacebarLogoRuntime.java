package dev.jason.gboardpatches.extension.spacebarlogo;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class GboardSpacebarLogoRuntime {
    private static final String TAG = "GboardPatches";
    private static final String OVERLAY_TAG = "gboard-patches:spacebar-logo";
    private static final Map<View, Integer> ORIGINAL_LABEL_VISIBILITIES = new WeakHashMap<>();
    private static final Map<View, BoundSpacebar> BOUND_SPACEBARS = new WeakHashMap<>();

    private GboardSpacebarLogoRuntime() {
    }

    public static void beforeSoftKeyBound(View softKeyView, ViewBindings bindings) {
        try {
            ViewGroup root = spacebarRoot(softKeyView, bindings);
            if (root != null) safeRestoreStockAppearance(root, bindings);
        } catch (Throwable throwable) {
            logWarningSafely("Failed to restore spacebar before bind", throwable);
        }
    }

    public static void afterSoftKeyBound(View softKeyView, boolean isSpaceAction,
            SettingsSnapshot settings, ViewBindings bindings) {
        try {
            ViewGroup root = spacebarRoot(softKeyView, bindings);
            if (root == null || settings == null) return;
            synchronized (BOUND_SPACEBARS) {
                BOUND_SPACEBARS.put(softKeyView,
                        new BoundSpacebar(softKeyView, isSpaceAction, bindings));
            }
            apply(root, isSpaceAction, settings, bindings, false);
        } catch (Throwable throwable) {
            logWarningSafely("Failed to bind spacebar logo", throwable);
        }
    }

    public static void refreshBoundSpacebars(SettingsSnapshot settings) {
        try {
            if (settings == null) return;
            List<BoundSpacebar> bound;
            synchronized (BOUND_SPACEBARS) {
                bound = new ArrayList<>(BOUND_SPACEBARS.values());
            }
            for (BoundSpacebar item : bound) {
                try {
                    View softKeyView = item == null ? null : item.softKeyView.get();
                    if (softKeyView != null && item != null) {
                        boolean posted = softKeyView.post(() ->
                                refreshBoundSpacebarSafely(item, settings));
                        if (!posted) {
                            logWarningSafely("Failed to schedule bound spacebar refresh", null);
                        }
                    }
                } catch (Throwable throwable) {
                    logWarningSafely("Failed to schedule bound spacebar refresh", throwable);
                }
            }
        } catch (Throwable throwable) {
            logWarningSafely("Failed to refresh bound spacebars", throwable);
        }
    }

    private static void refreshBoundSpacebarSafely(
            BoundSpacebar item, SettingsSnapshot settings) {
        try {
            View softKeyView = item == null ? null : item.softKeyView.get();
            ViewGroup root = spacebarRoot(
                    softKeyView, item == null ? null : item.bindings);
            if (root != null) {
                apply(root, item.isSpaceAction, settings, item.bindings, true);
            }
        } catch (Throwable throwable) {
            logWarningSafely("Failed to refresh bound spacebar", throwable);
        }
    }

    private static void apply(ViewGroup root, boolean isSpaceAction,
            SettingsSnapshot settings, ViewBindings bindings, boolean restoreFirst) {
        if (restoreFirst) safeRestoreStockAppearance(root, bindings);
        if (!settings.enabled || !isSpaceAction) {
            safeRestoreStockAppearance(root, bindings);
            return;
        }
        try {
            Drawable drawable;
            if (GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR.equals(settings.iconValue)) {
                drawable = GboardSpacebarLogoImages.googleDrawable(root.getResources());
            } else if (GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE.equals(settings.iconValue)) {
                drawable = GboardSpacebarLogoImages.customDrawable(root.getResources(), settings.customImage);
            } else {
                drawable = root.getContext().getDrawable(bindings.drawableResourceIdForIcon(settings.iconValue));
            }
            if (drawable == null) {
                safeRestoreStockAppearance(root, bindings);
                return;
            }
            drawable = drawable.mutate();
            if (settings.shouldTint()) drawable.setTint(settings.color);

            View label = root.findViewById(bindings.primaryLabelViewId);
            if (label != null) {
                rememberLabelVisibility(label);
                label.setVisibility(View.INVISIBLE);
            }
            ImageView overlay = ensureOverlay(root, settings.sizePercent);
            overlay.setImageDrawable(drawable);
            overlay.setVisibility(View.VISIBLE);
        } catch (Throwable throwable) {
            safeRestoreStockAppearance(root, bindings);
            logWarningSafely("Failed to apply spacebar logo", throwable);
        }
    }

    private static ViewGroup spacebarRoot(View view, ViewBindings bindings) {
        return view instanceof ViewGroup && bindings != null
                && view.getId() == bindings.spacebarViewId ? (ViewGroup) view : null;
    }

    private static ImageView ensureOverlay(ViewGroup root, int sizePercent) {
        ImageView existing = findOverlay(root);
        if (existing instanceof SizedOverlay sized) {
            sized.setSizePercent(sizePercent);
            return sized;
        }
        SizedOverlay overlay = new SizedOverlay(root);
        overlay.setTag(OVERLAY_TAG);
        overlay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        overlay.setClickable(false);
        overlay.setLongClickable(false);
        overlay.setFocusable(false);
        overlay.setFocusableInTouchMode(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.setDuplicateParentStateEnabled(true);
        root.addView(
                overlay,
                new FrameLayout.LayoutParams(1, 1, Gravity.CENTER));
        overlay.setSizePercent(sizePercent);
        return overlay;
    }

    private static void restoreStockAppearance(ViewGroup root, ViewBindings bindings) {
        View label = root.findViewById(bindings.primaryLabelViewId);
        if (label != null) restoreLabelVisibility(label);
        ImageView overlay = findOverlay(root);
        if (overlay != null) {
            if (overlay instanceof SizedOverlay sized) root.removeOnLayoutChangeListener(sized);
            root.removeView(overlay);
        }
    }

    private static void safeRestoreStockAppearance(ViewGroup root, ViewBindings bindings) {
        try {
            restoreStockAppearance(root, bindings);
        } catch (Throwable throwable) {
            logWarningSafely("Failed to restore stock spacebar appearance", throwable);
        }
    }

    private static void logWarningSafely(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Only sizes the decorative child; never changes the space key's own geometry. The box is
     * the key's inner area scaled by the percent, and FIT_CENTER keeps the image's aspect ratio,
     * so 100% touches the key's height (or width, for very wide images).
     */
    private static final class SizedOverlay extends ImageView implements View.OnLayoutChangeListener {
        private final ViewGroup root;
        private int sizePercent = GboardSpacebarLogoContract.DEFAULT_SIZE_PERCENT;

        SizedOverlay(ViewGroup root) {
            super(root.getContext());
            this.root = root;
            root.addOnLayoutChangeListener(this);
        }

        void setSizePercent(int sizePercent) {
            this.sizePercent = GboardSpacebarLogoContract.normalizeSizePercent(sizePercent);
            fitWithinKey();
        }

        private void fitWithinKey() {
            // Before the key's first layout the box stays 1 px; the layout listener sizes it.
            int width = scaled(root.getWidth() - root.getPaddingLeft() - root.getPaddingRight());
            int height = scaled(root.getHeight() - root.getPaddingTop() - root.getPaddingBottom());
            ViewGroup.LayoutParams params = getLayoutParams();
            if (params != null && (params.width != width || params.height != height)) {
                params.width = width;
                params.height = height;
                setLayoutParams(params);
            }
        }

        private int scaled(int innerPx) {
            return innerPx <= 0 ? 1 : Math.max(1, Math.round(innerPx * sizePercent / 100f));
        }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            try { fitWithinKey(); }
            catch (Throwable failure) { logWarningSafely("Failed to resize spacebar image", failure); }
        }
    }

    private static ImageView findOverlay(ViewGroup root) {
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            View child = root.getChildAt(index);
            if (child instanceof ImageView && OVERLAY_TAG.equals(child.getTag())) {
                return (ImageView) child;
            }
        }
        return null;
    }

    private static void rememberLabelVisibility(View label) {
        synchronized (ORIGINAL_LABEL_VISIBILITIES) {
            if (!ORIGINAL_LABEL_VISIBILITIES.containsKey(label)) {
                ORIGINAL_LABEL_VISIBILITIES.put(label, label.getVisibility());
            }
        }
    }

    private static void restoreLabelVisibility(View label) {
        Integer visibility;
        synchronized (ORIGINAL_LABEL_VISIBILITIES) {
            visibility = ORIGINAL_LABEL_VISIBILITIES.remove(label);
        }
        if (visibility != null) label.setVisibility(visibility.intValue());
    }

    public static final class SettingsSnapshot {
        public final boolean enabled;
        public final boolean useFixedColor;
        public final int color;
        public final String iconValue;
        public final String customImage;
        public final boolean customImageTint;
        public final int sizePercent;

        public SettingsSnapshot(boolean enabled, boolean useFixedColor, int color,
                String iconValue) {
            this(enabled, useFixedColor, color, iconValue, "", false);
        }

        public SettingsSnapshot(boolean enabled, boolean useFixedColor, int color,
                String iconValue, String customImage, boolean customImageTint) {
            this(enabled, useFixedColor, color, iconValue, customImage, customImageTint,
                    GboardSpacebarLogoContract.DEFAULT_SIZE_PERCENT);
        }

        public SettingsSnapshot(boolean enabled, boolean useFixedColor, int color,
                String iconValue, String customImage, boolean customImageTint, int sizePercent) {
            this.enabled = enabled;
            this.useFixedColor = useFixedColor;
            this.color = color;
            this.iconValue = GboardSpacebarLogoContract.normalizeIcon(iconValue);
            this.customImage = GboardSpacebarLogoContract.boundedImageValue(customImage);
            this.customImageTint = customImageTint;
            this.sizePercent = GboardSpacebarLogoContract.normalizeSizePercent(sizePercent);
        }

        public boolean shouldTint() {
            return useFixedColor
                    && !GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR.equals(iconValue)
                    && (!GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE.equals(iconValue)
                            || customImageTint);
        }
    }

    public static final class ViewBindings {
        public final int spacebarViewId;
        public final int primaryLabelViewId;
        private final int googleGDrawableId;
        private final int heartDrawableId;
        private final int emojiDrawableId;

        public ViewBindings(int spacebarViewId, int primaryLabelViewId,
                int googleGDrawableId, int heartDrawableId, int emojiDrawableId) {
            this.spacebarViewId = spacebarViewId;
            this.primaryLabelViewId = primaryLabelViewId;
            this.googleGDrawableId = googleGDrawableId;
            this.heartDrawableId = heartDrawableId;
            this.emojiDrawableId = emojiDrawableId;
        }

        public int drawableResourceIdForIcon(String iconValue) {
            String normalized = GboardSpacebarLogoContract.normalizeIcon(iconValue);
            if (GboardSpacebarLogoContract.ICON_HEART.equals(normalized)) return heartDrawableId;
            if (GboardSpacebarLogoContract.ICON_EMOJI.equals(normalized)) return emojiDrawableId;
            return googleGDrawableId;
        }
    }

    private static final class BoundSpacebar {
        final WeakReference<View> softKeyView;
        final boolean isSpaceAction;
        final ViewBindings bindings;

        BoundSpacebar(View softKeyView, boolean isSpaceAction, ViewBindings bindings) {
            this.softKeyView = new WeakReference<>(softKeyView);
            this.isSpaceAction = isSpaceAction;
            this.bindings = bindings;
        }
    }
}
