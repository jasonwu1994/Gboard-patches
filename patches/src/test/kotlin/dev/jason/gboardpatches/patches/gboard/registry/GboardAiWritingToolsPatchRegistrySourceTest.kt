package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803AutoFixAcceptancePatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803AutoFixRoutePatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803GenAiInitPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803GenAiRefreshPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingTools1803SmartEditInitPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.writingtools.gboardAiWritingToolsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
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
        val contributionWiring = Files.readString(
            Path.of(
                "src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                    "GboardContributionWiring.kt",
            ),
        )

        assertTrue(source.contains("val gboardAiWritingToolsPatch = gboardPublicResourcePatch("))
        assertTrue(source.contains("name = \"AI Writing Tools\""))
        assertTrue(source.contains("description = \"啟用 AI 撰寫工具，支援所有語言\\nEnable AI writing tools with support for all languages.\""))
        assertTrue(source.contains("default = true"))
        assertTrue(source.contains("gboardAiWritingToolsFeatureMarkerPatch"))
        assertTrue(source.contains("gboardAiWritingToolsFlagValuePatch"))
        assertTrue(source.contains("gboardAiWritingTools1803AutoFixRoutePatch"))
        assertTrue(source.contains("gboardAiWritingTools1803GenAiRefreshPatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsFlagValuePatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsSettingsVisibilityPatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsSignalPatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsVoiceCommandPatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsOfficialPreferencesPatch"))
        assertFalse(contributionWiring.contains("gboardAiWritingToolsBackendFactoryPatch"))
        assertFalse(source.contains("gboardAiWritingToolsDependencyPatch"))
        assertFalse(source.contains("gboardAiWritingToolsTracePatch"))
    }

    @Test
    fun formal1803AdmissionKeepsOnlyTheTypedFlagContribution() {
        assertEquals(
            setOf("ai_writing_tools.flag"),
            GboardTargetAdmission.admittedContributionIdsByFeature
                .getValue("ai_writing_tools"),
        )
        assertEquals(
            listOf(
                gboardPatchesSettingsPatch,
                gboardAiWritingToolsFeatureMarkerPatch,
                gboardAiWritingToolsFlagValuePatch,
                gboardAiWritingTools1803AutoFixRoutePatch,
                gboardAiWritingTools1803AutoFixAcceptancePatch,
                gboardAiWritingTools1803GenAiInitPatch,
                gboardAiWritingTools1803GenAiRefreshPatch,
                gboardAiWritingTools1803SmartEditInitPatch,
            ),
            gboardAiWritingToolsPatch.dependencies.toList(),
        )
    }

    @Test
    fun aiWritingToolsInventoryAndActiveSourcesHaveNoRetiredFallbackContracts() {
        val repositoryRoot = Path.of("..").toAbsolutePath().normalize()
        val patches = generatedPublishedPatches()
        assertMatchesPublishedRegistrationCount(patches.size)
        val writingTools = patches.single { patch ->
            patch.get("name").asString == "AI Writing Tools"
        }
        assertTrue(writingTools.get("use").asBoolean)
        assertTrue(writingTools.getAsJsonArray("dependencies").isEmpty)
        assertEquals(
            listOf("18.0.3.954559732-release-arm64-v8a"),
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
        assertTrue(
            generatedBindings.keySet().none { key -> key.startsWith("ai_writing_tools_") },
        )

        val contributionWiring = Files.readString(
            repositoryRoot.resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                    "GboardContributionWiring.kt",
            ),
        )
        assertFalse(contributionWiring.contains("ai_writing_tools.bytecode"))
    }
}
