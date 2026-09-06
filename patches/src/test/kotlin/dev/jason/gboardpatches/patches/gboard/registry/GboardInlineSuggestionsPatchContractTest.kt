package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardInlineSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardInlineSuggestionsPatchContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun inlineSuggestionsPublicPatchUsesExactMorpheMetadata() {
        val patch = gboardInlineSuggestionsFlagPatch

        assertEquals("Inline Suggestions", patch.name)
        assertTrue(patch.default)
        val compatibilities = checkNotNull(patch.compatibility)
        assertEquals(1, compatibilities.size)
        val compatibility = compatibilities.single()
        assertSame(COMPATIBILITY_GBOARD, compatibility)
        assertEquals(GBOARD_PACKAGE, compatibility.packageName)
        assertEquals(listOf(TARGET_VERSION), compatibility.targets.map { target -> target.version })
        assertTrue(compatibility.targets.none { target -> target.isExperimental })

        val expectedDependencies = listOf(
            gboardPatchesExtensionCarrierPatch,
            gboardInlineSuggestionsFlagValuePatch,
            gboardInlineSuggestionsFeatureMarkerPatch,
        )
        assertEquals(expectedDependencies.size, patch.dependencies.size)
        expectedDependencies.forEach { expected ->
            assertTrue(patch.dependencies.any { actual -> actual === expected })
        }
        assertTrue(
            patch.dependencies.all { actual ->
                expectedDependencies.any { expected -> actual === expected }
            },
        )
    }

    @Test
    fun inlineSuggestionsMarkerProducerMatchesTheRuntimeConsumerExactly() {
        assertFeatureFlagMarkerRuntimeContract(
            markerSource = readSource(INLINE_MARKER_PATH),
            availabilitySource = readSource(FEATURE_AVAILABILITY_PATH),
            runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH),
            contract = INLINE_CONTRACT,
        )
    }

    @Test
    fun inlineSuggestionsRuntimeContractRejectsSiblingFeatureRedirect() {
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedRuntime = runtimeSource.replaceFirst(
            Regex(
                """(featureKeys\.put\(\s*FLAG_INLINE_SUGGESTIONS,\s*""" +
                    """GboardPatchesFeatureAvailability\.)FEATURE_INLINE_SUGGESTIONS""",
            ),
            "$1FEATURE_GRAMMAR_CHECKER",
        )

        assertNotEquals(runtimeSource, mutatedRuntime)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(runtimeSource = mutatedRuntime)
        }
    }

    @Test
    fun inlineSuggestionsRuntimeContractRejectsOverloadMaskingBrokenZeroArgumentMap() {
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val brokenRuntime = runtimeSource.replaceFirst(
            Regex(
                """(featureKeys\.put\(\s*FLAG_INLINE_SUGGESTIONS,\s*""" +
                    """GboardPatchesFeatureAvailability\.)FEATURE_INLINE_SUGGESTIONS""",
            ),
            "$1FEATURE_GRAMMAR_CHECKER",
        )
        val zeroArgumentSignature =
            "    private static Map<String, String> createFlagToFeatureKeyMap() {"
        val overloadDecoy =
            """
            private static Map<String, String> createFlagToFeatureKeyMap(String ignored) {
                Map<String, String> featureKeys = new HashMap<String, String>();
                featureKeys.put(
                        FLAG_INLINE_SUGGESTIONS,
                        GboardPatchesFeatureAvailability.FEATURE_INLINE_SUGGESTIONS);
                return featureKeys;
            }

            """.trimIndent()
        val signatureIndex = brokenRuntime.indexOf(zeroArgumentSignature)
        if (signatureIndex < 0) throw AssertionError("Missing zero-argument runtime map factory")
        val mutatedRuntime = brokenRuntime.substring(0, signatureIndex) +
            overloadDecoy +
            brokenRuntime.substring(signatureIndex)

        assertNotEquals(runtimeSource, mutatedRuntime)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(runtimeSource = mutatedRuntime)
        }
    }

    @Test
    fun inlineSuggestionsMarkerContractRejectsWrongMarkerLiteral() {
        val markerSource = readSource(INLINE_MARKER_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            INLINE_MARKER,
            "dev.jason.gboardpatches.feature.grammar_checker",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(markerSource = mutatedMarker)
        }
    }

    @Test
    fun inlineSuggestionsMarkerContractRejectsWrongMarkerCall() {
        val markerSource = readSource(INLINE_MARKER_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "applyFeatureMarker(INLINE_SUGGESTIONS_FEATURE_MARKER_NAME)",
            "applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(markerSource = mutatedMarker)
        }
    }

    @Test
    fun inlineSuggestionsMarkerContractRejectsGuardedExecutableMarkerToken() {
        val markerSource = readSource(INLINE_MARKER_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(INLINE_SUGGESTIONS_FEATURE_MARKER_NAME)",
            "        if (false) {\n" +
                "            applyFeatureMarker(INLINE_SUGGESTIONS_FEATURE_MARKER_NAME)\n" +
                "        }",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(markerSource = mutatedMarker)
        }
    }

    @Test
    fun inlineSuggestionsMarkerContractRejectsExtraExecutableMarkerToken() {
        val markerSource = readSource(INLINE_MARKER_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(INLINE_SUGGESTIONS_FEATURE_MARKER_NAME)",
            "        applyFeatureMarker(INLINE_SUGGESTIONS_FEATURE_MARKER_NAME)\n" +
                "        applyFeatureMarker(KEY_SHAPE_SELECTION_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertInlineMarkerRuntimeContract(markerSource = mutatedMarker)
        }
    }

    @Test
    fun generatedInventoryKeepsTheInlineSuggestionsTargetOnlyAndEnabledByDefault() {
        val inventory = JsonParser.parseString(
            GboardPublishedPatchCatalog.publishedInventory("test-version"),
        ).asJsonObject
        val patches = inventory.getAsJsonArray("patches").map { element -> element.asJsonObject }
        val inlinePatch = patches.single { patch ->
            patch.get("name").asString == "Inline Suggestions"
        }
        val compatiblePackages = inlinePatch.getAsJsonObject("compatiblePackages")

        assertMatchesPublishedRegistrationCount(patches.size)
        assertTrue(inlinePatch.get("use").asBoolean)
        assertEquals(setOf(GBOARD_PACKAGE), compatiblePackages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            compatiblePackages.getAsJsonArray(GBOARD_PACKAGE).map { version -> version.asString },
        )
    }

    private fun assertInlineMarkerRuntimeContract(
        markerSource: String = readSource(INLINE_MARKER_PATH),
        availabilitySource: String = readSource(FEATURE_AVAILABILITY_PATH),
        runtimeSource: String = readSource(FEATURE_FLAGS_RUNTIME_PATH),
    ) {
        assertFeatureFlagMarkerRuntimeContract(
            markerSource = markerSource,
            availabilitySource = availabilitySource,
            runtimeSource = runtimeSource,
            contract = INLINE_CONTRACT,
        )
    }

    private fun readSource(relativePath: String): String = String(
        Files.readAllBytes(repositoryRoot.resolve(relativePath)),
        StandardCharsets.UTF_8,
    )

    private fun findRepositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { directory -> directory.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                    Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val TARGET_VERSION = "18.0.3.954559732-release-arm64-v8a"
        const val INLINE_MARKER = "dev.jason.gboardpatches.feature.inline_suggestions"
        const val INLINE_MARKER_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "featureflags/GboardInlineSuggestionsFeatureMarkerPatch.kt"
        const val FEATURE_AVAILABILITY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesFeatureAvailability.java"
        const val FEATURE_FLAGS_RUNTIME_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/featureflags/" +
                "GboardFeatureFlagsRuntime.java"
        val INLINE_CONTRACT = FeatureFlagMarkerRuntimeContract(
            markerPatchPropertyName = "gboardInlineSuggestionsFeatureMarkerPatch",
            markerConstantName = "INLINE_SUGGESTIONS_FEATURE_MARKER_NAME",
            markerLiteral = INLINE_MARKER,
            availabilityConstantName = "FEATURE_INLINE_SUGGESTIONS",
            runtimeFlagConstantName = "FLAG_INLINE_SUGGESTIONS",
            runtimeFlagLiteral = "enable_inline_suggestions_on_client_side",
            siblingMarkerLiterals = listOf(
                "dev.jason.gboardpatches.feature.device_intelligence",
                "dev.jason.gboardpatches.feature.grammar_checker",
                "dev.jason.gboardpatches.feature.key_shape_selection",
            ),
        )
    }
}
