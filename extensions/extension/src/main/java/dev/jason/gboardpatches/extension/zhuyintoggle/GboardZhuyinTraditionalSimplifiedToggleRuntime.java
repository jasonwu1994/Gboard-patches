package dev.jason.gboardpatches.extension.zhuyintoggle;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import dev.jason.gboardpatches.extension.zhuyinslide.GboardZhuyinSlideRuntime;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@SuppressWarnings("unused")
public final class GboardZhuyinTraditionalSimplifiedToggleRuntime {
    private static final Map<ClassLoader, GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport>
            HANDLES_BY_CLASS_LOADER = Collections.synchronizedMap(
                    new WeakHashMap<ClassLoader,
                            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport>());
    private static final WeakIdentityMap<Object, Boolean> PATCHED_METADATA_MARKERS =
            new WeakIdentityMap<Object, Boolean>();
    private static final WeakIdentityMap<Object, Object> BASE_METADATA_BY_PATCHED =
            new WeakIdentityMap<Object, Object>();
    static final Map<View, Object> VISIBLE_TOGGLE_KEYS = Collections.synchronizedMap(
            new WeakHashMap<View, Object>());
    private static final Map<View, Boolean> VISIBLE_CANDIDATE_BUTTONS =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());

    private GboardZhuyinTraditionalSimplifiedToggleRuntime() {
    }

    public static Object patchIncomingSoftKeyMetadata(Object receiver, Object metadata) {
        if (!(receiver instanceof View) || metadata == null
                || Boolean.TRUE.equals(PATCHED_METADATA_MARKERS.get(metadata))) {
            return metadata;
        }
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(receiver);
            if (!GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldHandleKey(
                    handles.keyId(metadata))) {
                return metadata;
            }
            Boolean simplifiedEnabled = handles.readSimplifiedEnabled(
                    ((View) receiver).getContext());
            if (simplifiedEnabled == null) {
                return metadata;
            }
            Object patched = handles.buildToggleMetadata(
                    metadata,
                    GboardZhuyinTraditionalSimplifiedTogglePolicy.nextLabel(
                            simplifiedEnabled.booleanValue()));
            if (patched == null || patched == metadata) {
                return metadata;
            }
            markPatchedMetadata(patched, resolveBaseMetadata(metadata));
            return patched;
        } catch (Throwable ignored) {
            return metadata;
        }
    }

    public static Object patchCandidateButtonMetadata(Object receiver, Object metadata) {
        if (!(receiver instanceof View) || metadata == null) {
            return metadata;
        }
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(receiver);
            if (!GboardZhuyinTraditionalSimplifiedTogglePolicy.isSingleCharacterCandidateKey(
                    handles.keyId(metadata))) {
                return metadata;
            }
            Object baseMetadata = resolveBaseMetadata(metadata);
            Context context = ((View) receiver).getContext();
            if (!GboardZhuyinSimplifiedCandidateButtonSettings.readEnabled(context)) {
                return baseMetadata;
            }
            if (Boolean.TRUE.equals(PATCHED_METADATA_MARKERS.get(metadata))) {
                return metadata;
            }
            Boolean simplified = handles.readSimplifiedEnabled(context);
            if (simplified == null) return metadata;
            Object patched = handles.buildCandidateMetadata(baseMetadata, simplified);
            if (patched == null || patched == baseMetadata) {
                return baseMetadata;
            }
            PATCHED_METADATA_MARKERS.put(patched, Boolean.TRUE);
            BASE_METADATA_BY_PATCHED.put(patched, baseMetadata);
            return patched;
        } catch (Throwable ignored) {
            return metadata;
        }
    }

    public static void afterSoftKeyBound(Object receiver) {
        if (!(receiver instanceof View)) {
            return;
        }
        View view = (View) receiver;
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(receiver);
            Object metadata = handles.currentMetadata(receiver);
            if (metadata == null
                    || !GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldHandleKey(
                            handles.keyId(metadata))) {
                VISIBLE_TOGGLE_KEYS.remove(view);
                return;
            }
            VISIBLE_TOGGLE_KEYS.put(view, resolveBaseMetadata(metadata));
        } catch (Throwable ignored) {
            VISIBLE_TOGGLE_KEYS.remove(view);
        }
    }

    public static void afterCandidateButtonBound(Object receiver) {
        if (!(receiver instanceof View)) {
            return;
        }
        View view = (View) receiver;
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(receiver);
            Object metadata = handles.currentMetadata(receiver);
            if (metadata == null ||
                    !GboardZhuyinTraditionalSimplifiedTogglePolicy.isSingleCharacterCandidateKey(
                            handles.keyId(metadata))) {
                VISIBLE_CANDIDATE_BUTTONS.remove(view);
                return;
            }
            // Track the stock key too, so enabling the feature can update a cached view.
            VISIBLE_CANDIDATE_BUTTONS.put(view, Boolean.TRUE);
            Context context = view.getContext();
            if (!GboardZhuyinSimplifiedCandidateButtonSettings.readEnabled(context)) {
                return;
            }
            Boolean simplifiedEnabled = handles.readSimplifiedEnabled(context);
            if (simplifiedEnabled == null) {
                return;
            }
            View label = view.findViewById(
                    GboardZhuyinTraditionalSimplifiedTogglePolicy
                            .SINGLE_CHARACTER_CANDIDATE_LABEL_LOCATION);
            if (label == null) {
                return;
            }
            VISIBLE_CANDIDATE_BUTTONS.put(view, Boolean.TRUE);
            setCandidateButtonState(view, simplifiedEnabled.booleanValue());
        } catch (Throwable ignored) {
            VISIBLE_CANDIDATE_BUTTONS.remove(view);
        }
    }

    public static Object patchPopupAction(Object sourceView, Object actionDef) {
        if (sourceView == null || actionDef == null) {
            return actionDef;
        }
        Object metadata = VISIBLE_TOGGLE_KEYS.get(sourceView);
        if (metadata == null) {
            return actionDef;
        }
        if (!(sourceView instanceof View)) {
            return actionDef;
        }
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(sourceView);
            if (!GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsume(
                    handles.keyId(metadata), handles.actionDefinitionName(actionDef))) {
                return actionDef;
            }
            Boolean simplifiedEnabled = handles.readSimplifiedEnabled(
                    ((View) sourceView).getContext());
            if (simplifiedEnabled == null) {
                return actionDef;
            }
            return handles.copyPopupActionWithLabel(
                    actionDef,
                    GboardZhuyinTraditionalSimplifiedTogglePolicy.nextLabel(
                            simplifiedEnabled.booleanValue()));
        } catch (Throwable ignored) {
            return actionDef;
        }
    }

    public static boolean maybeToggle(Object dispatcher, Object action, Object metadata) {
        if (dispatcher == null || action == null || metadata == null) {
            return false;
        }
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(dispatcher);
            if (!GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsume(
                    handles.keyId(metadata), handles.actionName(action))) {
                return false;
            }
            Context context = handles.dispatcherContext(dispatcher);
            Object preferences = handles.dispatcherPreferences(dispatcher);
            if (context == null || preferences == null) {
                return false;
            }
            Boolean simplifiedEnabled = handles.readSimplifiedEnabled(preferences);
            if (simplifiedEnabled == null) {
                return false;
            }
            boolean nextEnabled = !simplifiedEnabled.booleanValue();
            if (!handles.writeSimplifiedEnabled(preferences, nextEnabled)) {
                return false;
            }
            try {
                refreshVisibleKeys(handles, nextEnabled);
            } catch (Throwable ignored) {
                // The placeholder was already handled after a successful official write.
            }
            try {
                refreshCurrentInputView(context);
            } catch (Throwable ignored) {
                // Refresh failure must not dispatch the empty placeholder to stock.
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean maybeToggleCandidateButton(
            Object dispatcher, Object action, Object metadata) {
        if (dispatcher == null || action == null || metadata == null) {
            return false;
        }
        boolean handled = false;
        try {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(dispatcher);
            if (!GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsumeCandidateButton(
                    handles.keyId(metadata), handles.actionName(action))) {
                return false;
            }
            Context context = handles.dispatcherContext(dispatcher);
            if (context == null || !GboardZhuyinSimplifiedCandidateButtonSettings
                    .readEnabled(context)) {
                return false;
            }
            handled = true;
            Object preferences = handles.dispatcherPreferences(dispatcher);
            if (preferences == null) {
                return true;
            }
            Boolean simplifiedEnabled = handles.readSimplifiedEnabled(preferences);
            if (simplifiedEnabled == null) {
                return true;
            }
            boolean nextEnabled = !simplifiedEnabled.booleanValue();
            if (!handles.writeSimplifiedEnabled(preferences, nextEnabled)) {
                return true;
            }
            try {
                refreshVisibleCandidateButtons(nextEnabled);
            } catch (Throwable error) {
                Log.e("ZhuyinConversion", "Cannot rebind candidate toggle", error);
            }
            GboardZhuyinCandidateConversionRuntime.refreshCandidates();
            return true;
        } catch (Throwable error) {
            Log.e("ZhuyinConversion", "Candidate toggle failed", error);
            return handled;
        }
    }

    private static void refreshVisibleKeys(
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles,
            boolean simplifiedEnabled) {
        Map<View, Object> snapshot = new HashMap<View, Object>();
        synchronized (VISIBLE_TOGGLE_KEYS) {
            snapshot.putAll(VISIBLE_TOGGLE_KEYS);
        }
        for (Map.Entry<View, Object> entry : snapshot.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            try {
                Object patched = handles.buildToggleMetadata(entry.getValue(),
                        GboardZhuyinTraditionalSimplifiedTogglePolicy.nextLabel(
                                simplifiedEnabled));
                if (patched == null) {
                    continue;
                }
                markPatchedMetadata(patched, entry.getValue());
                handles.rebind(entry.getKey(), patched);
            } catch (Throwable ignored) {
                // Continue refreshing the remaining visible toggle keys.
            }
        }
    }

    private static void setCandidateButtonState(View view, boolean simplifiedEnabled)
            throws ReflectiveOperationException {
        GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(view);
        Object metadata = handles.currentMetadata(view);
        if (metadata == null || !GboardZhuyinTraditionalSimplifiedTogglePolicy
                .isSingleCharacterCandidateKey(handles.keyId(metadata))) return;
        if (Boolean.TRUE.equals(PATCHED_METADATA_MARKERS.get(metadata))
                && handles.candidateLayoutMatches(metadata, simplifiedEnabled)) return;
        Object base = resolveBaseMetadata(metadata);
        Object patched = handles.buildCandidateMetadata(base, simplifiedEnabled);
        PATCHED_METADATA_MARKERS.put(patched, Boolean.TRUE);
        BASE_METADATA_BY_PATCHED.put(patched, base);
        // Rebind this key alone: stock theme tags draw the active/inactive underline.
        handles.rebind(view, patched);
    }

    private static void refreshVisibleCandidateButtons(boolean simplifiedEnabled) {
        Map<View, Boolean> snapshot = new HashMap<View, Boolean>();
        synchronized (VISIBLE_CANDIDATE_BUTTONS) {
            snapshot.putAll(VISIBLE_CANDIDATE_BUTTONS);
        }
        for (View view : snapshot.keySet()) {
            if (view == null) {
                continue;
            }
            try {
                setCandidateButtonState(view, simplifiedEnabled);
            } catch (Throwable error) {
                Log.e("ZhuyinConversion", "Cannot apply native candidate key layout", error);
            }
        }
    }

    static void onCandidateButtonSettingChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() {
                Map<View, Boolean> snapshot = new HashMap<View, Boolean>();
                synchronized (VISIBLE_CANDIDATE_BUTTONS) {
                    snapshot.putAll(VISIBLE_CANDIDATE_BUTTONS);
                }
                for (View view : snapshot.keySet()) {
                    try {
                        GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles = handles(view);
                        Object current = handles.currentMetadata(view);
                        if (current == null || !GboardZhuyinTraditionalSimplifiedTogglePolicy
                                .isSingleCharacterCandidateKey(handles.keyId(current))) continue;
                        // Always rebind from stock metadata. The bind hook reads the saved
                        // setting and restores either the stock key or the conversion key.
                        handles.rebind(view, resolveBaseMetadata(current));
                    } catch (Throwable error) {
                        Log.e("ZhuyinConversion", "Cannot apply candidate button setting", error);
                    }
                }
                GboardZhuyinCandidateConversionRuntime.refreshCandidates();
            }
        });
    }

    private static void refreshCurrentInputView(Context context) {
        InputMethodService inputMethodService = unwrapInputMethodService(context);
        if (inputMethodService == null) {
            return;
        }
        EditorInfo editorInfo = inputMethodService.getCurrentInputEditorInfo();
        if (editorInfo == null) {
            return;
        }
        try {
            Method resetMethod = inputMethodService.getClass().getMethod("ar", boolean.class);
            resetMethod.setAccessible(true);
            resetMethod.invoke(inputMethodService, Boolean.TRUE);
            inputMethodService.onStartInput(editorInfo, true);
            inputMethodService.onStartInputView(editorInfo, true);
        } catch (Throwable ignored) {
            // A completed preference write remains consumed even when refresh is unavailable.
        }
    }

    private static InputMethodService unwrapInputMethodService(Context context) {
        Context current = context;
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (current instanceof InputMethodService) {
                return (InputMethodService) current;
            }
            if (!(current instanceof ContextWrapper)) {
                return null;
            }
            Context baseContext = ((ContextWrapper) current).getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
        }
        return null;
    }

    private static GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport handles(Object receiver)
            throws ReflectiveOperationException {
        ClassLoader classLoader = receiver.getClass().getClassLoader();
        if (classLoader == null) {
            throw new ClassNotFoundException("Zhuyin toggle receiver has no class loader");
        }
        synchronized (HANDLES_BY_CLASS_LOADER) {
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport cached =
                    HANDLES_BY_CLASS_LOADER.get(classLoader);
            if (cached != null) {
                return cached;
            }
            GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport created =
                    new GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport(classLoader);
            HANDLES_BY_CLASS_LOADER.put(classLoader, created);
            return created;
        }
    }

    private static Object resolveBaseMetadata(Object metadata) {
        Object base = BASE_METADATA_BY_PATCHED.get(metadata);
        return base != null ? base : metadata;
    }

    private static void markPatchedMetadata(Object patched, Object base) {
        PATCHED_METADATA_MARKERS.put(patched, Boolean.TRUE);
        BASE_METADATA_BY_PATCHED.put(patched, base != null ? base : patched);
        GboardZhuyinSlideRuntime.inheritPatchedMetadata(base, patched);
    }

    private static final class WeakIdentityMap<K, V> {
        private final ReferenceQueue<K> referenceQueue = new ReferenceQueue<K>();
        private final Map<IdentityWeakReference<K>, V> values =
                new HashMap<IdentityWeakReference<K>, V>();

        synchronized V get(K key) {
            if (key == null) {
                return null;
            }
            removeCollectedKeys();
            return values.get(new IdentityWeakReference<K>(key));
        }

        synchronized void put(K key, V value) {
            if (key == null) {
                return;
            }
            removeCollectedKeys();
            values.put(new IdentityWeakReference<K>(key, referenceQueue), value);
        }

        @SuppressWarnings("unchecked")
        private void removeCollectedKeys() {
            IdentityWeakReference<K> reference;
            while ((reference = (IdentityWeakReference<K>) referenceQueue.poll()) != null) {
                values.remove(reference);
            }
        }
    }

    private static final class IdentityWeakReference<T> extends WeakReference<T> {
        private final int identityHashCode;

        IdentityWeakReference(T referent) {
            super(referent);
            identityHashCode = System.identityHashCode(referent);
        }

        IdentityWeakReference(T referent, ReferenceQueue<T> referenceQueue) {
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
            if (!(other instanceof IdentityWeakReference<?>)) {
                return false;
            }
            Object value = get();
            return value != null && value == ((IdentityWeakReference<?>) other).get();
        }
    }
}
