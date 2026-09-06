package dev.jason.gboardpatches.extension.calculator;

/** Tracks the active editor session without depending on Android types. */
final class GboardCalculatorSessionState {
    private long generation;
    private boolean inputViewActive;
    private boolean selectionCollapsed;

    long beginInputView() {
        generation++;
        inputViewActive = true;
        selectionCollapsed = false;
        return generation;
    }

    void updateSelection(boolean collapsed) {
        selectionCollapsed = inputViewActive && collapsed;
    }

    void endInputView() {
        generation++;
        inputViewActive = false;
        selectionCollapsed = false;
    }

    long currentGeneration() {
        return generation;
    }

    boolean canAccept(long expectedGeneration) {
        return inputViewActive
                && selectionCollapsed
                && generation == expectedGeneration;
    }
}
