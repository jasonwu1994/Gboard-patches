package dev.jason.gboardpatches.extension.addsymbols;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowSystemClock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
public final class GboardAddSymbolsRuntimeTest {
    @Before
    @After
    public void resetRuntimeState() throws Exception {
        activeCustomEmoticonKeyboards().clear();
        currentCustomEmoticonRenderMode().clear();
        activeCustomEmoticonSession().set(false);
        currentCustomEmoticonSessionKeyboard().set(null);
        pendingStockEmoticonTabSwitchKeyboard().set(null);
        pendingStockEmoticonTabSwitchRenderMode().set(null);
        pendingStockEmoticonTabSwitchActiveSession().set(false);
        setStaticLong("pendingCustomFlowUntilUptimeMs", 0L);
        setStaticLong("pendingStockEmoticonTabSwitchUntilUptimeMs", 0L);
        ShadowLog.clear();
        clearWarningLogCountsIfPresent();
    }

    @Test
    public void markPendingStockTabSwitchSuppressesOnlyTrackedSessionKeyboard() throws Exception {
        Object sessionKeyboard = new Object();
        Object otherKeyboard = new Object();
        activeCustomEmoticonKeyboards().put(sessionKeyboard, Boolean.TRUE);
        activeCustomEmoticonKeyboards().put(otherKeyboard, Boolean.TRUE);
        currentCustomEmoticonRenderMode().put(sessionKeyboard, Boolean.TRUE);
        currentCustomEmoticonRenderMode().put(otherKeyboard, Boolean.TRUE);
        currentCustomEmoticonSessionKeyboard().set(sessionKeyboard);
        activeCustomEmoticonSession().set(true);

        privateMethod("markPendingStockEmoticonTabSwitch").invoke(null);

        Assert.assertFalse(activeCustomEmoticonSession().get());
        Assert.assertEquals(Boolean.FALSE, currentCustomEmoticonRenderMode().get(sessionKeyboard));
        Assert.assertEquals(Boolean.TRUE, currentCustomEmoticonRenderMode().get(otherKeyboard));
        Assert.assertSame(sessionKeyboard, pendingStockEmoticonTabSwitchKeyboard().get());
        Assert.assertEquals(Boolean.TRUE, pendingStockEmoticonTabSwitchRenderMode().get());
        Assert.assertTrue(pendingStockEmoticonTabSwitchActiveSession().get());
    }

    @Test
    public void expiredPendingStockTabSwitchRestoresPreviousSessionState() throws Exception {
        Object sessionKeyboard = new Object();
        activeCustomEmoticonKeyboards().put(sessionKeyboard, Boolean.TRUE);
        currentCustomEmoticonRenderMode().put(sessionKeyboard, Boolean.TRUE);
        currentCustomEmoticonSessionKeyboard().set(sessionKeyboard);
        activeCustomEmoticonSession().set(true);

        privateMethod("markPendingStockEmoticonTabSwitch").invoke(null);
        ShadowSystemClock.advanceBy(Duration.ofMillis(2_600L));

        boolean hasPending = (Boolean) privateMethod("hasPendingStockEmoticonTabSwitch")
                .invoke(null);

        Assert.assertFalse(hasPending);
        Assert.assertTrue(activeCustomEmoticonSession().get());
        Assert.assertEquals(Boolean.TRUE, currentCustomEmoticonRenderMode().get(sessionKeyboard));
        Assert.assertNull(pendingStockEmoticonTabSwitchKeyboard().get());
        Assert.assertNull(pendingStockEmoticonTabSwitchRenderMode().get());
        Assert.assertFalse(pendingStockEmoticonTabSwitchActiveSession().get());
    }

    @Test
    public void resolvesChineseLabelsOnlyForZhLocalesAndEnglishOtherwise() throws Exception {
        Assert.assertEquals(
                "常用",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_COMMON,
                        Locale.forLanguageTag("zh-Hant-TW")));
        Assert.assertEquals(
                "Common",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_COMMON,
                        Locale.US));
        Assert.assertEquals(
                "Common",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_COMMON,
                        Locale.JAPAN));
        Assert.assertEquals(
                "Common",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_COMMON,
                        null));
    }

    @Test
    public void resolvesMultipleCategoryLabelsConsistently() throws Exception {
        Assert.assertEquals(
                "Typography",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_TYPOGRAPHY,
                        Locale.CANADA_FRENCH));
        Assert.assertEquals(
                "箭頭",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_ARROWS,
                        Locale.forLanguageTag("zh-TW")));
        Assert.assertEquals(
                "Currency",
                localeAwareCategoryLabel(
                        GboardAddSymbolsDataset.KEY_CURRENCY,
                        Locale.KOREA));
        Assert.assertEquals(
                "Recents",
                localeAwareCategoryLabel("RECENTS", Locale.GERMANY));
    }

    @Test
    public void datasetDoesNotExposeLegacyCategoryLabelsConstant() throws Exception {
        try {
            GboardAddSymbolsDataset.class.getDeclaredField("CATEGORY_LABELS");
            Assert.fail("Legacy CATEGORY_LABELS constant should be removed.");
        } catch (NoSuchFieldException expected) {
            // Expected.
        }
    }

    @Test
    public void runtimeUsesOnlyGboard1853ReflectionHandles() throws Exception {
        String source = runtimeSource();
        List<String> targetClasses = List.of(
                "qch", "pla", "kr", "wnv", "wns", "wsw", "jiu", "jjn", "jhc",
                "gpk", "gpz", "gpm", "gpl", "gph", "aaaa", "gpf", "zmt", "gpd",
                "ntv", "gpo", "gpn", "gpb", "gpa", "gpe", "ohx", "ohn", "ohl",
                "jpv");
        for (String className : targetClasses) {
            Assert.assertTrue(
                    "Missing Gboard 18.5.4 runtime handle: " + className,
                    source.contains("Class.forName(\"" + className + "\", false, classLoader)"));
        }

        List<String> retired1803Classes = List.of(
                "ovf", "odz", "val", "ncc", "nbs", "nbq", "iru", "vai", "vfn",
                "ils", "ily", "iju", "kl", "frk", "fry", "frl", "qbv", "frg",
                "yks", "fre", "xxr", "frc", "mof", "fqz", "frn", "frm", "fra",
                "frd");
        for (String className : retired1803Classes) {
            Assert.assertFalse(
                    "Stale Gboard 18.5.4 runtime handle remains active: " + className,
                    source.contains("Class.forName(\"" + className + "\", false, classLoader)"));
        }

        Assert.assertTrue(source.contains(
                "Class.forName(\"qaz\", false, classLoader).getDeclaredMethod(\"ag\", Object.class)"));
        Assert.assertTrue(source.contains(
                "keyboardTypeNameField = keyboardTypeClass.getDeclaredField(\"m\")"));
        Assert.assertTrue(source.contains(
                "immutableMapBuilderClass.getDeclaredMethod(\"l\", Map.class)"));
        Assert.assertTrue(source.contains(
                "immutableMapBuilderClass.getDeclaredMethod(\"m\")"));
        Assert.assertTrue(source.contains(
                "\"D\", emoticonRecyclerViewClass, String.class"));
        Assert.assertFalse(source.contains(
                "\"y\", emoticonRecyclerViewClass, String.class"));
        Assert.assertFalse(source.contains("getMethod(\"ad\", int.class)"));
    }

    @Test
    public void activeReflectionFailuresAreLoggedInsteadOfSilentlyIgnored() throws Exception {
        String source = runtimeSource();
        assertMethodLogsFailure(source, "ensureExtensionProviderMapping",
                "ensureExtensionProviderMapping failed");
        assertMethodLogsFailure(source, "rewriteProviderRequestType",
                "rewriteProviderRequestType failed");
        assertMethodLogsFailure(source, "bridgeProviderReceiver",
                "bridgeProviderReceiver failed");
        assertMethodLogsFailure(source, "rewriteNavigationKeyboardType",
                "rewriteNavigationKeyboardType failed");
        assertMethodLogsFailure(source, "onExpressionCorpusFooterTabClick",
                "onExpressionCorpusFooterTabClick failed");
        assertMethodLogsFailure(source, "onKeyboardReady", "onKeyboardReady failed");
        assertMethodLogsFailure(source, "onEmoticonBodyReady", "onEmoticonBodyReady failed");
        assertMethodLogsFailure(source, "handleCategoryBind", "handleCategoryBind failed");
        assertMethodLogsFailure(source, "bindCustomViewHolder", "bindCustomViewHolder failed");
        assertMethodLogsFailure(source, "createCustomViewHolder", "createCustomViewHolder failed");
        assertMethodLogsFailure(source, "onHeaderCallbackAfter", "onHeaderCallbackAfter failed");
        assertMethodLogsFailure(source, "onEmoticonRecyclerAdapterConstructed",
                "onEmoticonRecyclerAdapterConstructed failed");
        assertMethodLogsFailure(source, "interceptHistoryWrite", "interceptHistoryWrite failed");
    }

    @Test
    public void customHistoryAcceptsOnlyFormal1853M2ClickCase() {
        Assert.assertTrue(GboardAddSymbolsRuntime.isCustomEmoticonHistoryConsumerCase(
                Integer.valueOf(7)));
        Assert.assertFalse(GboardAddSymbolsRuntime.isCustomEmoticonHistoryConsumerCase(
                Integer.valueOf(9)));
        Assert.assertFalse(GboardAddSymbolsRuntime.isCustomEmoticonHistoryConsumerCase(
                Integer.valueOf(4)));
        Assert.assertFalse(GboardAddSymbolsRuntime.isCustomEmoticonHistoryConsumerCase("7"));
        Assert.assertFalse(GboardAddSymbolsRuntime.isCustomEmoticonHistoryConsumerCase(null));
    }

    @Test
    public void persistentReflectionFailureLoggingIsBoundedPerSite() throws Exception {
        Method logWarn = GboardAddSymbolsRuntime.class.getDeclaredMethod(
                "logWarn", String.class, Throwable.class);
        logWarn.setAccessible(true);
        for (int index = 0; index < 5; index++) {
            logWarn.invoke(null, "persistent reflection drift", new IllegalStateException("drift"));
        }

        long warningCount = ShadowLog.getLogsForTag("GboardAddSymbols").stream()
                .filter(item -> item.type == android.util.Log.WARN)
                .filter(item -> "persistent reflection drift".equals(item.msg))
                .count();
        Assert.assertEquals(3L, warningCount);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Boolean> activeCustomEmoticonKeyboards() throws Exception {
        return (Map<Object, Boolean>) staticField("ACTIVE_CUSTOM_EMOTICON_KEYBOARDS").get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Boolean> currentCustomEmoticonRenderMode() throws Exception {
        return (Map<Object, Boolean>) staticField("CURRENT_CUSTOM_EMOTICON_RENDER_MODE").get(null);
    }

    private static AtomicBoolean activeCustomEmoticonSession() throws Exception {
        return (AtomicBoolean) staticField("ACTIVE_CUSTOM_EMOTICON_SESSION").get(null);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> currentCustomEmoticonSessionKeyboard() throws Exception {
        return (AtomicReference<Object>) staticField("CURRENT_CUSTOM_EMOTICON_SESSION_KEYBOARD")
                .get(null);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Object> pendingStockEmoticonTabSwitchKeyboard()
            throws Exception {
        return (AtomicReference<Object>) staticField("PENDING_STOCK_EMOTICON_TAB_SWITCH_KEYBOARD")
                .get(null);
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Boolean> pendingStockEmoticonTabSwitchRenderMode()
            throws Exception {
        return (AtomicReference<Boolean>)
                staticField("PENDING_STOCK_EMOTICON_TAB_SWITCH_RENDER_MODE").get(null);
    }

    private static AtomicBoolean pendingStockEmoticonTabSwitchActiveSession() throws Exception {
        return (AtomicBoolean) staticField("PENDING_STOCK_EMOTICON_TAB_SWITCH_ACTIVE_SESSION")
                .get(null);
    }

    private static void setStaticLong(String fieldName, long value) throws Exception {
        staticField(fieldName).setLong(null, value);
    }

    @SuppressWarnings("unchecked")
    private static void clearWarningLogCountsIfPresent() throws Exception {
        try {
            ((Map<String, ?>) staticField("WARNING_LOG_COUNTS").get(null)).clear();
        } catch (NoSuchFieldException ignored) {
            // RED compatibility: the bounded warning map does not exist yet.
        }
    }

    private static Field staticField(String fieldName) throws Exception {
        Field field = GboardAddSymbolsRuntime.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static Method privateMethod(String methodName) throws Exception {
        Method method = GboardAddSymbolsRuntime.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method;
    }

    private static String localeAwareCategoryLabel(String categoryKey, Locale locale)
            throws Exception {
        Method method = GboardAddSymbolsRuntime.class.getDeclaredMethod(
                "resolveCustomEmoticonCategoryLabel",
                String.class,
                Locale.class);
        method.setAccessible(true);
        return (String) method.invoke(null, categoryKey, locale);
    }

    private static String runtimeSource() throws Exception {
        Path path = Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/addsymbols/"
                        + "GboardAddSymbolsRuntime.java");
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void assertMethodLogsFailure(
            String source,
            String methodName,
            String warningMessage) {
        String method = methodSource(source, methodName);
        Assert.assertTrue(
                methodName + " must catch active reflection failures",
                method.contains("catch (Throwable"));
        Assert.assertTrue(
                methodName + " must log active reflection failures",
                method.contains("logWarn(\"" + warningMessage + "\", throwable)"));
        Assert.assertFalse(
                methodName + " must not catch-and-ignore active reflection failures",
                method.contains("catch (Throwable ignored)"));
    }

    private static String methodSource(String source, String methodName) {
        int nameIndex = source.indexOf(methodName + "(");
        Assert.assertTrue("Missing method source: " + methodName, nameIndex >= 0);
        int bodyStart = source.indexOf('{', nameIndex);
        Assert.assertTrue("Missing method body: " + methodName, bodyStart >= 0);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, index + 1);
                }
            }
        }
        throw new AssertionError("Unterminated method body: " + methodName);
    }
}
