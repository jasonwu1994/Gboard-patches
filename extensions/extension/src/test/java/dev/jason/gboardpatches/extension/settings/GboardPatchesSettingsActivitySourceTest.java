package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardPatchesSettingsActivitySourceTest {
    @Test
    public void activityRendersSectionsStatusBlocksAndExplicitPreviewAffordances()
            throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");

        Assert.assertTrue(source.contains("screen.getSections()"));
        Assert.assertTrue(source.contains("screen.getStatusBlocks()"));
        Assert.assertTrue(source.contains("showConfirmDialog("));
        Assert.assertFalse(source.contains("PREVIEW_HINT_PREFIX"));
        Assert.assertFalse(source.contains("bindPreviewLongPress("));
        Assert.assertTrue(source.contains("Palette.forConfiguration("));
        Assert.assertFalse(source.contains("R.color.gboard_patches_window_background"));
    }

    @Test
    public void contractAndActivitySupportVideoPreviewMedia() throws Exception {
        String contractSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsContract.java");
        String activitySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");

        Assert.assertTrue(contractSource.contains("class PreviewVideo"));
        Assert.assertTrue(contractSource.contains("class Section"));
        Assert.assertTrue(contractSource.contains("class StatusBlock"));
        Assert.assertTrue(activitySource.contains("VideoView"));
        Assert.assertTrue(activitySource.contains("showPreviewDialog("));
        Assert.assertTrue(activitySource.contains("buildPreviewMediaCard("));
        Assert.assertTrue(activitySource.contains("instanceof GboardPatchesSettingsContract.PreviewVideo"));
        Assert.assertTrue(activitySource.contains("getAssetPath()"));
        Assert.assertFalse(contractSource.contains("rawResourceId"));
        Assert.assertFalse(contractSource.contains("rawResourceName"));
        Assert.assertFalse(activitySource.contains("android.resource://"));
        Assert.assertFalse(activitySource.contains("getIdentifier(rawResourceName, \"raw\", getPackageName())"));
    }

    @Test
    public void selectorRowsShowValueInTrailingRailAndPreviewBelowSummary() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String selector = extractMethod(source, "private View createSelectorRow(");

        Assert.assertTrue(selector.contains("buildTrailingValue(rowModel.getCurrentValue())"));
        Assert.assertTrue(selector.contains("addPreviewBelowSummary(textColumn, rowModel)"));
        Assert.assertTrue(source.contains("R.string.gboard_patches_preview_label"));
        Assert.assertTrue(source.contains("R.string.gboard_patches_confirm_action"));
    }

    @Test
    public void shellUsesCodeDrawnIconsAndRestartBadge() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");

        Assert.assertFalse(source.contains("R.drawable."));
        Assert.assertTrue(source.contains("GboardPatchesIcons.create("));
        Assert.assertTrue(source.contains("GboardRestartPendingChanges.addListener("));
        Assert.assertTrue(source.contains("GboardRestartPendingChanges.removeListener("));
        Assert.assertTrue(source.contains("R.plurals.gboard_patches_restart_action_pending"));
        Assert.assertTrue(source.contains("WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS"));
        Assert.assertTrue(source.contains("row.isMasterToggleExplicit()"));
    }

    @Test
    public void latinGlobeFeatureUsesStableSettingsTextHelper() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/keyboard/"
                        + "GboardLatinGlobeKeyIgnoreIntervalSettingsFeature.java");

        Assert.assertTrue(source.contains("GboardSettingsText.get("));
    }

    @Test
    public void dialogsOnlyMarkManagedShownAfterSuccessfulShow() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");

        assertOccursInOrder(
                extractMethod(source, "public void showChoiceDialog("),
                "dialog.show();",
                "onManagedDialogShown();");
        assertOccursInOrder(
                extractMethod(source, "public void showPositiveIntegerDialog("),
                "dialog.show();",
                "onManagedDialogShown();");
        assertOccursInOrder(
                extractMethod(source, "public void showTextInputDialog("),
                "dialog.show();",
                "onManagedDialogShown();");
        assertOccursInOrder(
                extractMethod(source, "private void showPreviewSheet("),
                "dialog.show();",
                "onManagedDialogShown();");
        assertOccursInOrder(
                extractMethod(source, "private void showFullScreenViewer("),
                "viewer.show();",
                "onManagedDialogShown();");
        assertOccursInOrder(
                extractMethod(source, "private void showConfirmDialog("),
                "dialog.show();",
                "onManagedDialogShown();");
    }

    @Test
    public void choiceDialogIsBottomSheetCancelledByOutsideTapOrBack() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String method = extractMethod(source, "public void showChoiceDialog(");
        String sheet = extractMethod(source, "private Dialog buildBottomSheetDialog(");

        Assert.assertTrue(method.contains("buildBottomSheetDialog()"));
        Assert.assertTrue(sheet.contains("dialog.setCanceledOnTouchOutside(true);"));
        Assert.assertTrue(sheet.contains("window.setGravity(Gravity.BOTTOM);"));
        Assert.assertFalse(method.contains("android.R.string.cancel"));
    }

    @Test
    public void choiceDialogAllowsSelectionsWithoutACustomAction() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String method = extractMethod(source, "public void showChoiceDialog(");

        Assert.assertTrue(method.contains("customValue != null"));
        Assert.assertTrue(method.contains("customAction != null"));
        Assert.assertTrue(method.contains("customValue.equals(selectedValue)"));
        Assert.assertFalse(method.contains("if (customValue.equals(selectedValue))"));
    }

    @Test
    public void positiveIntegerDialogShowsFeatureRangeErrorsInline() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String method = extractMethod(source, "public void showPositiveIntegerDialog(");

        Assert.assertTrue(method.contains("catch (IllegalArgumentException exception)"));
        Assert.assertTrue(method.contains("input.setError(exception.getMessage());"));
    }

    @Test
    public void previewVideoErrorsAreHandledInsideSettingsHost() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String method = extractMethod(source, "private View buildPreviewVideoView(");

        Assert.assertTrue(method.contains("previewVideo.getAssetPath()"));
        Assert.assertTrue(method.contains("return true;"));
    }

    @Test
    public void mainThreadApplyScreenFallsBackToFeatureErrorScreen() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String method = extractMethod(source, "private void buildAndApplyCurrentScreen(");

        Assert.assertTrue(method.contains("applyScreen(finalScreen);"));
        Assert.assertTrue(method.contains(
                "Log.w(TAG, \"Failed to apply settings screen\", throwable);"));
        Assert.assertTrue(method.contains("buildFeatureErrorScreen("));
        Assert.assertTrue(method.contains("showFatalFallbackScreen(\"Failed to apply settings screen\""));
    }

    @Test
    public void featureNavigationRestoresParentScrollPositions() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String openFeature = extractMethod(source, "public void openFeature(");
        String navigateBack = extractMethod(source, "private boolean navigateBack(");
        String applyScreen = extractMethod(source, "private void applyScreen(");

        Assert.assertTrue(openFeature.contains(
                "scrollState.enterFeature(state.getCurrent() == null, currentScrollY())"));
        Assert.assertTrue(navigateBack.contains(
                "scrollState.leaveFeature(returningToRoot)"));
        Assert.assertTrue(applyScreen.contains(
                "consumeRequestedScrollPositionOnNextScreenApply()"));
        Assert.assertTrue(applyScreen.contains(
                "scrollContentToPositionAfterLayout(requestedScrollY)"));
    }

    @Test
    public void screenPrimaryActionRendersBelowScrollableContent() throws Exception {
        String contractSource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsContract.java");
        String activitySource = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String buildContent = extractMethod(activitySource, "private View buildContentView(");
        String applyScreen = extractMethod(activitySource, "private void applyScreen(");

        Assert.assertTrue(contractSource.contains("CommandRow getPrimaryAction()"));
        assertOccursInOrder(buildContent,
                "shell.addView(contentScrollView);",
                "shell.addView(screenActionContainer" );
        Assert.assertTrue(applyScreen.contains("screen.getPrimaryAction()"));
        Assert.assertTrue(applyScreen.contains("screenActionContainer.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void directFeaturePathsResetSyntheticParentScrollPositions() throws Exception {
        String source = readSource(
                "src/main/java/dev/jason/gboardpatches/extension/settings/"
                        + "GboardPatchesSettingsActivity.java");
        String openFeaturePath = extractMethod(source, "private void openFeaturePath(");
        String restoreNavigationPath = extractMethod(
                source,
                "private boolean restoreNavigationPathFromIntent(");

        Assert.assertTrue(openFeaturePath.contains(
                "scrollState.resetForDirectPath(sanitizedPath.size() - 1)"));
        Assert.assertTrue(restoreNavigationPath.contains(
                "scrollState.resetForDirectPath(resolvedPath.size() - 1)"));
    }

    private static String readSource(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private static String extractMethod(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        Assert.assertTrue("Missing method signature: " + signature, signatureIndex >= 0);

        int bodyStart = source.indexOf('{', signatureIndex);
        Assert.assertTrue("Missing method body start: " + signature, bodyStart >= 0);

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(signatureIndex, index + 1);
                }
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }

    private static void assertOccursInOrder(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        Assert.assertTrue("Missing snippet: " + first, firstIndex >= 0);
        Assert.assertTrue("Missing snippet: " + second, secondIndex >= 0);
        Assert.assertTrue(
                "Expected snippet order " + first + " before " + second,
                firstIndex < secondIndex);
    }
}
