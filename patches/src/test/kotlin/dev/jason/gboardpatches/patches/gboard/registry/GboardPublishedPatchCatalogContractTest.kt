package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardPublishedPatchCatalogContractTest {
    @Test
    fun catalogExposesEachPublishedMorpheRegistrationExactlyOnce() {
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations

        assertEquals(25, registrations.size)
        assertEquals(25, registrations.map { patch -> patch.name }.distinct().size)
        assertSame(
            gboardInlineSuggestionsFlagPatch,
            registrations.single { patch -> patch.name == "Inline Suggestions" },
        )
        assertSame(
            gboardSignatureBypassPatch,
            registrations.single { patch -> patch.name == "Add Gboard Signature Bypass" },
        )
        assertSame(
            gboardOcrScanTextPatch,
            registrations.single { patch -> patch.name == "Enable OCR / Scan Text" },
        )
    }

    @Test
    fun catalogRendersThePublishedInventoryFromItsMorpheRegistrations() {
        val inventory = JsonParser.parseString(
            GboardPublishedPatchCatalog.publishedInventory("test-version"),
        ).asJsonObject
        val patches = inventory.getAsJsonArray("patches").map { element -> element.asJsonObject }

        assertEquals("test-version", inventory.get("version").asString)
        assertEquals(
            patches.map { patch -> patch.get("name").asString }.sorted(),
            patches.map { patch -> patch.get("name").asString },
        )

        val inlineSuggestions = patches.single { patch ->
            patch.get("name").asString == "Inline Suggestions"
        }
        assertTrue(inlineSuggestions.get("use").asBoolean)
        assertEquals(
            emptyList<String>(),
            inlineSuggestions.getAsJsonArray("dependencies").map { dependency ->
                dependency.asString
            },
        )
        assertEquals(
            listOf("17.7.7.932364120-release-arm64-v8a"),
            inlineSuggestions.getAsJsonObject("compatiblePackages")
                .getAsJsonArray("com.google.android.inputmethod.latin")
                .map { version -> version.asString },
        )
        assertTrue(inlineSuggestions.getAsJsonArray("options").isEmpty)
    }

    @Test
    fun catalogMatchesTheCheckedInPublishedInventory() {
        val expected = JsonParser.parseString(
            Files.readString(repositoryRoot().resolve("patches-list.json"), StandardCharsets.UTF_8),
        )
        val version = expected.asJsonObject.get("version").asString
        val actual = JsonParser.parseString(
            GboardPublishedPatchCatalog.publishedInventory(version),
        )

        assertEquals(expected, actual)
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { directory -> directory.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("patches-list.json"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }
}
