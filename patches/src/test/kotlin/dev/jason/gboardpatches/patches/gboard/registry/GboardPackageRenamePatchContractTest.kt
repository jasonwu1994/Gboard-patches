package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.packagerename.gboardPackageRenameResourcePatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PACKAGE_NAME
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCHED_PACKAGE_NAME
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardPackageRenamePatchContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun `public patch keeps exact install time metadata dependency and compatibility`() {
        val patch = gboardPackageRenamePatch

        assertEquals("Package Rename", patch.name)
        assertEquals(PACKAGE_RENAME_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertEquals(setOf(APP_DISPLAY_NAME_OPTION_KEY), patch.options.keys)
        val option = patch.options[APP_DISPLAY_NAME_OPTION_KEY]
        assertEquals(APP_DISPLAY_NAME_OPTION_DESCRIPTION, option.description)
        assertEquals(APP_DISPLAY_NAME_DEFAULT, option.default)
        assertEquals(APP_DISPLAY_NAME_DEFAULT, option.value)
        assertTrue(option.required)
        assertEquals("kotlin.String", option.type.toString())
        assertEquals(APP_DISPLAY_NAME_PRESETS, option.values)
        assertEquals(1, patch.dependencies.size)
        assertSame(gboardPackageRenameResourcePatch, patch.dependencies.single())
        assertEquals("com.google.android.inputmethod.latin", GBOARD_PACKAGE_NAME)
        assertEquals("dev.jason.com.google.android.inputmethod.latin", GBOARD_PATCHED_PACKAGE_NAME)

        val compatibilities = checkNotNull(patch.compatibility)
        assertEquals(1, compatibilities.size)
        assertSame(COMPATIBILITY_GBOARD, compatibilities.single())
        assertEquals(GBOARD_PACKAGE_NAME, compatibilities.single().packageName)
        assertEquals(
            listOf(TARGET_VERSION),
            compatibilities.single().targets.map { target -> target.version },
        )
        assertTrue(compatibilities.single().targets.none { target -> target.isExperimental })
    }

    @Test
    fun `active inventory matches registrations with package rename exactly once`() {
        val patches = generatedPublishedPatches()

        assertMatchesPublishedRegistrationCount(patches.size)
        val rows = patches.filter { row -> row.get("name").asString == "Package Rename" }
        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.get("use").asBoolean)
        assertEquals(PACKAGE_RENAME_DESCRIPTION, row.get("description").asString)
        assertTrue(row.getAsJsonArray("dependencies").isEmpty)
        val options = row.getAsJsonArray("options")
        assertEquals(1, options.size())
        val option = options.single().asJsonObject
        assertEquals(APP_DISPLAY_NAME_OPTION_KEY, option.get("key").asString)
        assertEquals(APP_DISPLAY_NAME_OPTION_TITLE, option.get("title").asString)
        assertEquals(APP_DISPLAY_NAME_OPTION_DESCRIPTION, option.get("description").asString)
        assertEquals(APP_DISPLAY_NAME_DEFAULT, option.get("default").asString)
        assertTrue(option.get("required").asBoolean)
        assertEquals("kotlin.String", option.get("type").asString)
        assertEquals(
            APP_DISPLAY_NAME_PRESETS.entries.map { entry -> entry.key to entry.value },
            option.getAsJsonObject("values").entrySet().map { entry ->
                entry.key to entry.value.asString
            },
        )
        val packages = row.getAsJsonObject("compatiblePackages")
        assertEquals(setOf(GBOARD_PACKAGE_NAME), packages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            packages.getAsJsonArray(GBOARD_PACKAGE_NAME).map { it.asString },
        )
    }

    @Test
    fun `package rename adds no runtime binding family or flag factory`() {
        val profile = JsonParser.parseString(readSource(BINDINGS_PROFILE_PATH)).asJsonObject

        assertEquals("18.0.3", profile.get("target_version").asString)
        val bindings = profile.getAsJsonObject("bindings")
        assertFalse(bindings.has("flag_factory"))
        assertTrue(bindings.keySet().none { key -> key.contains("package_rename") })
        assertFalse(GboardPublishedPatchCatalog.publishedInventory("test-version").contains("family_ids"))
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
        const val TARGET_VERSION = "18.0.3.954559732-release-arm64-v8a"
        const val PACKAGE_RENAME_DESCRIPTION =
            "將套件名稱改成 dev.jason.com.google.android.inputmethod.latin，並可自訂 App 名稱，以便共存安裝\n" +
                "Rename the package to dev.jason.com.google.android.inputmethod.latin " +
                "and allow a custom app name so it can be installed alongside " +
                "the official Gboard."
        const val APP_DISPLAY_NAME_OPTION_KEY = "app_display_name"
        const val APP_DISPLAY_NAME_OPTION_TITLE = "App 名稱 / App name"
        const val APP_DISPLAY_NAME_OPTION_DESCRIPTION = ""
        const val APP_DISPLAY_NAME_DEFAULT = "GboardWu"
        val APP_DISPLAY_NAME_PRESETS = linkedMapOf(
            "Gboard" to "Gboard",
            "Gboard Dev" to "Gboard Dev",
            "Gboard Patches" to "Gboard Patches",
            "Gboard Mod" to "Gboard Mod",
            "GboardWu" to "GboardWu",
        )
        const val BINDINGS_PROFILE_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
    }
}
