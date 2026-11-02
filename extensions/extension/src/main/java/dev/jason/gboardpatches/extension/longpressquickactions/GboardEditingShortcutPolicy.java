package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.KeyEvent;

/** Version-independent editing semantics shared by shortcut gesture carriers. */
public final class GboardEditingShortcutPolicy {
    private GboardEditingShortcutPolicy() {
    }

    public static Shortcut resolveLatinChord(String pressText, int selectedCode,
            int typingPulseCode) {
        Shortcut shortcut = resolveLatinKey(pressText);
        if (shortcut == null) {
            return null;
        }
        char key = shortcut.latinKey;
        return selectedCode == key
                || selectedCode == Character.toUpperCase(key)
                || selectedCode == shortcut.androidKeyCode
                || selectedCode == typingPulseCode
                ? shortcut : null;
    }

    public static Shortcut resolveLatinKey(String pressText) {
        if (pressText == null || pressText.length() != 1) {
            return null;
        }
        return switch (Character.toLowerCase(pressText.charAt(0))) {
            case 'a' -> Shortcut.SELECT_ALL;
            case 'z' -> Shortcut.UNDO;
            case 'x' -> Shortcut.CUT;
            case 'c' -> Shortcut.COPY;
            case 'v' -> Shortcut.PASTE;
            case 'y' -> Shortcut.REDO;
            default -> null;
        };
    }

    public enum Shortcut {
        SELECT_ALL('a', KeyEvent.KEYCODE_A, android.R.id.selectAll, "Select all"),
        UNDO('z', KeyEvent.KEYCODE_Z, android.R.id.undo, "Undo"),
        CUT('x', KeyEvent.KEYCODE_X, android.R.id.cut, "Cut"),
        COPY('c', KeyEvent.KEYCODE_C, android.R.id.copy, "Copy"),
        PASTE('v', KeyEvent.KEYCODE_V, android.R.id.paste, "Paste"),
        REDO('y', KeyEvent.KEYCODE_Y, android.R.id.redo, "Redo");

        public final char latinKey;
        public final int androidKeyCode;
        public final int contextMenuActionId;
        public final String debugName;

        Shortcut(char latinKey, int androidKeyCode, int contextMenuActionId,
                String debugName) {
            this.latinKey = latinKey;
            this.androidKeyCode = androidKeyCode;
            this.contextMenuActionId = contextMenuActionId;
            this.debugName = debugName;
        }
    }
}
