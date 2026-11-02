package dev.jason.gboardpatches.extension.settings.keyboardpreview;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardKeyboardPreviewControllerSourceTest {
    private final Path root = Path.of(".").toAbsolutePath().normalize();

    @Test
    public void sharedShellOwnsSafeAreaLauncherAndFocusedRichContentEditor()
            throws Exception {
        String source = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/settings/keyboardpreview/" +
                        "GboardKeyboardPreviewController.java"));

        Assert.assertFalse(source.contains("TARGET_KEYBOARD_DRAWABLE_ID"));
        Assert.assertFalse(source.contains("R.drawable.ic_gboard_patches_keyboard"));
        Assert.assertTrue(source.contains("new KeyboardIconDrawable(iconColor)"));
        Assert.assertTrue(source.contains("canvas.drawRoundRect"));
        Assert.assertTrue(source.contains("drawKeyboardKey"));
        Assert.assertTrue(source.contains("BUTTON_SIZE_DP = 56"));
        Assert.assertTrue(source.contains("CONTENT_CLEARANCE_DP = 88"));
        Assert.assertTrue(source.contains("input.setSingleLine(true)"));
        Assert.assertTrue(source.contains("RichContentSinkEditText"));
        Assert.assertTrue(source.contains("\"image/gif\""));
        Assert.assertTrue(source.contains("\"image/*\""));
        Assert.assertTrue(source.contains("outAttrs.contentMimeTypes"));
        Assert.assertTrue(source.contains("boolean commitContent("));
        Assert.assertTrue(source.contains("input.requestFocus()"));
        Assert.assertTrue(source.contains("InputMethodManager.SHOW_IMPLICIT"));
        Assert.assertTrue(source.contains("SOFT_INPUT_STATE_ALWAYS_VISIBLE"));
        Assert.assertTrue(source.contains("runSafely(\"open keyboard preview\""));
        Assert.assertTrue(source.contains("runSafely(\"initialize keyboard preview dialog\""));
        Assert.assertTrue(source.contains("runSafely(\"show keyboard from preview\""));
        Assert.assertTrue(source.contains("runCallbackSafely"));
        Assert.assertTrue(source.contains("catch (Throwable throwable)"));
    }

    @Test
    public void patchesActivityHostsPreviewOutsideFeaturePackages() throws Exception {
        String source = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/settings/" +
                        "GboardPatchesSettingsActivity.java"));

        Assert.assertTrue(source.contains("GboardKeyboardPreviewController"));
        Assert.assertTrue(source.contains("keyboardPreviewController.attachTo(root)"));
        Assert.assertTrue(source.contains("contentBottomPadding(bottomInset)"));
        Assert.assertTrue(source.contains("updateSafeAreaInsets(resolveRightInset(insets)"));
        Assert.assertTrue(source.contains("detachKeyboardPreviewSafely"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
