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
        assertTrue(patch.options.isEmpty())
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
    fun `active inventory stays twenty two with package rename exactly once`() {
        val inventory = JsonParser.parseString(readSource(PATCHES_LIST_PATH)).asJsonObject
        val patches = inventory.getAsJsonArray("patches").map { it.asJsonObject }

        assertEquals(25, patches.size)
        val rows = patches.filter { row -> row.get("name").asString == "Package Rename" }
        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.get("use").asBoolean)
        assertEquals(PACKAGE_RENAME_DESCRIPTION, row.get("description").asString)
        assertTrue(row.getAsJsonArray("dependencies").isEmpty)
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

        assertEquals("17.7.7", profile.get("target_version").asString)
        val bindings = profile.getAsJsonObject("bindings")
        assertFalse(bindings.has("flag_factory"))
        assertTrue(bindings.keySet().none { key -> key.contains("package_rename") })
        assertFalse(readSource(PATCHES_LIST_PATH).contains("family_ids"))
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
        const val TARGET_VERSION = "17.7.7.932364120-release-arm64-v8a"
        const val PACKAGE_RENAME_DESCRIPTION =
            "將套件名稱改成 dev.jason.com.google.android.inputmethod.latin 以便共存安裝\n" +
                "Rename the package to dev.jason.com.google.android.inputmethod.latin " +
                "so it can be installed alongside the official Gboard."
        const val PATCHES_LIST_PATH = "patches-list.json"
        const val BINDINGS_PROFILE_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
    }
}
