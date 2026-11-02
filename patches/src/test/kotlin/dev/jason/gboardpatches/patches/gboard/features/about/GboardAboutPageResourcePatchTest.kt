package dev.jason.gboardpatches.patches.gboard.features.about

import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCH_AUTHOR
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCH_AUTHOR_URL
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCH_REPOSITORY_URL
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCH_VERSION
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardAboutPageResourcePatchTest {
    @Test
    fun `official 1853 about page inserts author and patch version after version row`() {
        val fixture = readFixture()
        assertEquals(FIXTURE_SHA256, sha256(normalizeLineEndings(fixture)))
        val document = parse(fixture)
        val stockChildren = document.documentElement.childElements().toList()
        val originalVersion = stockChildren.last().cloneNode(true)

        assertEquals(4, stockChildren.size)
        assertEquals(ABOUT_PREFERENCE_CLASS, stockChildren.last().tagName)
        assertEquals(ABOUT_VERSION_KEY_REF, androidAttribute(stockChildren.last(), "key"))

        applyGboardAboutPagePatch(document)

        val children = document.documentElement.childElements().toList()
        assertEquals(6, children.size)
        assertEquals(ABOUT_VERSION_KEY_REF, androidAttribute(children[3], "key"))
        assertTrue(originalVersion.isEqualNode(children[3]))
        assertAboutRow(
            element = children[4],
            key = "gboard_about_author",
            title = "Author",
            summary = GBOARD_PATCH_AUTHOR,
            intentUrl = GBOARD_PATCH_AUTHOR_URL,
        )
        assertAboutRow(
            element = children[5],
            key = "gboard_about_patch_version",
            title = "Patch Version",
            summary = GBOARD_PATCH_VERSION,
            intentUrl = GBOARD_PATCH_REPOSITORY_URL,
        )
    }

    @Test
    fun `official 1853 about page transformation is idempotent`() {
        val document = parse(readFixture())

        applyGboardAboutPagePatch(document)
        applyGboardAboutPagePatch(document)

        val children = document.documentElement.childElements().toList()
        assertEquals(6, children.size)
        assertEquals(1, children.count {
            androidAttribute(it, "key") == "gboard_about_author"
        })
        assertEquals(1, children.count {
            androidAttribute(it, "key") == "gboard_about_patch_version"
        })
        assertEquals(
            listOf(
                ABOUT_VERSION_KEY_REF,
                "gboard_about_author",
                "gboard_about_patch_version",
            ),
            children.takeLast(3).map { androidAttribute(it, "key") },
        )
    }

    @Test
    fun `morphe sanitized 1853 version key remains the exact insertion anchor`() {
        val sanitized = String(readFixture(), StandardCharsets.UTF_8)
            .replace("@string/APKTOOL_RENAMED_", "@string/string_")
        val document = parse(sanitized.toByteArray(StandardCharsets.UTF_8))

        applyGboardAboutPagePatch(document)

        val children = document.documentElement.childElements().toList()
        assertEquals(6, children.size)
        assertEquals(ABOUT_VERSION_SANITIZED_KEY_REF, androidAttribute(children[3], "key"))
        assertEquals("gboard_about_author", androidAttribute(children[4], "key"))
        assertEquals("gboard_about_patch_version", androidAttribute(children[5], "key"))
    }

    @Test
    fun `canonical Android namespace remains valid with another XML prefix`() {
        val fixture = String(readFixture(), StandardCharsets.UTF_8)
            .replace("xmlns:android=", "xmlns:a=")
            .replace("android:", "a:")
        val document = parse(fixture.toByteArray(StandardCharsets.UTF_8))

        applyGboardAboutPagePatch(document)

        val children = document.documentElement.childElements().toList()
        assertEquals(6, children.size)
        assertEquals(ABOUT_VERSION_KEY_REF, androidAttribute(children[3], "key"))
        assertEquals("gboard_about_author", androidAttribute(children[4], "key"))
        assertEquals("gboard_about_patch_version", androidAttribute(children[5], "key"))
    }

    @Test
    fun `decoy current-version title and historical key cannot steal the 1853 anchor`() {
        val document = parse(readFixture())
        val screen = document.documentElement
        val stockVersion = screen.childElements().last()
        val titleDecoy = document.createElement("Preference").apply {
            setAttributeNS(ANDROID_NS, "android:key", "unrelated_key")
            setAttributeNS(ANDROID_NS, "android:title", "@string/APKTOOL_RENAMED_0x7f140b3a")
        }
        val oldKeyDecoy = document.createElement(ABOUT_PREFERENCE_CLASS).apply {
            setAttributeNS(ANDROID_NS, "android:key", "@string/APKTOOL_RENAMED_0x7f140c23")
            setAttributeNS(ANDROID_NS, "android:title", "Morse settings")
        }
        screen.insertBefore(titleDecoy, stockVersion)
        screen.insertBefore(oldKeyDecoy, stockVersion)

        applyGboardAboutPagePatch(document)

        val children = screen.childElements().toList()
        assertEquals(8, children.size)
        assertTrue(children[3].isSameNode(titleDecoy))
        assertTrue(children[4].isSameNode(oldKeyDecoy))
        assertTrue(children[5].isSameNode(stockVersion))
        assertEquals("gboard_about_author", androidAttribute(children[6], "key"))
        assertEquals("gboard_about_patch_version", androidAttribute(children[7], "key"))
    }

    @Test
    fun `duplicate exact version keys are rejected before any mutation`() {
        val document = parse(readFixture())
        val screen = document.documentElement
        screen.appendChild(screen.childElements().last().cloneNode(true))

        assertRejectedWithoutMutation(document)
    }

    @Test
    fun `version key on another preference class is rejected before any mutation`() {
        val document = parse(readFixture())
        val screen = document.documentElement
        val stockVersion = screen.childElements().last()
        val wrongClass = document.createElement("Preference")
        for (index in 0 until stockVersion.attributes.length) {
            val attribute = stockVersion.attributes.item(index)
            wrongClass.setAttributeNS(attribute.namespaceURI, attribute.nodeName, attribute.nodeValue)
        }
        screen.replaceChild(wrongClass, stockVersion)

        assertRejectedWithoutMutation(document)
    }

    @Test
    fun `version key with a non-version title is rejected before any mutation`() {
        val document = parse(readFixture())
        document.documentElement.childElements().last().setAttributeNS(
            ANDROID_NS, "android:title", "@string/APKTOOL_RENAMED_0x7f140c23",
        )

        assertRejectedWithoutMutation(document)
    }

    @Test
    fun `historical 1803 key is not an active fallback`() {
        val document = parse(readFixture())
        document.documentElement.childElements().last().setAttributeNS(
            ANDROID_NS, "android:key", "@string/APKTOOL_RENAMED_0x7f140c23",
        )

        assertRejectedWithoutMutation(document)
    }

    @Test
    fun `mixed raw and sanitized identities are rejected before any mutation`() {
        val document = parse(readFixture())
        document.documentElement.childElements().last().setAttributeNS(
            ANDROID_NS, "android:title", "@string/string_0x7f140b3a",
        )

        assertRejectedWithoutMutation(document)
    }

    @Test
    fun `canonical version row metadata is required before mutation`() {
        mapOf(
            "persistent" to "true",
            "focusable" to "true",
            "selectable" to "true",
            "maxLines" to "1",
        ).forEach { (name, value) ->
            val document = parse(readFixture())
            document.documentElement.childElements().last().setAttributeNS(
                ANDROID_NS, "android:$name", value,
            )
            assertRejectedWithoutMutation(document)
        }
    }

    private fun assertRejectedWithoutMutation(document: Document) {
        val before = document.documentElement.cloneNode(true)
        assertThrows(IllegalStateException::class.java) { applyGboardAboutPagePatch(document) }
        assertTrue("Rejected About input must remain untouched", before.isEqualNode(document.documentElement))
    }

    private fun assertAboutRow(
        element: Element,
        key: String,
        title: String,
        summary: String,
        intentUrl: String,
    ) {
        assertEquals(ABOUT_PREFERENCE_CLASS, element.tagName)
        assertEquals(key, androidAttribute(element, "key"))
        assertEquals(title, androidAttribute(element, "title"))
        assertEquals(summary, androidAttribute(element, "summary"))
        assertEquals("false", androidAttribute(element, "persistent"))
        assertEquals("false", androidAttribute(element, "focusable"))
        assertEquals("true", androidAttribute(element, "selectable"))
        assertEquals("100", androidAttribute(element, "maxLines"))

        val intents = element.childElements("intent").toList()
        assertEquals(1, intents.size)
        assertEquals("android.intent.action.VIEW", androidAttribute(intents.single(), "action"))
        assertEquals(intentUrl, androidAttribute(intents.single(), "data"))
    }

    private fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun readFixture(): ByteArray =
        (CANONICAL_ABOUT_XML.trimIndent() + "\n").toByteArray(StandardCharsets.UTF_8)

    private fun androidAttribute(element: Element, localName: String): String? =
        element.getAttributeNS(ANDROID_NS, localName).takeIf { it.isNotBlank() }
            ?: element.getAttribute("android:$localName").takeIf { it.isNotBlank() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02X".format(byte) }

    private fun normalizeLineEndings(bytes: ByteArray): ByteArray =
        String(bytes, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .toByteArray(StandardCharsets.UTF_8)

    private companion object {
        val CANONICAL_ABOUT_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <PreferenceScreen android:title="@string/APKTOOL_RENAMED_0x7f140b39" android:key="@string/APKTOOL_RENAMED_0x7f140a69"
              xmlns:android="http://schemas.android.com/apk/res/android">
                <Preference android:persistent="false" android:title="@string/APKTOOL_RENAMED_0x7f140c9e">
                    <intent android:action="android.intent.action.VIEW" android:data="@string/APKTOOL_RENAMED_0x7f1411b6" />
                </Preference>
                <Preference android:persistent="false" android:title="@string/APKTOOL_RENAMED_0x7f140c4d">
                    <intent android:action="android.intent.action.VIEW" android:data="@string/APKTOOL_RENAMED_0x7f140ad7" />
                </Preference>
                <Preference android:persistent="false" android:title="@string/APKTOOL_RENAMED_0x7f140c1c" android:key="@string/APKTOOL_RENAMED_0x7f140a47" android:summary="@string/APKTOOL_RENAMED_0x7f140c1b">
                    <intent android:targetPackage="" android:action="android.intent.action.MAIN" android:targetClass="com.google.android.libraries.social.licenses.LicenseMenuActivity" />
                </Preference>
                <com.google.android.libraries.inputmethod.settings.widget.ExtendedPreference android:persistent="false" android:focusable="false" android:maxLines="100" android:title="@string/APKTOOL_RENAMED_0x7f140b3a" android:selectable="false" android:key="@string/APKTOOL_RENAMED_0x7f140b30" />
            </PreferenceScreen>
        """
        const val FIXTURE_SHA256 =
            "6789332303973A6D4242BBAFDDD9677DB7E1003615C1E06D8EE40204EF61B927"
        const val ABOUT_VERSION_KEY_REF = "@string/APKTOOL_RENAMED_0x7f140b30"
        const val ABOUT_VERSION_SANITIZED_KEY_REF = "@string/string_0x7f140b30"
        const val ABOUT_PREFERENCE_CLASS =
            "com.google.android.libraries.inputmethod.settings.widget.ExtendedPreference"
    }
}
