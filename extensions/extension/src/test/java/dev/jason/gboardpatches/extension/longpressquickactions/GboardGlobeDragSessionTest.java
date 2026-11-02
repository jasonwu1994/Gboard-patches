package dev.jason.gboardpatches.extension.longpressquickactions;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class GboardGlobeDragSessionTest {
    private static final long GRACE_MS = 750L;
    private static final Object DEFAULT_METADATA_IDENTITY = new Object();

    @Test
    public void invalidClaimCommitsAsNoOpWithoutCallingEditor() {
        AtomicInteger calls = new AtomicInteger();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                1, new Object(), shortcut -> {
                    calls.incrementAndGet();
                    return true;
                }, GRACE_MS);
        session.claimTarget(target(1, "q", null));

        GboardGlobeDragSession.CommitResult result = session.commitClaimedTarget(1_000L);

        Assert.assertTrue(result.consumed);
        Assert.assertFalse(result.actionSucceeded);
        Assert.assertEquals(0, calls.get());
        Assert.assertTrue(session.state.isAwaitingReplay(1_500L));
    }

    @Test
    public void editorFailureStillConsumesAndCleansSession() {
        RuntimeException failure = new RuntimeException("editor failed");
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                2, new Object(), shortcut -> { throw failure; }, GRACE_MS);
        session.claimTarget(target(
                2, "a", GboardEditingShortcutPolicy.Shortcut.SELECT_ALL));

        GboardGlobeDragSession.CommitResult result = session.commitClaimedTarget(2_000L);

        Assert.assertTrue(result.consumed);
        Assert.assertFalse(result.actionSucceeded);
        Assert.assertSame(failure, result.failure);
        Assert.assertNull(session.tracker);
        Assert.assertTrue(session.state.isAwaitingReplay(2_500L));
    }

    @Test
    public void pointerFinishClearsMissingReplayTrackerRegression() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                3, tracker, shortcut -> true, GRACE_MS);
        session.commitTarget(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL, 3_000L);
        Assert.assertTrue(session.shouldConsumeGestureReplay(tracker, 3_100L));

        session.onPointerFinish(tracker, 3_200L);

        Assert.assertFalse(session.shouldConsumeGestureReplay(tracker, 3_300L));
        Assert.assertTrue(session.state.isAwaitingReplay(3_300L));
    }

    @Test
    public void invalidShiftEndpointSurvivesFinishThenResetUntilTerminal() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                4, tracker, shortcut -> true, GRACE_MS);
        session.state.onTargetOwner(null);

        Assert.assertFalse(session.hasActionableClaim());
        session.onPointerFinish(tracker, 4_000L);
        Assert.assertTrue(session.state.isAwaitingTerminal(4_100L));

        Assert.assertTrue(session.retainAcrossPointerCleanup(tracker, 4_100L));
        Assert.assertTrue(session.state.isAwaitingTerminal(4_200L));
        Assert.assertFalse(session.state.isComplete());
    }

    @Test
    public void activeSessionDoesNotSurviveExplicitPointerCancel() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                5, tracker, shortcut -> true, GRACE_MS);

        Assert.assertFalse(session.retainAcrossPointerCleanup(tracker, 5_000L));
        Assert.assertTrue(session.state.isActive());
    }

    @Test
    public void replayGraceSurvivesResetButDropsGestureTracker() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                6, tracker, shortcut -> true, GRACE_MS);
        session.commitTarget(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL, 6_000L);
        Assert.assertTrue(session.shouldConsumeGestureReplay(tracker, 6_100L));

        Assert.assertTrue(session.retainAcrossPointerCleanup(tracker, 6_200L));

        Assert.assertFalse(session.shouldConsumeGestureReplay(tracker, 6_300L));
        Assert.assertTrue(session.state.isAwaitingReplay(6_300L));
    }

    @Test
    public void physicalClaimOverridesSemanticTerminalShortcut() {
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                8, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(
                0x7f0b1a06, "y", GboardEditingShortcutPolicy.Shortcut.UNDO));

        GboardEditingShortcutPolicy.Shortcut resolved = session.resolveTerminalShortcut(target(
                0x7f0b1a06, "y", GboardEditingShortcutPolicy.Shortcut.REDO));

        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.UNDO, resolved);
    }

    @Test
    public void terminalWithEqualValuesButDifferentMetadataIdentityIsRejected() {
        Object claimedMetadata = new Object();
        Object terminalMetadata = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                13, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(
                claimedMetadata,
                0x7f0b1a06,
                "y",
                GboardEditingShortcutPolicy.Shortcut.UNDO));

        Assert.assertNull(session.resolveTerminalShortcut(target(
                terminalMetadata,
                0x7f0b1a06,
                "y",
                GboardEditingShortcutPolicy.Shortcut.UNDO)));
    }

    @Test
    public void clonedMetadataCanMatchTheSamePhysicalSlotButNotAnotherSlot() {
        Object claimedMetadata = new Object();
        Object terminalMetadata = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                14, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(
                claimedMetadata,
                GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.SELECT_ALL,
                100,
                "a",
                GboardEditingShortcutPolicy.Shortcut.SELECT_ALL));

        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL,
                session.resolveTerminalShortcut(target(
                        terminalMetadata,
                        GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.SELECT_ALL,
                        100,
                        "a",
                        null)));
        Assert.assertNull(session.resolveTerminalShortcut(target(
                terminalMetadata,
                GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.COPY,
                100,
                "a",
                null)));
    }

    @Test
    public void finalAlphabetTerminalCanReplaceANonTextShiftClaim() {
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                15, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(new Object(), null, 59, null, null));

        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL,
                session.resolveTerminalShortcut(target(
                        new Object(),
                        GboardGlobeDragPhysicalSlotPolicy.ShortcutSlot.SELECT_ALL,
                        100,
                        "a",
                        GboardEditingShortcutPolicy.Shortcut.SELECT_ALL)));
    }

    @Test
    public void physicalClaimSurvivesTerminalWithoutLanguageSpecificMapping() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                9, tracker, shortcut -> true, GRACE_MS);
        session.onPointerFinish(tracker, 8_900L);
        session.claimTarget(target(
                0x7f0b0e15, "м", GboardEditingShortcutPolicy.Shortcut.PASTE));

        GboardEditingShortcutPolicy.Shortcut resolved = session.resolveTerminalShortcut(target(
                0x7f0b0e15, "м", null));

        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.PASTE, resolved);
    }

    @Test
    public void lateCyrillicOwnerSequenceUsesFinalNoOpClaimAtTerminal() {
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                10, tracker, shortcut -> true, GRACE_MS);
        session.onPointerFinish(tracker, 10_000L);
        session.claimTarget(target(
                0x7f0b0e0a, "ч", GboardEditingShortcutPolicy.Shortcut.CUT));
        session.claimTarget(target(0x7f0b0e4a, "в", null));

        Assert.assertNull(session.resolveTerminalShortcut(target(
                0x7f0b0e4a, "в", null)));
        Assert.assertFalse(session.state.canCommitClaimedTargetOnPointerFinish());
    }

    @Test
    public void unsupportedOrMismatchedClaimSuppressesTerminalSemanticFallback() {
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                11, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(100, "q", null));

        Assert.assertNull(session.resolveTerminalShortcut(target(
                100, "q", GboardEditingShortcutPolicy.Shortcut.SELECT_ALL)));
        Assert.assertNull(session.resolveTerminalShortcut(target(
                101, "a", GboardEditingShortcutPolicy.Shortcut.SELECT_ALL)));
    }

    @Test
    public void returningToGlobeClearsPhysicalClaim() {
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                12, new Object(), shortcut -> true, GRACE_MS);
        session.claimTarget(target(
                100, "м", GboardEditingShortcutPolicy.Shortcut.PASTE));

        session.onGlobeOwner();

        Assert.assertSame(GboardEditingShortcutPolicy.Shortcut.SELECT_ALL,
                session.resolveTerminalShortcut(target(
                        101, "a", GboardEditingShortcutPolicy.Shortcut.SELECT_ALL)));
    }

    private static GboardGlobeDragPort.TargetSignal target(int keyId, String text,
            GboardEditingShortcutPolicy.Shortcut shortcut) {
        return target(DEFAULT_METADATA_IDENTITY, keyId, text, shortcut);
    }

    private static GboardGlobeDragPort.TargetSignal target(Object metadataIdentity,
            int keyId, String text, GboardEditingShortcutPolicy.Shortcut shortcut) {
        return target(metadataIdentity, null, keyId, text, shortcut);
    }

    private static GboardGlobeDragPort.TargetSignal target(Object metadataIdentity,
            Object claimIdentity, int keyId, String text,
            GboardEditingShortcutPolicy.Shortcut shortcut) {
        return new GboardGlobeDragPort.TargetSignal(
                metadataIdentity, claimIdentity,
                keyId, text, null, shortcut, text != null);
    }
}
