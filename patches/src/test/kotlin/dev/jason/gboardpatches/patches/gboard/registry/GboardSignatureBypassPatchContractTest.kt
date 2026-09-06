package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.signaturebypass.gboardSignatureBypassBytecodePatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSignatureBypassPatchContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun `public patch stays one exact 1803 registry entry`() {
        val patch = gboardSignatureBypassPatch
        assertEquals("Add Gboard Signature Bypass", patch.name)
        assertEquals(SIGNATURE_BYPASS_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertTrue(patch.options.isEmpty())
        assertEquals(1, patch.dependencies.size)
        assertSame(gboardSignatureBypassBytecodePatch, patch.dependencies.single())

        val compatibilities = checkNotNull(patch.compatibility)
        assertEquals(1, compatibilities.size)
        assertSame(COMPATIBILITY_GBOARD, compatibilities.single())
        assertEquals(GBOARD_PACKAGE, compatibilities.single().packageName)
        assertEquals(
            listOf(TARGET_VERSION),
            compatibilities.single().targets.map { target -> target.version },
        )

        val patches = generatedPublishedPatches()
        assertMatchesPublishedRegistrationCount(patches.size)
        val signatureRows = patches.filter { row ->
            row.get("name").asString == "Add Gboard Signature Bypass"
        }
        assertEquals(1, signatureRows.size)
        val row = signatureRows.single()
        assertTrue(row.get("use").asBoolean)
        assertEquals(SIGNATURE_BYPASS_DESCRIPTION, row.get("description").asString)
        val packages = row.getAsJsonObject("compatiblePackages")
        assertEquals(setOf(GBOARD_PACKAGE), packages.keySet())
        assertEquals(
            listOf(TARGET_VERSION),
            packages.getAsJsonArray(GBOARD_PACKAGE).map { it.asString },
        )
    }

    @Test
    fun `production source uses only exact rpv normal return patch`() {
        val source = readSource(SIGNATURE_PATCH_PATH)
        assertTrue(source.contains("Lrpv;"))
        assertTrue(source.contains("Lrox;->b:Z"))
        assertTrue(source.contains("findGboardSignatureBypassTargetOrThrow"))
        assertTrue(source.contains("applyGboardSignatureBypass"))
        assertFalse(source.contains("Lqvi;"))
        assertFalse(source.contains("Lpuo;"))
        assertFalse(source.contains("17.0.10"))
        assertFalse(source.contains("addInstructions(0, SIGNATURE_BYPASS_DELEGATE)"))
        assertFalse(source.contains("SIGNATURE_BYPASS_DELEGATE"))
        assertFalse(source.contains("Ljava/lang/String;->equals"))
        assertFalse(source.contains("contains(\"com.google.android.inputmethod.latin\")"))
    }

    @Test
    fun `signature bypass does not add bindings or flag factory`() {
        val profile = JsonParser.parseString(readSource(BINDINGS_PROFILE_PATH)).asJsonObject
        assertEquals("18.0.3", profile.get("target_version").asString)
        val bindings = profile.getAsJsonObject("bindings")
        assertFalse(bindings.has("flag_factory"))
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
        const val SIGNATURE_BYPASS_DESCRIPTION =
            "攔截 Gboard 的簽章白名單檢查並強制通過\n" +
                "Bypass Gboard signature whitelist checks and force them to pass."
        const val SIGNATURE_PATCH_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "signaturebypass/GboardSignatureBypassBytecodePatch.kt"
        const val BINDINGS_PROFILE_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
    }
}
