package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAiWritingToolsPatchRegistrySourceTest {
    @Test
    fun registryDefinesDedicatedAiWritingToolsPatch() {
        val source = String(
            Files.readAllBytes(
                Path.of(
                    "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                        "GboardPatchRegistry.kt"
                )
            ),
            StandardCharsets.UTF_8
        )

        assertTrue(source.contains("val gboardAiWritingToolsPatch = resourcePatch("))
        assertTrue(source.contains("name = \"AI Writing Tools\""))
        assertTrue(source.contains("description = \"啟用 AI 撰寫工具，支援所有語言\\nEnable AI writing tools with support for all languages.\""))
        assertTrue(source.contains("default = true"))
        assertTrue(source.contains("gboardAiWritingToolsFeatureMarkerPatch"))
        assertTrue(source.contains("gboardAiWritingToolsSettingsVisibilityPatch"))
        assertTrue(source.contains("gboardAiWritingToolsFlagValuePatch"))
        assertTrue(source.contains("gboardAiWritingToolsSignalPatch"))
        assertTrue(source.contains("gboardAiWritingToolsOfficialPreferencesPatch"))
        assertTrue(source.contains("gboardAiWritingToolsBackendFactoryPatch"))
        assertFalse(source.contains("gboardAiWritingToolsDependencyPatch"))
        assertFalse(source.contains("gboardAiWritingToolsTracePatch"))
    }

    @Test
    fun aiWritingToolsPatchUsesDedicatedFamilyInsteadOfGenericFeatureflagsOnly() {
        val source = String(
            Files.readAllBytes(
                Path.of(
                    "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                        "GboardPatchRegistry.kt"
                )
            ),
            StandardCharsets.UTF_8
        )

        val patchStart = source.indexOf("val gboardAiWritingToolsPatch")
        val nextPatchStart = source.indexOf("val gboardSettingsHomepagePatch")
        assertTrue("AI Writing Tools patch missing", patchStart >= 0)
        assertTrue("Expected following patch boundary", nextPatchStart > patchStart)

        val aiWritingToolsBlock = source.substring(patchStart, nextPatchStart)
        assertFalse(
            "AI Writing Tools patch should not fall back to the generic featureflags patch only",
            aiWritingToolsBlock.contains("gboardFeatureFlagsBytecodePatch")
        )
        assertFalse(
            "AI Writing Tools patch should not depend on the debug trace patch in public builds",
            aiWritingToolsBlock.contains("gboardAiWritingToolsTracePatch")
        )
    }

    @Test
    fun aiWritingToolsInventoryAndActiveSourcesHaveNoRetiredFallbackContracts() {
        val repositoryRoot = Path.of("..").toAbsolutePath().normalize()
        val inventory = JsonParser.parseString(
            Files.readString(repositoryRoot.resolve("patches-list.json")),
        ).asJsonObject
        val patches = inventory.getAsJsonArray("patches")
            .map { element -> element.asJsonObject }
        assertEquals(25, patches.size)
        val writingTools = patches.single { patch ->
            patch.get("name").asString == "AI Writing Tools"
        }
        assertTrue(writingTools.get("use").asBoolean)
        assertTrue(writingTools.getAsJsonArray("dependencies").isEmpty)
        assertEquals(
            listOf("17.7.7.932364120-release-arm64-v8a"),
            writingTools.getAsJsonObject("compatiblePackages")
                .getAsJsonArray("com.google.android.inputmethod.latin")
                .map { version -> version.asString },
        )

        val generatedBindings = JsonParser.parseString(
            Files.readString(
                repositoryRoot.resolve(
                    "patches/src/main/resources/gboard/gboard-version-bindings.json",
                ),
            ),
        ).asJsonObject.getAsJsonObject("bindings")
        assertFalse(generatedBindings.has("flag_factory"))
        assertTrue(generatedBindings.has("ai_writing_tools_gen_ai_init"))

        val activeSources = sequenceOf(
            repositoryRoot.resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/writingtools",
            ),
            repositoryRoot.resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "writingtools",
            ),
        ).flatMap { directory ->
            Files.walk(directory).use { files ->
                files.filter { path -> Files.isRegularFile(path) }
                    .map { path -> Files.readString(path) }
                    .toList()
                    .asSequence()
            }
        }.joinToString("\n")
        assertFalse(activeSources.contains("GboardAiWritingToolsDependency"))
        assertFalse(activeSources.contains("pref_ai_writing_tools_use_google_servers"))
        assertFalse(activeSources.contains("flag_factory"))
        RETIRED_TARGET_TOKENS.forEach { token ->
            assertFalse(
                "Retired Writing Tools target token remains active: $token",
                Regex("""\b${Regex.escape(token)}\b""").containsMatchIn(activeSources),
            )
        }
    }

    private companion object {
        val RETIRED_TARGET_TOKENS = listOf(
            "oil",
            "oii",
            "oql",
            "ovc",
            "hcv",
            "bze",
            "mpn",
            "oeb",
        )
    }
}
