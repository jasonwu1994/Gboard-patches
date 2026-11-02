package dev.jason.gboardpatches.extension.toprowswipe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsProvider;
import dev.jason.gboardpatches.extension.zhuyinslide.GboardZhuyinSlideRuntime;

@SuppressWarnings("unused")
public final class GboardTopRowSwipeRuntime {
    private static final String TAG = "GboardTopRowSwipe";
    static final String QUICK_JS_PAYLOAD_PREFIX = "__hc_qjs__:";
    static final float HORIZONTAL_TRIGGER_DP = 10f;
    static final float HORIZONTAL_TRIGGER_KEY_WIDTHS = 0.5f;
    static final float MAX_DOWNWARD_DRIFT_DP = 72f;
    static final float MAX_UPWARD_DRIFT_DP = 24f;
    static final long ROW_TRANSITION_DURATION_MS = 160L;
    static final long SETTINGS_CACHE_WINDOW_MS = 1_000L;
    static final long CONSUMED_SESSION_RECYCLE_WINDOW_MS = 250L;
    private static final String ACCESS_POINTS_BAR_ITEM_SOFT_KEY_VIEW =
            "com.google.android.libraries.inputmethod.accesspoint.widget"
                    + ".AccessPointsBarItemSoftKeyView";
    private static final String ACCESS_POINTS_BAR_VIEW =
            "com.google.android.libraries.inputmethod.accesspoint.widget.AccessPointsBar";
    private static final String PROACTIVE_SUGGESTIONS_HOLDER_VIEW =
            "com.google.android.apps.inputmethod.libs.proactivesuggestion"
                    + ".ProactiveSuggestionsHolderView";
    private static final String FIXED_CANDIDATES_ID = "softkey_holder_fixed_candidates";
    private static final String PROACTIVE_SUGGESTIONS_ID = "key_pos_proactive_suggestions";
    private static final String HEADER_ID_PREFIX = "key_pos_header_";
    private static final String KEYBOARD_HEADER_TAG = ".keyboard-header-area";
    private static final String CANDIDATES_HOLDER_SUFFIX = "CandidatesHolderView";
    static final String ROW_TRANSITION_OVERLAY_TAG =
            "gboardpatches:top-row-swipe-transition";

    static final Map<Object, SwipeSession> SESSIONS =
            Collections.synchronizedMap(new WeakHashMap<Object, SwipeSession>());
    static final Map<Object, VisibleTopRowKey> VISIBLE_TOP_ROW_KEYS =
            Collections.synchronizedMap(new WeakHashMap<Object, VisibleTopRowKey>());
    static final Map<Object, Object> ORIGINAL_KEY_METADATA_BY_PATCHED =
            new WeakIdentityMap<Object>();
    static final Map<Object, PatchedTopRowMetadataState> TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS =
            new WeakIdentityMap<PatchedTopRowMetadataState>();
    private static final ThreadLocal<Map<View, VisibleTopRowKey>>
            PREVALIDATED_ENGLISH_REFRESH = new ThreadLocal<Map<View, VisibleTopRowKey>>();
    private static final Map<View, Runnable> ROW_TRANSITION_OVERLAY_CLEANUPS =
            Collections.synchronizedMap(new WeakHashMap<View, Runnable>());

    private static final Map<TopRowLayoutKind, TopRowPage> CURRENT_PAGE_BY_LAYOUT_KIND =
            Collections.synchronizedMap(new EnumMap<TopRowLayoutKind, TopRowPage>(
                    TopRowLayoutKind.class));
    private static final Object SETTINGS_LOCK = new Object();
    private static final AtomicInteger ENGLISH_FALLBACK_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger ENGLISH_SESSION_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SESSION_RECYCLE_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SESSION_CONSUME_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SESSION_APPLY_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SESSION_CACHE_HIT_LOG_COUNT = new AtomicInteger(0);

    private static volatile SettingsSnapshot cachedSettingsSnapshot;

    private GboardTopRowSwipeRuntime() {
    }

    enum TopRowPage {
        STOCK,
        CUSTOM
    }

    enum TopRowLayoutKind {
        NONE,
        ZHUYIN,
        ENGLISH_QWERTY
    }

    static final class TopRowSlot {
        final int rowIndex;
        final TopRowLayoutKind layoutKind;

        TopRowSlot(int rowIndex, TopRowLayoutKind layoutKind) {
            this.rowIndex = rowIndex;
            this.layoutKind = layoutKind;
        }
    }

    static final class SwipeSession {
        final Object anchorSoftKeyView;
        final float startX;
        final float startY;
        final TopRowLayoutKind layoutKind;
        final int rowIndex;
        boolean armed;
        boolean consumed;
        TopRowPage pendingPage;
        int swipeDirectionSign;
        long consumedAtElapsedMs;

        SwipeSession(Object anchorSoftKeyView, float startX, float startY,
                TopRowLayoutKind layoutKind) {
            this(anchorSoftKeyView, startX, startY, layoutKind, -1);
        }

        SwipeSession(Object anchorSoftKeyView, float startX, float startY,
                TopRowLayoutKind layoutKind, int rowIndex) {
            this.anchorSoftKeyView = anchorSoftKeyView;
            this.startX = startX;
            this.startY = startY;
            this.layoutKind = layoutKind;
            this.rowIndex = rowIndex;
        }
    }

    static final class VisibleTopRowKey {
        final Object originalMetadata;
        final int rowIndex;
        final TopRowLayoutKind layoutKind;

        VisibleTopRowKey(Object originalMetadata, int rowIndex,
                TopRowLayoutKind layoutKind) {
            this.originalMetadata = originalMetadata;
            this.rowIndex = rowIndex;
            this.layoutKind = layoutKind;
        }
    }

    static final class PatchedTopRowMetadataState {
        final TopRowLayoutKind layoutKind;
        final int rowIndex;
        final boolean isJavaScript;
        final String quickJsScriptSource;
        final int quickJsTimeoutMs;

        PatchedTopRowMetadataState(TopRowLayoutKind layoutKind, int rowIndex, TopRowItem item) {
            this.layoutKind = layoutKind;
            this.rowIndex = rowIndex;
            this.isJavaScript = item != null && item.isJavaScript;
            this.quickJsScriptSource = isJavaScript ? item.scriptText : null;
            this.quickJsTimeoutMs = item != null
                    ? item.timeoutMs : GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MS;
        }
    }

    static final class QuickJsSlotConfig {
        final TopRowLayoutKind layoutKind;
        final int rowIndex;
        final String scriptSource;
        final int timeoutMs;

        QuickJsSlotConfig(TopRowLayoutKind layoutKind, int rowIndex, String scriptSource,
                int timeoutMs) {
            this.layoutKind = layoutKind;
            this.rowIndex = rowIndex;
            this.scriptSource = scriptSource;
            this.timeoutMs = timeoutMs;
        }
    }

    static final class RowSnapshot {
        final ViewGroup host;
        final Rect bounds;
        final Bitmap bitmap;

        RowSnapshot(ViewGroup host, Rect bounds, Bitmap bitmap) {
            this.host = host;
            this.bounds = bounds;
            this.bitmap = bitmap;
        }
    }

    static final class TopRowItem {
        final String label;
        final String commitText;
        final boolean isJavaScript;
        final String scriptText;
        final int timeoutMs;

        TopRowItem(String label, String commitText) {
            this(label, commitText, false, "", GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MS);
        }

        TopRowItem(String label, String commitText, boolean isJavaScript, String scriptText,
                int timeoutMs) {
            this.label = label;
            this.commitText = commitText;
            this.isJavaScript = isJavaScript;
            this.scriptText = scriptText;
            this.timeoutMs = timeoutMs;
        }
    }

    static final class SettingsSnapshot {
        final long loadedAtElapsedMs;
        final boolean available;
        final boolean enabled;
        final boolean zhuyinEnabled;
        final boolean englishQwertyEnabled;
        final String globalJavaScript;
        final GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits;
        final TopRowItem[] items;

        SettingsSnapshot(long loadedAtElapsedMs, boolean available, boolean enabled,
                boolean zhuyinEnabled, boolean englishQwertyEnabled, String globalJavaScript,
                GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits,
                TopRowItem[] items) {
            this.loadedAtElapsedMs = loadedAtElapsedMs;
            this.available = available;
            this.enabled = enabled;
            this.zhuyinEnabled = zhuyinEnabled;
            this.englishQwertyEnabled = englishQwertyEnabled;
            this.globalJavaScript = globalJavaScript != null ? globalJavaScript : "";
            this.javaScriptRuntimeLimits = javaScriptRuntimeLimits != null
                    ? javaScriptRuntimeLimits
                    : GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits();
            this.items = items;
        }
    }

    public static void triggerSlotExecution(InputMethodService service, int slotIndex) {
        Log.i(TAG, "triggerSlotExecution called for slot " + slotIndex);
        if (service == null || slotIndex < 0 || slotIndex >= GboardTopRowSwipeSettings.SLOT_COUNT) {
            return;
        }
        try {
            SettingsSnapshot snapshot = settingsSnapshot(service);
            TopRowItem item = customTopRowItem(snapshot, slotIndex);
            if (item == null) {
                return;
            }
            
            if (item.isJavaScript) {
                Log.i(TAG, "Executing JS for slot " + slotIndex);
                android.view.inputmethod.InputConnection ic = service.getCurrentInputConnection();
                String scriptInput = GboardQuickJsFeature.selectedInputText(ic);
                QuickJsSlotConfig config = new QuickJsSlotConfig(TopRowLayoutKind.ENGLISH_QWERTY, slotIndex, item.scriptText, item.timeoutMs);
                GboardQuickJsFeature.executeSlotScript(
                        null, 
                        service, 
                        ic, 
                        new GboardQuickJsFeature.QuickJsSlotKey(TopRowLayoutKind.ENGLISH_QWERTY, slotIndex),
                        config, 
                        scriptInput, 
                        globalJavaScript(service),
                        snapshot.javaScriptRuntimeLimits);
            } else if (item.commitText != null) {
                Log.i(TAG, "Committing text for slot " + slotIndex + ": " + item.commitText);
                android.view.inputmethod.InputConnection ic = service.getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(item.commitText, 1);
                }
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "Slot execution failed", throwable);
        }
    }

    public static String getSlotLabel(Context context, int slotIndex) {
        if (context == null || slotIndex < 0 || slotIndex >= GboardTopRowSwipeSettings.SLOT_COUNT) {
            return null;
        }
        SettingsSnapshot snapshot = settingsSnapshot(context);
        TopRowItem item = customTopRowItem(snapshot, slotIndex);
        return item != null ? item.label : null;
    }

    public static void triggerManualRowSwitch(InputMethodService service) {
        Log.i(TAG, "triggerManualRowSwitch called");
        if (service == null) {
            return;
        }
        try {
            SettingsSnapshot snapshot = settingsSnapshot(service);
            if (!snapshot.enabled) {
                Log.w(TAG, "Manual switch: Top Row Swipe is NOT enabled in settings!");
                // We proceed anyway to toggle the internal state, but it won't show anything
                // unless patchIncomingSoftKeyMetadata also ignores the enabled flag (which it shouldn't).
            }
            TopRowLayoutKind activeLayout = TopRowLayoutKind.NONE;
            Map<Object, VisibleTopRowKey> visibleKeys = null;
            synchronized (VISIBLE_TOP_ROW_KEYS) {
                if (!VISIBLE_TOP_ROW_KEYS.isEmpty()) {
                    visibleKeys = new HashMap<>(VISIBLE_TOP_ROW_KEYS);
                    activeLayout = visibleKeys.values().iterator().next().layoutKind;
                }
            }
            Log.i(TAG, "Active layout from cache: " + activeLayout + ", visible keys: " + (visibleKeys != null ? visibleKeys.size() : 0));
            if (activeLayout == TopRowLayoutKind.NONE) {
                activeLayout = TopRowLayoutKind.ENGLISH_QWERTY;
                Log.i(TAG, "Defaulting active layout to ENGLISH_QWERTY");
            }
            TopRowPage previousPage = currentPage(activeLayout);
            TopRowPage nextPage = toggledPage(previousPage);
            Log.i(TAG, "Toggling page: " + previousPage + " -> " + nextPage);
            
            ClassLoader classLoader = service.getClass().getClassLoader();
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            setCurrentPage(activeLayout, nextPage);
            
            if (visibleKeys != null && !visibleKeys.isEmpty()) {
                Log.i(TAG, "Refreshing " + visibleKeys.size() + " visible keys");
                @SuppressWarnings("unchecked")
                Map<View, VisibleTopRowKey> castedKeys = (Map<View, VisibleTopRowKey>) (Map<?, ?>) visibleKeys;
                refreshVisibleTopRowKeyMetadata(handles, castedKeys);
            } else {
                Log.w(TAG, "No visible keys tracked! Manual switch might not be immediately visible.");
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "Manual row switch failed", throwable);
        }
    }

    public static Object patchIncomingSoftKeyMetadata(Object receiver, Object keyMetadata) {
        if (!(receiver instanceof View view) || keyMetadata == null) {
            return keyMetadata;
        }
        if (TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.containsKey(keyMetadata)) {
            return keyMetadata;
        }
        try {
            ClassLoader classLoader = receiver.getClass().getClassLoader();
            if (classLoader == null) {
                return keyMetadata;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            Object originalMetadata = resolveOriginalKeyMetadata(keyMetadata);
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                    GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(handles, originalMetadata);
            SettingsSnapshot snapshot = settingsSnapshot(view.getContext());
            TopRowSlot customTopRowSlot =
                    topRowSlotForCustomPage(snapshot, view, behavior);
            if (customTopRowSlot == null) {
                return keyMetadata;
            }
            TopRowItem topRowItem = customTopRowItem(snapshot, customTopRowSlot.rowIndex);
            Object builder = handles.keyMetadataBuilderConstructor.newInstance();
            handles.copyKeyMetadataMethod.invoke(builder, originalMetadata);
            GboardTopRowSwipeRuntimeSupport.LabelSet labelSet =
                    GboardTopRowSwipeRuntimeSupport.appendOrReplaceTextLabelIds(
                            handles.extractKeyLabelIds(originalMetadata),
                            handles.extractKeyLabelTexts(originalMetadata),
                            GboardTopRowSwipeRuntimeSupport.PRIMARY_LABEL_VIEW_ID,
                            topRowItem.label);
            handles.setKeyLabelTextsMethod.invoke(builder, labelSet.ids, labelSet.texts);
            String actionPayload = topRowItem.isJavaScript
                    ? QUICK_JS_PAYLOAD_PREFIX
                    : topRowItem.commitText;
            Object pressAction = handles.buildPlainTextAction(handles.pressActionType,
                    actionPayload, topRowItem.label);
            if (pressAction == null) {
                return keyMetadata;
            }
            handles.replaceActionOnKeyMetadataBuilder(builder, handles.pressActionType,
                    pressAction);
            Object patched = handles.buildKeyMetadataMethod.invoke(builder);
            if (patched != null) {
                GboardZhuyinSlideRuntime.inheritPatchedMetadata(originalMetadata, patched);
                ORIGINAL_KEY_METADATA_BY_PATCHED.put(patched, originalMetadata);
                TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(
                        patched,
                        new PatchedTopRowMetadataState(
                                customTopRowSlot.layoutKind,
                                customTopRowSlot.rowIndex,
                                topRowItem));
                return patched;
            }
        } catch (Throwable ignored) {
            // Fail closed and keep stock metadata.
        }
        return keyMetadata;
    }

    public static void afterSoftKeyBound(Object receiver) {
        if (!(receiver instanceof View view)) {
            return;
        }
        if (isPrevalidatedEnglishRefreshView(view)) {
            return;
        }
        try {
            ClassLoader classLoader = receiver.getClass().getClassLoader();
            if (classLoader == null) {
                VISIBLE_TOP_ROW_KEYS.remove(receiver);
                return;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            Object boundMetadata = handles.softKeyMetadataField.get(receiver);
            Object originalMetadata = resolveOriginalKeyMetadata(boundMetadata);
            if (originalMetadata == null) {
                VISIBLE_TOP_ROW_KEYS.remove(receiver);
                return;
            }
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                    GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(handles, originalMetadata);
            TopRowSlot slot = topRowSlotForViewAndBehavior(view, behavior);
            if (slot == null || !isLayoutEnabled(view.getContext(), slot.layoutKind)) {
                VISIBLE_TOP_ROW_KEYS.remove(receiver);
                return;
            }
            VISIBLE_TOP_ROW_KEYS.put(receiver,
                    new VisibleTopRowKey(originalMetadata, slot.rowIndex, slot.layoutKind));
        } catch (Throwable ignored) {
            VISIBLE_TOP_ROW_KEYS.remove(receiver);
        }
    }

    public static Object maybeArmAndResolveTopRowOwner(Object tracker, Object incomingSoftKeyView,
            Object hitSoftKeyView, float x, float y) {
        if (tracker == null) {
            return incomingSoftKeyView;
        }
        long now = SystemClock.elapsedRealtime();
        SwipeSession session = SESSIONS.get(tracker);
        if (shouldRecycleSessionForIncomingView(session, incomingSoftKeyView, hitSoftKeyView)) {
            logLimited(SESSION_RECYCLE_LOG_COUNT,
                    "recycle-detached"
                            + " tracker=" + trackerId(tracker)
                            + " layout=" + session.layoutKind);
            clearSwipeSession(tracker);
            session = null;
        }
        if (shouldRecycleConsumedSession(now, session)) {
            logLimited(SESSION_RECYCLE_LOG_COUNT,
                    "recycle"
                            + " tracker=" + trackerId(tracker)
                            + " layout=" + session.layoutKind
                            + " ageMs=" + (now - session.consumedAtElapsedMs)
                            + " pendingPage=" + session.pendingPage);
            clearSwipeSession(tracker);
            session = null;
        }
        if (session == null) {
            Object candidateOwner = incomingSoftKeyView instanceof View
                    ? incomingSoftKeyView : hitSoftKeyView;
            if (!(candidateOwner instanceof View candidateView)) {
                return incomingSoftKeyView;
            }
            try {
                SettingsSnapshot snapshot = settingsSnapshot(candidateView.getContext());
                if (!snapshot.enabled) {
                    return incomingSoftKeyView;
                }
                ClassLoader classLoader = candidateOwner.getClass().getClassLoader();
                if (classLoader == null) {
                    return incomingSoftKeyView;
                }
                GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                        GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
                Object currentKeyMetadata = handles.softKeyMetadataField.get(candidateOwner);
                Object originalKeyMetadata = resolveOriginalKeyMetadata(currentKeyMetadata);
                GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                        GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(
                                handles, originalKeyMetadata);
                TopRowSlot slot = topRowSlotFromVisibleCache(candidateOwner);
                if (slot == null) {
                    slot = topRowSlotForViewAndBehavior(candidateView, behavior);
                } else {
                    logLimited(SESSION_CACHE_HIT_LOG_COUNT,
                            "cache-hit"
                                    + " viewId=" + GboardTopRowSwipeRuntimeSupport.safeViewName(candidateView)
                                    + " rowIndex=" + slot.rowIndex
                                    + " layout=" + slot.layoutKind);
                }
                if (slot == null) {
                    slot = recoverMarkerSlot(
                            topRowSlotFromVisibleState(candidateOwner),
                            viewSlotIndex(candidateView),
                            hasQuickJsMarkerPayload(handles, null, currentKeyMetadata),
                            inferLayoutKind(candidateView, handles),
                            snapshot);
                }
                if (slot == null || !isLayoutEnabled(snapshot, slot.layoutKind)) {
                    maybeLogEnglishCandidateMiss(candidateView, behavior, snapshot);
                    return incomingSoftKeyView;
                }
                int hydratedRowIndex = switch (slot.layoutKind) {
                    case ZHUYIN -> hydrateConfirmedZhuyinRow(handles, candidateView);
                    case ENGLISH_QWERTY ->
                            hydrateConfirmedEnglishQwertyRow(handles, candidateView);
                    case NONE -> -1;
                };
                if (hydratedRowIndex != slot.rowIndex) {
                    return incomingSoftKeyView;
                }
                SESSIONS.put(tracker, new SwipeSession(
                        candidateOwner, x, y, slot.layoutKind, slot.rowIndex));
                maybeLogEnglishSessionCreate(candidateView, behavior, slot);
                return incomingSoftKeyView;
            } catch (Throwable ignored) {
                return incomingSoftKeyView;
            }
        }

        if (session.armed || session.consumed) {
            return session.armed ? session.anchorSoftKeyView : incomingSoftKeyView;
        }
        if (!(session.anchorSoftKeyView instanceof View anchorView)) {
            clearSwipeSession(tracker);
            return incomingSoftKeyView;
        }
        float dx = x - session.startX;
        float dy = y - session.startY;
        float horizontal = Math.abs(dx);
        float down = Math.max(0f, dy);
        float up = Math.max(0f, -dy);
        float horizontalTriggerPx = Math.max(
                dpToPx(anchorView, HORIZONTAL_TRIGGER_DP),
                anchorView.getWidth() * HORIZONTAL_TRIGGER_KEY_WIDTHS);
        float maxDownwardDriftPx = dpToPx(anchorView, MAX_DOWNWARD_DRIFT_DP);
        float maxUpwardDriftPx = dpToPx(anchorView, MAX_UPWARD_DRIFT_DP);
        if (horizontal >= horizontalTriggerPx
                && down <= maxDownwardDriftPx
                && up <= maxUpwardDriftPx) {
            session.armed = true;
            session.swipeDirectionSign = dx >= 0f ? 1 : -1;
            return session.anchorSoftKeyView;
        }
        return incomingSoftKeyView;
    }

    public static void clearSwipeSession(Object tracker) {
        if (tracker == null) {
            return;
        }
        SwipeSession removed = SESSIONS.remove(tracker);
        if (removed != null) {
            applyPendingPageIfNeeded(removed);
        }
    }

    public static void finishSwipeSession(Object tracker) {
        SwipeSession session = SESSIONS.get(tracker);
        if (session == null || (!session.consumed && session.pendingPage == null)) {
            return;
        }
        clearSwipeSession(tracker);
    }

    public static boolean maybeConsumeTopRowSwipe(Object gestureDispatcher, Object tracker,
            Object action, Object keyMetadata) {
        if (isTopRowCustomPatchedMetadata(keyMetadata)) {
            Context context = null;
            try {
                ClassLoader classLoader = gestureDispatcher != null
                        ? gestureDispatcher.getClass().getClassLoader() : null;
                if (classLoader != null) {
                    GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                            GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
                    context = handles.extractGestureDispatcherContext(gestureDispatcher);
                }
            } catch (Throwable ignored) {
                context = null;
            }
            if (shouldConsumeDisabledTopRowCustomDispatch(context, keyMetadata)) {
                return true;
            }
        }

        SwipeSession session = SESSIONS.get(tracker);
        if (session == null || !session.armed || session.consumed) {
            return false;
        }
        Context context = session.anchorSoftKeyView instanceof View view
                ? view.getContext() : null;
        if (!isLayoutEnabled(context, session.layoutKind)) {
            return false;
        }
        TopRowPage visiblePage = currentPage(session.layoutKind);
        if (keyMetadata != null) {
            visiblePage = isTopRowCustomPatchedMetadata(keyMetadata)
                    ? TopRowPage.CUSTOM : TopRowPage.STOCK;
            setCurrentPage(session.layoutKind, visiblePage);
        }
        session.consumed = true;
        session.consumedAtElapsedMs = SystemClock.elapsedRealtime();
        session.pendingPage = toggledPage(visiblePage);
        logLimited(SESSION_CONSUME_LOG_COUNT,
                "consume"
                        + " tracker=" + trackerId(tracker)
                        + " layout=" + session.layoutKind
                        + " pendingPage=" + session.pendingPage);
        return true;
    }

    public static boolean maybeConsumeQuickJsTopRowPress(Object gestureDispatcher,
            Object action, Object keyMetadata) {
        return maybeConsumeQuickJsTopRowPress(
                gestureDispatcher, null, action, null, keyMetadata);
    }

    public static boolean maybeConsumeQuickJsTopRowPress(Object gestureDispatcher,
            Object tracker, Object action, Object actionData, Object keyMetadata) {
        if (!(action instanceof Enum<?> actionEnum) || !"PRESS".equals(actionEnum.name())) {
            return false;
        }
        try {
            ClassLoader classLoader = gestureDispatcher != null
                    ? gestureDispatcher.getClass().getClassLoader() : null;
            if (classLoader == null) {
                return false;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            boolean quickJsMarkerPayload =
                    hasQuickJsMarkerPayload(handles, actionData, keyMetadata);
            QuickJsSlotConfig config = quickJsSlotConfig(keyMetadata);
            if (config == null && quickJsMarkerPayload) {
                config = quickJsSlotConfigFromSession(handles, tracker, gestureDispatcher);
            }
            return GboardQuickJsFeature.maybeConsumeQuickJsTopRowPress(
                    null, handles, gestureDispatcher, config, quickJsMarkerPayload);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static SettingsSnapshot settingsSnapshot(Context context) {
        long now = SystemClock.elapsedRealtime();
        SettingsSnapshot cached = cachedSettingsSnapshot;
        if (cached != null && cached.available
                && now - cached.loadedAtElapsedMs <= SETTINGS_CACHE_WINDOW_MS) {
            return cached;
        }
        SettingsSnapshot loaded;
        synchronized (SETTINGS_LOCK) {
            SettingsSnapshot current = cachedSettingsSnapshot;
            if (current != null && current.available
                    && now - current.loadedAtElapsedMs <= SETTINGS_CACHE_WINDOW_MS) {
                return current;
            }
            loaded = loadSettingsSnapshot(context, now);
            cachedSettingsSnapshot = loaded.available ? loaded : null;
        }
        applySettingsSnapshotRuntimeState(loaded);
        return loaded;
    }

    static void clearSettingsSnapshotCache() {
        synchronized (SETTINGS_LOCK) {
            cachedSettingsSnapshot = null;
        }
    }

    static SettingsSnapshot settingsSnapshotFromProviderValues(Boolean enabled,
            Boolean zhuyinEnabled,
            Boolean englishQwertyEnabled,
            List<String> displays,
            List<String> commits,
            long loadedAtElapsedMs) {
        return settingsSnapshotFromProviderValues(
                enabled,
                zhuyinEnabled,
                englishQwertyEnabled,
                displays,
                commits,
                new boolean[GboardTopRowSwipeSettings.SLOT_COUNT],
                defaultScriptTexts(),
                defaultTimeouts(),
                "",
                GboardTopRowSwipeSettings.DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
                GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MAX_MS,
                GboardTopRowSwipeSettings.DEFAULT_MEMORY_LIMIT_BYTES,
                GboardTopRowSwipeSettings.DEFAULT_MAX_STACK_BYTES,
                loadedAtElapsedMs);
    }

    static SettingsSnapshot settingsSnapshotFromProviderValues(Boolean enabled,
            Boolean zhuyinEnabled,
            Boolean englishQwertyEnabled,
            List<String> displays,
            List<String> commits,
            boolean[] isJavaScripts,
            List<String> scripts,
            int[] timeouts,
            String globalJavaScript,
            int responseBodyLimitBytes,
            int timeoutMaxMs,
            int memoryLimitBytes,
            int maxStackBytes,
            long loadedAtElapsedMs) {
        if (enabled == null
                || zhuyinEnabled == null
                || englishQwertyEnabled == null
                || !hasExactlySlotCount(displays)
                || !hasExactlySlotCount(commits)
                || !hasExactlySlotCount(isJavaScripts)
                || !hasExactlySlotCount(scripts)
                || !hasExactlySlotCount(timeouts)
                || !hasOnlyNonBlankText(displays)
                || !hasOnlyText(commits)
                || !hasOnlyText(scripts)) {
            return unavailableSettingsSnapshot(loadedAtElapsedMs);
        }
        GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits =
                GboardTopRowSwipeSettings.sanitizeJavaScriptRuntimeLimits(
                        new GboardTopRowSwipeSettings.JavaScriptRuntimeLimits(
                                responseBodyLimitBytes,
                                timeoutMaxMs,
                                memoryLimitBytes,
                                maxStackBytes));
        return new SettingsSnapshot(
                loadedAtElapsedMs,
                true,
                enabled.booleanValue(),
                zhuyinEnabled.booleanValue(),
                englishQwertyEnabled.booleanValue(),
                globalJavaScript != null ? globalJavaScript : "",
                javaScriptRuntimeLimits,
                itemsFromSlots(GboardTopRowSwipeSettings.sanitizeSlots(
                        displays,
                        commits,
                        boxedBooleans(isJavaScripts),
                        scripts,
                        boxedIntegers(timeouts),
                        javaScriptRuntimeLimits.timeoutMaxMs)));
    }

    static TopRowPage currentPage(TopRowLayoutKind layoutKind) {
        if (layoutKind == null || layoutKind == TopRowLayoutKind.NONE) {
            return TopRowPage.STOCK;
        }
        synchronized (CURRENT_PAGE_BY_LAYOUT_KIND) {
            TopRowPage page = CURRENT_PAGE_BY_LAYOUT_KIND.get(layoutKind);
            return page != null ? page : TopRowPage.STOCK;
        }
    }

    static void setCurrentPage(TopRowLayoutKind layoutKind, TopRowPage page) {
        if (layoutKind == null || layoutKind == TopRowLayoutKind.NONE || page == null) {
            return;
        }
        CURRENT_PAGE_BY_LAYOUT_KIND.put(layoutKind, page);
    }

    static TopRowPage toggledPage(TopRowPage page) {
        return page == TopRowPage.STOCK ? TopRowPage.CUSTOM : TopRowPage.STOCK;
    }

    static TopRowLayoutKind topRowLayoutKindForSlot(int rowIndex,
            GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind legacyKeyboardKind,
            String primaryLabel) {
        if (rowIndex < 0 || rowIndex >= GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return TopRowLayoutKind.NONE;
        }
        if (legacyKeyboardKind == GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN) {
            return GboardTopRowSwipeRuntimeSupport.matchesZhuyinTopRowLabel(
                    rowIndex, primaryLabel)
                    ? TopRowLayoutKind.ZHUYIN : TopRowLayoutKind.NONE;
        }
        String normalizedLabel =
                GboardTopRowSwipeRuntimeSupport.asciiLowercase(primaryLabel);
        String expected = GboardTopRowSwipeRuntimeSupport.ENGLISH_QWERTY_TOP_ROW_LABELS[rowIndex];
        if (expected.equals(normalizedLabel) || (rowIndex == 5 && "z".equals(normalizedLabel))) {
            return TopRowLayoutKind.ENGLISH_QWERTY;
        }
        return TopRowLayoutKind.NONE;
    }

    static TopRowSlot topRowSlotForViewAndBehavior(View view,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior) {
        if (view == null || behavior == null || isDisallowedTopRowSurface(view)) {
            return null;
        }
        if (behavior.legacyKeyboardKind
                == GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN) {
            int rowIndex = GboardTopRowSwipeRuntimeSupport.topRowSlotIndexForView(view);
            return topRowLayoutKindForSlot(
                    rowIndex, behavior.legacyKeyboardKind,
                    englishQwertyDetectionLabel(behavior)) == TopRowLayoutKind.ZHUYIN
                    ? new TopRowSlot(rowIndex, TopRowLayoutKind.ZHUYIN) : null;
        }
        if (behavior.legacyKeyboardKind
                != GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY) {
            return null;
        }
        TopRowSlot prevalidated = prevalidatedEnglishSlot(view, behavior);
        if (prevalidated != null) {
            return prevalidated;
        }
        try {
            ClassLoader classLoader = view.getClass().getClassLoader();
            if (classLoader == null) {
                return null;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            List<View> rowViews = confirmedEnglishQwertyRow(view, handles);
            int rowIndex = confirmedRowIndex(rowViews, view);
            if (rowIndex >= 0) {
                return new TopRowSlot(rowIndex, TopRowLayoutKind.ENGLISH_QWERTY);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    static TopRowSlot topRowSlotForEnglishQwertyLabelFallback(
            boolean visualFirstRowConfirmed,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior) {
        if (behavior == null
                || behavior.legacyKeyboardKind
                        == GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN) {
            return null;
        }
        int englishRowIndex =
                GboardTopRowSwipeRuntimeSupport.englishQwertyTopRowIndexForLabel(
                        englishQwertyDetectionLabel(behavior));
        if (englishRowIndex < 0) {
            return null;
        }
        if (behavior.legacyKeyboardKind
                == GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY) {
            return new TopRowSlot(englishRowIndex, TopRowLayoutKind.ENGLISH_QWERTY);
        }
        if (!visualFirstRowConfirmed) {
            return null;
        }
        return new TopRowSlot(englishRowIndex, TopRowLayoutKind.ENGLISH_QWERTY);
    }

    private static String englishQwertyDetectionLabel(
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior) {
        if (behavior == null) {
            return null;
        }
        if (behavior.primaryLabel != null && !behavior.primaryLabel.isBlank()) {
            return behavior.primaryLabel;
        }
        if (behavior.pressText != null && !behavior.pressText.isBlank()) {
            return behavior.pressText;
        }
        return null;
    }

    static TopRowSlot topRowSlotFromVisibleCache(Object softKeyView) {
        TopRowSlot slot = topRowSlotFromVisibleState(softKeyView);
        if (slot == null || currentPage(slot.layoutKind) != TopRowPage.CUSTOM) {
            return null;
        }
        return slot;
    }

    private static TopRowSlot topRowSlotFromVisibleState(Object softKeyView) {
        VisibleTopRowKey visible = VISIBLE_TOP_ROW_KEYS.get(softKeyView);
        if (visible == null || visible.layoutKind == null
                || visible.layoutKind == TopRowLayoutKind.NONE) {
            return null;
        }
        return new TopRowSlot(visible.rowIndex, visible.layoutKind);
    }

    private static TopRowSlot recoverMarkerSlot(TopRowSlot rememberedSlot,
            int viewSlotIndex,
            boolean markerPress,
            TopRowLayoutKind inferredLayoutKind,
            SettingsSnapshot snapshot) {
        if (rememberedSlot != null) {
            return rememberedSlot;
        }
        if (!markerPress || snapshot == null
                || viewSlotIndex < 0
                || viewSlotIndex >= GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return null;
        }
        boolean zhuyinCustomPage = snapshot.zhuyinEnabled
                && currentPage(TopRowLayoutKind.ZHUYIN) == TopRowPage.CUSTOM;
        boolean englishCustomPage = snapshot.englishQwertyEnabled
                && currentPage(TopRowLayoutKind.ENGLISH_QWERTY) == TopRowPage.CUSTOM;
        if (inferredLayoutKind != null && inferredLayoutKind != TopRowLayoutKind.NONE) {
            boolean inferredLayoutActive = switch (inferredLayoutKind) {
                case ZHUYIN -> zhuyinCustomPage;
                case ENGLISH_QWERTY -> englishCustomPage;
                case NONE -> false;
            };
            return inferredLayoutActive
                    ? new TopRowSlot(viewSlotIndex, inferredLayoutKind) : null;
        }
        if (zhuyinCustomPage == englishCustomPage) {
            return null;
        }
        return new TopRowSlot(
                viewSlotIndex,
                zhuyinCustomPage
                        ? TopRowLayoutKind.ZHUYIN : TopRowLayoutKind.ENGLISH_QWERTY);
    }

    private static int viewSlotIndex(View view) {
        int namedIndex = GboardTopRowSwipeRuntimeSupport.topRowSlotIndexForView(view);
        return namedIndex >= 0 ? namedIndex : visualRowIndex(view);
    }

    private static TopRowLayoutKind inferLayoutKind(View candidateView,
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles) {
        if (candidateView == null || handles == null
                || !(candidateView.getRootView() instanceof ViewGroup rootGroup)) {
            return TopRowLayoutKind.NONE;
        }
        List<View> sameClassViews = new ArrayList<View>();
        collectUsableSameClassViews(rootGroup, candidateView.getClass(), sameClassViews);
        TopRowLayoutKind inferred = TopRowLayoutKind.NONE;
        for (View view : sameClassViews) {
            if (view == candidateView) {
                continue;
            }
            try {
                Object metadata = handles.softKeyMetadataField.get(view);
                Object original = rowGuardOriginalMetadata(handles, view, metadata);
                GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                        GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(handles, original);
                TopRowLayoutKind detected = switch (behavior.legacyKeyboardKind) {
                    case ZHUYIN -> TopRowLayoutKind.ZHUYIN;
                    case ENGLISH_QWERTY -> TopRowLayoutKind.ENGLISH_QWERTY;
                    case NONE -> TopRowLayoutKind.NONE;
                };
                if (detected == TopRowLayoutKind.NONE) {
                    continue;
                }
                if (inferred != TopRowLayoutKind.NONE && inferred != detected) {
                    return TopRowLayoutKind.NONE;
                }
                inferred = detected;
            } catch (Throwable ignored) {
                // Ignore unrelated or transient sibling Views.
            }
        }
        return inferred;
    }

    private static int hydrateConfirmedEnglishQwertyRow(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            View candidateView) throws Throwable {
        List<View> rowViews = confirmedEnglishQwertyRow(candidateView, handles);
        int candidateIndex = confirmedRowIndex(rowViews, candidateView);
        if (candidateIndex < 0) {
            return -1;
        }
        Map<View, VisibleTopRowKey> hydrated = new HashMap<View, VisibleTopRowKey>();
        for (int rowIndex = 0; rowIndex < rowViews.size(); rowIndex++) {
            View rowView = rowViews.get(rowIndex);
            Object original = rowGuardOriginalMetadata(handles, rowView);
            if (original == null) {
                return -1;
            }
            hydrated.put(rowView, new VisibleTopRowKey(
                    original, rowIndex, TopRowLayoutKind.ENGLISH_QWERTY));
        }
        VISIBLE_TOP_ROW_KEYS.putAll(hydrated);
        return candidateIndex;
    }

    private static int hydrateConfirmedZhuyinRow(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            View candidateView) throws Throwable {
        if (handles == null || !isTopRowCacheEligibleView(candidateView, null)
                || !(candidateView.getRootView() instanceof ViewGroup rootGroup)) {
            return -1;
        }
        List<View> rowViews = new ArrayList<View>();
        collectSameClassVisualRow(rootGroup, candidateView, candidateView.getClass(), rowViews);
        sortViewsLeftToRight(rowViews);
        if (rowViews.size() != GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return -1;
        }
        int candidateIndex = confirmedRowIndex(rowViews, candidateView);
        if (candidateIndex < 0) {
            return -1;
        }
        Map<View, VisibleTopRowKey> hydrated = new HashMap<View, VisibleTopRowKey>();
        for (int rowIndex = 0; rowIndex < rowViews.size(); rowIndex++) {
            View rowView = rowViews.get(rowIndex);
            Object original = rowGuardOriginalMetadata(handles, rowView);
            if (original == null) {
                return -1;
            }
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                    GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(handles, original);
            if (topRowLayoutKindForSlot(
                    rowIndex,
                    behavior.legacyKeyboardKind,
                    englishQwertyDetectionLabel(behavior)) != TopRowLayoutKind.ZHUYIN) {
                return -1;
            }
            hydrated.put(rowView, new VisibleTopRowKey(
                    original, rowIndex, TopRowLayoutKind.ZHUYIN));
        }
        VISIBLE_TOP_ROW_KEYS.putAll(hydrated);
        return candidateIndex;
    }

    private static List<View> confirmedEnglishQwertyRow(View candidateView,
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles) {
        if (candidateView == null || handles == null || isDisallowedTopRowSurface(candidateView)
                || !(candidateView.getRootView() instanceof ViewGroup rootGroup)) {
            return Collections.emptyList();
        }
        List<View> rowViews = namedEnglishRow(rootGroup, candidateView);
        if (!hasEnglishQwertyMetadataSequence(rowViews, handles)) {
            if (!isTopRowCacheEligibleView(candidateView, null)) {
                return Collections.emptyList();
            }
            rowViews = new ArrayList<View>();
            collectSameClassVisualRow(
                    rootGroup, candidateView, candidateView.getClass(), rowViews);
            sortViewsLeftToRight(rowViews);
            if (!hasEnglishQwertyMetadataSequence(rowViews, handles)) {
                return Collections.emptyList();
            }
        }
        return confirmedRowIndex(rowViews, candidateView) >= 0
                ? rowViews : Collections.emptyList();
    }

    private static List<View> namedEnglishRow(ViewGroup rootGroup, View candidateView) {
        View[] slots = new View[GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length];
        if (!collectNamedEnglishRow(rootGroup, candidateView.getClass(), slots)) {
            return Collections.emptyList();
        }
        List<View> rowViews = new ArrayList<View>(slots.length);
        for (View slot : slots) {
            if (slot == null) {
                return Collections.emptyList();
            }
            rowViews.add(slot);
        }
        return confirmedRowIndex(rowViews, candidateView) >= 0
                ? rowViews : Collections.emptyList();
    }

    private static boolean collectNamedEnglishRow(View current,
            Class<?> candidateClass,
            View[] slots) {
        if (current == null) {
            return true;
        }
        if (current.getClass() == candidateClass
                && current.getVisibility() == View.VISIBLE
                && !isDisallowedTopRowSurface(current)) {
            int slotIndex = namedTopRowIndex(current);
            if (slotIndex >= 0) {
                if (slots[slotIndex] != null && slots[slotIndex] != current) {
                    return false;
                }
                slots[slotIndex] = current;
            }
        }
        if (!(current instanceof ViewGroup group)) {
            return true;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            if (!collectNamedEnglishRow(group.getChildAt(index), candidateClass, slots)) {
                return false;
            }
        }
        return true;
    }

    private static int namedTopRowIndex(View view) {
        if (view == null || view.getId() == View.NO_ID) {
            return -1;
        }
        return GboardTopRowSwipeRuntimeSupport.topRowSlotIndexForView(view);
    }

    private static boolean hasEnglishQwertyMetadataSequence(
            List<View> rowViews,
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles) {
        if (rowViews == null
                || rowViews.size()
                        != GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return false;
        }
        try {
            List<String> labels = new ArrayList<String>(rowViews.size());
            for (View rowView : rowViews) {
                Object original = rowGuardOriginalMetadata(handles, rowView);
                GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                        GboardTopRowSwipeRuntimeSupport.inspectKeyBehavior(handles, original);
                labels.add(englishQwertyDetectionLabel(behavior));
            }
            return GboardTopRowSwipeRuntimeSupport.isEnglishQwertyTopRowLabels(labels);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object rowGuardOriginalMetadata(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            View rowView) throws Throwable {
        return rowGuardOriginalMetadata(
                handles, rowView, handles.softKeyMetadataField.get(rowView));
    }

    private static Object rowGuardOriginalMetadata(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            View rowView,
            Object currentMetadata) throws Throwable {
        Object original = resolveOriginalKeyMetadata(currentMetadata);
        if (currentMetadata == null || original != currentMetadata
                || !hasQuickJsMarkerPayload(handles, null, currentMetadata)) {
            return original;
        }
        VisibleTopRowKey remembered = VISIBLE_TOP_ROW_KEYS.get(rowView);
        return remembered != null && remembered.originalMetadata != null
                ? remembered.originalMetadata : original;
    }

    private static TopRowSlot prevalidatedEnglishSlot(View view,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior) {
        if (view == null || behavior == null
                || behavior.legacyKeyboardKind
                        != GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY) {
            return null;
        }
        Map<View, VisibleTopRowKey> visible = PREVALIDATED_ENGLISH_REFRESH.get();
        VisibleTopRowKey state = visible != null ? visible.get(view) : null;
        if (state == null || state.layoutKind != TopRowLayoutKind.ENGLISH_QWERTY
                || state.rowIndex < 0
                || state.rowIndex >= GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return null;
        }
        return new TopRowSlot(state.rowIndex, TopRowLayoutKind.ENGLISH_QWERTY);
    }

    private static boolean isPrevalidatedEnglishRefreshView(View view) {
        Map<View, VisibleTopRowKey> visible = PREVALIDATED_ENGLISH_REFRESH.get();
        VisibleTopRowKey state = visible != null ? visible.get(view) : null;
        return state != null && state.layoutKind == TopRowLayoutKind.ENGLISH_QWERTY;
    }

    private static int confirmedRowIndex(List<?> row, Object candidate) {
        if (row == null || candidate == null) {
            return -1;
        }
        for (int index = 0; index < row.size(); index++) {
            if (row.get(index) == candidate) {
                return index;
            }
        }
        return -1;
    }

    private static int visualRowIndex(View candidateView) {
        if (!isTopRowCacheEligibleView(candidateView, null)
                || !(candidateView.getRootView() instanceof ViewGroup rootGroup)) {
            return -1;
        }
        List<View> sameRowViews = new ArrayList<View>();
        collectSameClassVisualRow(
                rootGroup, candidateView, candidateView.getClass(), sameRowViews);
        if (sameRowViews.size()
                != GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return -1;
        }
        sortViewsLeftToRight(sameRowViews);
        return confirmedRowIndex(sameRowViews, candidateView);
    }

    private static void collectSameClassVisualRow(View current,
            View candidateView,
            Class<?> candidateClass,
            List<View> result) {
        if (current == null || candidateView == null || candidateClass == null) {
            return;
        }
        if (current.getClass() == candidateClass
                && isTopRowCacheEligibleView(current, candidateView.getRootView())
                && isSameVisualRow(current, candidateView)) {
            result.add(current);
        }
        if (!(current instanceof ViewGroup group)) {
            return;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            collectSameClassVisualRow(
                    group.getChildAt(index), candidateView, candidateClass, result);
        }
    }

    private static void sortViewsLeftToRight(List<View> views) {
        views.sort((left, right) -> {
            int[] leftLocation = new int[2];
            int[] rightLocation = new int[2];
            left.getLocationOnScreen(leftLocation);
            right.getLocationOnScreen(rightLocation);
            return Integer.compare(leftLocation[0], rightLocation[0]);
        });
    }

    static boolean shouldRecycleConsumedSession(long now, SwipeSession session) {
        return session != null
                && session.consumed
                && session.consumedAtElapsedMs > 0L
                && now - session.consumedAtElapsedMs >= CONSUMED_SESSION_RECYCLE_WINDOW_MS;
    }

    static boolean shouldRecycleSessionForIncomingView(SwipeSession session,
            Object incomingSoftKeyView, Object hitSoftKeyView) {
        if (session == null) {
            return false;
        }
        if (!(session.anchorSoftKeyView instanceof View anchorView)) {
            return true;
        }
        if (!anchorView.isAttachedToWindow()) {
            return true;
        }
        Object candidateOwner = incomingSoftKeyView instanceof View
                ? incomingSoftKeyView : hitSoftKeyView;
        if (!(candidateOwner instanceof View candidateView)) {
            return false;
        }
        if (!candidateView.isAttachedToWindow()) {
            return false;
        }
        return anchorView.getRootView() != candidateView.getRootView();
    }

    private static void maybeLogEnglishCandidateMiss(View candidateView,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior,
            SettingsSnapshot snapshot) {
        if (candidateView == null || behavior == null) {
            return;
        }
        String detectionLabel = englishQwertyDetectionLabel(behavior);
        int viewSlotIndex = GboardTopRowSwipeRuntimeSupport.topRowSlotIndexForView(candidateView);
        boolean visualFirstRow = isConfirmedVisualFirstRow(candidateView);
        boolean englishLikeLabel =
                GboardTopRowSwipeRuntimeSupport.englishQwertyTopRowIndexForLabel(
                        detectionLabel) >= 0;
        if (!englishLikeLabel && viewSlotIndex < 0 && !visualFirstRow) {
            return;
        }
        logLimited(ENGLISH_FALLBACK_LOG_COUNT,
                "miss"
                        + " viewId=" + GboardTopRowSwipeRuntimeSupport.safeViewName(candidateView)
                        + " slotIndex=" + viewSlotIndex
                        + " visualFirstRow=" + visualFirstRow
                        + " primaryLabel=" + behavior.primaryLabel
                        + " pressText=" + behavior.pressText
                        + " detectionLabel=" + detectionLabel
                        + " legacyKind=" + behavior.legacyKeyboardKind
                        + " englishEnabled=" + (snapshot != null && snapshot.englishQwertyEnabled));
    }

    private static void maybeLogEnglishSessionCreate(View candidateView,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior,
            TopRowSlot slot) {
        if (candidateView == null || behavior == null || slot == null
                || slot.layoutKind != TopRowLayoutKind.ENGLISH_QWERTY) {
            return;
        }
        logLimited(ENGLISH_SESSION_LOG_COUNT,
                "armed"
                        + " viewId=" + GboardTopRowSwipeRuntimeSupport.safeViewName(candidateView)
                        + " rowIndex=" + slot.rowIndex
                        + " primaryLabel=" + behavior.primaryLabel
                        + " pressText=" + behavior.pressText
                        + " detectionLabel=" + englishQwertyDetectionLabel(behavior)
                        + " legacyKind=" + behavior.legacyKeyboardKind);
    }

    private static void logLimited(AtomicInteger counter, String message) {
        int current = counter.incrementAndGet();
        if (current <= 40) {
            Log.i(TAG, message + " [count=" + current + "]");
        }
    }

    private static String trackerId(Object tracker) {
        return tracker == null ? "<null>" : Integer.toHexString(System.identityHashCode(tracker));
    }

    static boolean isConfirmedVisualFirstRow(View candidateView) {
        if (!isUsableVisibleTopRowView(candidateView, null)
                || isDisallowedTopRowSurface(candidateView)) {
            return false;
        }
        View rootView = candidateView.getRootView();
        if (!(rootView instanceof ViewGroup rootGroup)) {
            return false;
        }

        List<View> sameClassViews = new ArrayList<View>();
        collectUsableSameClassViews(rootGroup, candidateView.getClass(), sameClassViews);
        if (sameClassViews.size() < GboardTopRowSwipeRuntimeSupport.TOP_ROW_SLOT_VIEW_NAMES.length) {
            return false;
        }

        int[] candidateLocation = new int[2];
        candidateView.getLocationOnScreen(candidateLocation);
        int candidateTop = candidateLocation[1];
        int minTop = Integer.MAX_VALUE;
        for (View sameClassView : sameClassViews) {
            int[] location = new int[2];
            sameClassView.getLocationOnScreen(location);
            minTop = Math.min(minTop, location[1]);
        }
        if (minTop == Integer.MAX_VALUE) {
            return false;
        }
        int sameRowTolerancePx = Math.max(2, candidateView.getHeight() / 4);
        return Math.abs(candidateTop - minTop) <= sameRowTolerancePx;
    }

    static boolean shouldAllowTopRowCustomPatchedDispatch(SettingsSnapshot snapshot,
            TopRowLayoutKind layoutKind) {
        return isLayoutEnabled(snapshot, layoutKind)
                && currentPage(layoutKind) == TopRowPage.CUSTOM;
    }

    static void markTopRowCustomPatchedMetadata(Object keyMetadata, TopRowLayoutKind layoutKind) {
        if (keyMetadata == null || layoutKind == null || layoutKind == TopRowLayoutKind.NONE) {
            return;
        }
        TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(
                keyMetadata,
                new PatchedTopRowMetadataState(layoutKind, -1, null));
    }

    private static String readScreenContext(Context context) {
        if (context == null) {
            return "{}";
        }
        // Try HTTP Server first (reaches Extension process)
        try {
            String json = GboardHttpFetcher.fetch("/context");
            if (json != null && !json.isEmpty() && !json.equals("{}")) {
                return json;
            }
        } catch (Throwable ignored) {
        }
        
        // Fallback to ContentProvider (might be local to Gboard)
        try {
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://dev.jason.gboardpatches.extension.gboard_patches"),
                    GboardTopRowSwipeSettings.METHOD_GET_SCREEN_CONTEXT,
                    null,
                    null);
            if (result != null && result.containsKey(GboardTopRowSwipeSettings.BUNDLE_KEY_SCREEN_CONTEXT_JSON)) {
                String json = result.getString(GboardTopRowSwipeSettings.BUNDLE_KEY_SCREEN_CONTEXT_JSON);
                if (json != null && !json.isEmpty()) {
                    return json;
                }
            }
        } catch (Throwable ignored) {
        }
        return "{}";
    }

    static String globalJavaScript(Context context) {
        SettingsSnapshot snapshot = settingsSnapshot(context);
        String baseGlobal = snapshot != null ? snapshot.globalJavaScript : "";
        String screenContext = readScreenContext(context);
        return "const context = " + screenContext + ";\n" + baseGlobal;
    }

    static GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits(
            Context context) {
        SettingsSnapshot snapshot = settingsSnapshot(context);
        return snapshot != null
                ? snapshot.javaScriptRuntimeLimits
                : GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits();
    }

    static boolean shouldConsumeDisabledTopRowCustomDispatch(Context context,
            Object keyMetadata) {
        if (!isTopRowCustomPatchedMetadata(keyMetadata)) {
            return false;
        }
        if (context == null) {
            clearSettingsSnapshotCache();
            resetRuntimeStateForUnavailable();
            return true;
        }
        SettingsSnapshot snapshot = loadSettingsSnapshot(context, SystemClock.elapsedRealtime());
        synchronized (SETTINGS_LOCK) {
            cachedSettingsSnapshot = snapshot;
        }
        applySettingsSnapshotRuntimeState(snapshot);
        return shouldConsumeDisabledTopRowCustomDispatch(snapshot, keyMetadata);
    }

    static boolean shouldConsumeDisabledTopRowCustomDispatch(SettingsSnapshot snapshot,
            Object keyMetadata) {
        if (!isTopRowCustomPatchedMetadata(keyMetadata)) {
            return false;
        }
        PatchedTopRowMetadataState metadataState =
                TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.get(keyMetadata);
        TopRowLayoutKind layoutKind = metadataState != null
                ? metadataState.layoutKind : TopRowLayoutKind.NONE;
        if (shouldAllowTopRowCustomPatchedDispatch(snapshot, layoutKind)) {
            return false;
        }
        resetRuntimeStateForLayout(layoutKind);
        return true;
    }

    public static Object resolveOriginalKeyMetadataForPatchInterop(Object keyMetadata) {
        Object originalMetadata = ORIGINAL_KEY_METADATA_BY_PATCHED.get(keyMetadata);
        return originalMetadata != null ? originalMetadata : keyMetadata;
    }

    public static void inheritPatchedMetadataForPatchInterop(Object source, Object target) {
        if (source == null || target == null || source == target) {
            return;
        }
        Object originalMetadata = ORIGINAL_KEY_METADATA_BY_PATCHED.get(source);
        if (originalMetadata != null) {
            ORIGINAL_KEY_METADATA_BY_PATCHED.put(target, originalMetadata);
        }
        PatchedTopRowMetadataState metadataState =
                TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.get(source);
        if (metadataState != null) {
            TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(target, metadataState);
        }
    }

    private static Object resolveOriginalKeyMetadata(Object keyMetadata) {
        return resolveOriginalKeyMetadataForPatchInterop(keyMetadata);
    }

    private static boolean isTopRowCustomPatchedMetadata(Object keyMetadata) {
        return TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.containsKey(keyMetadata);
    }

    static QuickJsSlotConfig quickJsSlotConfig(Object keyMetadata) {
        PatchedTopRowMetadataState metadataState =
                TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.get(keyMetadata);
        if (metadataState == null || !metadataState.isJavaScript) {
            return null;
        }
        return new QuickJsSlotConfig(
                metadataState.layoutKind,
                metadataState.rowIndex,
                metadataState.quickJsScriptSource,
                metadataState.quickJsTimeoutMs);
    }

    static QuickJsSlotConfig quickJsSlotConfigFromSession(
            SettingsSnapshot snapshot, Object tracker) {
        SwipeSession session = SESSIONS.get(tracker);
        return quickJsSlotConfigFromSession(snapshot, session);
    }

    private static QuickJsSlotConfig quickJsSlotConfigFromSession(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            Object tracker,
            Object gestureDispatcher) throws Throwable {
        SwipeSession session = SESSIONS.get(tracker);
        if (session == null) {
            return null;
        }
        if (session.anchorSoftKeyView != null && handles != null) {
            try {
                Object currentKeyMetadata =
                        handles.softKeyMetadataField.get(session.anchorSoftKeyView);
                QuickJsSlotConfig config = quickJsSlotConfig(currentKeyMetadata);
                if (config != null) {
                    return config;
                }
            } catch (Throwable ignored) {
                // Fall back to the session row and current settings below.
            }
        }
        Context context = session.anchorSoftKeyView instanceof View view
                ? view.getContext() : null;
        if (context == null && handles != null) {
            context = handles.extractGestureDispatcherContext(gestureDispatcher);
        }
        return quickJsSlotConfigFromSession(settingsSnapshot(context), session);
    }

    private static QuickJsSlotConfig quickJsSlotConfigFromSession(
            SettingsSnapshot snapshot, SwipeSession session) {
        if (snapshot == null || session == null
                || session.rowIndex < 0
                || session.layoutKind == null
                || session.layoutKind == TopRowLayoutKind.NONE
                || currentPage(session.layoutKind) != TopRowPage.CUSTOM
                || !isLayoutEnabled(snapshot, session.layoutKind)) {
            return null;
        }
        TopRowItem item = customTopRowItem(snapshot, session.rowIndex);
        if (item == null || !item.isJavaScript) {
            return null;
        }
        return new QuickJsSlotConfig(
                session.layoutKind,
                session.rowIndex,
                item.scriptText,
                item.timeoutMs);
    }

    private static boolean hasQuickJsMarkerPayload(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            Object actionData,
            Object keyMetadata) throws Throwable {
        if (QUICK_JS_PAYLOAD_PREFIX.equals(handles.extractActionDataPayload(actionData))) {
            return true;
        }
        Object pressAction = handles.findExactAction(keyMetadata, handles.pressActionType);
        return QUICK_JS_PAYLOAD_PREFIX.equals(handles.extractSinglePayloadToken(pressAction));
    }

    private static boolean isLayoutEnabled(Context context, TopRowLayoutKind layoutKind) {
        return isLayoutEnabled(settingsSnapshot(context), layoutKind);
    }

    private static boolean isLayoutEnabled(SettingsSnapshot snapshot,
            TopRowLayoutKind layoutKind) {
        if (snapshot == null || !snapshot.enabled || layoutKind == null) {
            return false;
        }
        return switch (layoutKind) {
            case ZHUYIN -> snapshot.zhuyinEnabled;
            case ENGLISH_QWERTY -> snapshot.englishQwertyEnabled;
            case NONE -> false;
        };
    }

    private static TopRowSlot topRowSlotForCustomPage(SettingsSnapshot snapshot, View view,
            GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior) {
        if (snapshot == null || !snapshot.enabled) {
            return null;
        }
        TopRowSlot slot = topRowSlotForViewAndBehavior(view, behavior);
        if (slot == null
                || !isLayoutEnabled(snapshot, slot.layoutKind)
                || currentPage(slot.layoutKind) != TopRowPage.CUSTOM) {
            return null;
        }
        return slot;
    }

    private static TopRowItem customTopRowItem(SettingsSnapshot snapshot, int rowIndex) {
        if (snapshot == null || rowIndex < 0 || rowIndex >= snapshot.items.length) {
            return defaultTopRowItem(rowIndex);
        }
        TopRowItem item = snapshot.items[rowIndex];
        return item != null ? item : defaultTopRowItem(rowIndex);
    }

    private static SettingsSnapshot loadSettingsSnapshot(Context context, long now) {
        if (context == null) {
            return unavailableSettingsSnapshot(now);
        }
        try {
            Bundle result = context.getContentResolver().call(
                    Uri.parse("content://" + context.getPackageName()
                            + GboardPatchesSettingsProvider.AUTHORITY_SUFFIX),
                    GboardTopRowSwipeSettings.METHOD_GET_TOP_ROW_SWIPE_SETTINGS,
                    null,
                    null);
            if (result == null) {
                return unavailableSettingsSnapshot(now);
            }
            Boolean enabled = result.containsKey(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ENABLED)
                            ? Boolean.valueOf(result.getBoolean(
                                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ENABLED,
                                    false))
                            : null;
            Boolean zhuyinEnabled = result.containsKey(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED)
                            ? Boolean.valueOf(result.getBoolean(
                                    GboardTopRowSwipeSettings
                                            .BUNDLE_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                                    false))
                            : null;
            Boolean englishQwertyEnabled = result.containsKey(
                    GboardTopRowSwipeSettings
                            .BUNDLE_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED)
                                    ? Boolean.valueOf(result.getBoolean(
                                            GboardTopRowSwipeSettings
                                                    .BUNDLE_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                                            false))
                                    : null;
            ArrayList<String> displays = result.getStringArrayList(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_DISPLAY_TEXTS);
            ArrayList<String> commits = result.getStringArrayList(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_COMMIT_TEXTS);
            boolean[] isJavaScripts = result.getBooleanArray(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_IS_JAVA_SCRIPT);
            ArrayList<String> scripts = result.getStringArrayList(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_SCRIPT_TEXTS);
            int[] timeouts = result.getIntArray(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_TIMEOUTS_MS);
            String globalJavaScript = result.getString(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT);
            int responseBodyLimitBytes = result.getInt(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                    GboardTopRowSwipeSettings.DEFAULT_RESPONSE_BODY_LIMIT_BYTES);
            int timeoutMaxMs = result.getInt(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                    GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MAX_MS);
            int memoryLimitBytes = result.getInt(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                    GboardTopRowSwipeSettings.DEFAULT_MEMORY_LIMIT_BYTES);
            int maxStackBytes = result.getInt(
                    GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                    GboardTopRowSwipeSettings.DEFAULT_MAX_STACK_BYTES);
            return settingsSnapshotFromProviderValues(enabled, zhuyinEnabled,
                    englishQwertyEnabled, displays, commits, isJavaScripts, scripts, timeouts,
                    globalJavaScript, responseBodyLimitBytes, timeoutMaxMs, memoryLimitBytes,
                    maxStackBytes, now);
        } catch (Throwable ignored) {
            return unavailableSettingsSnapshot(now);
        }
    }

    private static SettingsSnapshot unavailableSettingsSnapshot(long loadedAtElapsedMs) {
        return new SettingsSnapshot(loadedAtElapsedMs, false, false, false, false, "",
                GboardTopRowSwipeSettings.defaultJavaScriptRuntimeLimits(),
                defaultTopRowItems());
    }

    private static TopRowItem[] defaultTopRowItems() {
        TopRowItem[] items = new TopRowItem[GboardTopRowSwipeSettings.SLOT_COUNT];
        List<GboardTopRowSwipeSettings.SlotText> defaults =
                GboardTopRowSwipeSettings.defaultSlots();
        for (int index = 0; index < items.length; index++) {
            GboardTopRowSwipeSettings.SlotText slot = defaults.get(index);
            items[index] = new TopRowItem(slot.displayText, slot.commitText);
        }
        return items;
    }

    private static TopRowItem[] itemsFromSlots(
            List<GboardTopRowSwipeSettings.SlotText> slots) {
        TopRowItem[] items = defaultTopRowItems();
        if (slots == null) {
            return items;
        }
        int count = Math.min(items.length, slots.size());
        for (int index = 0; index < count; index++) {
            GboardTopRowSwipeSettings.SlotText slot = slots.get(index);
            if (slot != null) {
                items[index] = new TopRowItem(
                        slot.displayText,
                        slot.commitText,
                        slot.isJavaScript,
                        slot.scriptText,
                        slot.timeoutMs);
            }
        }
        return items;
    }

    private static TopRowItem defaultTopRowItem(int rowIndex) {
        TopRowItem[] defaults = defaultTopRowItems();
        if (rowIndex >= 0 && rowIndex < defaults.length) {
            return defaults[rowIndex];
        }
        return defaults[0];
    }

    private static boolean hasExactlySlotCount(List<String> values) {
        return values != null && values.size() == GboardTopRowSwipeSettings.SLOT_COUNT;
    }

    private static boolean hasExactlySlotCount(boolean[] values) {
        return values != null && values.length == GboardTopRowSwipeSettings.SLOT_COUNT;
    }

    private static boolean hasExactlySlotCount(int[] values) {
        return values != null && values.length == GboardTopRowSwipeSettings.SLOT_COUNT;
    }

    private static boolean hasOnlyNonBlankText(List<String> values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlyText(List<String> values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value == null) {
                return false;
            }
        }
        return true;
    }

    private static List<Boolean> boxedBooleans(boolean[] values) {
        ArrayList<Boolean> boxed = new ArrayList<Boolean>(values.length);
        for (boolean value : values) {
            boxed.add(Boolean.valueOf(value));
        }
        return boxed;
    }

    private static List<String> defaultScriptTexts() {
        ArrayList<String> scripts = new ArrayList<String>(GboardTopRowSwipeSettings.SLOT_COUNT);
        for (int index = 0; index < GboardTopRowSwipeSettings.SLOT_COUNT; index++) {
            scripts.add("");
        }
        return scripts;
    }

    private static int[] defaultTimeouts() {
        int[] timeouts = new int[GboardTopRowSwipeSettings.SLOT_COUNT];
        for (int index = 0; index < timeouts.length; index++) {
            timeouts[index] = GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MS;
        }
        return timeouts;
    }

    private static List<Integer> boxedIntegers(int[] values) {
        ArrayList<Integer> boxed = new ArrayList<Integer>(values.length);
        for (int value : values) {
            boxed.add(Integer.valueOf(value));
        }
        return boxed;
    }

    private static void applySettingsSnapshotRuntimeState(SettingsSnapshot loaded) {
        if (loaded == null) {
            return;
        }
        if (!loaded.available) {
            resetRuntimeStateForUnavailable();
        } else if (!loaded.enabled) {
            resetRuntimeStateForUnavailable();
        } else {
            resetRuntimeStateForDisabledLayouts(loaded);
        }
    }

    private static void resetRuntimeStateForUnavailable() {
        synchronized (CURRENT_PAGE_BY_LAYOUT_KIND) {
            CURRENT_PAGE_BY_LAYOUT_KIND.clear();
        }
        VISIBLE_TOP_ROW_KEYS.clear();
    }

    private static void resetRuntimeStateForDisabledLayouts(SettingsSnapshot snapshot) {
        if (snapshot == null) {
            resetRuntimeStateForUnavailable();
            return;
        }
        if (!snapshot.zhuyinEnabled) {
            resetRuntimeStateForLayout(TopRowLayoutKind.ZHUYIN);
        }
        if (!snapshot.englishQwertyEnabled) {
            resetRuntimeStateForLayout(TopRowLayoutKind.ENGLISH_QWERTY);
        }
    }

    private static boolean resetRuntimeStateForLayout(TopRowLayoutKind layoutKind) {
        if (layoutKind == null || layoutKind == TopRowLayoutKind.NONE) {
            return false;
        }
        boolean stateChanged = false;
        synchronized (CURRENT_PAGE_BY_LAYOUT_KIND) {
            stateChanged = CURRENT_PAGE_BY_LAYOUT_KIND.remove(layoutKind) != null;
        }
        synchronized (VISIBLE_TOP_ROW_KEYS) {
            Iterator<Map.Entry<Object, VisibleTopRowKey>> iterator =
                    VISIBLE_TOP_ROW_KEYS.entrySet().iterator();
            while (iterator.hasNext()) {
                VisibleTopRowKey visible = iterator.next().getValue();
                if (visible == null || visible.layoutKind == layoutKind) {
                    iterator.remove();
                    stateChanged = true;
                }
            }
        }
        return stateChanged;
    }

    private static void applyPendingPageIfNeeded(SwipeSession session) {
        if (session == null || session.pendingPage == null) {
            return;
        }
        try {
            View anchorView = session.anchorSoftKeyView instanceof View
                    ? (View) session.anchorSoftKeyView : null;
            Context context = anchorView != null ? anchorView.getContext() : null;
            if (!isLayoutEnabled(context, session.layoutKind)) {
                resetRuntimeStateForLayout(session.layoutKind);
                return;
            }
            ClassLoader classLoader = session.anchorSoftKeyView != null
                    ? session.anchorSoftKeyView.getClass().getClassLoader() : null;
            if (classLoader == null) {
                return;
            }
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles =
                    GboardTopRowSwipeRuntimeSupport.reflectionHandles(classLoader);
            TopRowPage previousPage = currentPage(session.layoutKind);
            AtomicInteger visibleCount = new AtomicInteger(0);
            boolean applied = applyTopRowPageTransition(
                    anchorView,
                    session.layoutKind,
                    previousPage,
                    session.pendingPage,
                    session.swipeDirectionSign,
                    GboardTopRowSwipeRuntime::captureTopRowSnapshot,
                    page -> setCurrentPage(session.layoutKind, page),
                    visibleTopRowKeys -> {
                        visibleCount.set(visibleTopRowKeys.size());
                        try {
                            refreshVisibleTopRowKeyMetadata(handles, visibleTopRowKeys);
                        } catch (Throwable throwable) {
                            throw new IllegalStateException(
                                    "Top Row metadata rebind failed", throwable);
                        }
                    });
            if (applied) {
                logLimited(SESSION_APPLY_LOG_COUNT,
                        "apply"
                                + " layout=" + session.layoutKind
                                + " page=" + session.pendingPage
                                + " visibleKeys=" + visibleCount.get());
            }
        } catch (Throwable ignored) {
            // Keep host alive; stale view state is preferable to crash.
        }
    }

    static boolean applyTopRowPageTransition(
            View anchorView,
            TopRowLayoutKind layoutKind,
            TopRowPage previousPage,
            TopRowPage pendingPage,
            int swipeDirectionSign,
            Function<Map<View, VisibleTopRowKey>, RowSnapshot> snapshotter,
            Consumer<TopRowPage> pageSetter,
            Consumer<Map<View, VisibleTopRowKey>> rebinder) {
        Map<View, VisibleTopRowKey> visibleTopRowKeys = Collections.emptyMap();
        RowSnapshot beforeSnapshot = null;
        RowSnapshot afterSnapshot = null;
        boolean pageChangeStarted = false;
        try {
            cleanupOwnedTopRowTransition(anchorView);
            visibleTopRowKeys = copyVisibleTopRowKeys(layoutKind, anchorView);
            beforeSnapshot = snapshotter.apply(visibleTopRowKeys);
            pageChangeStarted = true;
            pageSetter.accept(pendingPage);
            rebinder.accept(visibleTopRowKeys);
            afterSnapshot = snapshotter.apply(visibleTopRowKeys);
            maybeAnimateTopRowTransition(
                    visibleTopRowKeys,
                    beforeSnapshot,
                    afterSnapshot,
                    swipeDirectionSign);
            beforeSnapshot = null;
            afterSnapshot = null;
            return true;
        } catch (Throwable ignored) {
            cleanupOwnedTopRowTransition(anchorView);
            recycleSnapshot(beforeSnapshot);
            recycleSnapshot(afterSnapshot);
            setTopRowAlpha(visibleTopRowKeys, 1f);
            if (pageChangeStarted) {
                try {
                    pageSetter.accept(previousPage);
                } catch (Throwable rollbackPageFailure) {
                    // Continue with best-effort metadata restoration.
                }
                try {
                    rebinder.accept(visibleTopRowKeys);
                } catch (Throwable rollbackRebindFailure) {
                    // The previous page remains authoritative even if rebind cannot complete.
                }
            }
            cleanupOwnedTopRowTransition(anchorView);
            setTopRowAlpha(visibleTopRowKeys, 1f);
            return false;
        }
    }

    private static void cleanupOwnedTopRowTransition(View anchorView) {
        if (anchorView == null) {
            return;
        }
        View rootView = anchorView.getRootView();
        if (rootView instanceof ViewGroup host) {
            removeExistingOverlay(host);
        }
    }

    private static void refreshVisibleTopRowKeyMetadata(
            GboardTopRowSwipeRuntimeSupport.ReflectionHandles handles,
            Map<View, VisibleTopRowKey> visibleTopRowKeys) throws Throwable {
        clearSettingsSnapshotCache();
        boolean englishRefresh = containsLayout(
                visibleTopRowKeys, TopRowLayoutKind.ENGLISH_QWERTY);
        if (englishRefresh) {
            PREVALIDATED_ENGLISH_REFRESH.set(visibleTopRowKeys);
        }
        try {
            for (Map.Entry<View, VisibleTopRowKey> entry : visibleTopRowKeys.entrySet()) {
                View softKeyView = entry.getKey();
                VisibleTopRowKey visible = entry.getValue();
                if (softKeyView == null || visible == null || visible.originalMetadata == null) {
                    continue;
                }
                Object rebuilt = patchIncomingSoftKeyMetadata(
                        softKeyView, visible.originalMetadata);
                if (rebuilt != null) {
                    handles.rebindSoftKeyView(softKeyView, rebuilt);
                    softKeyView.invalidate();
                    softKeyView.requestLayout();
                }
            }
        } finally {
            if (englishRefresh) {
                PREVALIDATED_ENGLISH_REFRESH.remove();
            }
        }
    }

    private static boolean containsLayout(Map<View, VisibleTopRowKey> visibleTopRowKeys,
            TopRowLayoutKind layoutKind) {
        if (visibleTopRowKeys == null || visibleTopRowKeys.isEmpty()) {
            return false;
        }
        for (VisibleTopRowKey state : visibleTopRowKeys.values()) {
            if (state != null && state.layoutKind == layoutKind) {
                return true;
            }
        }
        return false;
    }

    static Map<View, VisibleTopRowKey> copyVisibleTopRowKeys(TopRowLayoutKind layoutKind) {
        return copyVisibleTopRowKeys(layoutKind, null);
    }

    private static Map<View, VisibleTopRowKey> copyVisibleTopRowKeys(
            TopRowLayoutKind layoutKind, View anchorView) {
        Map<View, VisibleTopRowKey> visibleTopRowKeys =
                new HashMap<View, VisibleTopRowKey>();
        View anchorRoot = anchorView != null && anchorView.isAttachedToWindow()
                ? anchorView.getRootView() : null;
        synchronized (VISIBLE_TOP_ROW_KEYS) {
            Iterator<Map.Entry<Object, VisibleTopRowKey>> iterator =
                    VISIBLE_TOP_ROW_KEYS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, VisibleTopRowKey> entry = iterator.next();
                VisibleTopRowKey visible = entry.getValue();
                if (visible == null || !(entry.getKey() instanceof View view)) {
                    iterator.remove();
                    continue;
                }
                if (!isTopRowCacheEligibleView(view, anchorRoot)
                        || !isSameVisualRow(view, anchorView)) {
                    iterator.remove();
                    continue;
                }
                if (visible.layoutKind == layoutKind) {
                    visibleTopRowKeys.put(view, visible);
                }
            }
        }
        return visibleTopRowKeys;
    }

    private static boolean isTopRowCacheEligibleView(View view, View anchorRoot) {
        if (!isUsableVisibleTopRowView(view, anchorRoot)) {
            return false;
        }
        if (!(view.getVisibility() == View.VISIBLE) || view.getAlpha() <= 0f) {
            return false;
        }
        return !isDisallowedTopRowSurface(view);
    }

    private static boolean isUsableVisibleTopRowView(View view, View anchorRoot) {
        if (view == null || !view.isAttachedToWindow()
                || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        return anchorRoot == null || view.getRootView() == anchorRoot;
    }

    private static boolean isSameVisualRow(View view, View anchorView) {
        if (view == null || anchorView == null) {
            return true;
        }
        int[] viewLocation = new int[2];
        int[] anchorLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        anchorView.getLocationOnScreen(anchorLocation);
        int sameRowTolerancePx = Math.max(
                2,
                Math.max(view.getHeight(), anchorView.getHeight()) / 4);
        return Math.abs(viewLocation[1] - anchorLocation[1]) <= sameRowTolerancePx;
    }

    static boolean isDisallowedTopRowSurface(View view) {
        if (view == null) {
            return true;
        }
        View current = view;
        while (current != null) {
            String className = current.getClass().getName();
            if (ACCESS_POINTS_BAR_ITEM_SOFT_KEY_VIEW.equals(className)
                    || ACCESS_POINTS_BAR_VIEW.equals(className)
                    || PROACTIVE_SUGGESTIONS_HOLDER_VIEW.equals(className)
                    || className.endsWith(CANDIDATES_HOLDER_SUFFIX)) {
                return true;
            }
            String viewName = GboardTopRowSwipeRuntimeSupport.safeViewName(current);
            if (viewName != null
                    && (FIXED_CANDIDATES_ID.equals(viewName)
                    || PROACTIVE_SUGGESTIONS_ID.equals(viewName)
                    || viewName.startsWith(HEADER_ID_PREFIX))) {
                return true;
            }
            Object tag = current.getTag();
            if (tag instanceof String tagString
                    && tagString.contains(KEYBOARD_HEADER_TAG)) {
                return true;
            }
            current = current.getParent() instanceof View parentView ? parentView : null;
        }
        return false;
    }

    private static void collectUsableSameClassViews(View view, Class<?> viewClass,
            List<View> result) {
        if (view == null || viewClass == null || result == null) {
            return;
        }
        if (view.getClass() == viewClass
                && view.getVisibility() == View.VISIBLE
                && isUsableVisibleTopRowView(view, null)) {
            result.add(view);
        }
        if (!(view instanceof ViewGroup group)) {
            return;
        }
        int childCount = group.getChildCount();
        for (int index = 0; index < childCount; index++) {
            collectUsableSameClassViews(group.getChildAt(index), viewClass, result);
        }
    }

    private static float dpToPx(View view, float dp) {
        if (view == null) {
            return dp;
        }
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                view.getResources().getDisplayMetrics());
    }

    private static RowSnapshot captureTopRowSnapshot(
            Map<View, VisibleTopRowKey> visibleTopRowKeys) {
        List<View> topRowViews = new ArrayList<View>(visibleTopRowKeys.keySet());
        if (topRowViews.isEmpty()) {
            return null;
        }
        topRowViews.sort((left, right) -> {
            int[] leftLocation = new int[2];
            int[] rightLocation = new int[2];
            left.getLocationOnScreen(leftLocation);
            right.getLocationOnScreen(rightLocation);
            return Integer.compare(leftLocation[0], rightLocation[0]);
        });

        ViewGroup host = resolveAnimationHost(topRowViews.get(0));
        if (host == null) {
            return null;
        }
        Rect bounds = resolveRowBounds(host, topRowViews);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[] hostLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        for (View topRowView : topRowViews) {
            int[] viewLocation = new int[2];
            topRowView.getLocationOnScreen(viewLocation);
            canvas.save();
            canvas.translate(viewLocation[0] - hostLocation[0] - bounds.left,
                    viewLocation[1] - hostLocation[1] - bounds.top);
            topRowView.draw(canvas);
            canvas.restore();
        }
        return new RowSnapshot(host, bounds, bitmap);
    }

    private static ViewGroup resolveAnimationHost(View view) {
        if (view == null) {
            return null;
        }
        View rootView = view.getRootView();
        return rootView instanceof ViewGroup rootGroup ? rootGroup : null;
    }

    private static Rect resolveRowBounds(ViewGroup host, List<View> topRowViews) {
        int[] hostLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        Rect unionBounds = null;
        for (View topRowView : topRowViews) {
            int[] viewLocation = new int[2];
            topRowView.getLocationOnScreen(viewLocation);
            Rect viewBounds = new Rect(
                    viewLocation[0] - hostLocation[0],
                    viewLocation[1] - hostLocation[1],
                    viewLocation[0] - hostLocation[0] + topRowView.getWidth(),
                    viewLocation[1] - hostLocation[1] + topRowView.getHeight());
            if (unionBounds == null) {
                unionBounds = viewBounds;
            } else {
                unionBounds.union(viewBounds);
            }
        }
        return unionBounds;
    }

    private static void maybeAnimateTopRowTransition(
            Map<View, VisibleTopRowKey> visibleTopRowKeys,
            RowSnapshot beforeSnapshot,
            RowSnapshot afterSnapshot,
            int swipeDirectionSign) {
        if (beforeSnapshot == null || afterSnapshot == null
                || beforeSnapshot.host == null
                || beforeSnapshot.bounds == null || afterSnapshot.bounds == null
                || beforeSnapshot.bitmap == null || afterSnapshot.bitmap == null) {
            recycleSnapshot(beforeSnapshot);
            recycleSnapshot(afterSnapshot);
            return;
        }
        ViewGroup host = beforeSnapshot.host;
        removeExistingOverlay(host);

        FrameLayout overlay = new FrameLayout(host.getContext());
        overlay.setTag(ROW_TRANSITION_OVERLAY_TAG);
        overlay.setLayoutParams(new ViewGroup.LayoutParams(
                beforeSnapshot.bounds.width(),
                beforeSnapshot.bounds.height()));
        overlay.setX(beforeSnapshot.bounds.left);
        overlay.setY(beforeSnapshot.bounds.top);
        overlay.setClipChildren(true);
        overlay.setClipToPadding(true);

        ImageView oldImage = transitionImage(host, beforeSnapshot.bitmap);
        ImageView newImage = transitionImage(host, afterSnapshot.bitmap);
        float offset = beforeSnapshot.bounds.width();
        float animationDirection = swipeDirectionSign >= 0 ? 1f : -1f;
        newImage.setTranslationX(-offset * animationDirection);
        overlay.addView(oldImage);
        overlay.addView(newImage);

        AtomicBoolean cleaned = new AtomicBoolean(false);
        Runnable cleanup = () -> cleanupTopRowTransition(
                visibleTopRowKeys,
                host,
                overlay,
                beforeSnapshot,
                afterSnapshot,
                cleaned);
        ROW_TRANSITION_OVERLAY_CLEANUPS.put(overlay, cleanup);
        runTopRowTransitionStartup(() -> {
            host.addView(overlay);
            setTopRowAlpha(visibleTopRowKeys, 0f);
            oldImage.animate()
                    .translationX(offset * animationDirection)
                    .setDuration(ROW_TRANSITION_DURATION_MS)
                    .start();
            newImage.animate()
                    .translationX(0f)
                    .setDuration(ROW_TRANSITION_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            cleanup.run();
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            cleanup.run();
                        }
                    })
                    .start();
        }, cleanup);
    }

    private static ImageView transitionImage(ViewGroup host, Bitmap bitmap) {
        ImageView image = new ImageView(host.getContext());
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return image;
    }

    private static void cleanupTopRowTransition(
            Map<View, VisibleTopRowKey> visibleTopRowKeys,
            ViewGroup host,
            View overlay,
            RowSnapshot beforeSnapshot,
            RowSnapshot afterSnapshot,
            AtomicBoolean cleaned) {
        if (!cleaned.compareAndSet(false, true)) {
            return;
        }
        setTopRowAlpha(visibleTopRowKeys, 1f);
        removeOverlay(host, overlay);
        recycleSnapshot(beforeSnapshot);
        recycleSnapshot(afterSnapshot);
    }

    private static void setTopRowAlpha(Map<View, VisibleTopRowKey> visibleTopRowKeys,
            float alpha) {
        for (View view : visibleTopRowKeys.keySet()) {
            if (view != null) {
                view.setAlpha(alpha);
            }
        }
    }

    private static void runTopRowTransitionStartup(Runnable startup, Runnable cleanup) {
        try {
            startup.run();
        } catch (Throwable ignored) {
            cleanup.run();
        }
    }

    private static void removeExistingOverlay(ViewGroup host) {
        if (host == null) {
            return;
        }
        View existingOverlay = host.findViewWithTag(ROW_TRANSITION_OVERLAY_TAG);
        if (existingOverlay == null) {
            return;
        }
        Runnable cleanup = ROW_TRANSITION_OVERLAY_CLEANUPS.remove(existingOverlay);
        if (cleanup != null) {
            cleanup.run();
        } else {
            removeOverlay(host, existingOverlay);
        }
    }

    private static void removeOverlay(ViewGroup host, View overlay) {
        ROW_TRANSITION_OVERLAY_CLEANUPS.remove(overlay);
        ViewGroup parent = overlay != null && overlay.getParent() instanceof ViewGroup group
                ? group : host;
        if (parent != null && overlay != null) {
            parent.removeView(overlay);
        }
    }

    private static void recycleSnapshot(RowSnapshot snapshot) {
        if (snapshot == null || snapshot.bitmap == null) {
            return;
        }
        try {
            if (!snapshot.bitmap.isRecycled()) {
                snapshot.bitmap.recycle();
            }
        } catch (Throwable ignored) {
            // Cleanup is best-effort and must not crash the keyboard.
        }
    }

    private static final class WeakIdentityMap<V> extends AbstractMap<Object, V> {
        private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<Object>();
        private final Map<IdentityWeakReference, V> values =
                new HashMap<IdentityWeakReference, V>();

        @Override
        public synchronized V get(Object key) {
            if (key == null) {
                return null;
            }
            removeCollectedKeys();
            return values.get(new IdentityWeakReference(key));
        }

        @Override
        public synchronized boolean containsKey(Object key) {
            if (key == null) {
                return false;
            }
            removeCollectedKeys();
            return values.containsKey(new IdentityWeakReference(key));
        }

        @Override
        public synchronized V put(Object key, V value) {
            if (key == null) {
                return null;
            }
            removeCollectedKeys();
            return values.put(new IdentityWeakReference(key, referenceQueue), value);
        }

        @Override
        public synchronized V remove(Object key) {
            if (key == null) {
                return null;
            }
            removeCollectedKeys();
            return values.remove(new IdentityWeakReference(key));
        }

        @Override
        public synchronized void clear() {
            values.clear();
            while (referenceQueue.poll() != null) {
                // Drain queued references after clearing the backing map.
            }
        }

        @Override
        public synchronized int size() {
            removeCollectedKeys();
            return values.size();
        }

        @Override
        public synchronized Set<Entry<Object, V>> entrySet() {
            removeCollectedKeys();
            Set<Entry<Object, V>> entries = new HashSet<Entry<Object, V>>();
            for (Map.Entry<IdentityWeakReference, V> entry : values.entrySet()) {
                Object key = entry.getKey().get();
                if (key != null) {
                    entries.add(new SimpleImmutableEntry<Object, V>(key, entry.getValue()));
                }
            }
            return Collections.unmodifiableSet(entries);
        }

        private void removeCollectedKeys() {
            Reference<?> reference;
            while ((reference = referenceQueue.poll()) != null) {
                values.remove(reference);
            }
        }
    }

    private static final class IdentityWeakReference extends WeakReference<Object> {
        private final int identityHashCode;

        IdentityWeakReference(Object referent) {
            super(referent);
            identityHashCode = System.identityHashCode(referent);
        }

        IdentityWeakReference(Object referent, ReferenceQueue<Object> referenceQueue) {
            super(referent, referenceQueue);
            identityHashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof IdentityWeakReference)) {
                return false;
            }
            Object referent = get();
            return referent != null
                    && referent == ((IdentityWeakReference) other).get();
        }
    }
}
