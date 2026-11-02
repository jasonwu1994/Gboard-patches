package dev.jason.gboardpatches.extension.customtheme;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardCustomThemeOfficialImportSourceTest {
    @Test
    public void officialThemePlusOffersPhotoAndZipSources() throws Exception {
        String runtime = read("GboardCustomThemeOfficialImportRuntime.java");

        Assert.assertTrue(runtime.contains("interceptPlus"));
        Assert.assertTrue(runtime.contains("Intent.ACTION_OPEN_DOCUMENT"));
        Assert.assertTrue(runtime.contains("application/zip"));
        Assert.assertTrue(runtime.contains("invokeStockPlus"));
    }

    @Test
    public void activityResultReturnsCommittedFileToStockThemeBuilder() throws Exception {
        String runtime = read("GboardCustomThemeOfficialImportRuntime.java");
        String importer = read("GboardCustomThemeZipImporter.java");

        Assert.assertTrue(runtime.contains("handleActivityResult"));
        Assert.assertTrue(runtime.contains("intent_extra_key_new_theme_file_name"));
        Assert.assertTrue(runtime.contains("GboardCustomThemeTargetContract.deliverImportedTheme("));
        Assert.assertTrue(importer.contains("GboardCustomThemeInstaller.importArchive"));
    }

    @Test
    public void innerThemeChooserUsesPatchLanguageAuthority() throws Exception {
        String importer = read("GboardCustomThemeZipImporter.java");

        Assert.assertTrue(importer.contains("GboardSettingsText.get(activity,"));
        Assert.assertTrue(importer.contains(
                "R.string.gboard_patches_custom_theme_choose_import"));
        Assert.assertFalse(importer.contains(".setTitle(\"Choose a theme to import\")"));
    }

    @Test
    public void zipImporterOwnsEveryAsyncDialogAndCompletionFailureBoundary()
            throws Exception {
        String importer = read("GboardCustomThemeZipImporter.java");
        String executor = methodBody(importer,
                "private static boolean executeSafely(",
                "    private static boolean postSafely(");
        String mainPost = methodBody(importer,
                "private static boolean postSafely(",
                "    private static void postFailureSafely(");
        String committed = methodBody(importer,
                "private static void notifyCommittedSafely(",
                "    private static void notifyFailureSafely(");
        String failed = methodBody(importer,
                "private static void notifyFailureSafely(",
                "    private static void invokeSafely(");
        String deletion = methodBody(importer,
                "private static void safeDelete(",
                "    private static void reportFailure(");

        Assert.assertEquals(1, occurrences(importer, "EXECUTOR.execute("));
        Assert.assertTrue(executor.contains("try {"));
        Assert.assertTrue(executor.contains("catch (Throwable failure)"));
        Assert.assertEquals(1, occurrences(importer, "main.post("));
        Assert.assertTrue(mainPost.contains("try {"));
        Assert.assertTrue(mainPost.contains("invokeSafely(operation, callback)"));
        Assert.assertTrue(importer.contains("dialog.show();"));
        Assert.assertTrue(importer.contains("handleSelectionSafely("));
        Assert.assertTrue(importer.contains("\"cancel theme chooser\""));
        Assert.assertTrue(importer.contains("\"dismiss theme chooser\""));
        Assert.assertEquals(1, occurrences(importer, ".delete();"));
        Assert.assertTrue(deletion.contains("catch (Throwable failure)"));
        Assert.assertTrue(committed.contains("try {"));
        Assert.assertTrue(committed.contains("completion.onCommitted(fileName);"));
        Assert.assertTrue(failed.contains("try {"));
        Assert.assertTrue(failed.contains("completion.onFailure(failure);"));
    }

    @Test
    public void customResultIsConsumedWhenTheVersionSpecificAdapterFails() throws Exception {
        String runtime = read("GboardCustomThemeOfficialImportRuntime.java");

        int handlerStart = runtime.indexOf("public static boolean handleActivityResult");
        int handlerEnd = runtime.indexOf("    private static void invokeStockPlus", handlerStart);
        String handler = runtime.substring(handlerStart, handlerEnd);

        Assert.assertTrue(handler.contains("if (requestCode != IMPORT_REQUEST_CODE) return false;"));
        Assert.assertTrue(handler.contains("showFailure(findActivity(fragment));"));
        Assert.assertFalse(handler.contains("} catch (Throwable failure) {\n            return false;"));
        Assert.assertTrue(handler.contains("} catch (Throwable failure) {\n            // This request code belongs to the patch."));
        Assert.assertTrue(handler.contains("return true;"));
    }

    private static String read(String fileName) throws Exception {
        return new String(Files.readAllBytes(Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/customtheme/" + fileName)),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String methodBody(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        Assert.assertTrue("Missing method start: " + startMarker, start >= 0);
        Assert.assertTrue("Missing method end: " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }
}
