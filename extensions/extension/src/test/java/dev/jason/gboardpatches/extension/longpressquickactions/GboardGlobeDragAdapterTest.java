package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardGlobeDragAdapterTest {
    @Test
    public void syntheticMarkerEventsDoNotDependOnLazyChordSubtypeBinding() {
        Assert.assertFalse(GboardGlobeDragAdapter.requiresEventSubtype(true, false));
        Assert.assertFalse(GboardGlobeDragAdapter.requiresEventSubtype(false, true));
        Assert.assertTrue(GboardGlobeDragAdapter.requiresEventSubtype(false, false));
    }

    @Test
    public void privateViewNamesNormalizeAtTheVersionAdapterSeam() {
        Assert.assertSame(GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.SELECT_ALL,
                GboardGlobeDragAdapter.shortcutSlot("C01"));
        Assert.assertSame(GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.UNDO,
                GboardGlobeDragAdapter.shortcutSlot("B01"));
        Assert.assertSame(GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.REDO,
                GboardGlobeDragAdapter.shortcutSlot("D06"));
        Assert.assertNull(GboardGlobeDragAdapter.shortcutSlot("C02"));
        Assert.assertNull(GboardGlobeDragAdapter.shortcutSlot("B04_dup"));
    }

    @Test
    public void nonZhuyinTerminalDoesNotFallBackToSemanticLetterMapping() {
        Assert.assertNull(GboardGlobeDragAdapter.resolveTerminalShortcut(
                false,
                GboardEditingShortcutPolicy.Shortcut.REDO,
                null));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.UNDO,
                GboardGlobeDragAdapter.resolveTerminalShortcut(
                        false,
                        GboardEditingShortcutPolicy.Shortcut.REDO,
                        GboardEditingShortcutPolicy.Shortcut.UNDO));
        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.CUT,
                GboardGlobeDragAdapter.resolveTerminalShortcut(
                        true,
                        GboardEditingShortcutPolicy.Shortcut.CUT,
                        GboardEditingShortcutPolicy.Shortcut.COPY));
    }
}
