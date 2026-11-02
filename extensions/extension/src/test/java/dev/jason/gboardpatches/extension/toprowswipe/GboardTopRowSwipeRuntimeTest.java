package dev.jason.gboardpatches.extension.toprowswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

public final class GboardTopRowSwipeRuntimeTest {
    @After
    public void resetRuntimeState() {
        GboardTopRowSwipeRuntime.SESSIONS.clear();
        GboardTopRowSwipeRuntime.VISIBLE_TOP_ROW_KEYS.clear();
        GboardTopRowSwipeRuntime.ORIGINAL_KEY_METADATA_BY_PATCHED.clear();
        GboardTopRowSwipeRuntime.TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.clear();
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.STOCK);
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                GboardTopRowSwipeRuntime.TopRowPage.STOCK);
        GboardTopRowSwipeRuntime.clearSettingsSnapshotCache();
    }

    @Test
    public void resolvesZhuyinAndEnglishQwertyTopRowSlots() {
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        0,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN,
                        "ㄅ"));
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        9,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY,
                        "p"));
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        0,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.NONE,
                        "q"));
    }

    @Test
    public void rejectsNonTopRowEnglishQwertyLetters() {
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.NONE,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        0,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY,
                        "a"));
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.NONE,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        9,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY,
                        "m"));
    }

    @Test
    public void zhuyinRepeatedSlotIdsRequireExactFirstRowLabels() {
        String[] topRow = {"ㄅ", "ㄉ", "ˇ", "ˋ", "ㄓ", "ˊ", "˙", "ㄚ", "ㄞ", "ㄢ"};
        String[][] lowerRows = {
                {"ㄆ", "ㄊ", "ㄍ", "ㄐ", "ㄔ", "ㄗ", "ㄧ", "ㄛ", "ㄟ", "ㄣ"},
                {"ㄇ", "ㄋ", "ㄎ", "ㄑ", "ㄕ", "ㄘ", "ㄨ", "ㄜ", "ㄠ", "ㄤ"},
                {"ㄈ", "ㄌ", "ㄏ", "ㄒ", "ㄖ", "ㄙ", "ㄩ", "ㄝ", "ㄡ", "ㄥ"}
        };

        for (int slot = 0; slot < topRow.length; slot++) {
            assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                    GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                            slot,
                            GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN,
                            topRow[slot]));
            for (String[] lowerRow : lowerRows) {
                assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.NONE,
                        GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                                slot,
                                GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN,
                                lowerRow[slot]));
            }
        }

        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.topRowLayoutKindForSlot(
                        6,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ZHUYIN,
                        "·"));
    }

    @Test
    public void completeQwertyRowGuardRejectsVariantsAndPartialRows() throws Exception {
        Method method = GboardTopRowSwipeRuntimeSupport.class.getDeclaredMethod(
                "isEnglishQwertyTopRowLabels", List.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(null,
                List.of("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")));
        assertFalse((Boolean) method.invoke(null,
                List.of("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")));
        assertFalse((Boolean) method.invoke(null,
                List.of("q", "w", "e", "r", "t", "z", "u", "i", "o", "p")));
        assertFalse((Boolean) method.invoke(null,
                List.of("q", "w", "e", "r", "t", "y", "u", "i", "o")));
        assertFalse((Boolean) method.invoke(null,
                List.of("w", "q", "e", "r", "t", "y", "u", "i", "o", "p")));
        assertFalse((Boolean) method.invoke(null, new Object[] {null}));
    }

    @Test
    public void resolvesEnglishQwertyTopRowIndexFromLabels() {
        assertEquals(0, GboardTopRowSwipeRuntimeSupport.englishQwertyTopRowIndexForLabel("q"));
        assertEquals(9, GboardTopRowSwipeRuntimeSupport.englishQwertyTopRowIndexForLabel("P"));
        assertEquals(-1, GboardTopRowSwipeRuntimeSupport.englishQwertyTopRowIndexForLabel("a"));
    }

    @Test
    public void englishQwertyFallbackUsesPressTextWhenPrimaryLabelIsMissing() {
        GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                new GboardTopRowSwipeRuntimeSupport.KeyBehavior(
                        null,
                        "q",
                        new String[0],
                        false,
                        false,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY);

        GboardTopRowSwipeRuntime.TopRowSlot slot =
                GboardTopRowSwipeRuntime.topRowSlotForEnglishQwertyLabelFallback(
                        true,
                        behavior);

        assertEquals(0, slot.rowIndex);
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                slot.layoutKind);
    }

    @Test
    public void englishQwertyFallbackDoesNotRequireVisualFirstRowWhenLegacyKindMatches() {
        GboardTopRowSwipeRuntimeSupport.KeyBehavior behavior =
                new GboardTopRowSwipeRuntimeSupport.KeyBehavior(
                        "e",
                        "e",
                        new String[0],
                        false,
                        false,
                        GboardTopRowSwipeRuntimeSupport.LegacyKeyboardKind.ENGLISH_QWERTY);

        GboardTopRowSwipeRuntime.TopRowSlot slot =
                GboardTopRowSwipeRuntime.topRowSlotForEnglishQwertyLabelFallback(
                        false,
                        behavior);

        assertEquals(2, slot.rowIndex);
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                slot.layoutKind);
    }

    @Test
    public void keepsZhuyinAndEnglishQwertyPageStateSeparate() {
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.CUSTOM);
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                GboardTopRowSwipeRuntime.TopRowPage.STOCK);

        assertEquals(GboardTopRowSwipeRuntime.TopRowPage.CUSTOM,
                GboardTopRowSwipeRuntime.currentPage(
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN));
        assertEquals(GboardTopRowSwipeRuntime.TopRowPage.STOCK,
                GboardTopRowSwipeRuntime.currentPage(
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY));
    }

    @Test
    public void visibleTopRowKeyDoesNotRetainSoftKeyView() {
        for (Field field : GboardTopRowSwipeRuntime.VisibleTopRowKey.class.getDeclaredFields()) {
            assertFalse("VisibleTopRowKey must not strongly retain the WeakHashMap key",
                    "softKeyView".equals(field.getName()));
        }
    }

    @Test
    public void metadataLineageCachesKeepEqualRebindGenerationsSeparateByIdentity() {
        EqualMetadataKey staleGeneration = new EqualMetadataKey("same-slot");
        EqualMetadataKey currentGeneration = new EqualMetadataKey("same-slot");
        Object staleOriginal = new Object();
        Object currentOriginal = new Object();
        GboardTopRowSwipeRuntime.ORIGINAL_KEY_METADATA_BY_PATCHED.put(
                staleGeneration, staleOriginal);
        GboardTopRowSwipeRuntime.ORIGINAL_KEY_METADATA_BY_PATCHED.put(
                currentGeneration, currentOriginal);
        GboardTopRowSwipeRuntime.TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(
                staleGeneration,
                new GboardTopRowSwipeRuntime.PatchedTopRowMetadataState(
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                        2,
                        new GboardTopRowSwipeRuntime.TopRowItem(
                                "old", "", true, "return 'old';", 1_000)));
        GboardTopRowSwipeRuntime.TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(
                currentGeneration,
                new GboardTopRowSwipeRuntime.PatchedTopRowMetadataState(
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                        7,
                        new GboardTopRowSwipeRuntime.TopRowItem(
                                "new", "", true, "return 'new';", 2_000)));

        assertSame(staleOriginal,
                GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(
                        staleGeneration));
        assertSame(currentOriginal,
                GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(
                        currentGeneration));
        GboardTopRowSwipeRuntime.QuickJsSlotConfig staleConfig =
                GboardTopRowSwipeRuntime.quickJsSlotConfig(staleGeneration);
        GboardTopRowSwipeRuntime.QuickJsSlotConfig currentConfig =
                GboardTopRowSwipeRuntime.quickJsSlotConfig(currentGeneration);
        assertNotNull(staleConfig);
        assertNotNull(currentConfig);
        assertEquals(2, staleConfig.rowIndex);
        assertEquals(7, currentConfig.rowIndex);

        GboardTopRowSwipeRuntime.ORIGINAL_KEY_METADATA_BY_PATCHED.remove(staleGeneration);
        GboardTopRowSwipeRuntime.TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.remove(staleGeneration);
        assertSame(currentOriginal,
                GboardTopRowSwipeRuntime.resolveOriginalKeyMetadataForPatchInterop(
                        currentGeneration));
        assertNotNull(GboardTopRowSwipeRuntime.quickJsSlotConfig(currentGeneration));
    }

    @Test
    public void sourceExcludesToolbarAndHeaderSurfacesFromTopRowCache() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntime.java");

        assertTrue(source.contains("isTopRowCacheEligibleView("));
        assertTrue(source.contains("view.getVisibility() == View.VISIBLE"));
        assertTrue(source.contains("view.getAlpha() <= 0f"));
        assertTrue(source.contains("isSameVisualRow("));
        assertTrue(source.contains("AccessPointsBarItemSoftKeyView"));
        assertTrue(source.contains("AccessPointsBar"));
        assertTrue(source.contains("softkey_holder_fixed_candidates"));
        assertTrue(source.contains("key_pos_header_"));
        assertTrue(source.contains(".keyboard-header-area"));
        assertTrue(source.contains("CandidatesHolderView"));
        assertTrue(source.contains("isDisallowedTopRowSurface("));
    }

    @Test
    public void sourceSkipsResourceNameLookupForNoIdViews() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntimeSupport.java");

        assertTrue(source.contains("view.getId() == View.NO_ID"));
    }

    @Test
    public void malformedProviderValuesDisableTopRowSwipeAtRuntime() {
        GboardTopRowSwipeRuntime.SettingsSnapshot snapshot =
                GboardTopRowSwipeRuntime.settingsSnapshotFromProviderValues(
                        Boolean.TRUE,
                        Boolean.TRUE,
                        Boolean.TRUE,
                        Collections.singletonList("A"),
                        defaultCommitTexts(),
                        1000L);

        assertFalse(snapshot.enabled);

        ArrayList<String> displays = defaultDisplayTexts();
        displays.set(0, "");
        snapshot = GboardTopRowSwipeRuntime.settingsSnapshotFromProviderValues(
                Boolean.TRUE,
                Boolean.TRUE,
                Boolean.TRUE,
                displays,
                defaultCommitTexts(),
                1000L);

        assertFalse(snapshot.enabled);
    }

    @Test
    public void settingsSnapshotKeepsLayoutTogglesSeparateFromGlobalEnable() {
        GboardTopRowSwipeRuntime.SettingsSnapshot snapshot =
                GboardTopRowSwipeRuntime.settingsSnapshotFromProviderValues(
                        Boolean.TRUE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        defaultDisplayTexts(),
                        defaultCommitTexts(),
                        1000L);

        assertTrue(snapshot.enabled);
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.CUSTOM);
        assertTrue(GboardTopRowSwipeRuntime.shouldAllowTopRowCustomPatchedDispatch(
                snapshot,
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN));
        assertFalse(GboardTopRowSwipeRuntime.shouldAllowTopRowCustomPatchedDispatch(
                snapshot,
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY));
    }

    @Test
    public void customTopRowPatchedDispatchRequiresCurrentCustomPage() {
        GboardTopRowSwipeRuntime.SettingsSnapshot snapshot = customSnapshot(true, false, 1000L);
        Object customTopRowMetadata = new Object();
        GboardTopRowSwipeRuntime.markTopRowCustomPatchedMetadata(
                customTopRowMetadata,
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN);

        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.STOCK);
        assertTrue(GboardTopRowSwipeRuntime.shouldConsumeDisabledTopRowCustomDispatch(
                snapshot,
                customTopRowMetadata));

        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.CUSTOM);
        assertFalse(GboardTopRowSwipeRuntime.shouldConsumeDisabledTopRowCustomDispatch(
                snapshot,
                customTopRowMetadata));
    }

    @Test
    public void customEnglishPageCanRecoverSlotFromVisibleCache() {
        Object softKeyView = new Object();
        GboardTopRowSwipeRuntime.VISIBLE_TOP_ROW_KEYS.put(
                softKeyView,
                new GboardTopRowSwipeRuntime.VisibleTopRowKey(
                        new Object(),
                        4,
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY));
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                GboardTopRowSwipeRuntime.TopRowPage.CUSTOM);

        GboardTopRowSwipeRuntime.TopRowSlot slot =
                GboardTopRowSwipeRuntime.topRowSlotFromVisibleCache(softKeyView);

        assertEquals(4, slot.rowIndex);
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY,
                slot.layoutKind);
    }

    @Test
    public void quickJsConfigCanRecoverFromActiveSessionRow() {
        Object tracker = new Object();
        GboardTopRowSwipeRuntime.SESSIONS.put(
                tracker,
                new GboardTopRowSwipeRuntime.SwipeSession(
                        new Object(),
                        0f,
                        0f,
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                        6));
        GboardTopRowSwipeRuntime.SettingsSnapshot snapshot = customJavaScriptSnapshot(
                6,
                "return 'ok';",
                1_500,
                1000L);
        GboardTopRowSwipeRuntime.setCurrentPage(
                GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN,
                GboardTopRowSwipeRuntime.TopRowPage.CUSTOM);

        GboardTopRowSwipeRuntime.QuickJsSlotConfig config =
                GboardTopRowSwipeRuntime.quickJsSlotConfigFromSession(snapshot, tracker);

        assertEquals(6, config.rowIndex);
        assertEquals(GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN, config.layoutKind);
        assertEquals("return 'ok';", config.scriptSource);
        assertEquals(1_500, config.timeoutMs);
    }

    @Test
    public void unresolvedQuickJsMarkerConsumesFailClosed() {
        assertTrue(GboardQuickJsFeature.maybeConsumeQuickJsTopRowPress(
                null,
                null,
                null,
                null,
                true));
    }

    @Test
    public void pointerFinishOnlyClearsConsumedOrPendingSessions() throws Exception {
        Method finishMethod = GboardTopRowSwipeRuntime.class.getDeclaredMethod(
                "finishSwipeSession", Object.class);
        finishMethod.setAccessible(true);

        Object tracker = new Object();
        GboardTopRowSwipeRuntime.SwipeSession session =
                new GboardTopRowSwipeRuntime.SwipeSession(
                        new Object(),
                        0f,
                        0f,
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ZHUYIN);
        GboardTopRowSwipeRuntime.SESSIONS.put(tracker, session);

        finishMethod.invoke(null, tracker);
        assertTrue(GboardTopRowSwipeRuntime.SESSIONS.containsKey(tracker));

        session.pendingPage = GboardTopRowSwipeRuntime.TopRowPage.CUSTOM;
        finishMethod.invoke(null, tracker);
        assertNull(GboardTopRowSwipeRuntime.SESSIONS.get(tracker));
    }

    @Test
    public void targetRuntimeSourceUsesOnly1853ReflectionMappings() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntimeSupport.java");

        assertTrue(source.contains("PRIMARY_LABEL_VIEW_ID = 0x7f0b05f5"));
        for (String targetType : List.of(
                "qae", "qbc", "qaf", "qcv", "qbb", "qin", "qil")) {
            assertTrue("missing target type " + targetType,
                    source.contains("\"" + targetType + "\""));
        }
        for (String baselineType : List.of(
                "oth", "oud", "otk", "owd", "oti", "ovv", "ouc", "pbl", "pbj")) {
            assertFalse("stale baseline type remains active: " + baselineType,
                    source.contains("\"" + baselineType + "\""));
        }
        assertFalse(source.contains("0x7f0b062a"));
        assertTrue(source.contains("getDeclaredMethod(\"s\", actionSetClass"));
        assertTrue(source.contains("getDeclaredField(\"f\")"));
        assertTrue(source.contains("getDeclaredField(\"i\")"));
        assertTrue(source.contains("getDeclaredField(\"h\")"));
        assertTrue(source.contains("getDeclaredMethod(\"g\", actionTypeClass)"));
        assertTrue(source.replaceAll("\\s+", " ").contains(
                "getDeclaredMethod(\"s\", int.class, intentionClass, Object.class)"));
    }

    @Test
    public void runtimeSourceHydratesExactlyTenSupportedRowKeysBeforeSessionStorage()
            throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntime.java");

        assertTrue(source.contains("hydrateConfirmedEnglishQwertyRow"));
        assertTrue(source.contains("hydrateConfirmedZhuyinRow"));
        assertTrue(source.contains("confirmedEnglishQwertyRow"));
        assertTrue(source.contains("PREVALIDATED_ENGLISH_REFRESH"));
        assertTrue(source.contains("isEnglishQwertyTopRowLabels"));

        int armStart = source.indexOf("public static Object maybeArmAndResolveTopRowOwner");
        int clearStart = source.indexOf("public static void clearSwipeSession", armStart);
        String arm = source.substring(armStart, clearStart);
        int zhuyinHydrate = arm.indexOf("hydrateConfirmedZhuyinRow(handles, candidateView)");
        int englishHydrate = arm.indexOf(
                "hydrateConfirmedEnglishQwertyRow(handles, candidateView)");
        int store = arm.indexOf("SESSIONS.put(tracker");
        assertTrue(zhuyinHydrate >= 0 && store > zhuyinHydrate);
        assertTrue(englishHydrate >= 0 && store > englishHydrate);
    }

    @Test
    public void afterBindTrackingReadsInstalledSoftKeyMetadataInsteadOfIncomingArgument()
            throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntime.java");
        int trackingStart = source.indexOf("public static void afterSoftKeyBound");
        int pointerStart = source.indexOf(
                "public static Object maybeArmAndResolveTopRowOwner", trackingStart);
        assertTrue(trackingStart >= 0 && pointerStart > trackingStart);
        String tracking = source.substring(trackingStart, pointerStart)
                .replaceAll("\\s+", " ");

        assertTrue(tracking.contains("afterSoftKeyBound(Object receiver)"));
        assertTrue(tracking.contains("handles.softKeyMetadataField.get(receiver)"));
        assertTrue(tracking.contains("resolveOriginalKeyMetadata(boundMetadata)"));
        assertFalse(tracking.contains("afterSoftKeyBound(Object receiver, Object keyMetadata)"));
        assertFalse(tracking.contains("resolveOriginalKeyMetadata(keyMetadata)"));
        assertFalse(tracking.contains("isTopRowCacheEligibleView("));
    }

    @Test
    public void everyTopRowMetadataBuildInheritsSlideIdentityBeforeInteropCaching()
            throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntime.java");
        int patchStart = source.indexOf("public static Object patchIncomingSoftKeyMetadata");
        int afterBindStart = source.indexOf("public static void afterSoftKeyBound", patchStart);
        assertTrue(patchStart >= 0 && afterBindStart > patchStart);
        String patchMethod = source.substring(patchStart, afterBindStart);

        int build = patchMethod.indexOf("buildKeyMetadataMethod.invoke(builder)");
        int inherit = patchMethod.indexOf(
                "GboardZhuyinSlideRuntime.inheritPatchedMetadata(originalMetadata, patched)");
        int originalCache = patchMethod.indexOf("ORIGINAL_KEY_METADATA_BY_PATCHED.put(");
        int customMarker = patchMethod.indexOf("TOP_ROW_CUSTOM_PATCHED_METADATA_MARKERS.put(");
        assertTrue(build >= 0);
        assertTrue(inherit > build);
        assertTrue(originalCache > inherit);
        assertTrue(customMarker > originalCache);
        assertEquals(1, countOccurrences(source, "buildKeyMetadataMethod.invoke(builder)"));

        int refreshStart = source.indexOf("private static void refreshVisibleTopRowKeyMetadata");
        String refresh = source.substring(refreshStart);
        assertTrue(refresh.contains("patchIncomingSoftKeyMetadata("));
    }

    @Test
    public void pageApplyTransactionCleansOwnedTransitionBeforeStrictVisibleCopyAndRollsBack()
            throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/toprowswipe/"
                        + "GboardTopRowSwipeRuntime.java");
        int applyStart = source.indexOf("static boolean applyTopRowPageTransition");
        int refreshStart = source.indexOf(
                "private static void refreshVisibleTopRowKeyMetadata", applyStart);
        assertTrue(applyStart >= 0 && refreshStart > applyStart);
        String apply = source.substring(applyStart, refreshStart);

        int cleanup = apply.indexOf("cleanupOwnedTopRowTransition(anchorView)");
        int copy = apply.indexOf("copyVisibleTopRowKeys(layoutKind, anchorView)");
        assertTrue(cleanup >= 0 && copy > cleanup);
        assertTrue(apply.contains("recycleSnapshot(beforeSnapshot)"));
        assertTrue(apply.contains("recycleSnapshot(afterSnapshot)"));
        assertTrue(apply.contains("pageSetter.accept(previousPage)"));
        assertTrue(apply.contains("rebinder.accept(visibleTopRowKeys)"));
        assertTrue(apply.contains("setTopRowAlpha(visibleTopRowKeys, 1f)"));
    }

    @Test
    public void consumedSessionRecyclesOnlyAfterRecycleWindow() throws Exception {
        Method recycleMethod = GboardTopRowSwipeRuntime.class.getDeclaredMethod(
                "shouldRecycleConsumedSession",
                long.class,
                GboardTopRowSwipeRuntime.SwipeSession.class);
        recycleMethod.setAccessible(true);

        GboardTopRowSwipeRuntime.SwipeSession session =
                new GboardTopRowSwipeRuntime.SwipeSession(
                        new Object(),
                        0f,
                        0f,
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY);
        session.consumed = true;
        session.consumedAtElapsedMs = 1_000L;

        assertFalse((Boolean) recycleMethod.invoke(
                null,
                1_000L + GboardTopRowSwipeRuntime.CONSUMED_SESSION_RECYCLE_WINDOW_MS - 1L,
                session));
        assertTrue((Boolean) recycleMethod.invoke(
                null,
                1_000L + GboardTopRowSwipeRuntime.CONSUMED_SESSION_RECYCLE_WINDOW_MS,
                session));
    }

    @Test
    public void nonViewAnchorSessionIsRecycledForNextIncomingView() {
        View incoming = new View(RuntimeEnvironment.getApplication());
        GboardTopRowSwipeRuntime.SwipeSession session =
                new GboardTopRowSwipeRuntime.SwipeSession(
                        new Object(),
                        0f,
                        0f,
                        GboardTopRowSwipeRuntime.TopRowLayoutKind.ENGLISH_QWERTY);

        assertTrue(GboardTopRowSwipeRuntime.shouldRecycleSessionForIncomingView(
                session,
                incoming,
                incoming));
    }

    private static GboardTopRowSwipeRuntime.SettingsSnapshot customSnapshot(
            boolean zhuyinEnabled, boolean englishEnabled, long loadedAtElapsedMs) {
        return GboardTopRowSwipeRuntime.settingsSnapshotFromProviderValues(
                Boolean.TRUE,
                Boolean.valueOf(zhuyinEnabled),
                Boolean.valueOf(englishEnabled),
                defaultDisplayTexts(),
                defaultCommitTexts(),
                loadedAtElapsedMs);
    }

    private static ArrayList<String> defaultDisplayTexts() {
        ArrayList<String> displays = new ArrayList<String>(GboardTopRowSwipeSettings.SLOT_COUNT);
        for (GboardTopRowSwipeSettings.SlotText slot : GboardTopRowSwipeSettings.defaultSlots()) {
            displays.add(slot.displayText);
        }
        return displays;
    }

    private static ArrayList<String> defaultCommitTexts() {
        ArrayList<String> commits = new ArrayList<String>(GboardTopRowSwipeSettings.SLOT_COUNT);
        for (GboardTopRowSwipeSettings.SlotText slot : GboardTopRowSwipeSettings.defaultSlots()) {
            commits.add(slot.commitText);
        }
        return commits;
    }

    private static GboardTopRowSwipeRuntime.SettingsSnapshot customJavaScriptSnapshot(
            int javaScriptSlotIndex,
            String script,
            int timeoutMs,
            long loadedAtElapsedMs) {
        boolean[] isJavaScripts = new boolean[GboardTopRowSwipeSettings.SLOT_COUNT];
        isJavaScripts[javaScriptSlotIndex] = true;
        ArrayList<String> scripts = new ArrayList<String>(GboardTopRowSwipeSettings.SLOT_COUNT);
        int[] timeouts = new int[GboardTopRowSwipeSettings.SLOT_COUNT];
        for (int index = 0; index < GboardTopRowSwipeSettings.SLOT_COUNT; index++) {
            scripts.add(index == javaScriptSlotIndex ? script : "");
            timeouts[index] = index == javaScriptSlotIndex
                    ? timeoutMs : GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MS;
        }
        return GboardTopRowSwipeRuntime.settingsSnapshotFromProviderValues(
                Boolean.TRUE,
                Boolean.TRUE,
                Boolean.FALSE,
                defaultDisplayTexts(),
                defaultCommitTexts(),
                isJavaScripts,
                scripts,
                timeouts,
                "",
                GboardTopRowSwipeSettings.DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
                GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MAX_MS,
                GboardTopRowSwipeSettings.DEFAULT_MEMORY_LIMIT_BYTES,
                GboardTopRowSwipeSettings.DEFAULT_MAX_STACK_BYTES,
                loadedAtElapsedMs);
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private static final class EqualMetadataKey {
        private final String value;

        EqualMetadataKey(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualMetadataKey
                    && value.equals(((EqualMetadataKey) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
