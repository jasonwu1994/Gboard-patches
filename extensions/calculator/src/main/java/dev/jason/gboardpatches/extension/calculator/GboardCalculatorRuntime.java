package dev.jason.gboardpatches.extension.calculator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/** Coordinates bounded calculator evaluation with the active editor session. */
public final class GboardCalculatorRuntime {
    private static final int TEXT_LOOKBACK = 256;
    private static final Object LOCK = new Object();
    private static final GboardCalculatorSessionState SESSION_STATE =
            new GboardCalculatorSessionState();
    private static WeakReference<InputMethodService> activeService = new WeakReference<>(null);
    private static WeakReference<View> activeInputView = new WeakReference<>(null);
    private static WeakReference<GboardCalculatorSuggestionView.Handle> activeSuggestion =
            new WeakReference<>(null);
    private static WeakReference<GboardCalculatorDisplaySnapshot> activeDisplay =
            new WeakReference<>(null);
    private static boolean enabledForInputView;

    private GboardCalculatorRuntime() {
    }

    public static void onInputViewStarted(Object receiver, boolean enabled) {
        GboardCalculatorSafety.run(() -> {
            if (!(receiver instanceof InputMethodService service)) {
                return;
            }
            synchronized (LOCK) {
                SESSION_STATE.beginInputView();
                activeService = new WeakReference<>(service);
                activeInputView.clear();
                enabledForInputView = enabled;
            }
            hideSuggestion();
        });
    }

    public static void onSelectionUpdated(Object receiver, View inputView,
            int selectionStart, int selectionEnd) {
        GboardCalculatorSafety.run(() -> {
            if (!(receiver instanceof InputMethodService service)) {
                synchronized (LOCK) {
                    SESSION_STATE.updateSelection(false);
                }
                hideSuggestion();
                return;
            }
            boolean collapsed = selectionStart >= 0 && selectionStart == selectionEnd;
            synchronized (LOCK) {
                activeService = new WeakReference<>(service);
                activeInputView = new WeakReference<>(inputView);
                SESSION_STATE.updateSelection(collapsed);
                if (!enabledForInputView || !collapsed) {
                    hideSuggestion();
                    return;
                }
            }
            postToMain(() -> refreshSuggestion(service, inputView));
        });
    }

    public static void onInputWindowHidden() {
        GboardCalculatorSafety.run(() -> {
            hideSuggestion();
            synchronized (LOCK) {
                SESSION_STATE.endInputView();
                activeService.clear();
                activeInputView.clear();
                enabledForInputView = false;
            }
        });
    }

    static void refreshSuggestion(InputMethodService service, View inputView) {
        try {
            long sessionGeneration;
            synchronized (LOCK) {
                sessionGeneration = SESSION_STATE.currentGeneration();
                if (!enabledForInputView || activeService.get() != service
                        || activeInputView.get() != inputView
                        || !SESSION_STATE.canAccept(sessionGeneration)) {
                    hideSuggestionNow();
                    return;
                }
            }
            InputConnection connection = service != null
                    ? service.getCurrentInputConnection() : null;
            CharSequence beforeCursor = connection != null
                    ? connection.getTextBeforeCursor(TEXT_LOOKBACK, 0) : null;
            GboardCalculatorSuggestion.Candidate candidate =
                    GboardCalculatorSuggestion.fromTextBeforeCursor(beforeCursor);
            if (candidate == null) {
                hideSuggestionNow();
                return;
            }
            GboardCalculatorSuggestionView.Placement placement =
                    GboardCalculatorSuggestionView.resolvePlacement(inputView);
            if (placement == null) {
                hideSuggestionNow();
                return;
            }
            GboardCalculatorSuggestionView.Handle currentHandle = activeSuggestion.get();
            GboardCalculatorDisplaySnapshot currentDisplay = activeDisplay.get();
            if (currentHandle != null && currentDisplay != null
                    && currentHandle.isAttachedTo(placement.host())
                    && currentDisplay.matchesCurrent(
                            service, inputView, connection, sessionGeneration, candidate)) {
                return;
            }

            hideSuggestionNow();
            GboardCalculatorDisplaySnapshot display = new GboardCalculatorDisplaySnapshot(
                    service, placement.host(), connection, sessionGeneration, candidate);
            GboardCalculatorSuggestionView.Handle handle =
                    GboardCalculatorSuggestionView.show(
                            placement,
                            candidate,
                            new GboardCalculatorSuggestionView.Actions() {
                                @Override
                                public void accept(boolean insertEquation) {
                                    acceptSuggestion(display, insertEquation);
                                }

                                @Override
                                public void copy() {
                                    copySuggestion(display.service(), display.candidate());
                                }
                            });
            synchronized (LOCK) {
                if (!SESSION_STATE.canAccept(sessionGeneration)
                        || activeService.get() != service
                        || activeInputView.get() != inputView) {
                    handle.close();
                    return;
                }
                activeDisplay = new WeakReference<>(display);
                activeSuggestion = new WeakReference<>(handle);
            }
        } catch (Throwable ignored) {
            hideSuggestionNow();
        }
    }

    private static void acceptSuggestion(GboardCalculatorDisplaySnapshot shown,
            boolean insertEquation) {
        try {
            InputMethodService service;
            View inputView;
            synchronized (LOCK) {
                if (shown == null || !SESSION_STATE.canAccept(shown.sessionGeneration())) {
                    hideSuggestionNow();
                    return;
                }
                service = activeService.get();
                inputView = activeInputView.get();
            }
            InputConnection connection = service != null
                    ? service.getCurrentInputConnection() : null;
            if (!shown.matchesCurrent(
                    service, inputView, connection, shown.sessionGeneration())) {
                hideSuggestionNow();
                return;
            }
            CharSequence beforeCursor = connection.getTextBeforeCursor(TEXT_LOOKBACK, 0);
            GboardCalculatorSuggestion.Candidate current =
                    GboardCalculatorSuggestion.fromTextBeforeCursor(beforeCursor);
            if (!shown.matchesCandidate(current)) {
                refreshSuggestion(service, inputView);
                return;
            }
            connection.beginBatchEdit();
            try {
                if (connection.deleteSurroundingText(current.getDeleteBeforeCursorCount(), 0)) {
                    connection.commitText(
                            insertEquation ? current.getEquation() : current.getResult(), 1);
                }
            } finally {
                connection.endBatchEdit();
            }
            hideSuggestionNow();
        } catch (Throwable ignored) {
            hideSuggestionNow();
        }
    }

    private static void copySuggestion(Context context,
            GboardCalculatorSuggestion.Candidate candidate) {
        try {
            ClipboardManager clipboard = context != null
                    ? (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE) : null;
            if (clipboard == null || candidate == null) {
                return;
            }
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Calculator result", candidate.getResult()));
            Toast.makeText(context, GboardCalculatorText.localized(
                    "已複製計算結果", "Calculator result copied"),
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            // Clipboard access must never escape into Gboard.
        }
    }

    private static void hideSuggestion() {
        postToMain(GboardCalculatorRuntime::hideSuggestionNow);
    }

    private static void hideSuggestionNow() {
        GboardCalculatorSuggestionView.Handle current;
        synchronized (LOCK) {
            current = activeSuggestion.get();
            activeSuggestion.clear();
            activeDisplay.clear();
        }
        if (current != null) {
            current.close();
        }
        View inputView = activeInputView.get();
        if (inputView instanceof FrameLayout host) {
            GboardCalculatorSuggestionView.removeFrom(host);
        }
    }

    private static void postToMain(Runnable action) {
        if (action == null) {
            return;
        }
        GboardCalculatorSafety.run(() -> {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                GboardCalculatorSafety.run(action);
            } else {
                new Handler(Looper.getMainLooper()).post(
                        () -> GboardCalculatorSafety.run(action));
            }
        });
    }
}
