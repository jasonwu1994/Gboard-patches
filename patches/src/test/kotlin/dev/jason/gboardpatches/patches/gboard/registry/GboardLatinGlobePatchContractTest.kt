package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalBytecodePatch
import dev.jason.gboardpatches.patches.gboard.features.latinglobe.gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardLatinGlobePatchContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun publicPatchHasExactMetadataDependenciesAnd1803Compatibility() {
        val patch = gboardLatinGlobeKeyIgnoreIntervalPatch
        assertEquals("Latin Globe Key Ignore Interval", patch.name)
        assertEquals(LATIN_GLOBE_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertTrue(patch.options.isEmpty())

        val expectedDependencies = listOf(
            gboardPatchesSettingsPatch,
            gboardLatinGlobeKeyIgnoreIntervalFeatureMarkerPatch,
            gboardLatinGlobeKeyIgnoreIntervalBytecodePatch,
        )
        assertEquals(expectedDependencies.size, patch.dependencies.size)
        expectedDependencies.forEach { expected ->
            assertTrue(patch.dependencies.any { actual -> actual === expected })
        }
        assertTrue(patch.dependencies.all { actual ->
            expectedDependencies.any { expected -> actual === expected }
        })

        val compatibilities = checkNotNull(patch.compatibility)
        assertEquals(1, compatibilities.size)
        val compatibility = compatibilities.single()
        assertSame(COMPATIBILITY_GBOARD, compatibility)
        assertEquals(GBOARD_PACKAGE, compatibility.packageName)
        assertEquals(
            listOf(TARGET_VERSION),
            compatibility.targets.map { target -> target.version },
        )
        assertTrue(compatibility.targets.none { target -> target.isExperimental })
    }

    @Test
    fun generatedInventoryMatchesRegistrationsAndContainsExactlyOneLatinGlobe() {
        val patches = generatedPublishedPatches()
        assertMatchesPublishedRegistrationCount(patches.size)
        val rows = patches.filter { row ->
            row.get("name").asString == "Latin Globe Key Ignore Interval"
        }
        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.get("use").asBoolean)
        assertEquals(LATIN_GLOBE_DESCRIPTION, row.get("description").asString)
        val compatiblePackages = row.getAsJsonObject("compatiblePackages")
        assertEquals(setOf(GBOARD_PACKAGE), compatiblePackages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            compatiblePackages.getAsJsonArray(GBOARD_PACKAGE).map { it.asString },
        )
    }

    @Test
    fun readmeListsLatinGlobeWithThePublicEnglishDescription() {
        val readme = readSource("README.md")
            .substringAfter("## Included Patches")
            .substringBefore("## Install")

        assertTrue(readme.contains(
            "<summary><code>Latin Globe Key Ignore Interval</code></summary>",
        ))
        assertTrue(readme.contains(
            "Add an independent English globe key ignore interval override for " +
                "post-typing language-switch delay.",
        ))
    }

    @Test
    fun latinGlobeAddsNoGeneratedBindingOrFlagFactory() {
        val profile = JsonParser.parseString(readSource(BINDINGS_PROFILE_PATH)).asJsonObject
        assertEquals("18.0.3", profile.get("target_version").asString)
        val bindings = profile.getAsJsonObject("bindings")
        assertFalse(bindings.has("flag_factory"))
        assertTrue(bindings.keySet().none { key -> key.contains("latin_globe") })
        assertTrue(bindings.toString().contains("flag_bool_getter"))
        assertFalse(bindings.toString().contains("vky"))
    }

    private fun readSource(relativePath: String): String = Files.readString(
        repositoryRoot.resolve(relativePath),
        StandardCharsets.UTF_8,
    )

    private fun findRepositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current
            current = current.parent ?: error("Could not resolve repository root")
        }
    }

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val TARGET_VERSION = "18.0.3.954559732-release-arm64-v8a"
        const val LATIN_GLOBE_DESCRIPTION =
            "新增英文鍵盤地球鍵忽略時間覆寫，可獨立控制輸入後切語言延遲\n" +
                "Add an independent English globe key ignore interval override for " +
                "post-typing language-switch delay."
        const val BINDINGS_PROFILE_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
    }
}
