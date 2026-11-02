package dev.jason.gboardpatches.extension.zhuyintoggle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class GboardZhuyinTraditionalSimplifiedToggleRuntimeSourceTest {
    @Test
    public void reflectionSupportUsesOnlyTheExact1853TargetContract() throws Exception {
        String source = readSupportSource();

        assertTrue(source.contains(
                "\"com.google.android.libraries.inputmethod.metadata.SoftKeyDef\""));
        assertTrue(source.contains("private static final String ACTION_TYPE_CLASS = \"qae\";"));
        assertTrue(source.contains(
                "\"com.google.android.libraries.inputmethod.metadata.ActionDef\""));
        assertTrue(source.contains("private static final String ACTION_ENTRY_CLASS = \"qbc\";"));
        assertTrue(source.contains("private static final String ACTION_BUILDER_CLASS = \"qaf\";"));
        assertTrue(source.contains("private static final String METADATA_BUILDER_CLASS = \"qcv\";"));
        assertTrue(source.contains("private static final String INTENTION_CLASS = \"qbb\";"));
        assertTrue(source.contains("private static final String GESTURE_DISPATCHER_CLASS = \"qil\";"));
        assertTrue(source.contains("private static final String PREFERENCE_CLASS = \"qvt\";"));
        assertTrue(source.contains("private static final String PREFERENCE_BASE_CLASS = \"cht\";"));
        assertTrue(source.contains("public static final int PREFERENCE_KEY_RES_ID = 0x7f140966;"));
        assertTrue(source.contains("getDeclaredMethod(\"s\", actionSetClass, long.class)"));
        assertTrue(source.contains("getDeclaredField(\"e\")"));
        assertTrue(source.contains("getDeclaredField(\"f\")"));
        assertTrue(source.contains("actionSetClass.getDeclaredField(\"f\")"));
        assertTrue(source.contains("gestureDispatcherClass.getDeclaredField(\"b\")"));
        assertTrue(source.contains("gestureDispatcherClass.getDeclaredField(\"c\")"));
        assertTrue(source.contains("preferenceClass.getDeclaredMethod(\"I\", Context.class)"));
        assertTrue(source.contains(
                "preferenceBaseClass.getDeclaredMethod("));
        assertTrue(source.contains("\"x\", int.class, boolean.class"));
        assertTrue(source.contains("\"f\", String.class, boolean.class"));
        assertFalse(source.contains("ACTION_SET_CLASS = \"owd\""));
        assertFalse(source.contains("GESTURE_DISPATCHER_CLASS = \"pbj\""));
        assertFalse(source.contains("\"oaa\""));
        assertFalse(source.contains("\"ofi\""));
        assertFalse(source.contains("\"nxi\""));
        assertFalse(source.contains("\"nyf\""));
        assertFalse(source.contains("0x7f0b23fd"));
        assertFalse(source.contains("0x7f140969"));
    }

    @Test
    public void popupRequiresExactTrackedViewAndPreservesActionEntries() throws Exception {
        String runtime = readRuntimeSource();
        String support = readSupportSource();
        String popupMethod = methodBody(runtime, "public static Object patchPopupAction(",
                "public static boolean maybeToggle(");

        int trackedLookup = popupMethod.indexOf("VISIBLE_TOGGLE_KEYS.get(sourceView)");
        int actionInspection = popupMethod.indexOf("handles.actionDefinitionName(actionDef)");
        assertTrue(trackedLookup >= 0);
        assertTrue(actionInspection > trackedLookup);
        assertTrue(popupMethod.contains("if (metadata == null)"));
        assertTrue(support.contains("actionBuilderCopyMethod.invoke(builder, actionDef)"));
        assertTrue(support.contains(
                "actionBuilderClass.getDeclaredMethod(\"i\", actionDefClass)"));
        assertTrue(support.contains("setPopupLabelsMethod.invoke("));
    }

    @Test
    public void successfulOfficialWritePrecedesConsumptionAndRefreshFailuresStayConsumed()
            throws Exception {
        String runtime = readRuntimeSource();
        String toggleMethod = methodBody(runtime, "public static boolean maybeToggle(",
                "private static void refreshVisibleKeys(");

        int write = toggleMethod.indexOf("handles.writeSimplifiedEnabled(");
        int visibleRefresh = toggleMethod.indexOf("refreshVisibleKeys(");
        int inputRefresh = toggleMethod.indexOf("refreshCurrentInputView(");
        int consumed = toggleMethod.lastIndexOf("return true;");
        assertTrue(write >= 0);
        assertTrue(toggleMethod.contains("if (!handles.writeSimplifiedEnabled("));
        assertTrue(visibleRefresh > write);
        assertTrue(inputRefresh > write);
        assertTrue(consumed > visibleRefresh);
        assertTrue(toggleMethod.contains("catch (Throwable ignored)"));
    }

    @Test
    public void visibleRefreshSnapshotsWeakViewsAndRebindsFromBaseMetadata() throws Exception {
        String runtime = readRuntimeSource();
        String support = readSupportSource();

        assertTrue(runtime.contains(
                "Map<View, Object> snapshot = new HashMap<View, Object>();"));
        assertTrue(runtime.contains("snapshot.putAll(VISIBLE_TOGGLE_KEYS);"));
        assertTrue(runtime.contains("handles.buildToggleMetadata(entry.getValue(),"));
        assertTrue(runtime.contains("handles.rebind(entry.getKey(), patched);"));
        assertTrue(support.contains("softKeyBindMethod.invoke(softKeyView, metadata, token)"));
        assertTrue(runtime.contains("resolveBaseMetadata(metadata)"));
        assertTrue(runtime.contains("WeakIdentityMap<Object, Boolean>"));
        assertTrue(runtime.contains("WeakIdentityMap<Object, Object>"));
    }

    @Test
    public void currentInputViewUsesAtAndOldSoftKeyboardRefreshPathIsAbsent() throws Exception {
        String runtime = readRuntimeSource();

        assertTrue(runtime.contains("getMethod(\"at\", boolean.class)"));
        assertFalse(runtime.contains("getMethod(\"aq\", boolean.class)"));
        assertFalse(runtime.contains("getMethod(\"ao\", boolean.class)"));
        assertFalse(runtime.contains("SoftKeyboardView"));
        assertFalse(runtime.contains("jasondevPatchTogglePopupMetadata"));
        assertFalse(runtime.contains("getDeclaredMethod(\"z\""));
    }

    @Test
    public void toggleMetadataCloneExplicitlyInheritsSlideIdentityFromItsBase()
            throws Exception {
        String runtime = readRuntimeSource();
        String markMethod = methodBody(runtime,
                "private static void markPatchedMetadata(",
                "private static final class WeakIdentityMap");

        assertTrue(runtime.contains(
                "import dev.jason.gboardpatches.extension.zhuyinslide."
                        + "GboardZhuyinSlideRuntime;"));
        assertTrue(markMethod.contains(
                "GboardZhuyinSlideRuntime.inheritPatchedMetadata(base, patched);"));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing method start: " + startMarker, start >= 0);
        assertTrue("Missing method end: " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static String readRuntimeSource() throws Exception {
        return readSource(
                "src/main/java/dev/jason/gboardpatches/extension/zhuyintoggle/"
                        + "GboardZhuyinTraditionalSimplifiedToggleRuntime.java");
    }

    private static String readSupportSource() throws Exception {
        return readSource(
                "src/main/java/dev/jason/gboardpatches/extension/zhuyintoggle/"
                        + "GboardZhuyinTraditionalSimplifiedToggleRuntimeSupport.java");
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
