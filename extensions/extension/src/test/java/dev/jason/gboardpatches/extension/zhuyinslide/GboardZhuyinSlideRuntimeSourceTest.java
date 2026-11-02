package dev.jason.gboardpatches.extension.zhuyinslide;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class GboardZhuyinSlideRuntimeSourceTest {
    @Test
    public void reflectionSupportUsesOnlyTheExact1853ActionAndPointerContract()
            throws Exception {
        String source = readSource("GboardZhuyinSlideRuntimeSupport.java");

        assertTrue(source.contains("com.google.android.libraries.inputmethod.metadata.SoftKeyDef"));
        assertTrue(source.contains("ACTION_TYPE_CLASS = \"qae\""));
        assertTrue(source.contains("com.google.android.libraries.inputmethod.metadata.ActionDef"));
        assertTrue(source.contains("ACTION_ENTRY_CLASS = \"qbc\""));
        assertTrue(source.contains("ACTION_BUILDER_CLASS = \"qaf\""));
        assertTrue(source.contains("METADATA_BUILDER_CLASS = \"qcv\""));
        assertTrue(source.contains("INTENTION_CLASS = \"qbb\""));
        assertTrue(source.contains("POINTER_TRACKER_CLASS = \"qin\""));
        assertTrue(source.contains("getDeclaredMethod(\"s\", actionSetClass, long.class)"));
        assertTrue(source.contains("actionSetClass.getDeclaredField(\"h\")"));
        assertTrue(source.contains("actionSetClass.getDeclaredField(\"i\")"));
        assertTrue(source.contains("actionSetClass.getDeclaredField(\"h\")"));
        assertTrue(source.contains("actionSetClass.getDeclaredMethod(\"g\", actionTypeClass)"));
        assertTrue(source.contains("actionSetClass.getDeclaredMethod(\"h\", actionTypeClass)"));
        assertTrue(source.contains("pointerTrackerClass.getDeclaredField(\"m\")"));
        assertTrue(source.contains("pointerTrackerClass.getDeclaredMethod(\"i\")"));
        assertTrue(source.contains("pointerTrackerClass.getDeclaredMethod(")
                && source.contains("\"h\", float.class, float.class, actionTypeClass"));
        assertFalse(source.contains("ACTION_SET_CLASS = \"owd\""));
        assertFalse(source.contains("POINTER_TRACKER_CLASS = \"pbl\""));

        for (String stale : new String[] {
                "oaa", "nxi", "nxl", "nyf", "nxj", "nzv", "nye", "ofk", "ofi"
        }) {
            assertFalse("Found stale target name " + stale, source.contains("\"" + stale + "\""));
        }
    }

    @Test
    public void runtimeUsesWeakIdentityForMetadataCachesMarkersAndPointerOwners()
            throws Exception {
        String source = readSource("GboardZhuyinSlideRuntime.java");

        assertTrue(source.contains("WeakIdentityMap<Object> patchedMetadataByOriginal"));
        assertTrue(source.contains("WeakIdentityMap<Boolean> patchedMetadataMarkers"));
        assertTrue(source.contains("WeakIdentityMap<Boolean> unpatchedMetadataMarkers"));
        assertTrue(source.contains("WeakIdentityMap<PointerAnchor> pointerAnchors"));
        assertTrue(source.contains("System.identityHashCode(referent)"));
        assertFalse(source.contains("WeakHashMap"));
    }

    @Test
    public void pointerContractCapturesBeforeSuppressionAndFailsOpenOnErrors()
            throws Exception {
        String source = readSource("GboardZhuyinSlideRuntime.java");
        String method = methodBody(source,
                "public static boolean maybeCaptureAndShouldSuppressRetarget(",
                "public static void clearPointerState(");

        int anchorLookup = method.indexOf("pointerAnchors.get(tracker)");
        int stockOwnerLookup = method.indexOf("handles.hasCurrentOwner(tracker)");
        int markerLookup = method.indexOf("isPatchedMetadata(metadata)");
        int capture = method.indexOf("pointerAnchors.put(tracker");
        int resolve = method.indexOf("handles.resolveGestureAction(tracker, x, y)");
        assertTrue(anchorLookup >= 0);
        assertTrue(stockOwnerLookup > anchorLookup);
        assertTrue(markerLookup > stockOwnerLookup);
        assertTrue(capture > markerLookup);
        assertTrue(resolve > capture);
        assertTrue(method.contains("catch (Throwable ignored)"));
        assertTrue(method.contains("return false;"));
    }

    @Test
    public void markerPropagationRequiresAWeakIdentityMarkedSourceAndDistinctTarget()
            throws Exception {
        String source = readSource("GboardZhuyinSlideRuntime.java");
        String method = methodBody(source,
                "public static void inheritPatchedMetadata(",
                "public static boolean maybeCaptureAndShouldSuppressRetarget(");

        assertTrue(method.contains("source == null || target == null || source == target"));
        assertTrue(method.contains("!isPatchedMetadata(source)"));
        assertTrue(method.contains("patchedMetadataMarkers.put(target, Boolean.TRUE)"));
        assertFalse(method.contains("equals("));
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start);
        return source.substring(start, end);
    }

    private static String readSource(String fileName) throws Exception {
        Path path = Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/zhuyinslide/" + fileName);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
