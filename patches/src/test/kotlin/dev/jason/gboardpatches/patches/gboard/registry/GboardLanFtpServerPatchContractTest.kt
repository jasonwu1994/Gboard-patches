package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.lanftp.LAN_FTP_FEATURE_MARKER
import dev.jason.gboardpatches.patches.gboard.features.lanftp.gboardLanFtpFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.lanftp.gboardLanFtpManifestPatch
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

class GboardLanFtpServerPatchContractTest {
    private val repositoryRoot = repositoryRoot()

    @Test
    fun `public patch has exact identity dependencies and target compatibility`() {
        val patch = gboardLanFtpServerPatch

        assertEquals("FTP Server", patch.name)
        assertEquals(LAN_FTP_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertTrue(patch.options.isEmpty())
        assertEquals(
            setOf(
                gboardPatchesSettingsPatch,
                gboardLanFtpFeatureMarkerPatch,
                gboardLanFtpManifestPatch,
            ),
            patch.dependencies,
        )
        assertTrue(patch.dependencies.none { dependency ->
            dependency === gboardWebClipboardPatch || dependency.name == "Web Clipboard"
        })

        val compatibility = checkNotNull(patch.compatibility).single()
        assertSame(COMPATIBILITY_GBOARD, compatibility)
        assertEquals("com.google.android.inputmethod.latin", compatibility.packageName)
        assertEquals(
            listOf("18.0.3.954559732-release-arm64-v8a"),
            compatibility.targets.map { target -> target.version },
        )
    }

    @Test
    fun `product catalog classifies FTP as version neutral with no bindings or public dependency`() {
        val catalog = JsonParser.parseString(
            Files.readString(
                repositoryRoot.resolve(CATALOG_PATH),
                StandardCharsets.UTF_8,
            ),
        ).asJsonObject
        val feature = catalog.getAsJsonArray("features").map { it.asJsonObject }.single {
            it["feature_id"].asString == "lan_ftp_server"
        }

        assertEquals("FTP Server", feature["public_patch_name"].asString)
        assertEquals(LAN_FTP_FEATURE_MARKER, feature["feature_marker"].asString)
        assertEquals("version-neutral-extension", feature["migration_scope"].asString)
        assertTrue(feature.getAsJsonArray("target_family_ids").isEmpty)
        assertFalse(feature.has("depends_on_feature_ids"))
        val contribution = feature.getAsJsonArray("contributions").single().asJsonObject
        assertEquals("resource_only", contribution["anchor_family_id"].asString)
        assertEquals("resource", contribution["phase"].asString)
        assertTrue(contribution.getAsJsonArray("required_bindings").isEmpty)
        assertTrue(contribution.getAsJsonArray("runtime_calls").isEmpty)

        val bindings = JsonParser.parseString(
            Files.readString(repositoryRoot.resolve(BINDINGS_PATH), StandardCharsets.UTF_8),
        ).asJsonObject.getAsJsonObject("bindings")
        assertTrue(bindings.keySet().none { key -> key.contains("lan_ftp") })
    }

    @Test
    fun `published inventory contains exactly one FTP patch`() {
        val rows = generatedPublishedPatches().filter { row ->
            row["name"].asString == "FTP Server"
        }

        assertEquals(1, rows.size)
        assertEquals(LAN_FTP_DESCRIPTION, rows.single()["description"].asString)
        assertTrue(rows.single()["use"].asBoolean)
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { directory -> directory.parent }
            .firstOrNull { directory ->
                Files.isRegularFile(directory.resolve("settings.gradle.kts"))
            } ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val LAN_FTP_DESCRIPTION =
            "新增區域網路 FTP 伺服器，支援檔案瀏覽、傳輸與下載續傳\n" +
                "Add a LAN FTP server with file browsing, transfers, and download resume."
        const val CATALOG_PATH =
            "patches/src/main/resources/gboard/gboard-port-product-catalog.json"
        const val BINDINGS_PATH =
            "patches/src/main/resources/gboard/gboard-version-bindings.json"
    }
}
