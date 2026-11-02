package dev.jason.gboardpatches.extension.zhuyinbottomrow;

import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime;

@SuppressWarnings("unused")
public final class GboardZhuyinBottomRowWeightRuntime {
    private static final String TAG = "GboardZhuyinBottomRow";
    private static final String SOFT_KEY_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyView";
    private static final String SOFT_KEYBOARD_VIEW_CLASS =
            "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView";
    private static final long SETTINGS_CACHE_WINDOW_MS = 250L;
    private static final String[] NORMAL_EXPECTED_SLOT_NAMES = new String[] {
            "key_pos_switch_to_symbol",
            "A02",
            "key_pos_switch_to_next_language",
            "key_pos_space",
            "A06",
            "key_pos_del",
            "key_pos_ime_action"
    };
    private static final int NORMAL_FOOTER_DIRECT_CHILD_COUNT = 5;
    private static final int NORMAL_GLOBE_CLUSTER_CHILD_COUNT = 2;
    private static final int NORMAL_SPACE_CLUSTER_CHILD_COUNT = 2;

    private static final Map<View, AppliedWeight> APPLIED_WEIGHTS = new WeakHashMap<>();

    private static final Object SETTINGS_LOCK = new Object();
    private static volatile SettingsSnapshot cachedSettingsSnapshot;

    private GboardZhuyinBottomRowWeightRuntime() {
    }

    public static void afterSoftKeyBound(Object receiver) {
        if (!(receiver instanceof View softKeyView)) {
            return;
        }
        try {
            SettingsSnapshot settings = settingsSnapshot(softKeyView);
            if (settings == null || !settings.enabled) {
                restoreOwnedWeights(softKeyView);
                return;
            }
            ClassLoader classLoader = receiver.getClass().getClassLoader();
            if (classLoader == null) {
                return;
            }
            GboardZhuyinBottomRowWeightRuntimeSupport.ReflectionHandles handles =
                    GboardZhuyinBottomRowWeightRuntimeSupport.reflectionHandles(classLoader);
            Object receiverMetadata =
                    resolveKeyMetadataForInterop(handles.softKeyMetadataField.get(softKeyView));
            GboardZhuyinBottomRowWeightRuntimeSupport.KeyBehavior receiverBehavior =
                    receiverMetadata == null
                            ? null
                            : GboardZhuyinBottomRowWeightRuntimeSupport.inspectKeyBehavior(
                                    handles,
                                    receiverMetadata);
            FooterCandidate footer = resolveFooterCandidate(softKeyView, handles, receiverBehavior);
            if (footer == null) {
                return;
            }
            if (applyWeights(footer, settings)) {
                footer.footer.requestLayout();
                footer.middleCluster.requestLayout();
                footer.spaceCluster.requestLayout();
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to apply Zhuyin bottom row weights", throwable);
        }
    }

    static SettingsSnapshot settingsSnapshotFromPreferences(SharedPreferences preferences,
            long loadedAtElapsedMs) {
        if (preferences == null) {
            return new SettingsSnapshot(
                    loadedAtElapsedMs,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_ENABLED,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_SWITCH_TO_SYMBOL,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_A02,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_SWITCH_TO_NEXT_LANGUAGE,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_SPACE,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_A06,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_A08,
                    GboardZhuyinBottomRowWeightSettings.DEFAULT_WEIGHT_IME_ACTION);
        }
        return new SettingsSnapshot(
                loadedAtElapsedMs,
                GboardZhuyinBottomRowWeightSettings.readEnabled(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightSwitchToSymbol(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightA02(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightSwitchToNextLanguage(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightSpace(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightA06(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightA08(preferences),
                GboardZhuyinBottomRowWeightSettings.readWeightImeAction(preferences));
    }

    static boolean isNormalZhuyinFooter(String[] slotNames, String commaLabel, String spaceLabel,
            String a06Label) {
        if (slotNames == null || !Arrays.equals(NORMAL_EXPECTED_SLOT_NAMES, slotNames)) {
            return false;
        }
        String normalizedComma = safeTrim(commaLabel);
        String normalizedSpace = safeTrim(spaceLabel);
        String normalizedA06 = safeTrim(a06Label);
        return ("，".equals(normalizedComma) || ",".equals(normalizedComma)
                || normalizedComma.contains("，"))
                && normalizedSpace.contains("注音")
                && normalizedA06.contains("ㄦ");
    }

    private static SettingsSnapshot settingsSnapshot(View softKeyView) {
        long now = android.os.SystemClock.elapsedRealtime();
        SettingsSnapshot cached = cachedSettingsSnapshot;
        if (cached != null && now - cached.loadedAtElapsedMs <= SETTINGS_CACHE_WINDOW_MS) {
            return cached;
        }
        synchronized (SETTINGS_LOCK) {
            SettingsSnapshot current = cachedSettingsSnapshot;
            if (current != null && now - current.loadedAtElapsedMs <= SETTINGS_CACHE_WINDOW_MS) {
                return current;
            }
            SharedPreferences preferences =
                    GboardZhuyinBottomRowWeightSettings.preferences(softKeyView.getContext());
            GboardZhuyinBottomRowWeightSettings.ensureDefaults(preferences);
            SettingsSnapshot loaded = settingsSnapshotFromPreferences(preferences, now);
            cachedSettingsSnapshot = loaded;
            return loaded;
        }
    }

    private static FooterCandidate resolveFooterCandidate(View softKeyView,
            GboardZhuyinBottomRowWeightRuntimeSupport.ReflectionHandles handles,
            GboardZhuyinBottomRowWeightRuntimeSupport.KeyBehavior receiverBehavior)
            throws Throwable {
        FooterCandidate footer = findFooterCandidate(softKeyView);
        if (footer == null) {
            footer = findFooterCandidateFromKeyboardRoot(softKeyView);
        }
        if (footer == null) {
            restoreOwnedWeights(softKeyView);
            return null;
        }
        if (!Arrays.equals(NORMAL_EXPECTED_SLOT_NAMES, footer.slotNames)) {
            return null;
        }
        String commaLabel = bestVisibleLabel(handles, footer.slotViews[1]);
        String spaceLabel = bestVisibleLabel(handles, footer.slotViews[3]);
        String a06Label = bestVisibleLabel(handles, footer.slotViews[4]);
        KeyboardBodyKind bodyKind = inspectKeyboardBody(
                findKeyboardRoot(softKeyView), footer.footer, handles);
        if (bodyKind == KeyboardBodyKind.LATIN) {
            restoreOwnedWeights(softKeyView);
            return null;
        }
        if (bodyKind != KeyboardBodyKind.ZHUYIN
                && !shouldApplyZhuyinFooter(footer.slotNames, receiverBehavior, commaLabel,
                        spaceLabel, a06Label)) {
            return null;
        }
        return footer;
    }

    private static boolean shouldApplyZhuyinFooter(String[] slotNames,
            GboardZhuyinBottomRowWeightRuntimeSupport.KeyBehavior receiverBehavior,
            String commaLabel, String spaceLabel, String a06Label) {
        if (slotNames == null || !Arrays.equals(NORMAL_EXPECTED_SLOT_NAMES, slotNames)) {
            return false;
        }
        return receiverBehavior != null
                && receiverBehavior.legacyKeyboardKind
                == GboardZhuyinBottomRowWeightRuntimeSupport.LegacyKeyboardKind.ZHUYIN
                || isNormalZhuyinFooter(slotNames, commaLabel, spaceLabel, a06Label);
    }

    private enum KeyboardBodyKind { UNKNOWN, LATIN, ZHUYIN }

    /** Control keys and their composing labels cannot establish the body's language. */
    private static KeyboardBodyKind inspectKeyboardBody(View view, View footer,
            GboardZhuyinBottomRowWeightRuntimeSupport.ReflectionHandles handles) throws Throwable {
        if (view == null || view == footer) {
            return KeyboardBodyKind.UNKNOWN;
        }
        if (SOFT_KEY_VIEW_CLASS.equals(view.getClass().getName())
                || "SoftKeyView".equals(view.getClass().getSimpleName())) {
            Object metadata = resolveKeyMetadataForInterop(handles.softKeyMetadataField.get(view));
            if (metadata == null) {
                return KeyboardBodyKind.UNKNOWN;
            }
            GboardZhuyinBottomRowWeightRuntimeSupport.KeyBehavior behavior =
                    GboardZhuyinBottomRowWeightRuntimeSupport.inspectKeyBehavior(handles, metadata);
            if (behavior == null) {
                return KeyboardBodyKind.UNKNOWN;
            }
            if (behavior.legacyKeyboardKind == GboardZhuyinBottomRowWeightRuntimeSupport.LegacyKeyboardKind.ZHUYIN) {
                return KeyboardBodyKind.ZHUYIN;
            }
            return isLatinLetter(behavior.primaryLabel) || isLatinLetter(behavior.pressText)
                    ? KeyboardBodyKind.LATIN : KeyboardBodyKind.UNKNOWN;
        }
        KeyboardBodyKind result = KeyboardBodyKind.UNKNOWN;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                KeyboardBodyKind child = inspectKeyboardBody(
                        group.getChildAt(index), footer, handles);
                if (child == KeyboardBodyKind.ZHUYIN) {
                    return child;
                }
                if (child == KeyboardBodyKind.LATIN) {
                    result = child;
                }
            }
        }
        return result;
    }

    private static boolean isLatinLetter(String value) {
        if (value == null || value.length() != 1) {
            return false;
        }
        char letter = value.charAt(0);
        return (letter >= 'a' && letter <= 'z') || (letter >= 'A' && letter <= 'Z');
    }

    private static FooterCandidate findFooterCandidate(View softKeyView) {
        ViewParent parent = softKeyView.getParent();
        while (parent instanceof ViewGroup group) {
            FooterCandidate candidate = tryBuildNormalFooterCandidate(group);
            if (candidate != null) {
                return candidate;
            }
            parent = group.getParent();
        }
        return null;
    }

    private static FooterCandidate findFooterCandidateFromKeyboardRoot(View softKeyView) {
        ViewGroup keyboardRoot = findKeyboardRoot(softKeyView);
        if (keyboardRoot == null) {
            return null;
        }
        return findFooterCandidateInSubtree(keyboardRoot);
    }

    private static ViewGroup findKeyboardRoot(View softKeyView) {
        ViewParent current = softKeyView == null ? null : softKeyView.getParent();
        while (current instanceof ViewGroup group) {
            String className = group.getClass().getName();
            if (SOFT_KEYBOARD_VIEW_CLASS.equals(className)
                    || "SoftKeyboardView".equals(group.getClass().getSimpleName())) {
                return group;
            }
            current = group.getParent();
        }
        return null;
    }

    private static FooterCandidate findFooterCandidateInSubtree(View root) {
        if (!(root instanceof ViewGroup group)) {
            return null;
        }
        FooterCandidate candidate = tryBuildNormalFooterCandidate(group);
        if (candidate != null) {
            return candidate;
        }
        int childCount = group.getChildCount();
        for (int index = 0; index < childCount; index++) {
            FooterCandidate nested = findFooterCandidateInSubtree(group.getChildAt(index));
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static FooterCandidate tryBuildNormalFooterCandidate(ViewGroup footer) {
        if (footer == null || footer.getChildCount() != NORMAL_FOOTER_DIRECT_CHILD_COUNT) {
            return null;
        }

        View switchToSymbol = footer.getChildAt(0);
        View a02 = footer.getChildAt(1);
        View middleClusterView = footer.getChildAt(2);
        View deleteKey = footer.getChildAt(3);
        View imeAction = footer.getChildAt(4);
        if (!matchesSlot(switchToSymbol, NORMAL_EXPECTED_SLOT_NAMES[0])
                || !matchesSlot(a02, NORMAL_EXPECTED_SLOT_NAMES[1])
                || !matchesSlot(deleteKey, NORMAL_EXPECTED_SLOT_NAMES[5])
                || !matchesSlot(imeAction, NORMAL_EXPECTED_SLOT_NAMES[6])) {
            return null;
        }

        ViewGroup middleCluster = asGroupWithChildCount(
                middleClusterView, NORMAL_GLOBE_CLUSTER_CHILD_COUNT);
        if (middleCluster == null) {
            return null;
        }
        View switchToNextLanguage = middleCluster.getChildAt(0);
        View spaceClusterView = middleCluster.getChildAt(1);
        if (!matchesSlot(switchToNextLanguage, NORMAL_EXPECTED_SLOT_NAMES[2])) {
            return null;
        }

        ViewGroup spaceCluster = asGroupWithChildCount(
                spaceClusterView, NORMAL_SPACE_CLUSTER_CHILD_COUNT);
        if (spaceCluster == null) {
            return null;
        }
        View space = spaceCluster.getChildAt(0);
        View a06 = spaceCluster.getChildAt(1);
        if (!matchesSlot(space, NORMAL_EXPECTED_SLOT_NAMES[3])
                || !matchesSlot(a06, NORMAL_EXPECTED_SLOT_NAMES[4])) {
            return null;
        }

        return new FooterCandidate(
                footer,
                middleCluster,
                spaceCluster,
                new View[] {
                        switchToSymbol,
                        a02,
                        switchToNextLanguage,
                        space,
                        a06,
                        deleteKey,
                        imeAction
                },
                Arrays.copyOf(NORMAL_EXPECTED_SLOT_NAMES, NORMAL_EXPECTED_SLOT_NAMES.length));
    }

    private static ViewGroup asGroupWithChildCount(View view, int expectedChildCount) {
        if (!(view instanceof ViewGroup group) || group.getChildCount() != expectedChildCount) {
            return null;
        }
        return group;
    }

    private static boolean matchesSlot(View view, String expectedSlotName) {
        return expectedSlotName.equals(slotName(view));
    }

    private static String bestVisibleLabel(
            GboardZhuyinBottomRowWeightRuntimeSupport.ReflectionHandles handles,
            View keyView) throws Throwable {
        Object keyMetadata = resolveKeyMetadataForInterop(handles.softKeyMetadataField.get(keyView));
        if (keyMetadata == null) {
            return "";
        }
        String primaryLabel = safeTrim(handles.extractPrimaryLabel(keyMetadata));
        if (!primaryLabel.isEmpty()) {
            return primaryLabel;
        }
        Object pressAction = handles.findExactAction(keyMetadata, handles.pressActionType);
        return safeTrim(handles.extractSinglePayloadToken(pressAction));
    }

    static Object resolveKeyMetadataForInterop(Object keyMetadata) {
        return GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(keyMetadata);
    }

    private static String slotName(View view) {
        if (view == null || view.getId() == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean applyWeights(FooterCandidate footer, SettingsSnapshot settings) {
        WeightTarget[] weightTargets = new WeightTarget[] {
                new WeightTarget(footer.slotViews[0], settings.switchToSymbolWeight),
                new WeightTarget(footer.slotViews[1], settings.a02Weight),
                new WeightTarget(footer.middleCluster,
                        settings.switchToNextLanguageWeight + settings.spaceWeight
                                + settings.a06Weight),
                new WeightTarget(footer.slotViews[2], settings.switchToNextLanguageWeight),
                new WeightTarget(footer.spaceCluster,
                        settings.spaceWeight + settings.a06Weight),
                new WeightTarget(footer.slotViews[3], settings.spaceWeight),
                new WeightTarget(footer.slotViews[4], settings.a06Weight),
                new WeightTarget(footer.slotViews[5], settings.a08Weight),
                new WeightTarget(footer.slotViews[6], settings.imeActionWeight)
        };

        LinearLayout.LayoutParams[] paramsByTarget = resolveLinearLayoutParams(weightTargets);
        if (paramsByTarget == null) {
            return false;
        }

        boolean changed = false;
        for (int index = 0; index < weightTargets.length; index++) {
            WeightTarget weightTarget = weightTargets[index];
            LinearLayout.LayoutParams params = paramsByTarget[index];
            if (Float.compare(params.weight, weightTarget.weight) != 0) {
                rememberWeight(weightTarget.view, params, weightTarget.weight, footer.footer);
                params.weight = weightTarget.weight;
                weightTarget.view.setLayoutParams(params);
                changed = true;
            }
        }
        return changed;
    }

    private static void rememberWeight(View view, LinearLayout.LayoutParams params,
            float applied, ViewGroup footer) {
        synchronized (APPLIED_WEIGHTS) {
            AppliedWeight previous = APPLIED_WEIGHTS.get(view);
            float baseline = previous != null && previous.stillOwns(view, params)
                    ? previous.baseline : params.weight;
            ViewGroup keyboard = findKeyboardRoot(view);
            APPLIED_WEIGHTS.put(view, new AppliedWeight(view, params, baseline, applied,
                    keyboard == null ? footer : keyboard));
        }
    }

    static void restoreOwnedWeights(View softKeyView) {
        ViewGroup scope = findKeyboardRoot(softKeyView);
        if (scope == null && softKeyView != null) {
            FooterCandidate footer = findFooterCandidate(softKeyView);
            scope = footer == null ? null : footer.footer;
        }
        if (scope == null) {
            return;
        }
        synchronized (APPLIED_WEIGHTS) {
            Iterator<Map.Entry<View, AppliedWeight>> iterator = APPLIED_WEIGHTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<View, AppliedWeight> entry = iterator.next();
                AppliedWeight applied = entry.getValue();
                if (applied.scope.get() != scope) {
                    continue;
                }
                View view = entry.getKey();
                ViewGroup.LayoutParams raw = view.getLayoutParams();
                iterator.remove();
                if (raw instanceof LinearLayout.LayoutParams params
                        && applied.stillOwns(view, params)) {
                    params.weight = applied.baseline;
                    view.setLayoutParams(params);
                }
            }
        }
    }

    private static LinearLayout.LayoutParams[] resolveLinearLayoutParams(
            WeightTarget[] weightTargets) {
        if (weightTargets == null) {
            return null;
        }
        LinearLayout.LayoutParams[] paramsByTarget =
                new LinearLayout.LayoutParams[weightTargets.length];
        for (int index = 0; index < weightTargets.length; index++) {
            ViewGroup.LayoutParams rawParams = weightTargets[index].view.getLayoutParams();
            if (!(rawParams instanceof LinearLayout.LayoutParams params)) {
                return null;
            }
            paramsByTarget[index] = params;
        }
        return paramsByTarget;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    static final class SettingsSnapshot {
        final long loadedAtElapsedMs;
        final boolean enabled;
        final int switchToSymbolWeight;
        final int a02Weight;
        final int switchToNextLanguageWeight;
        final int spaceWeight;
        final int a06Weight;
        final int a08Weight;
        final int imeActionWeight;

        SettingsSnapshot(long loadedAtElapsedMs, boolean enabled, int switchToSymbolWeight,
                int a02Weight, int switchToNextLanguageWeight, int spaceWeight,
                int a06Weight, int a08Weight, int imeActionWeight) {
            this.loadedAtElapsedMs = loadedAtElapsedMs;
            this.enabled = enabled;
            this.switchToSymbolWeight =
                    GboardZhuyinBottomRowWeightSettings.sanitizeWeight(switchToSymbolWeight);
            this.a02Weight = GboardZhuyinBottomRowWeightSettings.sanitizeWeight(a02Weight);
            this.switchToNextLanguageWeight =
                    GboardZhuyinBottomRowWeightSettings.sanitizeWeight(
                            switchToNextLanguageWeight);
            this.spaceWeight = GboardZhuyinBottomRowWeightSettings.sanitizeWeight(spaceWeight);
            this.a06Weight = GboardZhuyinBottomRowWeightSettings.sanitizeWeight(a06Weight);
            this.a08Weight = GboardZhuyinBottomRowWeightSettings.sanitizeWeight(a08Weight);
            this.imeActionWeight =
                    GboardZhuyinBottomRowWeightSettings.sanitizeWeight(imeActionWeight);
        }
    }

    private static final class FooterCandidate {
        final ViewGroup footer;
        final ViewGroup middleCluster;
        final ViewGroup spaceCluster;
        final View[] slotViews;
        final String[] slotNames;

        FooterCandidate(ViewGroup footer, ViewGroup middleCluster, ViewGroup spaceCluster,
                View[] slotViews, String[] slotNames) {
            this.footer = footer;
            this.middleCluster = middleCluster;
            this.spaceCluster = spaceCluster;
            this.slotViews = slotViews;
            this.slotNames = slotNames;
        }
    }

    private static final class WeightTarget {
        final View view;
        final float weight;

        WeightTarget(View view, float weight) {
            this.view = view;
            this.weight = weight;
        }
    }
    private static final class AppliedWeight {
        final WeakReference<LinearLayout.LayoutParams> params;
        final WeakReference<ViewParent> parent;
        final WeakReference<ViewGroup> scope;
        final float baseline;
        final float applied;

        AppliedWeight(View view, LinearLayout.LayoutParams params, float baseline,
                float applied, ViewGroup scope) {
            this.params = new WeakReference<>(params);
            this.parent = new WeakReference<>(view.getParent());
            this.scope = new WeakReference<>(scope);
            this.baseline = baseline;
            this.applied = applied;
        }

        boolean stillOwns(View view, LinearLayout.LayoutParams current) {
            return params.get() == current && parent.get() == view.getParent()
                    && Float.compare(current.weight, applied) == 0;
        }
    }
}
