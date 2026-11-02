package dev.jason.gboardpatches.extension.customtheme;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardCustomThemeVersionBindingsTest {
    @Test
    public void generatedRuntimeBindingsMatchReviewed1853Contract() {
        Assert.assertEquals("18.5.4", GboardCustomThemeVersionBindings.TARGET_VERSION);
        Assert.assertEquals("khr", GboardCustomThemeVersionBindings.PLUS_CLASS);
        Assert.assertEquals("f", GboardCustomThemeVersionBindings.PLUS_METHOD);
        Assert.assertEquals("khu", GboardCustomThemeVersionBindings.ADAPTER_CLASS);
        Assert.assertEquals(
                "com.google.android.apps.inputmethod.libs.theme.listing.ThemeListingFragment",
                GboardCustomThemeVersionBindings.RESULT_CLASS);
        Assert.assertEquals("X", GboardCustomThemeVersionBindings.RESULT_METHOD);
        Assert.assertEquals("a", GboardCustomThemeVersionBindings.FRAGMENT_PEER_FIELD);
        Assert.assertEquals("khl", GboardCustomThemeVersionBindings.PEER_CLASS);
        Assert.assertEquals("b", GboardCustomThemeVersionBindings.PEER_CONTEXT_FIELD);
        Assert.assertEquals("s", GboardCustomThemeVersionBindings.PEER_LAUNCHER_FIELD);
        Assert.assertEquals("i", GboardCustomThemeVersionBindings.PEER_RESULT_METHOD);
        Assert.assertEquals("srk", GboardCustomThemeVersionBindings.LAUNCHER_CLASS);
        Assert.assertEquals("H", GboardCustomThemeVersionBindings.LAUNCHER_METHOD);
        Assert.assertEquals("rnw", GboardCustomThemeVersionBindings.VALIDATOR_CLASS);
        Assert.assertEquals("h", GboardCustomThemeVersionBindings.VALIDATOR_METHOD);
    }

    @Test
    public void productionRuntimeKeepsObfuscatedNamesInsideGeneratedContract() throws Exception {
        Path root = Path.of("src/main/java/dev/jason/gboardpatches/extension/customtheme");
        String runtime = read(root.resolve("GboardCustomThemeOfficialImportRuntime.java"));
        String installer = read(root.resolve("GboardCustomThemeInstaller.java"));
        String adapter = read(root.resolve("GboardCustomThemeTargetContract.java"));

        Assert.assertFalse(runtime.contains("getDeclaredField(\"a\")"));
        Assert.assertFalse(runtime.contains("getDeclaredField(\"b\")"));
        Assert.assertFalse(runtime.contains("getDeclaredField(\"r\")"));
        Assert.assertFalse(runtime.contains("getDeclaredMethod(\"i\""));
        Assert.assertFalse(runtime.contains("getDeclaredMethod(\"K\""));
        Assert.assertFalse(installer.contains("Class.forName(\"qzi\""));
        Assert.assertTrue(adapter.contains("GboardCustomThemeVersionBindings"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
