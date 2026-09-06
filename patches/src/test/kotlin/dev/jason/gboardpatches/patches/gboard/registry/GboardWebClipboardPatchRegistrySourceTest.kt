package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardAssetsPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardCapturePatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.gboardWebClipboardManifestPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardWebClipboardPatchRegistrySourceTest {
    private val repositoryRoot = repositoryRoot()

    @Test
    fun actualPublicPatchHasExactMetadataDependenciesAnd1803Compatibility() {
        val patch = gboardWebClipboardPatch
        assertEquals("Web Clipboard", patch.name)
        assertEquals(WEB_CLIPBOARD_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertTrue(patch.options.isEmpty())

        val expectedDependencies = listOf(
            gboardPatchesSettingsPatch,
            gboardWebClipboardFeatureMarkerPatch,
            gboardWebClipboardManifestPatch,
            gboardWebClipboardAssetsPatch,
            gboardWebClipboardCapturePatch,
        )
        assertEquals(expectedDependencies.size, patch.dependencies.size)
        expectedDependencies.forEach { expected ->
            assertTrue(patch.dependencies.any { actual -> actual === expected })
        }
        assertTrue(patch.dependencies.all { actual ->
            expectedDependencies.any { expected -> actual === expected }
        })

        val compatibility = checkNotNull(patch.compatibility)
        assertEquals(1, compatibility.size)
        val target = compatibility.single()
        assertSame(COMPATIBILITY_GBOARD, target)
        assertEquals(GBOARD_PACKAGE, target.packageName)
        assertEquals(listOf(TARGET_VERSION), target.targets.map { it.version })
        assertTrue(target.targets.none { it.isExperimental })
    }

    @Test
    fun generatedInventoryMatchesRegistrationsAndHasExactlyOneWebClipboard() {
        val patches = generatedPublishedPatches()
        assertMatchesPublishedRegistrationCount(patches.size)

        val rows = patches.filter { it.get("name").asString == "Web Clipboard" }
        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.get("use").asBoolean)
        assertEquals(WEB_CLIPBOARD_DESCRIPTION, row.get("description").asString)
        val compatiblePackages = row.getAsJsonObject("compatiblePackages")
        assertEquals(setOf(GBOARD_PACKAGE), compatiblePackages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            compatiblePackages.getAsJsonArray(GBOARD_PACKAGE).map { it.asString },
        )
        assertEquals(1, patches.count { it.get("name").asString == "Clipboard Enhancements" })
    }

    @Test
    fun webClipboardAddsNoBindingAndGeneratedBindingsStayReviewed() {
        val bindingsText = readSource(BINDINGS_JSON_PATH)
        val bindings = JsonParser.parseString(bindingsText).asJsonObject
            .getAsJsonObject("bindings")
        assertFalse(bindings.has("flag_factory"))
        assertTrue(bindings.keySet().none { it.contains("web_clipboard") })
    }

    private fun readSource(relativePath: String): String = Files.readString(
        repositoryRoot.resolve(relativePath),
        StandardCharsets.UTF_8,
    )

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { value -> "%02X".format(value) }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts")) &&
                    Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val TARGET_VERSION = "18.0.3.954559732-release-arm64-v8a"
        const val WEB_CLIPBOARD_DESCRIPTION =
            "新增手機自架的 Web Clipboard，支援瀏覽器同步、配對碼與快速設定開關\n" +
                "Add the phone-hosted Web Clipboard with browser sync, pairing, and a " +
                "Quick Settings Tile."
        const val BINDINGS_JSON_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
        const val BINDINGS_KOTLIN_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/generated/" +
                "GboardVersionBindings.kt"
        const val BINDINGS_JSON_SHA256 =
            "EA4D35FDC483DCA17E1461BA5C494EAA788CEE8028942E6722C7C02C48140BDC"
        const val BINDINGS_KOTLIN_SHA256 =
            "069F62E5E1AE824D0B9322B5331CDA744D4BBAAC0E4FCA910EC7C83B5151056F"
    }
}
