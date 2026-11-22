package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.core.input.GboardEditorActions;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardLongPressQuickActionsRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-long-press-quick-actions-18.5.4] ";

    private static final Map<ClassLoader,
            WeakReference<GboardLongPressQuickActionsReflectionHandles>>
            HANDLES_BY_CLASS_LOADER = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Object> PATCHED_METADATA_BY_ORIGINAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<Object>> ORIGINAL_METADATA_BY_PATCHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> UNPATCHED_METADATA_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Patched metadata mapped to its shortcut placement: true when the shortcut is first. */
    private static final Map<Object, Boolean> PATCHED_METADATA_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final AtomicInteger PATCH_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger SCHEDULE_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ACTION_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ERROR_LOG_COUNT = new AtomicInteger();

    private GboardLongPressQuickActionsRuntime() {
    }

    public static GboardLongPressQuickActionsReflectionHandles reflectionHandles(
            ClassLoader classLoader) throws Throwable {
        if (classLoader == null) {
            throw new IllegalArgumentException("Target ClassLoader is required");
        }
        synchronized (HANDLES_BY_CLASS_LOADER) {
            WeakReference<GboardLongPressQuickActionsReflectionHandles> reference =
                    HANDLES_BY_CLASS_LOADER.get(classLoader);
            GboardLongPressQuickActionsReflectionHandles cached =
                    reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            GboardLongPressQuickActionsReflectionHandles created =
                    new GboardLongPressQuickActionsReflectionHandles(classLoader);
            HANDLES_BY_CLASS_LOADER.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    public static Object maybePatchMetadata(Object metadata, View softKeyView) {
        metadata = GboardGlobeDragRuntime.maybePatchMetadata(metadata, softKeyView);
        return maybePatchMetadataWithPatcher(metadata, (candidate, shortcutFirst) -> {
            ClassLoader classLoader = candidate.getClass().getClassLoader();
            GboardLongPressQuickActionsReflectionHandles handles =
                    reflectionHandles(classLoader);
            Context context = softKeyView == null ? null : softKeyView.getContext();
            GboardLongPressQuickActionsPolicy.QuickAction action =
                    GboardLongPressQuickActionsPolicy.plan(
                            handles.extractKeyId(candidate),
                            handles.extractPressText(candidate),
                            handles.extractLongPressCodes(candidate));
            if (action == null) {
                return null;
            }
            Object patched = handles.insertLongPressAction(
                    context, candidate, action, shortcutFirst);
            if (patched != null) {
                logInfo(PATCH_LOG_COUNT, 30,
                        "inserted " + (shortcutFirst ? "first" : "last")
                                + " keycode=" + action.actionCode
                                + ", name=" + action.debugName
                                + ", icon=0x" + Integer.toHexString(action.iconResId));
            }
            return patched;
        });
    }

    static Object maybePatchMetadataWithPatcher(Object metadata, MetadataPatcher patcher) {
        Object fallbackMetadata = metadata;
        try {
            boolean enabled = GboardLongPressQuickActionsRuntimeSettings.isEnabled();
            boolean shortcutFirst = GboardLongPressQuickActionsRuntimeSettings.isShortcutFirst();
            metadata = metadataForBind(enabled, shortcutFirst, metadata);
            fallbackMetadata = metadata != null ? metadata : fallbackMetadata;
            if (!enabled || patcher == null || metadata == null || isPatchedMetadata(metadata)
                    || Boolean.TRUE.equals(UNPATCHED_METADATA_MARKERS.get(metadata))) {
                return metadata;
            }
            synchronized (PATCHED_METADATA_BY_ORIGINAL) {
                Object cached = PATCHED_METADATA_BY_ORIGINAL.get(metadata);
                if (cached != null && hasPlacement(cached, shortcutFirst)) {
                    return cached;
                }
                Object patched = patcher.patch(metadata, shortcutFirst);
                if (patched == null) {
                    UNPATCHED_METADATA_MARKERS.put(metadata, Boolean.TRUE);
                    return metadata;
                }
                rememberPatchedMetadata(metadata, patched, shortcutFirst);
                return patched;
            }
        } catch (Throwable throwable) {
            markUnpatchedSafely(fallbackMetadata);
            logError("metadata patch failed", throwable);
            return fallbackMetadata;
        }
    }

    interface MetadataPatcher {
        Object patch(Object metadata, boolean shortcutFirst) throws Throwable;
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
            GboardLongPressQuickActionsReflectionHandles handles =
                    reflectionHandles(service.getClass().getClassLoader());
            Object metadata = handles.extractEventMetadata(event);
            String actionTypeName = handles.extractEventActionTypeName(event);
            int keyId = handles.extractKeyId(metadata);
            String pressText = handles.extractPressText(metadata);
            int selectedCode = handles.extractSelectedEventCode(event);
            boolean enabled = GboardLongPressQuickActionsRuntimeSettings.isEnabled();
            boolean quickActionEvent = GboardLongPressQuickActionsPolicy
                    .isQuickActionEvent(keyId, pressText, actionTypeName, selectedCode);
            if (shouldConsumeDisabledInjectedEvent(enabled, metadata, quickActionEvent)) {
                logInfo(ACTION_LOG_COUNT, 30, "suppressed disabled injected action");
                return true;
            }
            if (!enabled) {
                return false;
            }
            Integer contextMenuAction =
                    GboardLongPressQuickActionsPolicy.contextMenuActionFor(
                            keyId, pressText, actionTypeName, selectedCode);
            if (contextMenuAction == null) {
                return false;
            }
            return consumeRecognizedContextMenuAction(
                    service::getCurrentInputConnection, contextMenuAction.intValue(),
                    GboardLongPressQuickActionsRuntimeSettings.isSelectAllWhenUnselectedEnabled(), service);
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
            GboardLongPressQuickActionsReflectionHandles handles =
                    reflectionHandles(pointerTracker.getClass().getClassLoader());
            Object metadata = handles.extractSoftKeyMetadata(softKeyView);
            if (metadata == null) {
                return;
            }
            int keyId = handles.extractKeyId(metadata);
            String pressText = handles.extractPressText(metadata);
            int[] longPressCodes = handles.extractLongPressCodes(metadata);
            if (!GboardLongPressQuickActionsPolicy.containsAssignedAction(
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
        return attemptContextMenuAction(connection, actionId, false);
    }

    static boolean attemptContextMenuAction(InputConnection connection, int actionId,
            boolean selectAllWhenUnselected) {
        return GboardEditorActions.perform(connection, actionId, selectAllWhenUnselected, () -> true);
    }

    static boolean consumeRecognizedContextMenuAction(InputConnection connection, int actionId) {
        return consumeRecognizedContextMenuAction(() -> connection, actionId);
    }

    static boolean consumeRecognizedContextMenuAction(InputConnectionSupplier supplier, int actionId) {
        return consumeRecognizedContextMenuAction(supplier, actionId, false);
    }

    static boolean consumeRecognizedContextMenuAction(InputConnectionSupplier supplier, int actionId,
            boolean selectAllWhenUnselected) {
        return consumeRecognizedContextMenuAction(supplier, actionId, selectAllWhenUnselected, null);
    }

    private static boolean consumeRecognizedContextMenuAction(InputConnectionSupplier supplier,
            int actionId, boolean selectAllWhenUnselected, InputMethodService service) {
        try {
            InputConnection connection = supplier == null ? null : supplier.get();
            boolean dispatched = service == null
                    ? attemptContextMenuAction(connection, actionId, selectAllWhenUnselected)
                    : GboardEditorActions.perform(service, actionId, selectAllWhenUnselected,
                            () -> service.getCurrentInputConnection() == connection);
            logInfo(ACTION_LOG_COUNT, 30,
                    (dispatched ? "dispatched" : "suppressed unavailable")
                            + " editorAction=0x" + Integer.toHexString(actionId));
        } catch (Throwable throwable) {
            logError("editor connection or action failed", throwable);
        }
        return true;
    }

    interface InputConnectionSupplier {
        InputConnection get() throws Throwable;
    }

    static void rememberPatchedMetadata(Object original, Object patched,
            boolean shortcutFirst) {
        if (original == null || patched == null) {
            return;
        }
        PATCHED_METADATA_BY_ORIGINAL.put(original, patched);
        ORIGINAL_METADATA_BY_PATCHED.put(patched, new WeakReference<>(original));
        PATCHED_METADATA_MARKERS.put(patched, Boolean.valueOf(shortcutFirst));
    }

    /**
     * Returns the stock metadata for a patched key that is disabled or was built for the other
     * shortcut placement, so the next bind restores it or rebuilds it in the current order.
     */
    static Object metadataForBind(boolean enabled, boolean shortcutFirst, Object metadata) {
        if (metadata == null || (enabled
                && (!isPatchedMetadata(metadata) || hasPlacement(metadata, shortcutFirst)))) {
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
        return metadata != null && PATCHED_METADATA_MARKERS.get(metadata) != null;
    }

    private static boolean hasPlacement(Object patched, boolean shortcutFirst) {
        return Boolean.valueOf(shortcutFirst).equals(PATCHED_METADATA_MARKERS.get(patched));
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
