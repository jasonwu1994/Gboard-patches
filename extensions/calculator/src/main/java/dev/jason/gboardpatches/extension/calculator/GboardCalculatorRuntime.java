package dev.jason.gboardpatches.extension.calculator;

import android.inputmethodservice.InputMethodService;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;

/** Tracks the editor session and reads the calculation at the cursor, on Gboard's main thread. */
public final class GboardCalculatorRuntime {
    private static final int TEXT_LOOKBACK = 256;
    private static WeakReference<InputMethodService> activeService = new WeakReference<>(null);
    private static boolean enabledForInputView;
    private static boolean selectionCollapsed;

    private GboardCalculatorRuntime() {
    }

    public static void onInputViewStarted(Object receiver, boolean enabled) {
        if (!(receiver instanceof InputMethodService service)) {
            return;
        }
        activeService = new WeakReference<>(service);
        enabledForInputView = enabled;
        EditorInfo editor = service.getCurrentInputEditorInfo();
        selectionCollapsed = editor != null && editor.initialSelStart >= 0
                && editor.initialSelStart == editor.initialSelEnd;
        // The keyboard can reopen on a calculation, for example after the screen is unlocked.
        GboardCalculatorCandidates.refreshIfOutdated(currentCandidate());
    }

    public static void onSelectionUpdated(Object receiver, int selectionStart, int selectionEnd) {
        if (!(receiver instanceof InputMethodService service)) {
            return;
        }
        activeService = new WeakReference<>(service);
        selectionCollapsed = selectionStart >= 0 && selectionStart == selectionEnd;
        // Gboard can ask for suggestions before the editor has applied a keystroke.
        GboardCalculatorCandidates.refreshIfOutdated(currentCandidate());
    }

    public static void onInputWindowHidden() {
        activeService.clear();
        enabledForInputView = false;
        selectionCollapsed = false;
    }

    /** The calculation before the cursor in the active editor, or null if there is none. */
    static GboardCalculatorSuggestion.Candidate currentCandidate() {
        if (!enabledForInputView || !selectionCollapsed) {
            return null;
        }
        return candidateAt(connection());
    }

    /** Replaces the calculation before the cursor with its result or full equation. */
    static void acceptCurrent(boolean insertEquation) {
        InputConnection connection = connection();
        GboardCalculatorSuggestion.Candidate current = candidateAt(connection);
        if (current == null) {
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
    }

    private static InputConnection connection() {
        InputMethodService service = activeService.get();
        return service != null ? service.getCurrentInputConnection() : null;
    }

    private static GboardCalculatorSuggestion.Candidate candidateAt(InputConnection connection) {
        if (connection == null) {
            return null;
        }
        try {
            return GboardCalculatorSuggestion.fromTextBeforeCursor(
                    connection.getTextBeforeCursor(TEXT_LOOKBACK, 0));
        } catch (RuntimeException ignored) {
            // The editor went away; never let the calculator break Gboard.
            return null;
        }
    }
}
