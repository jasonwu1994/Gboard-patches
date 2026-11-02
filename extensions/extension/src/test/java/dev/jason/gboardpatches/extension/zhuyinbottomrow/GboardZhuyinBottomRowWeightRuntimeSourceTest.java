package dev.jason.gboardpatches.extension.zhuyinbottomrow;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardZhuyinBottomRowWeightRuntimeSourceTest {
    @Test
    public void runtimeSupportUsesExact1853MetadataContractWithoutBaselineFallback()
            throws Exception {
        String source = readRuntimeSupportSource();

        Assert.assertTrue(source.contains(
                "private static final String ACTION_TYPE_CLASS = \"qae\";"));
        Assert.assertTrue(source.contains(
                "\"com.google.android.libraries.inputmethod.metadata.SoftKeyDef\""));
        Assert.assertTrue(source.contains(
                "\"com.google.android.libraries.inputmethod.metadata.ActionDef\""));
        Assert.assertTrue(source.contains(
                "private static final String ACTION_DATA_CLASS = \"qbc\";"));
        Assert.assertTrue(source.contains("softKeyViewClass.getDeclaredField(\"e\")"));
        Assert.assertTrue(source.contains("actionSetClass.getDeclaredField(\"i\")"));
        Assert.assertTrue(source.contains(
                "actionSetClass.getDeclaredMethod(\"g\", actionTypeClass)"));
        Assert.assertTrue(source.contains("actionDefClass.getDeclaredField(\"f\")"));
        Assert.assertTrue(source.contains("actionDataClass.getDeclaredField(\"e\")"));
        Assert.assertFalse(source.contains("\"nxi\""));
        Assert.assertFalse(source.contains("\"oaa\""));
        Assert.assertFalse(source.contains("\"nxl\""));
        Assert.assertFalse(source.contains("\"nyf\""));
        Assert.assertFalse(source.contains("getDeclaredField(\"n\")"));
        Assert.assertFalse(source.contains("getDeclaredMethod(\"a\", actionTypeClass)"));
        Assert.assertFalse(source.contains("= \"owd\""));
        Assert.assertFalse(source.contains("= \"oth\""));
    }

    @Test
    public void runtimeResolvesOriginalMetadataBeforeInspectingReceiverAndFooterLabels()
            throws Exception {
        String source = readRuntimeSource();

        Assert.assertTrue(source.contains(
                "resolveKeyMetadataForInterop(handles.softKeyMetadataField.get(softKeyView))"));
        Assert.assertTrue(source.contains(
                "resolveKeyMetadataForInterop(handles.softKeyMetadataField.get(keyView))"));
    }

    @Test
    public void runtimeValidatesAllLayoutParamsBeforeMutatingAnyWeight() throws Exception {
        String source = readRuntimeSource();

        Assert.assertTrue(source.contains(
                "LinearLayout.LayoutParams[] paramsByTarget = resolveLinearLayoutParams(weightTargets);"));
        Assert.assertTrue(source.contains("if (paramsByTarget == null) {"));
        Assert.assertTrue(source.contains(
                "private static LinearLayout.LayoutParams[] resolveLinearLayoutParams("));
    }

    private static String readRuntimeSource() throws Exception {
        return new String(
                Files.readAllBytes(
                        Path.of(
                                "src/main/java/dev/jason/gboardpatches/extension/"
                                        + "zhuyinbottomrow/GboardZhuyinBottomRowWeightRuntime.java")),
                StandardCharsets.UTF_8);
    }

    private static String readRuntimeSupportSource() throws Exception {
        return new String(
                Files.readAllBytes(
                        Path.of(
                                "src/main/java/dev/jason/gboardpatches/extension/"
                                        + "zhuyinbottomrow/"
                                        + "GboardZhuyinBottomRowWeightRuntimeSupport.java")),
                StandardCharsets.UTF_8);
    }
}
