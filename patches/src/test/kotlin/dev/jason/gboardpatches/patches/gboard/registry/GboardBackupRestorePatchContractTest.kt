package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_FEATURE_MARKER
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_INIT_PROVIDER_AUTHORITY_SUFFIX
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.BACKUP_RESTORE_INIT_PROVIDER_CLASS
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.applyGboardBackupRestoreManifest
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.backuprestore.gboardBackupRestoreManifestPatch
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
    fun `public patch is independent version neutral and enabled by default`() {
        val patch = gboardBackupRestorePatch

        assertEquals("Backup & Restore", patch.name)
        assertTrue(patch.default)
        assertEquals(
            setOf(
                gboardPatchesSettingsPatch,
                gboardBackupRestoreFeatureMarkerPatch,
                gboardBackupRestoreManifestPatch,
            ),
            patch.dependencies,
        )

        val catalog = JsonParser.parseString(
            Files.readString(repositoryRoot.resolve(CATALOG_PATH), StandardCharsets.UTF_8),
        ).asJsonObject
        val feature = catalog.getAsJsonArray("features").map { it.asJsonObject }.single {
            it["feature_id"].asString == "backup_restore"
        }
        assertEquals("version-neutral-extension", feature["migration_scope"].asString)
        assertTrue(feature.getAsJsonArray("target_family_ids").isEmpty)
        assertEquals(BACKUP_RESTORE_FEATURE_MARKER, feature["feature_marker"].asString)
        val contribution = feature.getAsJsonArray("contributions").single().asJsonObject
        assertEquals("resource_only", contribution["anchor_family_id"].asString)
        assertTrue(contribution.getAsJsonArray("required_bindings").isEmpty)
        assertTrue(contribution.getAsJsonArray("runtime_calls").isEmpty)
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

        val text = Files.readString(
            repositoryRoot.resolve(SETTINGS_TEXT_PATH),
            StandardCharsets.UTF_8,
        )
        assertTrue(text.contains("<translation locale=\"en\">Backup &amp; Restore</translation>"))
        assertTrue(text.contains(
            "<translation locale=\"en\">Export or restore all Patches settings</translation>",
        ))
        assertTrue(text.contains("<translation locale=\"zh-Hant\">備份與還原</translation>"))
        assertTrue(text.contains(
            "<translation locale=\"zh-Hant\">匯出或還原全部 Patches 設定</translation>",
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
                "GboardPatchesSettingsFeatureRegistry.java"
        const val SETTINGS_TEXT_PATH =
            "extensions/extension/src/main/settings-text/gboard_settings_text.xml"
    }
}
