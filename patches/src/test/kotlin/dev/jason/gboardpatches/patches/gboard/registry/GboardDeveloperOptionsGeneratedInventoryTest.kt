package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardDeveloperOptionsGeneratedInventoryTest {
    @Test
    fun generatedInventoryContainsTwentyFourPublishedPatchesAndNoRetiredRows() {
        val repositoryRoot = findRepositoryRoot()
        val inventory = JsonParser.parseString(
            String(
                Files.readAllBytes(repositoryRoot.resolve("patches-list.json")),
                StandardCharsets.UTF_8,
            ),
        ).asJsonObject
        val patches = inventory.getAsJsonArray("patches")
        val names = patches.map { it.asJsonObject.get("name").asString }

        assertEquals(25, patches.size())
        RETIRED_PATCH_NAMES.forEach { retiredName ->
            assertFalse("Retired patch must stay absent: $retiredName", names.contains(retiredName))
        }
        SURVIVING_PATCH_NAMES.forEach { survivorName ->
            assertTrue("Surviving patch missing: $survivorName", names.contains(survivorName))
        }

    }

    private fun findRepositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("README.md")) &&
                    Files.isRegularFile(candidate.resolve("patches-list.json"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        val RETIRED_PATCH_NAMES = listOf(
            "Clipboard Entity Extraction",
            "Clipboard Item Edit",
            "Chinese Online Voice Input",
            "Enable Undo/Redo feature",
        )
        val SURVIVING_PATCH_NAMES = listOf(
            "AI Writing Tools",
            "Add Gboard Signature Bypass",
            "Advanced Voice Typing",
            "Clipboard Enhancements",
            "Clipboard Custom Character Limit",
            "Custom Symbols",
            "Developer options",
            "Emojis, stickers & GIFs Tab Order",
            "Enable Inline Autofill Suggestions",
            "English QWERTY Up-Flick Uppercase",
            "Grammar Checker",
            "Inline Suggestions",
            "Key Shape Selection",
            "Latin Globe Key Ignore Interval",
            "Long-Press Editing Shortcuts",
            "Package Rename",
            "Settings Homepage Override",
            "Swipeable Custom Top Row",
            "Use Bluetooth Microphone",
            "Web Clipboard",
            "Zhuyin Bottom Row Key Sizes",
            "Zhuyin Quick Traditional/Simplified Toggle",
            "Zhuyin Slide Input",
        )
    }
}
