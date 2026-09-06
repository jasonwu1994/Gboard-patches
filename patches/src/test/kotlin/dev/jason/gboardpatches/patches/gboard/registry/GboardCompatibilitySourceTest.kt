package dev.jason.gboardpatches.patches.gboard.registry

import app.morphe.patcher.patch.ApkFileType
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCompatibilitySourceTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun defaultCompatibilityPreservesExactGboard1803Identity() {
        val constants = readSource(CONSTANTS_PATH)

        assertEquals(GBOARD_PACKAGE, COMPATIBILITY_GBOARD.packageName)
        assertEquals(ApkFileType.APK, COMPATIBILITY_GBOARD.apkFileType)
        assertEquals(EXPECTED_SIGNATURES, COMPATIBILITY_GBOARD.signatures)
        assertEquals(listOf(TARGET_VERSION), COMPATIBILITY_GBOARD.targets.map { it.version })
        assertTrue(COMPATIBILITY_GBOARD.targets.none { it.isExperimental })
        assertTrue(constants.contains("GboardTargetAdmission.packageName"))
        assertTrue(constants.contains("GboardTargetAdmission.versionNames"))
        assertFalse(constants.contains("17.0.10"))
    }

    @Test
    fun allThirtyFivePublishedPatchesUseTheSharedCompatibility() {
        val registry = readSource(REGISTRY_PATH)
        val activeRegistry = registry.replace(Regex("(?s)/\\*.*?\\*/"), "")
        val publicPatchBlocks = activeRegistry.split("@Suppress(\"unused\")")
            .filter { block ->
                Regex("val\\s+gboard[A-Za-z0-9]+Patch\\s*=\\s*gboardPublicResourcePatch\\(")
                    .containsMatchIn(block)
            }

        assertMatchesPublishedRegistrationCount(publicPatchBlocks.size)
        publicPatchBlocks.forEach { block ->
            assertEquals(
                1,
                Regex("""compatibleWith\s*\(\s*COMPATIBILITY_GBOARD\s*\)""")
                    .findAll(block)
                    .count(),
            )
            assertFalse(
                Regex("""compatibleWith\s*\((?!\s*COMPATIBILITY_GBOARD\s*\))""")
                    .containsMatchIn(block),
            )
        }
        assertTrue(
            registry.contains(
                "import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD",
            ),
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
                    Files.isRegularFile(candidate.resolve(CONSTANTS_PATH))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val TARGET_VERSION = "18.0.3.954559732-release-arm64-v8a"
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        val EXPECTED_SIGNATURES = setOf(
            "7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053",
            "f0fd6c5b410f25cb25c3b53346c8972fae30f8ee7411df910480ad6b2d60db83",
        )
        const val CONSTANTS_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/shared/Constants.kt"
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
    }
}
