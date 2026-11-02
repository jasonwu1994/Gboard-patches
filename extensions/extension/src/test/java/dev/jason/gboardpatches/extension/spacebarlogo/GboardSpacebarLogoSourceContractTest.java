package dev.jason.gboardpatches.extension.spacebarlogo;

import dev.jason.gboardpatches.extension.zhuyinslide.GboardZhuyinSlideTargetFixture;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class GboardSpacebarLogoSourceContractTest {
    private final Path root = Path.of(".").toAbsolutePath().normalize();

    @Test
    public void versionAdapterOwnsAllSymbols() throws Exception {
        String source = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoAdapter.java"));

        Assert.assertTrue(source.contains("ACTION_TYPE_CLASS = \"qae\""));
        Assert.assertTrue(source.contains(
                "com.google.android.libraries.inputmethod.metadata.ActionDef"));
        Assert.assertTrue(source.contains("ACTION_ENTRY_CLASS = \"qbc\""));
        Assert.assertTrue(source.contains("SPACEBAR_VIEW_ID = 0x7f0b05b5"));
        Assert.assertTrue(source.contains("PRIMARY_LABEL_VIEW_ID = 0x7f0b05f5"));
        Assert.assertTrue(source.contains("GOOGLE_G_DRAWABLE_ID = 0x7f080467"));
        Assert.assertTrue(source.contains("HEART_DRAWABLE_ID = 0x7f080452"));
        Assert.assertTrue(source.contains("EMOJI_DRAWABLE_ID = 0x7f0804a0"));
    }

    @Test
    public void productRuntimeIsVersionNeutralAndTracksWeakViews() throws Exception {
        String source = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoRuntime.java"));

        Assert.assertTrue(source.contains("WeakHashMap"));
        Assert.assertTrue(source.contains("IMPORTANT_FOR_ACCESSIBILITY_NO"));
        Assert.assertTrue(source.contains("setVisibility(View.INVISIBLE)"));
        Assert.assertFalse(source.contains("0x7f"));
        Assert.assertFalse(source.contains("Class.forName"));
    }

    @Test
    public void keyboardGroupKeepsGLogoFirst() throws Exception {
        String source = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/keyboard/" +
                        "GboardKeyboardLayoutSettingsGroupFeature.java"));
        int list = source.indexOf("Arrays.asList(");
        int logo = source.indexOf("new GboardSpacebarLogoSettingsFeature", list);
        int next = source.indexOf("new GboardLatinGlobeKeyIgnoreIntervalSettingsFeature", list);

        Assert.assertTrue(logo > list);
        Assert.assertTrue(logo < next);
    }

    @Test
    public void featureDoesNotOwnSharedKeyboardPreview() throws Exception {
        Path featureRoot = root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo");
        try (var files = Files.walk(featureRoot)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Assert.assertFalse(read(file).contains("KeyboardPreview"));
            }
        }
    }

    @Test
    public void decoderAcceptsOnlyPressActionKeyCode62() throws Exception {
        Object space = targetMetadataWithKeyCode(62);
        Object enter = targetMetadataWithKeyCode(66);
        Class<?> metadataClass = space.getClass();
        java.lang.reflect.Method resolver = GboardSpacebarLogoAdapter.class
                .getDeclaredMethod("decoder", ClassLoader.class);
        resolver.setAccessible(true);
        Object decoder = resolver.invoke(null, metadataClass.getClassLoader());
        java.lang.reflect.Method method = decoder.getClass()
                .getDeclaredMethod("isSpaceAction", Object.class);
        method.setAccessible(true);

        Assert.assertEquals(Boolean.TRUE, method.invoke(decoder, space));
        Assert.assertEquals(Boolean.FALSE, method.invoke(decoder, enter));
    }

    @Test
    public void incomingSpaceClassificationSurvivesReturnMetadataMutation() throws Exception {
        Object incomingSpace = targetMetadataWithKeyCode(62);
        Object mutatedReturnMetadata = targetMetadataWithKeyCode(66);
        Object receiver = new Object();

        GboardSpacebarLogoAdapter.class
                .getMethod("beforeSoftKeyBound", Object.class, Object.class)
                .invoke(null, receiver, incomingSpace);
        java.lang.reflect.Method resolver = GboardSpacebarLogoAdapter.class
                .getDeclaredMethod("resolveSpaceAction", Object.class, Object.class);
        resolver.setAccessible(true);

        Assert.assertEquals(Boolean.TRUE,
                resolver.invoke(null, receiver, mutatedReturnMetadata));
    }

    private static Object targetMetadataWithKeyCode(int keyCode) throws Exception {
        java.lang.reflect.Field metadataClassField = GboardZhuyinSlideTargetFixture.class
                .getDeclaredField("METADATA_CLASS");
        metadataClassField.setAccessible(true);
        Class<?> metadataClass = (Class<?>) metadataClassField.get(null);
        return metadataClass.getMethod("actionWithKeyCode", int.class, int.class)
                .invoke(null, Integer.valueOf(1), Integer.valueOf(keyCode));
    }

    @Test
    public void runtimeAndDialogCallbacksFailClosedAtHostBoundaries() throws Exception {
        String adapter = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoAdapter.java"));
        String runtime = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoRuntime.java"));
        String dialogs = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoDialogs.java"));
        String picker = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoColorPickerView.java"));

        Assert.assertTrue(adapter.contains("restoreDisabledAppearanceSafely"));
        Assert.assertTrue(adapter.contains("forgetIncomingSpaceActionSafely"));
        Assert.assertTrue(runtime.contains("safeRestoreStockAppearance"));
        Assert.assertTrue(runtime.contains("Failed to refresh bound spacebar"));
        Assert.assertTrue(runtime.contains("softKeyView.post(() ->"));
        Assert.assertTrue(runtime.contains("refreshBoundSpacebarSafely"));
        Assert.assertTrue(runtime.contains("Failed to schedule bound spacebar refresh"));
        Assert.assertTrue(dialogs.contains("runSafely(\"handle icon selection\""));
        Assert.assertTrue(dialogs.contains("runSafely(\"save custom color\""));
        Assert.assertTrue(dialogs.contains("runSafely(\"dismiss spacebar logo dialog\""));
        Assert.assertTrue(picker.contains("Failed to handle color picker touch"));
        Assert.assertTrue(picker.contains("Failed to notify color picker listener"));
    }

    @Test
    public void dialogsKeepEqualTileHeightsAndAccessibleTouchTargets() throws Exception {
        String dialogs = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoDialogs.java"));
        String picker = read(root.resolve(
                "src/main/java/dev/jason/gboardpatches/extension/spacebarlogo/" +
                        "GboardSpacebarLogoColorPickerView.java"));

        Assert.assertTrue(dialogs.contains("ICON_TILE_HEIGHT_DP = 120"));
        Assert.assertTrue(dialogs.contains("COLOR_TILE_HEIGHT_DP = 136"));
        Assert.assertTrue(dialogs.contains("setSelected(selected)"));
        Assert.assertTrue(dialogs.contains("RippleDrawable"));
        Assert.assertTrue(picker.contains("hueHeight = dp(48)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
