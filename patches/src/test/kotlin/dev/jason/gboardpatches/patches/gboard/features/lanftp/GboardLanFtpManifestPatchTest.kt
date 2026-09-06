package dev.jason.gboardpatches.patches.gboard.features.lanftp

import dev.jason.gboardpatches.patches.gboard.features.packagerename.applyGboardPackageRename
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardLanFtpManifestPatchTest {
    @Test
    fun `manifest injects the private process service provider and selected-only tile surface`() {
        val manifest = parse(MINIMAL_MANIFEST)

        applyGboardLanFtpManifest(manifest)
        applyGboardLanFtpManifest(manifest)

        assertEquals(EXPECTED_PERMISSIONS, elements(manifest, "uses-permission").map {
            androidAttribute(it, "name")
        }.toSet())

        val application = elements(manifest, "application").single()
        assertEquals(
            "@xml/gboard_patches_lan_ftp_backup_rules",
            androidAttribute(application, "fullBackupContent"),
        )
        assertEquals(
            "@xml/gboard_patches_lan_ftp_data_extraction_rules",
            androidAttribute(application, "dataExtractionRules"),
        )

        val ftpService = component(manifest, "service", LAN_FTP_SERVICE_CLASS)
        assertEquals("false", androidAttribute(ftpService, "exported"))
        assertEquals("connectedDevice", androidAttribute(ftpService, "foregroundServiceType"))
        assertEquals(LAN_FTP_PROCESS, androidAttribute(ftpService, "process"))
        assertEquals(null, androidAttribute(ftpService, "isolatedProcess"))

        val provider = component(manifest, "provider", LAN_FTP_STATUS_PROVIDER_CLASS)
        assertEquals("$SOURCE_PACKAGE$LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX",
            androidAttribute(provider, "authorities"))
        assertEquals("false", androidAttribute(provider, "exported"))
        assertEquals(LAN_FTP_PROCESS, androidAttribute(provider, "process"))

        val tile = component(manifest, "service", LAN_FTP_TILE_SERVICE_CLASS)
        assertEquals("true", androidAttribute(tile, "exported"))
        assertEquals("FTP Server", androidAttribute(tile, "label"))
        assertEquals("@drawable/ic_lan_ftp_tile", androidAttribute(tile, "icon"))
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE",
            androidAttribute(tile, "permission"))
        assertEquals(null, androidAttribute(tile, "process"))
        assertEquals(
            listOf("android.service.quicksettings.action.QS_TILE"),
            childElements(tile, "action").map { androidAttribute(it, "name") },
        )
        assertEquals(
            setOf(
                "android.service.quicksettings.ACTIVE_TILE",
                "android.service.quicksettings.TOGGLEABLE_TILE",
                "dev.jason.gboardpatches.tile.NAVIGATION_PATH",
            ),
            childElements(tile, "meta-data").map { androidAttribute(it, "name") }.toSet(),
        )
        assertEquals(
            "dev.jason.gboardpatches.extension.lanftp.settings.GboardLanFtpSettingsFeature",
            childElements(tile, "meta-data").single {
                androidAttribute(it, "name") ==
                    "dev.jason.gboardpatches.tile.NAVIGATION_PATH"
            }.let { androidAttribute(it, "value") },
        )

        val tilePreferences = component(
            manifest,
            "activity",
            TILE_PREFERENCES_ACTIVITY_CLASS,
        )
        assertEquals("true", androidAttribute(tilePreferences, "exported"))
        assertEquals(
            listOf("android.service.quicksettings.action.QS_TILE_PREFERENCES"),
            childElements(tilePreferences, "action").map { androidAttribute(it, "name") },
        )
        assertEquals(
            listOf("android.intent.category.DEFAULT"),
            childElements(tilePreferences, "category").map { androidAttribute(it, "name") },
        )
        assertTrue(elements(manifest, "receiver").isEmpty())
    }

    @Test
    fun `provider authority follows final package when FTP is applied before package rename`() {
        val manifest = fixture(MANIFEST_FIXTURE)
        val settings = settingsFixtures()

        applyGboardLanFtpManifest(manifest)
        applyGboardPackageRename(manifest, settings)

        assertFinalProviderAuthority(manifest)
    }

    @Test
    fun `provider authority follows final package when package rename is applied before FTP`() {
        val manifest = fixture(MANIFEST_FIXTURE)
        val settings = settingsFixtures()

        applyGboardPackageRename(manifest, settings)
        applyGboardLanFtpManifest(manifest)

        assertFinalProviderAuthority(manifest)
    }

    @Test
    fun `backup resources exclude the dedicated FTP preference file`() {
        for (name in listOf(
            "gboard_patches_lan_ftp_backup_rules.xml",
            "gboard_patches_lan_ftp_data_extraction_rules.xml",
        )) {
            val resource = checkNotNull(
                javaClass.classLoader.getResource("lan-ftp-res/xml/$name"),
            ) { "Missing backup resource $name" }.readText()
            assertTrue(resource.contains("domain=\"sharedpref\""))
            assertTrue(resource.contains("path=\"lan_ftp_settings.xml\""))
        }
    }

    @Test
    fun `existing identical backup policies are accepted idempotently`() {
        val manifest = parse(MINIMAL_MANIFEST_WITH_FTP_BACKUP_POLICIES)

        applyGboardLanFtpManifest(manifest)
        applyGboardLanFtpManifest(manifest)

        val application = elements(manifest, "application").single()
        assertEquals(
            "@xml/gboard_patches_lan_ftp_backup_rules",
            androidAttribute(application, "fullBackupContent"),
        )
        assertEquals(
            "@xml/gboard_patches_lan_ftp_data_extraction_rules",
            androidAttribute(application, "dataExtractionRules"),
        )
    }

    @Test
    fun `foreign backup policies fail fast instead of being overwritten`() {
        for ((attribute, value) in listOf(
            "fullBackupContent" to "@xml/stock_backup_rules",
            "dataExtractionRules" to "@xml/stock_data_extraction_rules",
        )) {
            val manifest = parse(
                "<manifest xmlns:android=\"$ANDROID_NS\" package=\"$SOURCE_PACKAGE\">" +
                    "<application android:$attribute=\"$value\" /></manifest>",
            )

            val failure = assertThrows(IllegalStateException::class.java) {
                applyGboardLanFtpManifest(manifest)
            }

            assertTrue(failure.message.orEmpty().contains(attribute))
            assertEquals(value, androidAttribute(elements(manifest, "application").single(), attribute))
        }
    }

    private fun assertFinalProviderAuthority(manifest: Document) {
        val providers = elements(manifest, "provider").filter {
            androidAttribute(it, "name") == LAN_FTP_STATUS_PROVIDER_CLASS
        }
        assertEquals(1, providers.size)
        assertEquals(FINAL_PACKAGE, manifest.documentElement.getAttribute("package"))
        assertEquals(
            "$FINAL_PACKAGE$LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX",
            androidAttribute(providers.single(), "authorities"),
        )
    }

    private fun component(document: Document, tag: String, className: String): Element =
        elements(document, tag).single { androidAttribute(it, "name") == className }

    private fun childElements(parent: Element, tag: String): List<Element> =
        parent.getElementsByTagName("*").asList().filterIsInstance<Element>().filter {
            (it.localName ?: it.tagName.substringAfter(':')) == tag
        }

    private fun elements(document: Document, tag: String): List<Element> =
        document.getElementsByTagName("*").asList().filterIsInstance<Element>().filter {
            (it.localName ?: it.tagName.substringAfter(':')) == tag
        }

    private fun androidAttribute(element: Element, localName: String): String? =
        element.getAttributeNS(ANDROID_NS, localName).takeIf { it.isNotBlank() }
            ?: element.getAttribute("android:$localName").takeIf { it.isNotBlank() }

    private fun settingsFixtures(): List<Document> = listOf(
        fixture(SETTINGS_FIXTURE),
        fixture(SETTINGS_LEGACY_FIXTURE),
    )

    private fun fixture(path: String): Document = parse(
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing fixture $path" }
            .use { it.readBytes() },
    )

    private fun parse(xml: String): Document = parse(xml.toByteArray(Charsets.UTF_8))

    private fun parse(xml: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(xml))

    private fun org.w3c.dom.NodeList.asList(): List<org.w3c.dom.Node> =
        (0 until length).map(::item)

    private companion object {
        const val SOURCE_PACKAGE = "com.google.android.inputmethod.latin"
        const val FINAL_PACKAGE = "dev.jason.com.google.android.inputmethod.latin"
        const val MANIFEST_FIXTURE =
            "/gboard/18.0.3/package-rename/AndroidManifest.normalized.xml"
        const val SETTINGS_FIXTURE = "/gboard/18.0.3/package-rename/settings.xml"
        const val SETTINGS_LEGACY_FIXTURE =
            "/gboard/18.0.3/package-rename/settings_legacy.xml"
        const val MINIMAL_MANIFEST =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "package=\"$SOURCE_PACKAGE\"><application /></manifest>"
        const val MINIMAL_MANIFEST_WITH_FTP_BACKUP_POLICIES =
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                "package=\"$SOURCE_PACKAGE\"><application " +
                "android:fullBackupContent=\"@xml/gboard_patches_lan_ftp_backup_rules\" " +
                "android:dataExtractionRules=\"@xml/gboard_patches_lan_ftp_data_extraction_rules\" " +
                "/></manifest>"
        val EXPECTED_PERMISSIONS = setOf(
            "android.permission.ACCESS_LOCAL_NETWORK",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            "android.permission.INTERNET",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.WAKE_LOCK",
        )
    }
}
