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
            if (!enabled || metadata == null || isPatchedMetadata(metadata)
                    || Boolean.TRUE.equals(UNPATCHED_METADATA_MARKERS.get(metadata))) {
                return metadata;
            }
            synchronized (PATCHED_METADATA_BY_ORIGINAL) {
                Object cached = PATCHED_METADATA_BY_ORIGINAL.get(metadata);
                if (cached != null) {
                    return cached;
                }
                ClassLoader classLoader = metadata.getClass().getClassLoader();
                GboardLongPressQuickActions1803ReflectionHandles handles =
                        reflectionHandles(classLoader);
                Context context = softKeyView == null ? null : softKeyView.getContext();
                Object identityMetadata = dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(metadata);
                GboardLongPressQuickActions1803Policy.QuickAction action =
                        GboardLongPressQuickActions1803Policy.plan(
                                handles.extractKeyId(identityMetadata),
                                handles.extractPressText(identityMetadata),
                                handles.extractLongPressCodes(metadata));
                
                Object currentMetadata = metadata;
                
                // Detection for Top Row slots 1-4 (Q, W, E, R)
                String pressText = handles.extractPressText(identityMetadata);
                int topRowIndex = -1;
                if (pressText != null && pressText.length() == 1) {
                    char c = Character.toLowerCase(pressText.charAt(0));
                    if (c == 'q') topRowIndex = 0;
                    else if (c == 'w') topRowIndex = 1;
                    else if (c == 'e') topRowIndex = 2;
                    else if (c == 'r') topRowIndex = 3;
                }

                if (topRowIndex != -1) {
                    // Q (index 0) gets Switch Top Row button
                    if (topRowIndex == 0) {
                        GboardLongPressQuickActions1803Policy.QuickAction switchAction = 
                            new GboardLongPressQuickActions1803Policy.QuickAction(-0x27f0, 0x7f08022a, "Switch top row", 0);
                        currentMetadata = handles.appendLongPressAction(context, currentMetadata, switchAction);
                    }
                    
                    // All 1-4 get their corresponding Slot action
                    GboardLongPressQuickActions1803Policy.QuickAction slotAction = 
                        GboardLongPressQuickActions1803Policy.getSlotAction(topRowIndex);
                    
                    if (slotAction != null) {
                        String slotLabel = dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime.getSlotLabel(context, topRowIndex);
                        if (slotLabel == null || slotLabel.isEmpty()) {
                            slotLabel = "J" + (topRowIndex + 1);
                        }
                        slotAction.label = slotLabel;
                        currentMetadata = handles.appendLongPressAction(context, currentMetadata, slotAction);
                    }
                    
                    // We don't want standard Policy actions for these keys if we've handled them
                    rememberPatchedMetadata(metadata, currentMetadata);
                    return currentMetadata;
                }

                if (shouldHaveToggleQuotes(identityMetadata, handles)) {
                    GboardLongPressQuickActions1803Policy.QuickAction quotesAction =
                            GboardLongPressQuickActions1803Policy.TOGGLE_QUOTES;
                    Object quotesPatched = handles.appendLongPressAction(context, currentMetadata, quotesAction);
                    if (quotesPatched != null) {
                        currentMetadata = quotesPatched;
                    }
                }

                if (action != null) {
                    Object actionPatched = handles.appendLongPressAction(context, currentMetadata, action);
                    if (actionPatched != null) {
                        currentMetadata = actionPatched;
                    }
                }

                rememberPatchedMetadata(metadata, currentMetadata);
                logInfo(PATCH_LOG_COUNT, 30,
                        "patched longpress quick actions for keyId=0x"
                                + Integer.toHexString(handles.extractKeyId(identityMetadata)));
                return currentMetadata;
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
            Object identityMetadata = dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(metadata);
            int keyId = handles.extractKeyId(identityMetadata);
            String pressText = handles.extractPressText(identityMetadata);
            int selectedCode = handles.extractSelectedEventCode(event);
            Log.i(TAG, LOG_PREFIX + "Handling input event, selectedCode=" + selectedCode + " (0x" + Integer.toHexString(selectedCode) + ")");
            
            boolean enabled = GboardLongPressQuickActionsRuntimeSettings.isEnabled();
            boolean quickActionEvent = GboardLongPressQuickActions1803Policy
                    .isQuickActionEvent(keyId, pressText, actionTypeName, selectedCode);
            
            // Check for our custom codes even if Policy didn't plan them via hardcoded IDs
            if (selectedCode == -0x27f0) { // SWITCH_TOP_ROW
                Log.i(TAG, LOG_PREFIX + "Triggering manual row switch via code -0x27f0");
                dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime
                        .triggerManualRowSwitch(service);
                return true;
            }
            if (selectedCode >= -0x27f5 && selectedCode <= -0x27f2) { // SLOTS 0-3
                int slotIndex = -0x27f2 - selectedCode;
                Log.i(TAG, LOG_PREFIX + "Triggering slot " + slotIndex + " via code " + selectedCode);
                dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime
                        .triggerSlotExecution(service, slotIndex);
                return true;
            }

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
                if (selectedCode == -0x27f1) { // TOGGLE_CASE
                    return attemptToggleCaseAction(service.getCurrentInputConnection());
                }
                if (selectedCode == -0x27f6) { // TOGGLE_QUOTES
                    return attemptToggleQuotesAction(service.getCurrentInputConnection());
                }
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
            Object identityMetadata = dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(metadata);
            int keyId = handles.extractKeyId(identityMetadata);
            String pressText = handles.extractPressText(identityMetadata);
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

    static boolean attemptToggleCaseAction(InputConnection connection) {
        if (connection == null) {
            return false;
        }
        CharSequence selectedText = connection.getSelectedText(0);
        if (selectedText != null && selectedText.length() > 0) {
            String toggled = toggleCase(selectedText.toString());
            connection.commitText(toggled, 1);
            return true;
        }

        CharSequence before = connection.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) {
            return false;
        }

        String text = before.toString();
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        if (end == 0) {
            return false;
        }

        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }

        String lastWord = text.substring(start, end);
        String suffix = text.substring(end);
        String toggledWord = toggleCase(lastWord);

        connection.beginBatchEdit();
        connection.deleteSurroundingText(text.length() - start, 0);
        connection.commitText(toggledWord + suffix, 1);
        connection.endBatchEdit();
        return true;
    }

    private static String toggleCase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        boolean hasUppercase = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isUpperCase(text.charAt(i))) {
                hasUppercase = true;
                break;
            }
        }
        if (hasUppercase) {
            return text.toLowerCase();
        } else {
            return Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
    }

    private static boolean shouldHaveToggleQuotes(
            Object metadata,
            GboardLongPressQuickActions1803ReflectionHandles handles) {
        try {
            int keyId = handles.extractKeyId(metadata);
            if (keyId == 0x7f0b1abf || keyId == 0x7f0b1964 || keyId == 0x7f0b2975) {
                return true;
            }
            String pressText = handles.extractPressText(metadata);
            if (pressText != null && pressText.length() == 1) {
                char c = Character.toLowerCase(pressText.charAt(0));
                return c == 'x' || c == '"' || c == '\'' || c == '”' || c == '“' || c == '„';
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean attemptToggleQuotesAction(InputConnection connection) {
        if (connection == null) {
            return false;
        }
        CharSequence selectedText = connection.getSelectedText(0);
        if (selectedText != null && selectedText.length() > 0) {
            String text = selectedText.toString();
            int leadingLen = 0;
            while (leadingLen < text.length()
                    && Character.isWhitespace(text.charAt(leadingLen))) {
                leadingLen++;
            }
            int trailingLen = 0;
            while (trailingLen < (text.length() - leadingLen)
                    && Character.isWhitespace(
                            text.charAt(text.length() - 1 - trailingLen))) {
                trailingLen++;
            }

            String leadingSpaces = text.substring(0, leadingLen);
            String inner = text.substring(leadingLen, text.length() - trailingLen);
            String trailingSpaces = text.substring(text.length() - trailingLen);

            if (inner.isEmpty()) {
                String quoted = "\"" + text + "\"";
                connection.commitText(quoted, 1);
                return true;
            }

            if (inner.length() >= 2 && isQuoteChar(inner.charAt(0))
                    && isQuoteChar(inner.charAt(inner.length() - 1))) {
                String unquoted = inner.substring(1, inner.length() - 1);
                connection.commitText(leadingSpaces + unquoted + trailingSpaces, 1);
            } else {
                String quoted = "\"" + inner + "\"";
                connection.commitText(leadingSpaces + quoted + trailingSpaces, 1);
            }
            return true;
        }

        CharSequence before = connection.getTextBeforeCursor(64, 0);
        CharSequence after = connection.getTextAfterCursor(64, 0);

        String beforeText = before != null ? before.toString() : "";
        String afterText = after != null ? after.toString() : "";

        if (beforeText.isEmpty() && afterText.isEmpty()) {
            return false;
        }

        int startBefore = beforeText.length();
        while (startBefore > 0 && !Character.isWhitespace(beforeText.charAt(startBefore - 1))) {
            startBefore--;
        }
        String wordBefore = beforeText.substring(startBefore);

        int endAfter = 0;
        while (endAfter < afterText.length() && !Character.isWhitespace(afterText.charAt(endAfter))) {
            endAfter++;
        }
        String wordAfter = afterText.substring(0, endAfter);

        String fullWord = wordBefore + wordAfter;
        if (fullWord.isEmpty()) {
            return false;
        }

        connection.beginBatchEdit();
        connection.deleteSurroundingText(beforeText.length() - startBefore, endAfter);

        if (fullWord.length() >= 2 && isQuoteChar(fullWord.charAt(0))
                && isQuoteChar(fullWord.charAt(fullWord.length() - 1))) {
            String unquoted = fullWord.substring(1, fullWord.length() - 1);
            connection.commitText(unquoted, 1);
        } else {
            String quoted = "\"" + fullWord + "\"";
            connection.commitText(quoted, 1);
        }
        connection.endBatchEdit();
        return true;
    }

    private static boolean isQuoteChar(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”' || c == '„'
                || c == '‘' || c == '’' || c == '«' || c == '»';
    }

    static void rememberPatchedMetadata(Object original, Object patched) {
        if (original == null || patched == null) {
            return;
        }
        dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeRuntime.inheritPatchedMetadataForPatchInterop(original, patched);
        PATCHED_METADATA_BY_ORIGINAL.put(original, patched);
        ORIGINAL_METADATA_BY_PATCHED.put(patched, new WeakReference<>(original));
        PATCHED_METADATA_MARKERS.put(patched, Boolean.TRUE);
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
