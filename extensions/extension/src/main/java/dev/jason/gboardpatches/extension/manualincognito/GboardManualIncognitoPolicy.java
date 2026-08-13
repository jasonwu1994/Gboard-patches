package dev.jason.gboardpatches.extension.manualincognito;

import android.view.inputmethod.EditorInfo;

public final class GboardManualIncognitoPolicy {
    static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000;

    private GboardManualIncognitoPolicy() {
    }

    static boolean stockRequested(EditorInfo editorInfo) {
        return editorInfo != null
                && (editorInfo.imeOptions & IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    static boolean effectiveIncognito(boolean stockRequested, boolean manualApplied) {
        return stockRequested || manualApplied;
    }

    static boolean blocked(boolean effectiveIncognito, boolean allowed) {
        return effectiveIncognito && !allowed;
    }
}
