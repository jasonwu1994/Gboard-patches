package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

public final class GboardLongPressQuickActionsInsertionTest {
    @Test
    public void insertsIconAtRequestedPositionWithoutReorderingStockIcons() {
        Assert.assertArrayEquals(new int[] {99, 10, 20, 30},
                GboardLongPressQuickActions1803ReflectionHandles.insertIcon(
                        new int[] {10, 20, 30}, 3, 0, 99));
        Assert.assertArrayEquals(new int[] {10, 20, 99, 30},
                GboardLongPressQuickActions1803ReflectionHandles.insertIcon(
                        new int[] {10, 20, 30}, 3, 2, 99));
        Assert.assertArrayEquals(new int[] {10, 20, 30, 99},
                GboardLongPressQuickActions1803ReflectionHandles.insertIcon(
                        new int[] {10, 20, 30}, 3, 3, 99));
    }

    @Test
    public void expandsSingleStockIconBeforeInsertion() {
        Assert.assertArrayEquals(new int[] {7, 99, 7, 7},
                GboardLongPressQuickActions1803ReflectionHandles.insertIcon(
                        new int[] {7}, 3, 1, 99));
    }

    @Test
    public void insertsNullLabelAndPreservesStockLabels() {
        Assert.assertArrayEquals(new String[] {"á", null, "à"},
                GboardLongPressQuickActions1803ReflectionHandles.insertLabel(
                        new String[] {"á", "à"}, 2, 1));
    }
}
