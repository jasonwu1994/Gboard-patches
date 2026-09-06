package dev.jason.gboardpatches.patches.gboard.features

import dev.jason.gboardpatches.patches.gboard.features.lanftp.applyGboardLanFtpManifest
import dev.jason.gboardpatches.patches.gboard.features.webclipboard.applyGboardWebClipboardManifest
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardTilePreferencesManifestCompositionTest {
    @Test
    fun `FTP alone registers exactly one generic dispatcher`() {
        val manifest = manifest()
        applyGboardLanFtpManifest(manifest)
        assertDispatcher(manifest)
        assertTilePath(manifest, FTP_TILE, FTP_PATH)
    }

    @Test
    fun `Web Clipboard alone registers exactly one generic dispatcher`() {
        val manifest = manifest()
        applyGboardWebClipboardManifest(manifest)
        assertDispatcher(manifest)
        assertTilePath(manifest, WEB_TILE, WEB_PATH)
    }

    @Test
    fun `both patches share one dispatcher in either order`() {
        for (ftpFirst in listOf(true, false)) {
            val manifest = manifest()
            if (ftpFirst) {
                applyGboardLanFtpManifest(manifest)
                applyGboardWebClipboardManifest(manifest)
            } else {
                applyGboardWebClipboardManifest(manifest)
                applyGboardLanFtpManifest(manifest)
            }
            assertDispatcher(manifest)
            assertTilePath(manifest, FTP_TILE, FTP_PATH)
            assertTilePath(manifest, WEB_TILE, WEB_PATH)
        }
    }

    private fun assertDispatcher(document: Document) {
        val dispatchers = elements(document, "activity").filter {
            attr(it, "name") == DISPATCHER
        }
        assertEquals(1, dispatchers.size)
        val filters = children(dispatchers.single(), "intent-filter").filter { filter ->
            children(filter, "action").any {
                attr(it, "name") == "android.service.quicksettings.action.QS_TILE_PREFERENCES"
            }
        }
        assertEquals(1, filters.size)
        assertEquals(BIND_QUICK_SETTINGS_TILE_PERMISSION, attr(dispatchers.single(), "permission"))
    }

    private fun assertTilePath(document: Document, tileClass: String, expected: String) {
        val tile = elements(document, "service").single { attr(it, "name") == tileClass }
        val metadata = children(tile, "meta-data").single { attr(it, "name") == PATH_KEY }
        assertEquals(expected, attr(metadata, "value"))
    }

    private fun manifest(): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream((
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
            "package=\"com.google.android.inputmethod.latin\"><application /></manifest>"
        ).toByteArray()))

    private fun elements(document: Document, tag: String): List<Element> =
        document.getElementsByTagName(tag).asList().filterIsInstance<Element>()

    private fun children(parent: Element, tag: String): List<Element> =
        parent.childNodes.asList().filterIsInstance<Element>().filter { it.tagName == tag }

    private fun attr(element: Element, name: String): String =
        element.getAttributeNS(ANDROID_NS, name).ifBlank {
            element.getAttribute("android:$name")
        }

    private fun org.w3c.dom.NodeList.asList(): List<org.w3c.dom.Node> =
        (0 until length).map(::item)

    private companion object {
        const val DISPATCHER =
            "dev.jason.gboardpatches.extension.settings.GboardTilePreferencesActivity"
        const val PATH_KEY = "dev.jason.gboardpatches.tile.NAVIGATION_PATH"
        const val BIND_QUICK_SETTINGS_TILE_PERMISSION =
            "android.permission.BIND_QUICK_SETTINGS_TILE"
        const val FTP_TILE =
            "dev.jason.gboardpatches.extension.lanftp.android.LanFtpTileService"
        const val WEB_TILE =
            "dev.jason.gboardpatches.extension.webclipboard.WebClipboardTileService"
        const val FTP_PATH =
            "dev.jason.gboardpatches.extension.lanftp.settings.GboardLanFtpSettingsFeature"
        const val WEB_PATH =
            "dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature;" +
                "dev.jason.gboardpatches.extension.clipboard.GboardWebClipboardSettingsFeature"
    }
}
