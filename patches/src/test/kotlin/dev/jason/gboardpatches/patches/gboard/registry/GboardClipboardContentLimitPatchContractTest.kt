package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.clipboardcontentlimit.gboardClipboardContentLimitFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Test

class GboardClipboardContentLimitPatchContractTest {
    @Test
    fun `content limit stays an independent public patch`() {
        val patch = gboardClipboardContentLimitPatch
        assertEquals("Clipboard Custom Character Limit", patch.name)
        assertEquals(listOf(gboardPatchesSettingsPatch, gboardClipboardContentLimitFeatureMarkerPatch,
            gboardClipboardContentLimitFlagValuePatch), patch.dependencies.toList())
        assertTrue(patch.dependencies.none { it === gboardClipboardEnhancementsPatch })
        assertSame(patch, GboardPublishedPatchCatalog.morpheRegistrations.single {
            it.name == "Clipboard Custom Character Limit"
        })
    }

    @Test
    fun `marker runtime and settings use the dedicated boundary`() {
        val marker = read(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "clipboardcontentlimit/GboardClipboardContentLimitFeatureMarkerPatch.kt",
        )
        val runtime = read(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "clipboardcontentlimit/GboardClipboardContentLimitRuntime.java",
        )
        val policy = read(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "clipboardcontentlimit/GboardClipboardContentLimitPolicy.java",
        )
        val settings = read(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "clipboardcontentlimit/GboardClipboardContentLimitSettings.java",
        )

        assertTrue(marker.contains(
            "dev.jason.gboardpatches.feature.clipboard_content_limit",
        ))
        assertTrue(policy.contains("text_clip_item_char_limit"))
        assertTrue(runtime.contains("stockResult instanceof Long"))
        assertTrue(settings.contains("gboard_clipboard_content_limit"))
        assertTrue(settings.contains("DEFAULT_MAX_CHARACTERS = 20_000"))
    }

    private fun read(relative: String): String =
        Files.readString(repositoryRoot().resolve(relative))

    private fun repositoryRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
}
