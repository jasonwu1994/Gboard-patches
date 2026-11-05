package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardEnglishQwertyPatchRegistrySourceTest {
    @Test
    fun registryRenamesEnglishPatchToUppercaseToggle() {
        val source = readSource()

        assertTrue(source.contains("val gboardEnglishQwertySlideUppercaseTogglePatch = gboardPublicResourcePatch("))
        assertTrue(source.contains("name = \"English QWERTY Up-Flick Uppercase\""))
        assertTrue(source.contains("description = \"英文 QWERTY 鍵盤上滑大小寫\\n\" +"))
        assertTrue(source.contains(
            "\"Flick up on the English QWERTY keyboard to toggle uppercase and lowercase.\\n\" +",
        ))
        assertTrue(source.contains(
            "\"Balayer vers le haut sur le clavier QWERTY anglais pour basculer entre " +
                "majuscules et minuscules.\"",
        ))
        assertFalse(source.contains("name = \"English QWERTY Slide Uppercase Toggle\""))
        assertFalse(source.contains("name = \"English QWERTY Slide Symbols\""))
        assertFalse(source.contains("slide-up and slide-down symbol input"))
    }

    @Test
    fun englishPatchUsesBindTimeSoftKeyAndPointerImplementationWithoutResourceMutation() {
        val source = readSource()
        val wiring = Files.readString(
            Path.of(
                "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                    "GboardContributionWiring.kt",
            ),
            StandardCharsets.UTF_8,
        )

        val patchStart = source.indexOf("val gboardEnglishQwertySlideUppercaseTogglePatch")
        val nextPatchStart = source.indexOf("val gboardZhuyinQuickTraditionalSimplifiedTogglePatch")
        assertTrue("English QWERTY patch missing", patchStart >= 0)
        assertTrue("Expected following patch boundary", nextPatchStart > patchStart)

        val patchBlock = source.substring(patchStart, nextPatchStart)
        assertTrue(patchBlock.contains("gboardAboutPageResourcePatch"))
        assertTrue(patchBlock.contains("gboardPatchesSettingsPatch"))
        assertTrue(patchBlock.contains("gboardEnglishUppercaseToggleFeatureMarkerPatch"))
        assertTrue(wiring.contains("gboardEnglishQwertySoftKeyPatch"))
        assertTrue(wiring.contains("gboardEnglishQwertyPointerPatch"))
        assertFalse(patchBlock.contains("gboardZhuyin"))

        val dependencyBlock = patchBlock.substringAfter("dependsOn(").substringBefore(")")
        val dependencies = Regex("""gboard[A-Za-z0-9]+Patch""")
            .findAll(dependencyBlock)
            .map { match -> match.value }
            .toSet()
        assertEquals(
            setOf(
                "gboardAboutPageResourcePatch",
                "gboardPatchesSettingsPatch",
                "gboardEnglishUppercaseToggleFeatureMarkerPatch"
            ),
            dependencies
        )
    }

    @Test
    fun englishPatchUsesInAppSettingsInsteadOfManagerPatchOptions() {
        val source = readSource()

        val patchStart = source.indexOf("val gboardEnglishQwertySlideUppercaseTogglePatch")
        val nextPatchStart = source.indexOf("val gboardZhuyinQuickTraditionalSimplifiedTogglePatch")
        assertTrue("English QWERTY patch missing", patchStart >= 0)
        assertTrue("Expected following patch boundary", nextPatchStart > patchStart)

        val patchBlock = source.substring(patchStart, nextPatchStart)
        assertFalse(patchBlock.contains("booleanOption("))
        assertFalse(patchBlock.contains("title = \"啟用英文鍵盤上滑大寫\""))
        assertFalse(patchBlock.contains("\"啟用\" to true"))
    }

    private fun readSource(): String =
        String(
            Files.readAllBytes(
                Path.of(
                    "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                        "GboardPatchRegistry.kt"
                )
            ),
            StandardCharsets.UTF_8
        )
}
