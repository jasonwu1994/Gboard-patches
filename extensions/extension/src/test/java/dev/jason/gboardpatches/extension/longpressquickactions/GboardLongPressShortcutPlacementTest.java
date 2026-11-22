package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardLongPressShortcutPlacementTest {
    @Test
    public void normalizeAcceptsOnlyFirstOrLastAndDefaultsToFirst() {
        Assert.assertEquals("first", GboardLongPressShortcutPlacement.normalize(null));
        Assert.assertEquals("first", GboardLongPressShortcutPlacement.normalize("bogus"));
        Assert.assertEquals("first", GboardLongPressShortcutPlacement.normalize(Boolean.FALSE));
        Assert.assertEquals("first", GboardLongPressShortcutPlacement.normalize(" FIRST "));
        Assert.assertEquals("last", GboardLongPressShortcutPlacement.normalize("Last"));
        Assert.assertTrue(GboardLongPressShortcutPlacement.isFirst(null));
        Assert.assertFalse(GboardLongPressShortcutPlacement.isFirst("last"));
    }

    @Test
    public void firstPutsShortcutAheadOfStockEntriesInOrder() {
        Object entries = GboardLongPressShortcutPlacement.entries(
                String.class, new Object[] {"\u00e0", "\u00e1", "\u00e2"}, "select-all", true);

        Assert.assertTrue(entries instanceof String[]);
        Assert.assertArrayEquals(
                new String[] {"select-all", "\u00e0", "\u00e1", "\u00e2"},
                (String[]) entries);
        Assert.assertArrayEquals(
                new String[] {null, "\u00e0", "\u00e1", "\u00e2"},
                GboardLongPressShortcutPlacement.labels(
                        new String[] {"\u00e0", "\u00e1", "\u00e2"}, 3, true));
        Assert.assertArrayEquals(
                new int[] {9, 1, 2, 3},
                GboardLongPressShortcutPlacement.icons(new int[] {1, 2, 3}, 3, 9, true));
    }

    @Test
    public void lastKeepsStockEntriesFirst() {
        Assert.assertArrayEquals(
                new String[] {"\u00e7", "copy"},
                (String[]) GboardLongPressShortcutPlacement.entries(
                        String.class, new Object[] {"\u00e7"}, "copy", false));
        Assert.assertArrayEquals(
                new String[] {"\u00e7", null},
                GboardLongPressShortcutPlacement.labels(new String[] {"\u00e7"}, 1, false));
        Assert.assertArrayEquals(
                new int[] {0, 9},
                GboardLongPressShortcutPlacement.icons(new int[] {0}, 1, 9, false));
    }

    @Test
    public void sharedLabelAndIconRepeatForEveryStockEntry() {
        Assert.assertArrayEquals(
                new String[] {null, "x", "x", "x"},
                GboardLongPressShortcutPlacement.labels(new String[] {"x"}, 3, true));
        Assert.assertArrayEquals(
                new String[] {"x", "x", "x", null},
                GboardLongPressShortcutPlacement.labels(new String[] {"x"}, 3, false));
        Assert.assertArrayEquals(
                new int[] {9, 5, 5},
                GboardLongPressShortcutPlacement.icons(new int[] {5}, 2, 9, true));
    }

    @Test
    public void keyWithoutLongPressGetsOnlyTheShortcut() {
        for (boolean first : new boolean[] {true, false}) {
            Assert.assertArrayEquals(
                    new String[] {"paste"},
                    (String[]) GboardLongPressShortcutPlacement.entries(
                            String.class, null, "paste", first));
            Assert.assertArrayEquals(
                    new String[] {null},
                    GboardLongPressShortcutPlacement.labels(new String[0], 0, first));
            Assert.assertArrayEquals(
                    new int[] {9},
                    GboardLongPressShortcutPlacement.icons(null, 0, 9, first));
        }
    }

    @Test
    public void shorterStockArraysLeaveMissingSlotsEmpty() {
        Assert.assertArrayEquals(
                new String[] {null, "a", "b", null},
                GboardLongPressShortcutPlacement.labels(new String[] {"a", "b"}, 3, true));
        Assert.assertArrayEquals(
                new int[] {1, 2, 0, 9},
                GboardLongPressShortcutPlacement.icons(new int[] {1, 2}, 3, 9, false));
    }
}
