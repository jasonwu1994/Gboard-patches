package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardLongPressQuickActions1803Runtime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-long-press-quick-actions-18.0.3] ";

    private static final Map<ClassLoader,
            WeakReference<GboardLongPressQuickActions1803ReflectionHandles>>
            HANDLES_BY_CLASS_LOADER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> PATCHED_METADATA_BY_ORIGINAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<Object>> ORIGINAL_METADATA_BY_PATCHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> UNPATCHED_METADATA_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> PATCHED_METADATA_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Integer> PATCHED_POSITION_BY_METADATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final AtomicInteger PATCH_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger SCHEDULE_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ACTION_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ERROR_LOG_COUNT = new AtomicInteger();

    private GboardLongPressQuickActions1803Runtime() {
    }

    public static GboardLongPressQuickActions1803ReflectionHandles reflectionHandles(
            ClassLoader classLoader) throws Throwable {
        if (classLoader == null) {
            throw new IllegalArgumentException("Target ClassLoader is required");
        }
        synchronized (HANDLES_BY_CLASS_LOADER) {
            WeakReference<GboardLongPressQuickActions1803ReflectionHandles> reference =
                    HANDLES_BY_CLASS_LOADER.get(classLoader);
            GboardLongPressQuickActions1803ReflectionHandles cached =
                    reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            GboardLongPressQuickActions1803ReflectionHandles created =
                    new GboardLongPressQuickActions1803ReflectionHandles(classLoader);
            HANDLES_BY_CLASS_LOADER.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    public static Object maybePatchMetadata(Object metadata, View softKeyView) {
        metadata = GboardGlobeDragRuntime.maybePatchMetadata(metadata, softKeyView);
        Object incomingMetadata = metadata;
        try {
            boolean enabled = GboardLongPressQuickActionsRuntimeSettings.isEnabled();
            metadata = metadataForBind(enabled, metadata);
            if (!enabled || metadata == null) {
                return metadata;
            }
            Context context = softKeyView == null ? null : softKeyView.getContext();
            int position = GboardLongPressQuickActionsRuntimeSettings.position();
            metadata = metadataForPosition(metadata, position);
            if (isPatchedMetadata(metadata)
                    || Boolean.TRUE.equals(UNPATCHED_METADATA_MARKERS.get(metadata))) {
                return metadata;
            }
            synchronized (PATCHED_METADATA_BY_ORIGINAL) {
                Object cached = cachedMetadataForPosition(metadata, position);
                if (cached != null) {
                    return cached;
                }
                ClassLoader classLoader = metadata.getClass().getClassLoader();
                GboardLongPressQuickActions1803ReflectionHandles handles =
                        reflectionHandles(classLoader);
                GboardLongPressQuickActions1803Policy.QuickAction action =
                        GboardLongPressQuickActions1803Policy.plan(
                                handles.extractKeyId(metadata),
                                handles.extractPressText(metadata),
                                handles.extractLongPressCodes(metadata));
                if (action == null) {
                    UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
                    return metadata;
                }
                Object patched = handles.insertLongPressAction(
                        context, metadata, action, position);
                if (patched == null) {
                    UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
                    return metadata;
                }
                rememberPatchedMetadata(metadata, patched, position);
                logInfo(PATCH_LOG_COUNT, 30,
                        "inserted keycode=" + action.actionCode
                                + ", name=" + action.debugName
                                + ", icon=0x" + Integer.toHexString(action.iconResId)
                                + ", position=" + position);
                return patched;
            }
        } catch (Throwable throwable) {
            markUnpatchedSafely(incomingMetadata);
            logError("metadata patch failed", throwable);
            return incomingMetadata;
        }
    }

    public static boolean maybeHandleInputEvent(
            InputMethodService service,
            Object event) {
        if (service == null || event == null) {
            return false;
        }
        if (GboardGlobeDragRuntime.maybeHandleInputEvent(service, event)) {
            return true;
        }
        try {
            GboardLongPressQuickActions1803ReflectionHandles handles =
                    reflectionHandles(service.getClass().getClassLoader());
            Object metadata = handles.extractEventMetadata(event);
            String actionTypeName = handles.extractEventActionTypeName(event);
            int keyId = handles.extractKeyId(metadata);
            String pressText = handles.extractPressText(metadata);
            int selectedCode = handles.extractSelectedEventCode(event);
            boolean enabled = GboardLongPressQuickActionsRuntimeSettings.isEnabled();
            boolean quickActionEvent = GboardLongPressQuickActions1803Policy
                    .isQuickActionEvent(keyId, pressText, actionTypeName, selectedCode);
            if (shouldConsumeDisabledInjectedEvent(enabled, metadata, quickActionEvent)) {
                logInfo(ACTION_LOG_COUNT, 30, "suppressed disabled injected action");
                return true;
            }
            if (!enabled) {
                return false;
            }
            Integer contextMenuAction =
                    GboardLongPressQuickActions1803Policy.contextMenuActionFor(
                            keyId, pressText, actionTypeName, selectedCode);
            if (contextMenuAction == null) {
                return false;
            }
            return consumeRecognizedContextMenuAction(
                    service::getCurrentInputConnection, contextMenuAction.intValue());
        } catch (Throwable throwable) {
            logError("input event handling failed", throwable);
            return false;
        }
    }

    public static void maybeEnsureLongPressScheduled(
            Object pointerTracker,
            View softKeyView) {
        try {
            GboardGlobeDragRuntime.onPointerOwner(pointerTracker, softKeyView);
            if (pointerTracker == null || softKeyView == null
                    || !GboardLongPressQuickActionsRuntimeSettings.isEnabled()) {
                return;
            }
            GboardLongPressQuickActions1803ReflectionHandles handles =
                    reflectionHandles(pointerTracker.getClass().getClassLoader());
            Object metadata = handles.extractSoftKeyMetadata(softKeyView);
            if (metadata == null) {
                return;
            }
            int keyId = handles.extractKeyId(metadata);
            String pressText = handles.extractPressText(metadata);
            int[] longPressCodes = handles.extractLongPressCodes(metadata);
            if (!GboardLongPressQuickActions1803Policy.containsAssignedAction(
                    keyId, pressText, longPressCodes)) {
                return;
            }
            handles.scheduleLongPress(pointerTracker);
            logInfo(SCHEDULE_LOG_COUNT, 30,
                    "ensured long-press schedule keyId=0x" + Integer.toHexString(keyId));
        } catch (Throwable throwable) {
            logError("long-press schedule failed", throwable);
        }
    }

    static boolean attemptContextMenuAction(InputConnection connection, int actionId) {
        if (!GboardEditingShortcutDispatchGuard.shouldDispatchContextMenuAction(
                connection, actionId)) {
            return false;
        }
        try {
            connection.performContextMenuAction(actionId);
            return true;
        } catch (Throwable throwable) {
            logError("editor action failed", throwable);
            return false;
        }
    }

    static boolean consumeRecognizedContextMenuAction(
            InputConnection connection, int actionId) {
        return consumeRecognizedContextMenuAction(() -> connection, actionId);
    }

    static boolean consumeRecognizedContextMenuAction(
            InputConnectionSupplier connectionSupplier, int actionId) {
        try {
            InputConnection connection = connectionSupplier == null
                    ? null : connectionSupplier.get();
            if (attemptContextMenuAction(connection, actionId)) {
                logInfo(ACTION_LOG_COUNT, 30,
                        "performed contextMenuAction=0x"
                                + Integer.toHexString(actionId));
            } else {
                logInfo(ACTION_LOG_COUNT, 30,
                        "suppressed unavailable contextMenuAction=0x"
                                + Integer.toHexString(actionId));
            }
        } catch (Throwable throwable) {
            logError("editor connection lookup failed", throwable);
        }
        return true;
    }

    interface InputConnectionSupplier {
        InputConnection get() throws Throwable;
    }

    static void rememberPatchedMetadata(Object original, Object patched, int position) {
        if (original == null || patched == null) {
            return;
        }
        PATCHED_METADATA_BY_ORIGINAL.put(original, patched);
        ORIGINAL_METADATA_BY_PATCHED.put(patched, new WeakReference<>(original));
        PATCHED_METADATA_MARKERS.put(patched, Boolean.TRUE);
        PATCHED_POSITION_BY_METADATA.put(patched, Integer.valueOf(position));
    }

    static Object metadataForPosition(Object metadata, int position) {
        if (metadata == null || !isPatchedMetadata(metadata)) {
            return metadata;
        }
        Integer patchedPosition = PATCHED_POSITION_BY_METADATA.get(metadata);
        if (patchedPosition != null && patchedPosition.intValue() == position) {
            return metadata;
        }
        WeakReference<Object> originalReference = ORIGINAL_METADATA_BY_PATCHED.get(metadata);
        Object original = originalReference == null ? null : originalReference.get();
        return original == null ? metadata : original;
    }

    static Object cachedMetadataForPosition(Object original, int position) {
        Object cached = PATCHED_METADATA_BY_ORIGINAL.get(original);
        if (cached == null) {
            return null;
        }
        Integer cachedPosition = PATCHED_POSITION_BY_METADATA.get(cached);
        return cachedPosition != null && cachedPosition.intValue() == position
                ? cached : null;
    }

    static Object metadataForBind(boolean enabled, Object metadata) {
        if (enabled || metadata == null) {
            return metadata;
        }
        WeakReference<Object> originalReference = ORIGINAL_METADATA_BY_PATCHED.get(metadata);
        Object original = originalReference == null ? null : originalReference.get();
        return original == null ? metadata : original;
    }

    static boolean shouldConsumeDisabledInjectedEvent(boolean enabled, Object metadata,
            boolean quickActionEvent) {
        return !enabled && quickActionEvent && isPatchedMetadata(metadata);
    }

    private static boolean isPatchedMetadata(Object metadata) {
        return metadata != null && Boolean.TRUE.equals(PATCHED_METADATA_MARKERS.get(metadata));
    }

    private static void markUnpatchedSafely(Object metadata) {
        try {
            if (metadata != null) {
                UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
            }
        } catch (Throwable ignored) {
            // Cache cleanup must not affect the keyboard path.
        }
    }

    private static void logInfo(AtomicInteger counter, int limit, String message) {
        try {
            if (counter.getAndIncrement() >= limit) {
                return;
            }
            Log.i(TAG, LOG_PREFIX + message);
        } catch (Throwable ignored) {
            // Logging must not affect the keyboard path.
        }
    }

    private static void logError(String message, Throwable throwable) {
        try {
            if (ERROR_LOG_COUNT.getAndIncrement() >= 8) {
                return;
            }
            Log.w(TAG, LOG_PREFIX + message, throwable);
        } catch (Throwable ignored) {
            // Logging must not affect the keyboard path.
        }
    }
}
