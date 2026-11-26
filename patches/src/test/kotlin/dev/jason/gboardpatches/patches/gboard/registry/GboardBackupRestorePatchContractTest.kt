package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_FEATURE_MARKER
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_INIT_PROVIDER_AUTHORITY_SUFFIX
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_INIT_PROVIDER_CLASS
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.applyGboardBackupRestoreManifest
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreManifestPatch
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardDataBackupFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardBackupRestorePatchContractTest {
    private val repositoryRoot = repositoryRoot()

    @Test
    fun `public patch is independent and enabled by default with one flag contribution`() {
        val patch = gboardBackupRestorePatch

        assertEquals("Backup & Restore", patch.name)
        assertTrue(patch.default)
        assertEquals(
            setOf(
                gboardPatchesSettingsPatch,
                gboardBackupRestoreFeatureMarkerPatch,
                gboardBackupRestoreManifestPatch,
                gboardDataBackupFlagValuePatch,
            ),
            patch.dependencies,
        )

        val catalog = JsonParser.parseString(
            Files.readString(repositoryRoot.resolve(CATALOG_PATH), StandardCharsets.UTF_8),
        ).asJsonObject
        val feature = catalog.getAsJsonArray("features").map { it.asJsonObject }.single {
            it["feature_id"].asString == "backup_restore"
        }
        // The only version-sensitive part is the flag getter that registers every Gboard
        // backup provider for the personal data backup.
        assertEquals("version-sensitive", feature["migration_scope"].asString)
        assertEquals(listOf("flag.reader"),
            feature.getAsJsonArray("target_family_ids").map { it.asString })
        assertEquals(BACKUP_RESTORE_FEATURE_MARKER, feature["feature_marker"].asString)
        val contributions = feature.getAsJsonArray("contributions").map { it.asJsonObject }
        assertEquals(listOf("backup_restore.resources", "backup_restore.flag"),
            contributions.map { it["contribution_id"].asString })
        val resources = contributions[0]
        assertEquals("resource_only", resources["anchor_family_id"].asString)
        assertTrue(resources.getAsJsonArray("required_bindings").isEmpty)
        assertTrue(resources.getAsJsonArray("runtime_calls").isEmpty)
        val flag = contributions[1]
        assertEquals("flag_bool_getter", flag["anchor_family_id"].asString)
        assertEquals(listOf("GBOARD_DATA_BACKUP_RUNTIME_APPLY_FLAG_VALUE"),
            flag.getAsJsonArray("runtime_calls").map { it.asString })
    }

    @Test
    fun `manifest initializer is private default process and package derived`() {
        val document = parseManifest(
            """<manifest xmlns:android="$ANDROID_NS" package="com.example.gboard">""" +
                "<application /></manifest>",
        )

        applyGboardBackupRestoreManifest(document)

        val providers = document.getElementsByTagName("provider")
        assertEquals(1, providers.length)
        val provider = providers.item(0) as org.w3c.dom.Element
        assertEquals(BACKUP_RESTORE_INIT_PROVIDER_CLASS, provider.android("name"))
        assertEquals("false", provider.android("exported"))
        assertEquals(Int.MAX_VALUE.toString(), provider.android("initOrder"))
        assertEquals(
            "com.example.gboard$BACKUP_RESTORE_INIT_PROVIDER_AUTHORITY_SUFFIX",
            provider.android("authorities"),
        )
        assertFalse(provider.hasAttributeNS(ANDROID_NS, "process"))
        assertFalse(provider.hasAttribute("android:process"))
    }

    @Test
    fun `settings row order and text match the established settings surface`() {
        val registry = Files.readString(
            repositoryRoot.resolve(SETTINGS_REGISTRY_PATH),
            StandardCharsets.UTF_8,
        )
        val homepage = registry.indexOf("new GboardSettingsHomepageSettingsFeature()")
        val backup = registry.indexOf("new GboardBackupRestoreSettingsFeature(context)")
        val developer = registry.indexOf("new GboardDeveloperOptionsSettingsFeature(context)")
        assertTrue(homepage >= 0 && homepage < backup && backup < developer)

        val text = SETTINGS_TEXT_PATHS.joinToString("\n") { path ->
            Files.readString(repositoryRoot.resolve(path), StandardCharsets.UTF_8)
        }
        assertTrue(text.contains(">Backup &amp; Restore</string>"))
        assertTrue(text.contains(
            ">Export or restore Patches settings and Gboard data</string>",
        ))
        assertTrue(text.contains(">備份與還原</string>"))
        assertTrue(text.contains(
            ">匯出或還原 Patches 設定與 Gboard 資料</string>",
        ))
    }

    private fun parseManifest(xml: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))

    private fun org.w3c.dom.Element.android(name: String): String =
        getAttributeNS(ANDROID_NS, name).ifBlank { getAttribute("android:$name") }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
            } ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val CATALOG_PATH =
            "patches/src/main/resources/gboard/gboard-port-product-catalog.json"
        const val SETTINGS_REGISTRY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardMoreSettingsGroupFeature.java"
        val SETTINGS_TEXT_PATHS = listOf(
            "extensions/extension/src/main/settings-text/values/gboard_settings_strings.xml",
            "extensions/extension/src/main/settings-text/values-b+zh+Hant/gboard_settings_strings.xml",
        )
    }
}
