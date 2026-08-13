package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardOcrPatchContractTest {
    @Test
    fun `ocr is one independent public patch with settings marker and runtime slices`() {
        val root = repositoryRoot()
        val registry = read(root, REGISTRY)
        val keyboardGroup = read(root, KEYBOARD_GROUP)
        val availability = read(root, AVAILABILITY)
        val settingsText = read(root, SETTINGS_TEXT)
        val readme = read(root, README)

        assertTrue(registry.contains("val gboardOcrScanTextPatch = resourcePatch("))
        assertTrue(registry.contains("name = \"Enable OCR / Scan Text\""))
        assertTrue(registry.contains(
            "Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and " +
                "Devanagari recognition backends.",
        ))
        assertTrue(registry.contains("gboardOcrFeatureMarkerPatch"))
        assertTrue(registry.contains("gboardOcrFlagValuePatch"))
        assertTrue(registry.contains("gboardOcrRuntimePatch"))
        assertTrue(keyboardGroup.contains("new GboardOcrSettingsFeature(context)"))
        assertTrue(
            keyboardGroup.indexOf("new GboardLongPressQuickActionsSettingsFeature(context)") <
                keyboardGroup.indexOf("new GboardOcrSettingsFeature(context)"),
        )
        assertTrue(!keyboardGroup.contains("GboardLatinGlobeKeyIgnoreIntervalSettingsFeature"))
        assertTrue(availability.contains("FEATURE_OCR_SCAN_TEXT"))
        assertTrue(availability.contains("dev.jason.gboardpatches.feature.ocr_scan_text"))
        assertTrue(settingsText.contains("<translation locale=\"zh-Hant\">OCR / 掃描文字</translation>"))
        assertTrue(settingsText.contains(
            "<translation locale=\"en\">Control the backend engine used for text " +
                "recognition.</translation>",
        ))
        assertTrue(settingsText.contains("<translation locale=\"zh-Hant\">控制文字辨識的後端引擎。</translation>"))
        assertTrue(settingsText.contains("<translation locale=\"zh-Hant\">文字辨識後端引擎</translation>"))
        assertTrue(settingsText.contains("gboard_patches_ocr_header_summary"))
        assertTrue(settingsText.contains(
            "<translation locale=\"en\">Official models must be downloaded through Google " +
                "Play services.</translation>",
        ))
        assertTrue(settingsText.contains(
            "<translation locale=\"zh-Hant\">需要透過 Google Play services " +
                "下載官方模型</translation>",
        ))
        assertTrue(!settingsText.contains("gboard_patches_ocr_engine_summary"))
        assertTrue(
            readme.indexOf("<summary><code>Long-Press Editing Shortcuts</code></summary>") <
                readme.indexOf("<summary><code>Enable OCR / Scan Text</code></summary>"),
        )
        assertTrue(readme.contains(
            "Enable the OCR / Scan Text feature with Latin, Chinese, Japanese, Korean, and " +
                "Devanagari recognition backends.",
        ))
    }

    private fun read(root: Path, relative: String): String =
        Files.readString(root.resolve(relative), StandardCharsets.UTF_8)

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .first { Files.isRegularFile(it.resolve("patches-list.json")) }
    }

    private companion object {
        const val REGISTRY =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
        const val KEYBOARD_GROUP =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/keyboard/" +
                "GboardKeyboardToolsSettingsGroupFeature.java"
        const val AVAILABILITY =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesFeatureAvailability.java"
        const val SETTINGS_TEXT =
            "extensions/extension/src/main/settings-text/gboard_settings_text.xml"
        const val README = "README.md"
    }
}
