package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GboardLongPressQuickActionsPlacementRuntimeTest {
    @After
    public void clearSettings() {
        GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
    }

    @Test
    public void placementChangeRebuildsOnceAndReusesTheMatchingCache() {
        RecordingPatcher patcher = new RecordingPatcher();
        Object original = new Object();

        setPlacement(true);
        Patched first = (Patched) patch(patcher, original);
        Assert.assertTrue(first.shortcutFirst);
        Assert.assertSame(original, first.original);
        Assert.assertSame(first, patch(patcher, original));
        Assert.assertSame(first, patch(patcher, first));
        Assert.assertEquals(1, patcher.inserted.size());

        setPlacement(false);
        Patched last = (Patched) patch(patcher, first);
        Assert.assertFalse(last.shortcutFirst);
        Assert.assertSame(original, last.original);
        Assert.assertSame(last, patch(patcher, original));
        Assert.assertEquals(2, patcher.inserted.size());

        setPlacement(true);
        Patched firstAgain = (Patched) patch(patcher, last);
        Assert.assertTrue(firstAgain.shortcutFirst);
        Assert.assertSame(original, firstAgain.original);
        Assert.assertEquals(3, patcher.inserted.size());
    }

    @Test
    public void disablingRestoresOriginalForEitherPlacement() {
        RecordingPatcher patcher = new RecordingPatcher();
        Object original = new Object();
        setPlacement(false);
        Object patched = patch(patcher, original);

        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(
                false, false, false, true);

        Assert.assertSame(original, patch(patcher, patched));
        Assert.assertSame(original, patch(patcher, original));
        Assert.assertEquals(1, patcher.inserted.size());
    }

    @Test
    public void failedInsertKeepsStockMetadataAndIsNotRetriedAfterPlacementChange() {
        RecordingPatcher patcher = new RecordingPatcher();
        patcher.returnNull = true;
        Object original = new Object();

        setPlacement(true);
        Assert.assertSame(original, patch(patcher, original));
        setPlacement(false);
        Assert.assertSame(original, patch(patcher, original));
        Assert.assertEquals(1, patcher.inserted.size());
    }

    @Test
    public void patcherFailureDuringPlacementSwitchRestoresStockMetadataWithoutThrowing() {
        RecordingPatcher patcher = new RecordingPatcher();
        Object original = new Object();

        setPlacement(true);
        Object patchedFirst = patch(patcher, original);
        Assert.assertNotSame(original, patchedFirst);

        setPlacement(false);
        patcher.throwOnPatch = true;
        Assert.assertSame(original, patch(patcher, patchedFirst));
        Assert.assertEquals(2, patcher.inserted.size());
        Assert.assertSame(original, patch(patcher, original));
        Assert.assertEquals(2, patcher.inserted.size());
    }

    private static Object patch(RecordingPatcher patcher, Object metadata) {
        return GboardLongPressQuickActionsRuntime.maybePatchMetadataWithPatcher(metadata, patcher);
    }

    private static void setPlacement(boolean shortcutFirst) {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(
                true, false, false, shortcutFirst);
    }

    private static final class Patched {
        final Object original;
        final boolean shortcutFirst;

        Patched(Object original, boolean shortcutFirst) {
            this.original = original;
            this.shortcutFirst = shortcutFirst;
        }
    }

    private static final class RecordingPatcher
            implements GboardLongPressQuickActionsRuntime.MetadataPatcher {
        final List<Boolean> inserted = new ArrayList<>();
        boolean returnNull;
        boolean throwOnPatch;

        @Override
        public Object patch(Object metadata, boolean shortcutFirst) {
            inserted.add(shortcutFirst);
            if (throwOnPatch) {
                throw new IllegalStateException("metadata insert failed");
            }
            return returnNull ? null : new Patched(metadata, shortcutFirst);
        }
    }
}
