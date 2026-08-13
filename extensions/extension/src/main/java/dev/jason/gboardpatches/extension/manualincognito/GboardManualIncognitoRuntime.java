package dev.jason.gboardpatches.extension.manualincognito;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class GboardManualIncognitoRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-manual-incognito-17.7.7] ";
    private static final AtomicLong GENERATION = new AtomicLong(0L);
    private static final AtomicBoolean STOCK_SESSION_RESTART_PENDING =
            new AtomicBoolean(false);
    private static final AtomicBoolean KEYBOARD_REOPEN_AFTER_HIDE_PENDING =
            new AtomicBoolean(false);

    private static volatile GboardManualIncognitoSessionPolicy activeSession =
            GboardManualIncognitoSessionPolicy.defaults();
    private static volatile GboardManualIncognitoSettings.RequestedPolicy requestedPolicy =
            GboardManualIncognitoSettings.RequestedPolicy.defaults();
    private static volatile WeakReference<InputMethodService> activeInputMethodService =
            new WeakReference<>(null);

    private GboardManualIncognitoRuntime() {
    }

    public static void onInputStarting(Object inputMethodService, EditorInfo editorInfo) {
        STOCK_SESSION_RESTART_PENDING.set(false);
        latchInputSession(inputMethodService, editorInfo);
    }

    public static void onInputViewStarting(Object inputMethodService, EditorInfo editorInfo) {
        maybeRestartStockInputSession(inputMethodService, editorInfo);
        latchInputSession(inputMethodService, editorInfo);
    }

    public static void onInputWindowHidden(Object inputMethodService) {
        if (!(inputMethodService instanceof InputMethodService service)
                || !KEYBOARD_REOPEN_AFTER_HIDE_PENDING.compareAndSet(true, false)) {
            return;
        }
        new Handler(service.getMainLooper()).post(() -> {
            try {
                service.requestShowSelf(0);
            } catch (Throwable failure) {
                logFailure("keyboard reopen after toggle failed", failure);
            }
        });
    }

    public static Object includeAccessPointInOrderCatalog(Object original) {
        try {
            return GboardManualIncognitoAccessPoint1777Support.includeToken(original);
        } catch (Throwable failure) {
            logFailure("failed to extend Access Point order catalog", failure);
            return original;
        }
    }

    public static boolean applyIncognitoPredicate(boolean stockIncognito) {
        return stockIncognito || activeSession.manualApplied;
    }

    public static boolean applyClipboardIncognitoPolicy(boolean stockIncognito) {
        GboardManualIncognitoSessionPolicy session = activeSession;
        boolean effective = GboardManualIncognitoPolicy.effectiveIncognito(
                stockIncognito || session.stockRequestedAtStart,
                session.manualApplied);
        return GboardManualIncognitoPolicy.blocked(
                effective, session.allowClipboardApplied);
    }

    public static boolean applyVoiceIncognitoPolicy(boolean stockIncognito) {
        GboardManualIncognitoSessionPolicy session = activeSession;
        boolean effective = GboardManualIncognitoPolicy.effectiveIncognito(
                stockIncognito || session.stockRequestedAtStart,
                session.manualApplied);
        return GboardManualIncognitoPolicy.blocked(effective, session.allowVoiceApplied);
    }

    public static boolean applyDictationIncognitoFlag(boolean stockIncognito) {
        GboardManualIncognitoSessionPolicy session = activeSession;
        if (!session.allowVoiceApplied) {
            return stockIncognito;
        }
        return GboardManualIncognitoPolicy.effectiveIncognito(
                session.stockRequestedAtStart,
                session.manualApplied)
                ? false
                : stockIncognito;
    }

    public static void afterAccessPointControllerCreated(Object controller, Context context) {
        try {
            GboardManualIncognitoSettings.RequestedPolicy requested =
                    GboardManualIncognitoSettings.read(context);
            requestedPolicy = requested;
            GboardManualIncognitoAccessPoint1777Support.register(
                    controller, context, requested);
        } catch (Throwable failure) {
            logFailure("Access Point registration failed", failure);
        }
    }

    static void toggleManualRequested(Context context, Object controller) {
        if (context == null) {
            return;
        }
        try {
            GboardManualIncognitoSettings.ToggleResult result =
                    GboardManualIncognitoSettings.toggleManualRequested(context);
            if (!result.success) {
                return;
            }
            requestedPolicy = requestedPolicy.withManualRequested(result.requested);
            STOCK_SESSION_RESTART_PENDING.set(true);
            GboardManualIncognitoAccessPoint1777Support.register(
                    controller, context, requestedPolicy);
            restartActiveKeyboard();
        } catch (Throwable failure) {
            logFailure("toolbar toggle failed", failure);
        }
    }

    private static void latchInputSession(Object inputMethodService, EditorInfo editorInfo) {
        try {
            if (inputMethodService instanceof InputMethodService service) {
                activeInputMethodService = new WeakReference<>(service);
            }
            Context context = inputMethodService instanceof Context
                    ? (Context) inputMethodService
                    : null;
            GboardManualIncognitoSettings.RequestedPolicy requested =
                    context == null
                            ? GboardManualIncognitoSettings.RequestedPolicy.defaults()
                            : GboardManualIncognitoSettings.read(context);
            boolean enabled = requested.featureEnabled;
            GboardManualIncognitoSessionPolicy session =
                    new GboardManualIncognitoSessionPolicy(
                            enabled && requested.manualRequested,
                            enabled && requested.allowClipboardRequested,
                            enabled && requested.allowVoiceRequested,
                            GboardManualIncognitoPolicy.stockRequested(editorInfo),
                            GENERATION.incrementAndGet());
            requestedPolicy = requested;
            activeSession = session;
            GboardManualIncognitoAccessPoint1777Support.refreshRegisteredControllers(requested);
            Log.d(TAG, LOG_PREFIX + "latched generation=" + session.generation
                    + ", manual=" + session.manualApplied
                    + ", stock=" + session.stockRequestedAtStart
                    + ", clipboardAllowed=" + session.allowClipboardApplied
                    + ", voiceAllowed=" + session.allowVoiceApplied);
        } catch (Throwable failure) {
            logFailure("session latch failed", failure);
        }
    }

    private static void maybeRestartStockInputSession(
            Object inputMethodService, EditorInfo editorInfo) {
        if (!(inputMethodService instanceof InputMethodService service)
                || editorInfo == null
                || !STOCK_SESSION_RESTART_PENDING.compareAndSet(true, false)) {
            return;
        }
        try {
            service.onStartInput(editorInfo, true);
        } catch (Throwable failure) {
            STOCK_SESSION_RESTART_PENDING.set(true);
            logFailure("stock input session restart failed", failure);
        }
    }

    private static void restartActiveKeyboard() {
        InputMethodService service = activeInputMethodService.get();
        if (service == null) {
            return;
        }
        Runnable hide = () -> {
            KEYBOARD_REOPEN_AFTER_HIDE_PENDING.set(true);
            try {
                service.requestHideSelf(0);
            } catch (Throwable failure) {
                KEYBOARD_REOPEN_AFTER_HIDE_PENDING.set(false);
                logFailure("keyboard hide after toggle failed", failure);
            }
        };
        if (Looper.myLooper() == service.getMainLooper()) {
            hide.run();
        } else {
            new Handler(service.getMainLooper()).post(hide);
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, LOG_PREFIX + message, failure);
        } catch (Throwable ignored) {
            // Logging must never affect the keyboard path.
        }
    }

    static GboardManualIncognitoSessionPolicy sessionForTests() {
        return activeSession;
    }

    static void setSessionForTests(GboardManualIncognitoSessionPolicy session) {
        activeSession = session == null
                ? GboardManualIncognitoSessionPolicy.defaults()
                : session;
    }
}
