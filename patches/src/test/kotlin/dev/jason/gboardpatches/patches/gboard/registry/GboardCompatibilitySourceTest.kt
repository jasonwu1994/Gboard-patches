package dev.jason.gboardpatches.patches.gboard.registry

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
    fun compatibilityTargetsExactlyGboard1777() {
        val constants = readSource(CONSTANTS_PATH)
        val versions = Regex("""version\s*=\s*"([^"]+)"""")
            .findAll(constants)
            .map { match -> match.groupValues[1] }
            .toList()

        assertEquals(listOf(TARGET_VERSION), versions)
        assertFalse(constants.contains("17.0.10"))
    }

    @Test
    fun allTwentyFourPublishedPatchesUseTheSharedCompatibility() {
        val registry = readSource(REGISTRY_PATH)
        val activeRegistry = registry.replace(Regex("(?s)/\\*.*?\\*/"), "")
        val publicPatchBlocks = activeRegistry.split("@Suppress(\"unused\")")
            .filter { block -> block.contains("resourcePatch(") }

        assertEquals(25, publicPatchBlocks.size)
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
        const val TARGET_VERSION = "17.7.7.932364120-release-arm64-v8a"
        const val CONSTANTS_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/shared/Constants.kt"
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
    }
}
