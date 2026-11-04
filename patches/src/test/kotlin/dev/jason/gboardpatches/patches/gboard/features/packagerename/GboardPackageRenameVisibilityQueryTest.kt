package dev.jason.gboardpatches.patches.gboard.features.packagerename

import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardPackageRenameVisibilityQueryTest {
    private val supported = listOf(
        "com.google.android.inputmethod.latin",
        "com.google.android.inputmethod.latin.jason.dev",
        "dev.jason.com.google.android.inputmethod.latin",
    )

    private fun load(path: String): Document {
        val input: InputStream = checkNotNull(javaClass.getResourceAsStream(path)) {
            "Missing Gboard fixture $path"
        }
        return input.use {
            DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }.newDocumentBuilder().parse(it)
        }
    }
    private fun manifest() =
        load("/gboard/18.0.3/package-rename/AndroidManifest.normalized.xml")

    private fun settings() = listOf(
        load("/gboard/18.0.3/package-rename/settings.xml"),
        load("/gboard/18.0.3/package-rename/settings_legacy.xml"),
    )
    private fun addQuery(manifest: Document, name: String): Element {
        val queries = manifest.documentElement
            .getElementsByTagName("queries").item(0) as Element
        return manifest.createElement("package").also { item ->
            item.setAttributeNS(ANDROID_NS, "android:name", name)
            queries.appendChild(item)
        }
    }

    @Test fun acceptsOnlyThreeKnownVisibilityQueriesAndRepeatedRename() {
        val manifest = manifest()
        val settings = settings()
        val items = supported.map { name -> name to addQuery(manifest, name) }
        assertEquals(GboardPackageRenameResult.RENAMED,
            applyGboardPackageRename(manifest, settings))
        assertEquals("dev.jason.com.google.android.inputmethod.latin",
            manifest.documentElement.getAttribute("package"))
        items.forEach { (name, item) ->
            assertEquals(name, item.getAttributeNS(ANDROID_NS, "name"))
        }
        assertEquals(GboardPackageRenameResult.ALREADY_RENAMED,
            applyGboardPackageRename(manifest, settings))
    }

    @Test fun duplicateKnownGboardQueryIsRejected() {
        val manifest = manifest()
        addQuery(manifest, supported.first())
        addQuery(manifest, supported.first())
        val error = runCatching {
            applyGboardPackageRename(manifest, settings())
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("Duplicate known Gboard"))
        assertEquals("com.google.android.inputmethod.latin",
            manifest.documentElement.getAttribute("package"))
    }

    @Test fun unexpectedGboardQueryIsStillRejected() {
        val manifest = manifest()
        addQuery(manifest, "com.google.android.inputmethod.latin.unexpected")
        val error = runCatching {
            applyGboardPackageRename(manifest, settings())
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("Unexpected package-derived"))
        assertEquals("com.google.android.inputmethod.latin",
            manifest.documentElement.getAttribute("package"))
    }

    @Test fun originalPackageReferenceOutsideQueriesIsStillRejected() {
        val manifest = manifest()
        supported.forEach { addQuery(manifest, it) }
        val app = manifest.getElementsByTagName("application").item(0) as Element
        val entry = manifest.createElement("meta-data")
        entry.setAttributeNS(ANDROID_NS, "android:name",
            "com.google.android.inputmethod.latin")
        app.appendChild(entry)
        val error = runCatching {
            applyGboardPackageRename(manifest, settings())
        }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("Unexpected package-derived"))
    }
}