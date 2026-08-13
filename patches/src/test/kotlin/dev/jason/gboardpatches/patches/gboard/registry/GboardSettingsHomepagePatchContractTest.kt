package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSettingsHomepagePatchContractTest {
    @Test
    fun actualPublicPatchKeepsExactDependenciesMarkerAndSingleRegistryRow() {
        val registry = readSource(REGISTRY_PATH)
        val patchBlock = registry.substring(
            registry.indexOf("val gboardSettingsHomepagePatch = resourcePatch("),
            registry.indexOf("@Suppress(\"unused\")", registry.indexOf(
                "val gboardSettingsHomepagePatch = resourcePatch(",
            ) + 1),
        )

        assertEquals(1, registry.countExact("name = \"Settings Homepage Override\""))
        assertTrue(patchBlock.contains("gboardPatchesSettingsPatch"))
        assertTrue(patchBlock.contains("gboardSettingsHomepageFeatureMarkerPatch"))
        assertTrue(patchBlock.contains("gboardSettingsHomepageBytecodePatch"))
        assertEquals(3, Regex("gboard[A-Za-z]+Patch").findAll(
            patchBlock.substringAfter("dependsOn(").substringBeforeLast(")"),
        ).count())

        val marker = readSource(FEATURE_MARKER_PATH)
        assertTrue(marker.contains(
            "dev.jason.gboardpatches.feature.settings_homepage",
        ))
        assertTrue(marker.contains("setAndroidAttribute(\"value\", \"true\")"))
    }

    @Test
    fun generatedInventoryStaysTwentyFourWithSettingsOnceAndTargetOnlyCompatibility() {
        val repository = repositoryRoot()
        val patchesList = JsonParser.parseString(
            Files.readString(repository.resolve("patches-list.json"), StandardCharsets.UTF_8),
        ).asJsonObject.getAsJsonArray("patches").map { it.asJsonObject }
        val settings = patchesList.filter {
            it.get("name").asString == "Settings Homepage Override"
        }

        assertEquals(25, patchesList.size)
        assertEquals(1, settings.size)
        assertEquals(true, settings.single().get("use").asBoolean)
        assertEquals(
            listOf(TARGET_VERSION),
            settings.single().getAsJsonObject("compatiblePackages")
                .getAsJsonArray("com.google.android.inputmethod.latin")
                .map { it.asString },
        )

        val compatibility = readSource(CONSTANTS_PATH)
        assertEquals(1, compatibility.countExact("version = \"$TARGET_VERSION\""))
        assertFalse(compatibility.contains("17.0.10"))
    }

    @Test
    fun generatedBindingsStayOnCurrentTargetWithoutRetiredFlagFactory() {
        val bindingsText = readSource(BINDINGS_PATH)
        val bindings = JsonParser.parseString(bindingsText).asJsonObject
            .getAsJsonObject("bindings")

        assertFalse(bindings.has("flag_factory"))
        assertFalse(bindingsText.contains("17.0.10"))
    }

    @Test
    fun generatedPatchInventoryRemainsAtReviewedStageTwoDigest() {
        val repository = repositoryRoot()

        assertEquals(
            PATCH_INVENTORY_SHA256,
            patchInventorySha256(repository.resolve("patches-list.json")),
        )
    }

    @Test
    fun activeProductionContainsOnlyTheEntryVoidDelegateAndNoStaleSelectorTokens() {
        val repository = repositoryRoot()
        val sources = PRODUCTION_SOURCE_ROOTS.joinToString("\n") { relativeRoot ->
            Files.walk(repository.resolve(relativeRoot)).use { paths ->
                paths.filter { path ->
                    Files.isRegularFile(path) &&
                        (path.fileName.toString().endsWith(".kt") ||
                            path.fileName.toString().endsWith(".java"))
                }.sorted().map { path ->
                    Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n")
                }.toList().joinToString("\n")
            }
        }

        assertTrue(sources.contains(
            "applySettingsHomepagePolicy(Ljava/lang/Object;)V",
        ))
        assertFalse(sources.contains("Lddg;"))
        assertFalse(Regex("\\bddg\\b").containsMatchIn(sources))
        assertFalse(sources.contains("shouldUseNewSettingsStyle"))
        assertFalse(sources.contains("Ldgj;->a(Landroid/content/Context;)Z"))
        assertFalse(sources.contains("SettingsActivity;->t()I"))
        assertFalse(sources.contains("0x7f170e7e"))
        assertFalse(sources.contains("0x7f170e7f"))
        assertFalse(sources.contains("returnAndSkip"))
    }

    private fun readSource(relativePath: String): String = Files.readString(
        repositoryRoot().resolve(relativePath),
        StandardCharsets.UTF_8,
    ).replace("\r\n", "\n")

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("README.md")) &&
                    Files.isDirectory(candidate.resolve("patches"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { value -> "%02X".format(value) }

    private fun patchInventorySha256(path: Path): String {
        val patches = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8))
            .asJsonObject
            .getAsJsonArray("patches")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(patches)
            .joinToString("") { value -> "%02X".format(value) }
    }

    private fun String.countExact(value: String): Int = split(value).size - 1

    private companion object {
        const val TARGET_VERSION = "17.7.7.932364120-release-arm64-v8a"
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
        const val FEATURE_MARKER_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "settingshomepage/GboardSettingsHomepageFeatureMarkerPatch.kt"
        const val BYTECODE_PATCH_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "settingshomepage/GboardSettingsHomepageBytecodePatch.kt"
        val PRODUCTION_SOURCE_ROOTS = listOf(
            "patches/src/main/kotlin",
            "extensions/extension/src/main/java",
        )
        const val CONSTANTS_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/shared/Constants.kt"
        const val BINDINGS_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
        const val PATCH_INVENTORY_SHA256 =
            "C8EDA382689B8F61D170CBC1F7BB477961293D972B7005B04FD8E26F94656295"
        const val BINDINGS_SHA256 =
            "EA4D35FDC483DCA17E1461BA5C494EAA788CEE8028942E6722C7C02C48140BDC"
    }
}
