package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardStagedReleasePatchContractTest {
    @Test
    fun formerlyStagedPatchesAreActiveAndPublishedExactlyOnce() {
        val root = repositoryRoot()
        val registry = Files.readString(root.resolve(REGISTRY_PATH), StandardCharsets.UTF_8)
        val activeRegistry = registry.replace(Regex("(?s)/\\*.*?\\*/"), "")
        val inventory = JsonParser.parseString(
            Files.readString(root.resolve("patches-list.json"), StandardCharsets.UTF_8),
        ).asJsonObject
        val names = inventory.getAsJsonArray("patches")
            .map { it.asJsonObject.get("name").asString }

        assertEquals(25, names.size)
        PUBLISHED_PATCHES.forEach { published ->
            assertTrue(registry.contains("val ${published.declaration} = resourcePatch("))
            assertTrue(activeRegistry.contains("val ${published.declaration} = resourcePatch("))
            assertEquals(1, names.count { it == published.name })
        }
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("patches-list.json")) &&
                    Files.isRegularFile(candidate.resolve(REGISTRY_PATH))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private data class PublishedPatch(
        val name: String,
        val declaration: String,
    )

    private companion object {
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"

        val PUBLISHED_PATCHES = listOf(
            PublishedPatch("Use Bluetooth Microphone", "gboardBluetoothMicrophonePatch"),
            PublishedPatch(
                "Long-Press Editing Shortcuts",
                "gboardLongPressQuickActionsPatch",
            ),
            PublishedPatch("Advanced Voice Typing", "gboardAdvancedVoiceTypingPatch"),
        )
    }
}
