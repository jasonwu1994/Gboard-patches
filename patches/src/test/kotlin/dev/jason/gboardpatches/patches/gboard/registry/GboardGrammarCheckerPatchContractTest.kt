package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardFeatureFlagsBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.gboardGrammarCheckerFeatureMarkerPatch
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

class GboardGrammarCheckerPatchContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun grammarCheckerPublicPatchUsesExactMorpheMetadata() {
        val patch = gboardGrammarCheckerFlagPatch

        assertEquals("Grammar Checker", patch.name)
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
            gboardFeatureFlagsBytecodePatch,
            gboardGrammarCheckerFeatureMarkerPatch,
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
    fun grammarCheckerMarkerProducerMatchesTheRuntimeConsumerExactly() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)

        assertGrammarMarkerRuntimeContract(markerSource, availabilitySource, runtimeSource)
    }

    @Test
    fun grammarCheckerRuntimeContractRejectsSiblingFeatureRedirect() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedRuntime = runtimeSource.replaceFirst(
            Regex(
                """(featureKeys\.put\(\s*FLAG_GRAMMAR_CHECKER,\s*""" +
                    """GboardPatchesFeatureAvailability\.)FEATURE_GRAMMAR_CHECKER""",
            ),
            "$1FEATURE_INLINE_SUGGESTIONS",
        )

        assertNotEquals(runtimeSource, mutatedRuntime)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(markerSource, availabilitySource, mutatedRuntime)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsCommentedMarkerCall() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        // applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsUnreachableMarkerCall() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        if (false) {\n" +
                "            applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)\n" +
                "        }",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsUnreachableMarkerCallWithoutBraces() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        if (false) applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsMarkerCallAfterFinalizeReturn() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        return@finalize\n" +
                "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsMarkerCallAfterThrowingPrefix() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        error(\"stop\")\n" +
                "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractIgnoresCommentDecoysAroundDirectMarkerCall() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
            "        // return@finalize\n" +
                "        /* error(\"stop\") */\n" +
                "        applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
    }

    @Test
    fun grammarCheckerMarkerContractRejectsFinalizeInsideBracedIf() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            Regex(
                """    finalize\s*\{\s*""" +
                    """applyFeatureMarker\(GRAMMAR_CHECKER_FEATURE_MARKER_NAME\)\s*}""",
            ),
            "    if (false) {\n" +
                "        finalize {\n" +
                "            applyFeatureMarker(GRAMMAR_CHECKER_FEATURE_MARKER_NAME)\n" +
                "        }\n" +
                "    }",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsFinalizeBehindNoBraceIf() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            "    finalize {",
            "    if (false) finalize {",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerMarkerContractRejectsCommentedMarkerConstant() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedMarker = markerSource.replaceFirst(
            Regex(
                """private const val GRAMMAR_CHECKER_FEATURE_MARKER_NAME\s*=\s*""" +
                    """\"dev\.jason\.gboardpatches\.feature\.grammar_checker\"""",
            ),
            "// private const val GRAMMAR_CHECKER_FEATURE_MARKER_NAME =\n" +
                "//     \"dev.jason.gboardpatches.feature.grammar_checker\"",
        )

        assertNotEquals(markerSource, mutatedMarker)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(mutatedMarker, availabilitySource, runtimeSource)
        }
    }

    @Test
    fun grammarCheckerRuntimeContractRejectsCommentedRuntimeFlagConstant() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedRuntime = runtimeSource.replaceFirst(
            Regex("""public static final String FLAG_GRAMMAR_CHECKER\s*=\s*\"enable_grammar_checker\";"""),
            "// public static final String FLAG_GRAMMAR_CHECKER = \"enable_grammar_checker\";",
        )

        assertNotEquals(runtimeSource, mutatedRuntime)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(markerSource, availabilitySource, mutatedRuntime)
        }
    }

    @Test
    fun grammarCheckerRuntimeContractRejectsCommentedRuntimeMapCall() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedRuntime = runtimeSource.replaceFirst(
            Regex(
                """featureKeys\.put\(\s*FLAG_GRAMMAR_CHECKER,\s*""" +
                    """GboardPatchesFeatureAvailability\.FEATURE_GRAMMAR_CHECKER\s*\);""",
            ),
            "// featureKeys.put(FLAG_GRAMMAR_CHECKER, " +
                "GboardPatchesFeatureAvailability.FEATURE_GRAMMAR_CHECKER);",
        )

        assertNotEquals(runtimeSource, mutatedRuntime)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(markerSource, availabilitySource, mutatedRuntime)
        }
    }

    @Test
    fun grammarCheckerAvailabilityContractRejectsCommentedMarkerConstant() {
        val markerSource = readSource(GRAMMAR_MARKER_PATH)
        val availabilitySource = readSource(FEATURE_AVAILABILITY_PATH)
        val runtimeSource = readSource(FEATURE_FLAGS_RUNTIME_PATH)
        val mutatedAvailability = availabilitySource.replaceFirst(
            Regex(
                """public static final String FEATURE_GRAMMAR_CHECKER\s*=\s*""" +
                    """\"dev\.jason\.gboardpatches\.feature\.grammar_checker\";""",
            ),
            "// public static final String FEATURE_GRAMMAR_CHECKER = " +
                "\"dev.jason.gboardpatches.feature.grammar_checker\";",
        )

        assertNotEquals(availabilitySource, mutatedAvailability)
        assertThrows(AssertionError::class.java) {
            assertGrammarMarkerRuntimeContract(markerSource, mutatedAvailability, runtimeSource)
        }
    }

    private fun assertGrammarMarkerRuntimeContract(
        markerSource: String,
        availabilitySource: String,
        runtimeSource: String,
    ) {
        assertFeatureFlagMarkerRuntimeContract(
            markerSource = markerSource,
            availabilitySource = availabilitySource,
            runtimeSource = runtimeSource,
            contract = GRAMMAR_CONTRACT,
        )
    }

    @Test
    fun generatedInventoryKeepsTheGrammarCheckerTargetOnlyAndEnabledByDefault() {
        val inventory = JsonParser.parseString(readSource(PATCHES_LIST_PATH)).asJsonObject
        val patches = inventory.getAsJsonArray("patches").map { element -> element.asJsonObject }
        val grammarPatch = patches.single { patch ->
            patch.get("name").asString == "Grammar Checker"
        }
        val compatiblePackages = grammarPatch.getAsJsonObject("compatiblePackages")

        assertEquals(25, patches.size)
        assertTrue(grammarPatch.get("use").asBoolean)
        assertEquals(setOf(GBOARD_PACKAGE), compatiblePackages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            compatiblePackages.getAsJsonArray(GBOARD_PACKAGE).map { version -> version.asString },
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
                    Files.isRegularFile(candidate.resolve(PATCHES_LIST_PATH))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val GRAMMAR_FLAG = "enable_grammar_checker"
        const val GRAMMAR_MARKER = "dev.jason.gboardpatches.feature.grammar_checker"
        const val TARGET_VERSION = "17.7.7.932364120-release-arm64-v8a"
        const val GRAMMAR_MARKER_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "featureflags/GboardGrammarCheckerFeatureMarkerPatch.kt"
        const val FEATURE_AVAILABILITY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesFeatureAvailability.java"
        const val FEATURE_FLAGS_RUNTIME_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/featureflags/" +
                "GboardFeatureFlagsRuntime.java"
        const val PATCHES_LIST_PATH = "patches-list.json"
        val GRAMMAR_CONTRACT = FeatureFlagMarkerRuntimeContract(
            markerPatchPropertyName = "gboardGrammarCheckerFeatureMarkerPatch",
            markerConstantName = "GRAMMAR_CHECKER_FEATURE_MARKER_NAME",
            markerLiteral = GRAMMAR_MARKER,
            availabilityConstantName = "FEATURE_GRAMMAR_CHECKER",
            runtimeFlagConstantName = "FLAG_GRAMMAR_CHECKER",
            runtimeFlagLiteral = GRAMMAR_FLAG,
            siblingMarkerLiterals = listOf(
                "dev.jason.gboardpatches.feature.device_intelligence",
                "dev.jason.gboardpatches.feature.inline_suggestions",
                "dev.jason.gboardpatches.feature.key_shape_selection",
            ),
        )
    }
}
