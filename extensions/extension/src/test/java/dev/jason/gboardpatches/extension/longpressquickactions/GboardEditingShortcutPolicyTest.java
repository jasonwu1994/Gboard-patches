package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardEditingShortcutPolicyTest {
    private static final int TYPING_PULSE = -10043;

    @Test
    public void latinChordMappingMatchesLongPressEditingShortcuts() {
        assertShortcut("a", 'a', GboardEditingShortcutPolicy.Shortcut.SELECT_ALL);
        assertShortcut("Z", 'Z', GboardEditingShortcutPolicy.Shortcut.UNDO);
        assertShortcut("x", TYPING_PULSE,
                GboardEditingShortcutPolicy.Shortcut.CUT);
        assertShortcut("C", TYPING_PULSE,
                GboardEditingShortcutPolicy.Shortcut.COPY);
        assertShortcut("v", 'v', GboardEditingShortcutPolicy.Shortcut.PASTE);
        assertShortcut("Y", 'Y', GboardEditingShortcutPolicy.Shortcut.REDO);
    }

    @Test
    public void latinChordMappingIsClosedByDefault() {
        Assert.assertNull(GboardEditingShortcutPolicy.resolveLatinChord(
                "e", 'e', TYPING_PULSE));
        Assert.assertNull(GboardEditingShortcutPolicy.resolveLatinChord(
                "a", 'e', TYPING_PULSE));
        Assert.assertNull(GboardEditingShortcutPolicy.resolveLatinChord(
                "@", TYPING_PULSE, TYPING_PULSE));
        Assert.assertNull(GboardEditingShortcutPolicy.resolveLatinChord(
                null, 'a', TYPING_PULSE));
    }

    @Test
    public void shortcutsExposeEditorContextActions() {
        Assert.assertEquals(android.R.id.selectAll,
                GboardEditingShortcutPolicy.Shortcut.SELECT_ALL.contextMenuActionId);
        Assert.assertEquals(android.R.id.undo,
                GboardEditingShortcutPolicy.Shortcut.UNDO.contextMenuActionId);
        Assert.assertEquals(android.R.id.cut,
                GboardEditingShortcutPolicy.Shortcut.CUT.contextMenuActionId);
        Assert.assertEquals(android.R.id.copy,
                GboardEditingShortcutPolicy.Shortcut.COPY.contextMenuActionId);
        Assert.assertEquals(android.R.id.paste,
                GboardEditingShortcutPolicy.Shortcut.PASTE.contextMenuActionId);
        Assert.assertEquals(android.R.id.redo,
                GboardEditingShortcutPolicy.Shortcut.REDO.contextMenuActionId);
    }

    private static void assertShortcut(String pressText, int selectedCode,
            GboardEditingShortcutPolicy.Shortcut expected) {
        Assert.assertSame(expected, GboardEditingShortcutPolicy.resolveLatinChord(
                pressText, selectedCode, TYPING_PULSE));
    }
}
