package dev.jason.gboardpatches.extension.longpressquickactions;

import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Stable ownership runtime; version-private details enter only through {@link GboardGlobeDragPort}. */
public final class GboardGlobeDragRuntime {
    enum MarkerDownDisposition {
        PASS_THROUGH,
        CONSUME_ONLY,
        ARM_SESSION
    }

    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-globe-drag-18.0.3] ";
    private static final String MARKER = "__gboard_patches_globe_drag__";
    private static final long RELEASE_EVENT_GRACE_MS = 2_000L;
    private static final int LOG_LIMIT = 40;

    private static final Object LOCK = new Object();
    private static final Map<Object, Object> PATCHED_BY_ORIGINAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> PATCHED_MARKERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, WeakReference<Object>> ORIGINAL_BY_PATCHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ClassLoader, WeakReference<GboardGlobeDragPort>> PORTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicInteger INFO_LOG_COUNT = new AtomicInteger();
    private static final AtomicInteger ERROR_LOG_COUNT = new AtomicInteger();

    private static GboardGlobeDragSession session;
    private static WeakReference<Object> latestGlobeTracker = new WeakReference<>(null);
    private static int nextSessionId;

    private GboardGlobeDragRuntime() {
    }

    public static Object maybePatchMetadata(Object metadata, View view) {
        if (metadata == null || view == null) {
            return metadata;
        }
        try {
            GboardGlobeDragPort port = portFor(metadata);
            port.observeBoundKey(metadata, view);
            if (!port.isGlobeView(view)) {
                return metadata;
            }
            boolean enabled = isEnabled();
            metadata = metadataForBind(enabled, metadata);
            if (!enabled) {
                clearDisabledState();
                return metadata;
            }
            if (isPatched(metadata)) {
                return metadata;
            }
            synchronized (PATCHED_BY_ORIGINAL) {
                Object cached = PATCHED_BY_ORIGINAL.get(metadata);
                if (cached != null) {
                    return cached;
                }
                Object patched = port.patchGlobeMetadata(metadata, MARKER);
                if (patched == null) {
                    return metadata;
                }
                PATCHED_BY_ORIGINAL.put(metadata, patched);
                PATCHED_MARKERS.put(patched, Boolean.TRUE);
                ORIGINAL_BY_PATCHED.put(patched, new WeakReference<>(metadata));
                logInfo("patched stock globe metadata");
                return patched;
            }
        } catch (Throwable throwable) {
            logError("metadata patch failed", throwable);
            return metadata;
        }
    }

    public static boolean maybeHandleInputEvent(InputMethodService service, Object event) {
        if (service == null || event == null) {
            return false;
        }
        try {
            GboardGlobeDragPort port = portFor(service);
            GboardGlobeDragPort.InputSignal signal = port.inspectInputEvent(event, MARKER);
            long now = SystemClock.uptimeMillis();
            synchronized (LOCK) {
                expireSession(now);
                boolean patchedMetadata = isPatched(signal.metadata);
                MarkerDownDisposition markerDisposition = markerDownDisposition(
                        signal.markerDown, patchedMetadata);
                if (markerDisposition == MarkerDownDisposition.CONSUME_ONLY) {
                    return true;
                }
                if (markerDisposition == MarkerDownDisposition.ARM_SESSION) {
                    boolean actionsAdmitted = isEnabled();
                    Object tracker = latestGlobeTracker.get();
                    WeakReference<InputMethodService> serviceReference =
                            new WeakReference<>(service);
                    session = new GboardGlobeDragSession(
                            ++nextSessionId,
                            tracker,
                            shortcut -> actionsAdmitted && isEnabled()
                                    && performShortcut(serviceReference, shortcut),
                            RELEASE_EVENT_GRACE_MS);
                    logInfo("session=" + session.id + " armed");
                    return true;
                }
                if (session == null && !patchedMetadata) {
                    return false;
                }
                if (signal.markerUp && patchedMetadata) {
                    return false;
                }
                if (session == null) {
                    return false;
                }

                if ("LONG_PRESS".equals(signal.actionType)) {
                    if (session.state.isFailedActive()) {
                        return true;
                    }
                    if (isPatched(signal.metadata)) {
                        session = null;
                        return false;
                    }
                    if (session.state.isAwaitingTerminal(now)) {
                        return consumeTargetLocked(
                                port, signal.metadata, signal.selectedCode, now);
                    }
                    if (session.state.isActive()) {
                        session.state.onTargetLongPress();
                        return true;
                    }
                }

                if (!"PRESS".equals(signal.actionType)) {
                    return false;
                }
                if (session.state.isFailedActive()) {
                    if (signal.languageSwitch && isPatched(signal.metadata) && !signal.chord) {
                        session.abort();
                        session = null;
                        return false;
                    }
                    return signal.chord;
                }
                if (signal.languageSwitch && isPatched(signal.metadata)) {
                    if (signal.chord && (session.state.isAwaitingReplay(now)
                            || session.state.isAwaitingTerminal(now))) {
                        session.state.onReplayConsumed();
                        session = null;
                        return true;
                    }
                    session = null;
                    return false;
                }
                if (!signal.chord) {
                    return false;
                }
                if (!session.state.canConsumeTerminal(now)) {
                    return session.state.isAwaitingReplay(now);
                }
                return consumeTargetLocked(port, signal.metadata, signal.selectedCode, now);
            }
        } catch (Throwable throwable) {
            boolean consumed = failClosed(null, "input exception");
            logError("input event failed", throwable);
            return consumed;
        }
    }

    public static boolean maybeHandleGestureDispatch(Object tracker,
            Object actionType, Object entry, Object metadata) {
        if (actionType == null || entry == null || metadata == null) {
            return false;
        }
        try {
            GboardGlobeDragPort port = portFor(metadata);
            GboardGlobeDragPort.GestureSignal signal =
                    port.inspectGesture(actionType, entry, metadata);
            long now = SystemClock.uptimeMillis();
            synchronized (LOCK) {
                expireSession(now);
                if (session == null || !"PRESS".equals(signal.actionType)) {
                    return false;
                }
                if (session.tracker != null && tracker != null && session.tracker != tracker) {
                    return false;
                }
                if (session.state.isFailedActive()) {
                    return true;
                }
                if (!session.state.canConsumeTerminal(now)) {
                    if (signal.languageSwitch && isPatched(signal.metadata)) {
                        return false;
                    }
                    return session.state.isAwaitingReplay(now)
                            && session.shouldConsumeGestureReplay(tracker, now);
                }
                if (signal.languageSwitch && isPatched(signal.metadata)) {
                    return false;
                }
                return consumeTargetLocked(port, signal.metadata, signal.selectedCode, now);
            }
        } catch (Throwable throwable) {
            boolean consumed = failClosed(tracker, "gesture exception");
            logError("gesture dispatch failed", throwable);
            return consumed;
        }
    }

    private static boolean consumeTargetLocked(GboardGlobeDragPort port,
            Object metadata, int selectedCode, long now) throws Throwable {
        GboardGlobeDragPort.TargetSignal target =
                port.inspectTerminalTarget(metadata, selectedCode);
        if (!target.terminalCandidate) {
            return true;
        }
        GboardEditingShortcutPolicy.Shortcut resolvedShortcut =
                session.resolveTerminalShortcut(target);
        GboardGlobeDragSession.CommitResult result =
                session.commitTarget(resolvedShortcut, now);
        if (result.failure != null) {
            logError("editor action failed", result.failure);
        }
        logInfo("session=" + session.id + " consumed shortcut="
                + (result.shortcut == null ? "none" : result.shortcut.debugName)
                + " success=" + result.actionSucceeded);
        return true;
    }

    public static void onPointerOwner(Object tracker, View softKeyView) {
        if (tracker == null || softKeyView == null) {
            return;
        }
        try {
            GboardGlobeDragPort port = portFor(tracker);
            Object metadata = port.extractSoftKeyMetadata(softKeyView);
            int activeSessionId = 0;
            String pressText = null;
            synchronized (LOCK) {
                long now = SystemClock.uptimeMillis();
                expireSession(now);
                if (isPatched(metadata)) {
                    latestGlobeTracker = new WeakReference<>(tracker);
                    if (session != null && session.tracker == null) {
                        session.tracker = tracker;
                    }
                    if (session != null
                            && (session.tracker == null || session.tracker == tracker)) {
                        session.onGlobeOwner();
                    }
                    return;
                }
                if (session == null || !session.state.canAcceptTargetOwner(now)
                        || (session.tracker != null && session.tracker != tracker)) {
                    return;
                }
                if (session.tracker == null) {
                    session.tracker = tracker;
                }
                GboardGlobeDragPort.TargetSignal target =
                        port.inspectPointerTarget(softKeyView, metadata);
                session.claimTarget(target);
                activeSessionId = session.id;
                pressText = target.pressText;
            }
            postLongPressCancellation(port, tracker, softKeyView, activeSessionId, pressText);
        } catch (Throwable throwable) {
            failClosed(tracker, "pointer owner exception");
            logError("pointer owner failed", throwable);
        }
    }

    private static void postLongPressCancellation(GboardGlobeDragPort port,
            Object tracker, View view, int sessionId, String pressText) {
        WeakReference<Object> trackerReference = new WeakReference<>(tracker);
        boolean posted = view.post(() -> {
            Object currentTracker = trackerReference.get();
            if (currentTracker == null) {
                return;
            }
            try {
                synchronized (LOCK) {
                    long now = SystemClock.uptimeMillis();
                    expireSession(now);
                    if (session == null || session.id != sessionId
                            || !session.state.canAcceptTargetOwner(now)
                            || (session.tracker != null && session.tracker != currentTracker)) {
                        return;
                    }
                    if (port.cancelScheduledLongPress(currentTracker)) {
                        logInfo("session=" + session.id
                                + " cancelled target long press=" + pressText);
                    }
                }
            } catch (Throwable throwable) {
                failClosed(currentTracker, "deferred long-press cancellation exception");
                logError("deferred long-press cancellation failed", throwable);
            }
        });
        if (!posted) {
            failClosed(tracker, "long-press cancellation post rejected");
        }
    }

    public static void onPointerFinish(Object tracker) {
        if (tracker == null) {
            return;
        }
        try {
            synchronized (LOCK) {
                if (latestGlobeTracker.get() == tracker) {
                    latestGlobeTracker = new WeakReference<>(null);
                }
                if (session == null || (session.tracker != null && session.tracker != tracker)) {
                    return;
                }
                long now = SystemClock.uptimeMillis();
                if (session.hasActionableClaim()) {
                    GboardGlobeDragSession.CommitResult result =
                            session.commitClaimedTarget(now);
                    if (result.failure != null) {
                        logError("pointer-finish editor action failed", result.failure);
                    }
                    return;
                }
                session.onPointerFinish(tracker, now);
            }
        } catch (Throwable throwable) {
            failClosed(tracker, "pointer finish exception");
            logError("pointer finish failed", throwable);
        }
    }

    public static void onPointerCancel(Object tracker) {
        if (tracker == null) {
            return;
        }
        try {
            synchronized (LOCK) {
                if (latestGlobeTracker.get() == tracker) {
                    latestGlobeTracker = new WeakReference<>(null);
                }
                if (session == null || (session.tracker != null && session.tracker != tracker)) {
                    return;
                }
                if (session.retainAcrossPointerCleanup(
                        tracker, SystemClock.uptimeMillis())) {
                    return;
                }
                session.abort();
                session = null;
            }
        } catch (Throwable throwable) {
            failClosed(tracker, "pointer cancel exception");
            logError("pointer cancel failed", throwable);
        }
    }

    private static boolean performShortcut(WeakReference<InputMethodService> serviceReference,
            GboardEditingShortcutPolicy.Shortcut shortcut) {
        InputMethodService service = serviceReference == null ? null : serviceReference.get();
        InputConnection connection = service == null
                ? null : service.getCurrentInputConnection();
        if (connection == null || shortcut == null) {
            return false;
        }
        if (shortcut == GboardEditingShortcutPolicy.Shortcut.UNDO) {
            return sendControlChord(connection, KeyEvent.KEYCODE_Z);
        }
        if (shortcut == GboardEditingShortcutPolicy.Shortcut.REDO) {
            return sendControlChord(connection, KeyEvent.KEYCODE_Y);
        }
        if (!GboardEditingShortcutDispatchGuard.shouldDispatchContextMenuAction(
                connection, shortcut.contextMenuActionId)) {
            return false;
        }
        return connection.performContextMenuAction(shortcut.contextMenuActionId);
    }

    private static boolean sendControlChord(InputConnection connection, int keyCode) {
        return sendControlChord(connection, keyCode, SystemClock.uptimeMillis());
    }

    static boolean sendControlChord(InputConnection connection, int keyCode, long now) {
        int ctrlMetaState = KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        boolean ctrlDownAttempted = false;
        boolean keyDownAttempted = false;
        boolean keyUpCompleted = false;
        boolean ctrlUpCompleted = false;
        try {
            ctrlDownAttempted = true;
            boolean handled = connection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0));
            keyDownAttempted = true;
            handled &= connection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_DOWN, keyCode, 0, ctrlMetaState));
            handled &= connection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_UP, keyCode, 0, ctrlMetaState));
            keyUpCompleted = true;
            handled &= connection.sendKeyEvent(new KeyEvent(
                    now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0));
            ctrlUpCompleted = true;
            return handled;
        } finally {
            if (keyDownAttempted && !keyUpCompleted) {
                sendKeyEventBestEffort(connection, new KeyEvent(
                        now, now, KeyEvent.ACTION_UP, keyCode, 0, ctrlMetaState));
            }
            if (ctrlDownAttempted && !ctrlUpCompleted) {
                sendKeyEventBestEffort(connection, new KeyEvent(
                        now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0));
            }
        }
    }

    static MarkerDownDisposition markerDownDisposition(
            boolean markerDown, boolean patchedMetadata) {
        if (!markerDown) {
            return MarkerDownDisposition.PASS_THROUGH;
        }
        return patchedMetadata
                ? MarkerDownDisposition.ARM_SESSION
                : MarkerDownDisposition.CONSUME_ONLY;
    }

    private static void sendKeyEventBestEffort(InputConnection connection, KeyEvent event) {
        try {
            connection.sendKeyEvent(event);
        } catch (Throwable ignored) {
            // Modifier cleanup must not escape into the keyboard path.
        }
    }

    private static GboardGlobeDragPort portFor(Object targetObject) throws Throwable {
        if (targetObject == null) {
            throw new IllegalArgumentException("Target object is required");
        }
        ClassLoader classLoader = targetObject.getClass().getClassLoader();
        synchronized (PORTS) {
            WeakReference<GboardGlobeDragPort> reference = PORTS.get(classLoader);
            GboardGlobeDragPort cached = reference == null ? null : reference.get();
            if (cached != null) {
                return cached;
            }
            GboardGlobeDragPort created = GboardGlobeDragPortFactory.create(classLoader);
            PORTS.put(classLoader, new WeakReference<>(created));
            return created;
        }
    }

    private static boolean failClosed(Object tracker, String reason) {
        synchronized (LOCK) {
            if (session == null || (tracker != null
                    && session.tracker != null && session.tracker != tracker)) {
                return false;
            }
            session.failClosed(SystemClock.uptimeMillis());
            logInfo("session=" + session.id + " consumed as no-op: " + reason);
            return true;
        }
    }

    private static void expireSession(long now) {
        if (session != null && session.state.expire(now)) {
            session = null;
        }
    }

    private static boolean isPatched(Object metadata) {
        return metadata != null && Boolean.TRUE.equals(PATCHED_MARKERS.get(metadata));
    }

    private static Object metadataForBind(boolean enabled, Object metadata) {
        if (enabled || metadata == null) {
            return metadata;
        }
        WeakReference<Object> originalReference = ORIGINAL_BY_PATCHED.get(metadata);
        Object original = originalReference == null ? null : originalReference.get();
        return original == null ? metadata : original;
    }

    private static boolean isEnabled() {
        try {
            return GboardLongPressQuickActionsRuntimeSettings.isGlobeDragEnabled();
        } catch (Throwable throwable) {
            logError("enablement check failed", throwable);
            return false;
        }
    }

    private static void clearDisabledState() {
        synchronized (LOCK) {
            session = null;
            latestGlobeTracker = new WeakReference<>(null);
        }
    }

    private static void logInfo(String message) {
        try {
            if (INFO_LOG_COUNT.getAndIncrement() < LOG_LIMIT) {
                Log.i(TAG, LOG_PREFIX + message);
            }
        } catch (Throwable ignored) {
            // Logging must not alter owned gesture behavior.
        }
    }

    private static void logError(String message, Throwable throwable) {
        try {
            if (ERROR_LOG_COUNT.getAndIncrement() < 8) {
                Log.w(TAG, LOG_PREFIX + message, throwable);
            }
        } catch (Throwable ignored) {
            // Error reporting must not alter fail-closed behavior.
        }
    }

}
