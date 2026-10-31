package dev.jason.gboardpatches.patches.gboard.features.packagerename

import app.morphe.patcher.resource.PublicXmlManager
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.applyGboardPatchesSettings
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class GboardPackageRenameTransformTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `application display name uses a dedicated resource and preserves component labels`() {
        val manifest = loadManifest()
        val application = elements(manifest, "application").single()
        val componentLabelsBefore = componentLabels(manifest)
        val latinIme = elements(manifest, "service").single { element ->
            androidAttribute(element, "name") == "com.android.inputmethod.latin.LatinIME"
        }
        val launcher = elements(manifest, "activity").single { element ->
            androidAttribute(element, "name") ==
                "com.google.android.libraries.inputmethod.launcher.LauncherActivity"
        }

        assertEquals("@string/ime_name", androidAttribute(application, "label"))
        assertEquals(null, androidAttribute(latinIme, "label"))
        assertEquals(null, androidAttribute(launcher, "label"))

        applyGboardApplicationDisplayName(manifest, "Gboard Dev", displayNameResources())

        assertEquals("@string/gboard_patches_app_display_name", androidAttribute(application, "label"))
        assertEquals(componentLabelsBefore, componentLabels(manifest))
    }

    @Test
    fun `application display name uses a resource in namespace unaware cli documents`() {
        val manifest = parse(readFixtureBytes(MANIFEST_FIXTURE), namespaceAware = false)
        val application = elements(manifest, "application").single()

        applyGboardApplicationDisplayName(manifest, "Gboard Dev", displayNameResources())

        assertEquals("@string/gboard_patches_app_display_name", androidAttribute(application, "label"))
        assertEquals(
            1,
            application.attributes.asList().count { attribute ->
                attribute.nodeName == "android:label" ||
                    (attribute.namespaceURI == ANDROID_NS && attribute.localName == "label")
            },
        )
        assertFalse(serialize(manifest).contains("android:label=\"@string/ime_name\""))
    }

    @Test
    fun `application display name accepts safe unicode and rejects unsafe values`() {
        listOf(
            "Gboard Dev",
            "Jason 鍵盤",
            "Keyboard 🚀",
            "x".repeat(MAX_GBOARD_APP_DISPLAY_NAME_CODE_POINTS),
        ).forEach { value ->
            assertTrue(value, isValidGboardAppDisplayName(value))
        }

        listOf(
            null,
            "",
            " ",
            " Gboard Dev",
            "Gboard Dev ",
            "@string/ime_name",
            "?attr/appName",
            "Gboard\nDev",
            "Gboard\u0000Dev",
            "Gboard\u2028Dev",
            "x".repeat(MAX_GBOARD_APP_DISPLAY_NAME_CODE_POINTS + 1),
        ).forEach { value ->
            assertFalse(value, isValidGboardAppDisplayName(value))
        }
    }

    @Test
    fun `application display name requires exactly one application element`() {
        val manifest = loadManifest()
        manifest.documentElement.appendChild(manifest.createElement("application"))
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardApplicationDisplayName(manifest, "Gboard Dev", displayNameResources())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `display name is a default-only string without changing native or locale values`() {
        val manifest = loadManifest()
        val resources = displayNameResources()
        val locales = listOf(
            parse("<resources><string name=\"ime_name\">原生鍵盤</string></resources>", true),
            parse("<resources><string name=\"ime_name\">Native keyboard</string></resources>", true),
        )
        val beforeLocales = locales.map(::serialize)

        applyGboardApplicationDisplayName(manifest, "GboardWu", resources, locales)

        val entry = elements(resources, "string").single {
            it.getAttribute("name") == "gboard_patches_app_display_name"
        }
        assertEquals("\"GboardWu\"", entry.textContent)
        assertEquals("false", entry.getAttribute("translatable"))
        assertEquals("false", entry.getAttribute("formatted"))
        assertEquals("Original name", elements(resources, "string").single {
            it.getAttribute("name") == "ime_name"
        }.textContent)
        assertEquals(beforeLocales, locales.map(::serialize))
        assertEquals("@string/gboard_patches_app_display_name",
            androidAttribute(elements(manifest, "application").single(), "label"))
    }

    @Test
    fun `display name resource is idempotent and can update its owned value`() {
        val manifest = loadManifest()
        val resources = displayNameResources()
        applyGboardApplicationDisplayName(manifest, "GboardWu", resources)
        val once = listOf(serialize(manifest), serialize(resources))

        applyGboardApplicationDisplayName(manifest, "GboardWu", resources)
        assertEquals(once, listOf(serialize(manifest), serialize(resources)))
        applyGboardApplicationDisplayName(manifest, "鍵盤 🚀", resources)

        val entries = elements(resources, "string").filter {
            it.getAttribute("name") == "gboard_patches_app_display_name"
        }
        assertEquals(1, entries.size)
        assertEquals("\"鍵盤 🚀\"", entries.single().textContent)
    }

    @Test
    fun `display name preserves literal Android punctuation spaces and Unicode through XML`() {
        val manifest = loadManifest()
        val resources = displayNameResources()
        val displayName = "Jason's <&> \"鍵盤\" \\  100% 🚀"

        applyGboardApplicationDisplayName(manifest, displayName, resources)

        val roundTrip = parse(serialize(resources), true)
        val entry = elements(roundTrip, "string").single {
            it.getAttribute("name") == "gboard_patches_app_display_name"
        }
        assertEquals("\"Jason\\'s <&> \\\"鍵盤\\\" \\\\  100% 🚀\"", entry.textContent)
        assertEquals("false", entry.getAttribute("formatted"))
    }

    @Test
    fun `an occupied default display-name resource is rejected before either DOM changes`() {
        val manifest = loadManifest()
        val resources = parse("""
            <resources><string name="gboard_patches_app_display_name">Foreign</string></resources>
        """.trimIndent(), true)

        assertDisplayNameRejected(manifest, resources)
    }

    @Test
    fun `duplicate display-name definitions are rejected before either DOM changes`() {
        val manifest = loadManifest()
        val resources = displayNameResources()
        applyGboardApplicationDisplayName(manifest, "GboardWu", resources)
        val entry = elements(resources, "string").single {
            it.getAttribute("name") == "gboard_patches_app_display_name"
        }
        resources.documentElement.appendChild(entry.cloneNode(true))

        assertDisplayNameRejected(manifest, resources)
    }

    @Test
    fun `localized or other-file display-name values cannot shadow the selected name`() {
        listOf(
            """<resources><string name="gboard_patches_app_display_name">Localized</string></resources>""",
            """<resources><item type="string" name="gboard_patches_app_display_name">@string/ime_name</item></resources>""",
        ).forEach { collision ->
            assertDisplayNameRejected(loadManifest(), displayNameResources(), listOf(parse(collision, true)))
        }
    }

    @Test
    fun `patch-owned display-name reference requires its unique correctly marked definition`() {
        val missingManifest = loadManifest()
        elements(missingManifest, "application").single().setAttributeNS(
            ANDROID_NS, "android:label", "@string/gboard_patches_app_display_name",
        )
        assertDisplayNameRejected(missingManifest, displayNameResources())
        listOf("formatted", "translatable").forEach { attribute ->
            val manifest = loadManifest()
            val resources = displayNameResources()
            applyGboardApplicationDisplayName(manifest, "GboardWu", resources)
            elements(resources, "string").single {
                it.getAttribute("name") == "gboard_patches_app_display_name"
            }.setAttribute(attribute, "true")
            assertDisplayNameRejected(manifest, resources)
        }
    }

    @Test
    fun `malformed default resource root is rejected before manifest changes`() {
        assertDisplayNameRejected(loadManifest(), parse("<not-resources/>", true))
    }

    @Test
    fun `public resource ID metadata is not mistaken for a localized value`() {
        val manifest = loadManifest()
        val resources = displayNameResources()
        val publicIds = parse("""
            <resources><public type="string" name="gboard_patches_app_display_name" id="0x7f140001"/></resources>
        """.trimIndent(), true)
        val beforePublic = serialize(publicIds)

        applyGboardApplicationDisplayName(manifest, "GboardWu", resources, listOf(publicIds))

        assertEquals(beforePublic, serialize(publicIds))
        assertEquals(1, elements(resources, "string").count {
            it.getAttribute("name") == "gboard_patches_app_display_name"
        })
    }

    private fun displayNameResources(): Document = parse(
        "<resources><string name=\"ime_name\">Original name</string></resources>", true,
    )

    private fun assertDisplayNameRejected(
        manifest: Document,
        resources: Document,
        others: List<Document> = emptyList(),
    ) {
        val documents = listOf(manifest, resources) + others
        val before = documents.map(::serialize)
        assertFails<IllegalStateException> {
            applyGboardApplicationDisplayName(manifest, "GboardWu", resources, others)
        }
        assertEquals(before, documents.map(::serialize))
    }

    @Test
    fun `production display-name seam registers one allocated public string ID persistently`() {
        val publicFile = temporaryFolder.newFile("public.xml")
        publicFile.writeText("""
            <resources package="com.google.android.inputmethod.latin" id="0x7f">
                <public type="string" name="ime_name" id="0x7f140000"/>
                <public type="string" name="other_name" id="0x7f140010"/>
                <public type="drawable" name="existing_icon" id="0x7f080003"/>
            </resources>
        """.trimIndent())
        val manifest = loadManifest()
        val resources = displayNameResources()
        var originalIds = emptyMap<Pair<String, String>, Int>()
        PublicXmlManager(publicFile).use { publicIds ->
            originalIds = publicIds.getDefinedIds().toMap()
            applyGboardApplicationDisplayName(manifest, "GboardWu", resources, emptyList(), publicIds)
            applyGboardApplicationDisplayName(manifest, "GboardWu", resources, emptyList(), publicIds)
        }

        PublicXmlManager(publicFile).use { persisted ->
            assertTrue("Production must register the new string before resource encoding",
                persisted.idExists("string", "gboard_patches_app_display_name"))
            val ids = persisted.getDefinedIds()
            val newKey = "string" to "gboard_patches_app_display_name"
            assertEquals(originalIds, ids.filterKeys { it != newKey })
            assertEquals(originalIds.size + 1, ids.size)
            assertEquals(0x7f140011, ids.getValue(newKey))
            assertEquals(ids.size, ids.values.toSet().size)
        }
    }

    @Test
    fun `production display-name values path is encoded by ARSC as string resources`() {
        val typeParser = Class.forName("com.reandroid.arsc.coder.xml.XmlEncodeUtil")
            .getMethod("getTypeFromValuesXml", File::class.java)
        val actualType = typeParser.invoke(null, File(APP_DISPLAY_NAME_RESOURCE_PATH))

        assertEquals("The real ARSC encoder selects its type from the production filename",
            "string", actualType)
    }

    @Test
    fun `mapping inventory is the exact sixteen target attributes`() {
        val actual = GBOARD_PACKAGE_RENAME_MAPPINGS.map { mapping ->
            listOf(
                mapping.elementName,
                mapping.qualifiedAttributeName,
                mapping.originalValue,
                mapping.renamedValue,
            )
        }

        assertEquals(EXPECTED_MAPPINGS, actual)
        assertEquals(16, actual.size)
    }

    @Test
    fun `stock target transforms every selected attribute exactly once`() {
        val manifest = loadManifest()
        val originalComponents = componentNames(manifest)

        val result = applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertEquals(GboardPackageRenameResult.RENAMED, result)
        assertEquals(FINAL_PACKAGE, manifest.documentElement.getAttribute("package"))
        GBOARD_PACKAGE_RENAME_MAPPINGS.forEach { mapping ->
            assertEquals(0, mappingAttributes(manifest, mapping, mapping.originalValue).size)
            assertEquals(1, mappingAttributes(manifest, mapping, mapping.renamedValue).size)
        }
        assertFalse(serialize(manifest).contains(DOUBLE_PREFIX))
        assertEquals(originalComponents, componentNames(manifest))
        assertEquals("com.android.inputmethod.latin", originalPackageName(manifest))
    }

    @Test
    fun `custom target package renames every selected attribute`() {
        val custom = "com.example.keyboard"
        val manifest = loadManifest()

        val result = applyGboardPackageRename(manifest, loadSettingsDocuments(), custom)

        assertEquals(GboardPackageRenameResult.RENAMED, result)
        assertEquals(custom, manifest.documentElement.getAttribute("package"))
        gboardPackageRenameMappings(custom).forEach { mapping ->
            assertEquals(0, mappingAttributes(manifest, mapping, mapping.originalValue).size)
            assertEquals(1, mappingAttributes(manifest, mapping, mapping.renamedValue).size)
        }
        assertFalse(serialize(manifest).contains(FINAL_PACKAGE))
    }

    @Test
    fun `custom target package is idempotent and rejects a different renamed root`() {
        val custom = "com.touchtype.swiftkey"
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()
        applyGboardPackageRename(manifest, settings, custom)

        assertEquals(
            GboardPackageRenameResult.ALREADY_RENAMED,
            applyGboardPackageRename(manifest, settings, custom),
        )
        try {
            applyGboardPackageRename(manifest, settings, "com.example.other")
            fail("Expected a different target package to be rejected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Unexpected Gboard manifest package"))
        }
    }

    @Test
    fun `target package validation accepts android package names only`() {
        listOf("com.example.keyboard", "com.touchtype.swiftkey", "a.b", "My.App_1.x9")
            .forEach { assertTrue(it, isValidGboardTargetPackageName(it)) }
        listOf(
            null, "", "single", "1com.example", "com..example", "com.example.", ".com.example",
            "com.exa mple", "com.example-app", "com.1example", "com.google.android.inputmethod.latin",
            "a." + "b".repeat(300),
        ).forEach { assertFalse(it.toString(), isValidGboardTargetPackageName(it)) }
    }

    @Test
    fun `standalone package rename removes required split markers but retains split inventory`() {
        val manifest = loadManifest()
        val root = manifest.documentElement
        val requiredSplitMetadata = elements(manifest, "meta-data").single { element ->
            androidAttribute(element, "name") == "com.android.vending.splits.required"
        }
        val splitInventoryMetadata = elements(manifest, "meta-data").single { element ->
            androidAttribute(element, "name") == "com.android.vending.splits"
        }

        assertTrue(root.hasAttributeNS(ANDROID_NS, "requiredSplitTypes"))
        assertTrue(root.hasAttributeNS(ANDROID_NS, "splitTypes"))
        assertEquals("true", androidAttribute(requiredSplitMetadata, "value"))
        assertEquals("@xml/splits0", androidAttribute(splitInventoryMetadata, "resource"))

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertFalse(root.hasAttributeNS(ANDROID_NS, "requiredSplitTypes"))
        assertFalse(root.hasAttributeNS(ANDROID_NS, "splitTypes"))
        assertTrue(elements(manifest, "meta-data").none { element ->
            androidAttribute(element, "name") == "com.android.vending.splits.required"
        })
        assertEquals(
            "@xml/splits0",
            elements(manifest, "meta-data").single { element ->
                androidAttribute(element, "name") == "com.android.vending.splits"
            }.let { element -> androidAttribute(element, "resource") },
        )
    }

    @Test
    fun `stock target component inventory is pinned and preserved`() {
        val manifest = loadManifest()
        val before = componentNames(manifest)

        assertEquals(30, before.getValue("activity").size)
        assertEquals(22, before.getValue("service").size)
        assertEquals(12, before.getValue("receiver").size)
        assertEquals(9, before.getValue("provider").size)
        assertEquals(0, manifest.getElementsByTagName("activity-alias").length)
        assertTrue(before.values.flatten().none { name -> name.startsWith(".") })
        assertTrue(before.values.flatten().none { name -> name.contains(SOURCE_PACKAGE) })

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertEquals(before, componentNames(manifest))
        assertEquals(0, manifest.getElementsByTagName("activity-alias").length)
    }

    @Test
    fun `declared permissions receiver permission and nine authorities stay paired and exact`() {
        val manifest = loadManifest()

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        val declaredPermissions = androidAttributeValues(manifest, "permission", "name")
        assertEquals(
            setOf(
                "$FINAL_PACKAGE.pixelbundle.RECEIVER",
                "$FINAL_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            declaredPermissions.toSet(),
        )
        val receiverPermissions = androidAttributeValues(manifest, "receiver", "permission")
        val renamedReceiverPermission = "$FINAL_PACKAGE.pixelbundle.RECEIVER"
        assertEquals(1, receiverPermissions.count { it == renamedReceiverPermission })
        assertFalse(receiverPermissions.contains("$SOURCE_PACKAGE.pixelbundle.RECEIVER"))
        assertTrue(renamedReceiverPermission in declaredPermissions)
        assertEquals(
            setOf(
                FINAL_PACKAGE,
                "$FINAL_PACKAGE.clipboard_content",
                "$FINAL_PACKAGE.inputactionprovider",
                "$FINAL_PACKAGE.swissarmyknifefileprovider",
                "$FINAL_PACKAGE.androidx-startup",
                "$FINAL_PACKAGE.fileprovider",
                "$FINAL_PACKAGE.tracing",
                "$FINAL_PACKAGE.wdb",
                "$FINAL_PACKAGE.mlkitinitprovider",
            ),
            androidAttributeValues(manifest, "provider", "authorities").toSet(),
        )
    }

    @Test
    fun `second application is an exact semantic no-op`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()
        applyGboardPackageRename(manifest, settings)
        val before = listOf(manifest, *settings.toTypedArray()).map(::serialize)

        val result = applyGboardPackageRename(manifest, settings)

        assertEquals(GboardPackageRenameResult.ALREADY_RENAMED, result)
        assertEquals(before, listOf(manifest, *settings.toTypedArray()).map(::serialize))
        assertFalse(before.any { xml -> xml.contains(DOUBLE_PREFIX) })
    }

    @Test
    fun `mismatched root fails before any manifest or settings mutation`() {
        val manifest = loadManifest().also {
            it.documentElement.setAttribute("package", "example.unexpected")
        }
        val settings = loadSettingsDocuments()
        val before = listOf(manifest, *settings.toTypedArray()).map(::serialize)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, settings)
        }

        assertEquals(before, listOf(manifest, *settings.toTypedArray()).map(::serialize))
    }

    @Test
    fun `transform requires both production settings documents`() {
        val manifest = loadManifest()
        val before = serialize(manifest)

        assertFails<IllegalArgumentException> {
            applyGboardPackageRename(manifest, emptyList())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `mixed source and renamed mappings fail before mutation`() {
        val manifest = loadManifest()
        val mixedMapping = GBOARD_PACKAGE_RENAME_MAPPINGS.single {
            it.originalValue == "$SOURCE_PACKAGE.fileprovider"
        }
        mappingAttributes(manifest, mixedMapping, mixedMapping.originalValue)
            .single()
            .value = mixedMapping.renamedValue
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, loadSettingsDocuments())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `double prefixed mapping fails before mutation`() {
        val manifest = loadManifest()
        val mapping = GBOARD_PACKAGE_RENAME_MAPPINGS.single {
            it.originalValue == "$SOURCE_PACKAGE.tracing"
        }
        mappingAttributes(manifest, mapping, mapping.originalValue)
            .single()
            .value = "$DOUBLE_PREFIX.tracing"
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, loadSettingsDocuments())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `missing selected mapping fails before mutation`() {
        val manifest = loadManifest()
        val mapping = GBOARD_PACKAGE_RENAME_MAPPINGS.single {
            it.originalValue == "$SOURCE_PACKAGE.wdb"
        }
        val attribute = mappingAttributes(manifest, mapping, mapping.originalValue).single()
        attribute.ownerElement.removeAttributeNode(attribute)
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, loadSettingsDocuments())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `duplicate selected mapping fails before mutation`() {
        val manifest = loadManifest()
        val mapping = GBOARD_PACKAGE_RENAME_MAPPINGS.single {
            it.originalValue == "$SOURCE_PACKAGE.mlkitinitprovider"
        }
        val provider = mappingAttributes(manifest, mapping, mapping.originalValue)
            .single()
            .ownerElement
        provider.parentNode.appendChild(provider.cloneNode(true))
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, loadSettingsDocuments())
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `already renamed root rejects partial official derived values`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()
        applyGboardPackageRename(manifest, settings)
        val mapping = GBOARD_PACKAGE_RENAME_MAPPINGS.single {
            it.renamedValue == "$FINAL_PACKAGE.clipboard_content"
        }
        mappingAttributes(manifest, mapping, mapping.renamedValue)
            .single()
            .value = mapping.originalValue
        val before = serialize(manifest)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, settings)
        }

        assertEquals(before, serialize(manifest))
    }

    @Test
    fun `android attributes work with namespace aware alternate prefix`() {
        val source = readFixtureText(MANIFEST_FIXTURE)
            .replace("xmlns:android=", "xmlns:a=")
            .replace("android:", "a:")
        val manifest = parse(source, namespaceAware = true)

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertEquals(FINAL_PACKAGE, manifest.documentElement.getAttribute("package"))
        GBOARD_PACKAGE_RENAME_MAPPINGS.forEach { mapping ->
            assertEquals(1, mappingAttributes(manifest, mapping, mapping.renamedValue).size)
        }
    }

    @Test
    fun `literal android prefixed attributes work without namespace awareness`() {
        val manifest = parse(readFixtureText(MANIFEST_FIXTURE), namespaceAware = false)

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertEquals(FINAL_PACKAGE, manifest.documentElement.getAttribute("package"))
        GBOARD_PACKAGE_RENAME_MAPPINGS.forEach { mapping ->
            assertEquals(1, mappingAttributes(manifest, mapping, mapping.renamedValue).size)
        }
    }

    @Test
    fun `resource package literals are intentionally retained`() {
        val retainedBefore = readFixtureBytes(RETAINED_LITERALS_FIXTURE)
        val manifest = loadManifest()

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        val retainedAfter = readFixtureBytes(RETAINED_LITERALS_FIXTURE)
        assertTrue(retainedBefore.contentEquals(retainedAfter))
        val retained = parse(retainedAfter, namespaceAware = true)
        val values = elements(retained, "literal").map { it.textContent.trim() }
        assertEquals(
            listOf(
                SOURCE_PACKAGE,
                "market://details?id=$SOURCE_PACKAGE",
                "http://play.google.com/store/apps/details?id=$SOURCE_PACKAGE",
                SOURCE_PACKAGE,
            ),
            values,
        )
    }

    @Test
    fun `package rename alone does not inject settings components or entries`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()
        val componentsBefore = componentNames(manifest)
        val settingsBefore = settings.map(::serialize)

        applyGboardPackageRename(manifest, settings)

        assertEquals(componentsBefore, componentNames(manifest))
        assertEquals(settingsBefore, settings.map(::serialize))
        assertTrue(elements(manifest, "provider").none { provider ->
            androidAttribute(provider, "name") == SETTINGS_PROVIDER_CLASS
        })
    }

    @Test
    fun `partial injected settings identity fails before mutation`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()
        applyGboardPatchesSettings(manifest, settings)
        val legacyIntent = elements(settings.last(), "intent").single { intent ->
            androidAttribute(intent, "targetClass") == SETTINGS_ACTIVITY_CLASS
        }
        legacyIntent.parentNode.removeChild(legacyIntent)
        val before = listOf(manifest, *settings.toTypedArray()).map(::serialize)

        assertFails<IllegalStateException> {
            applyGboardPackageRename(manifest, settings)
        }

        assertEquals(before, listOf(manifest, *settings.toTypedArray()).map(::serialize))
    }

    @Test
    fun `package rename then settings finalize uses only final identity`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()

        applyGboardPackageRename(manifest, settings)
        applyGboardPatchesSettings(manifest, settings)
        val beforeSecondRename = listOf(manifest, *settings.toTypedArray()).map(::serialize)
        val secondResult = applyGboardPackageRename(manifest, settings)

        assertEquals(GboardPackageRenameResult.ALREADY_RENAMED, secondResult)
        assertEquals(
            beforeSecondRename,
            listOf(manifest, *settings.toTypedArray()).map(::serialize),
        )
        assertFinalSettingsIdentity(manifest, settings)
    }

    @Test
    fun `settings finalize then package rename uses only final identity`() {
        val manifest = loadManifest()
        val settings = loadSettingsDocuments()

        applyGboardPatchesSettings(manifest, settings)
        applyGboardPackageRename(manifest, settings)

        assertFinalSettingsIdentity(manifest, settings)
    }

    @Test
    fun `package rename never rewrites extension component class names`() {
        val manifest = loadManifest()
        val application = elements(manifest, "application").single()
        val service = manifest.createElement("service")
        service.setAttributeNS(
            ANDROID_NS,
            "android:name",
            "dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService",
        )
        application.appendChild(service)

        applyGboardPackageRename(manifest, loadSettingsDocuments())

        assertEquals(
            "dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService",
            androidAttribute(service, "name"),
        )
    }

    private fun assertFinalSettingsIdentity(
        manifest: Document,
        settings: List<Document>,
    ) {
        assertEquals(FINAL_PACKAGE, manifest.documentElement.getAttribute("package"))
        assertFalse(serialize(manifest).contains(DOUBLE_PREFIX))

        val settingsProviders = elements(manifest, "provider").filter { provider ->
            androidAttribute(provider, "name") == SETTINGS_PROVIDER_CLASS
        }
        assertEquals(1, settingsProviders.size)
        assertEquals(
            "$FINAL_PACKAGE.gboard_patches",
            androidAttribute(settingsProviders.single(), "authorities"),
        )

        settings.forEach { document ->
            val matchingIntents = elements(document, "intent").filter { intent ->
                androidAttribute(intent, "targetClass") == SETTINGS_ACTIVITY_CLASS
            }
            assertEquals(1, matchingIntents.size)
            assertEquals(FINAL_PACKAGE, androidAttribute(matchingIntents.single(), "targetPackage"))
            val xml = serialize(document)
            assertFalse(xml.contains("targetPackage=\"$SOURCE_PACKAGE\""))
            assertFalse(xml.contains(DOUBLE_PREFIX))
        }

        val components = componentNames(manifest)
        assertEquals(31, components.getValue("activity").size)
        assertEquals(22, components.getValue("service").size)
        assertEquals(12, components.getValue("receiver").size)
        assertEquals(10, components.getValue("provider").size)
        assertTrue(components.values.flatten().contains(SETTINGS_ACTIVITY_CLASS))
        assertTrue(components.values.flatten().contains(SETTINGS_PROVIDER_CLASS))
    }

    private fun loadManifest(): Document = parse(
        readFixtureBytes(MANIFEST_FIXTURE),
        namespaceAware = true,
    )

    private fun loadSettingsDocuments(): List<Document> = listOf(
        parse(readFixtureBytes(SETTINGS_FIXTURE), namespaceAware = true),
        parse(readFixtureBytes(SETTINGS_LEGACY_FIXTURE), namespaceAware = true),
    )

    private fun readFixtureText(path: String): String =
        String(readFixtureBytes(path), StandardCharsets.UTF_8)

    private fun readFixtureBytes(path: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream(path)) {
            "Missing fixture $path"
        }.use { it.readBytes() }

    private fun parse(source: String, namespaceAware: Boolean): Document =
        parse(source.toByteArray(StandardCharsets.UTF_8), namespaceAware)

    private fun parse(source: ByteArray, namespaceAware: Boolean): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = namespaceAware
        }.newDocumentBuilder().parse(ByteArrayInputStream(source))

    private fun serialize(document: Document): String {
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    private fun componentNames(document: Document): Map<String, List<String>> =
        COMPONENT_TAGS.associateWith { tag ->
            elements(document, tag).map { element ->
                checkNotNull(androidAttribute(element, "name")) {
                    "Missing android:name on $tag"
                }
            }
        }

    private fun componentLabels(document: Document): Map<Pair<String, String>, String?> =
        COMPONENT_TAGS.flatMap { tag ->
            elements(document, tag).map { element ->
                (tag to checkNotNull(androidAttribute(element, "name"))) to
                    androidAttribute(element, "label")
            }
        }.toMap()

    private fun originalPackageName(document: Document): String =
        checkNotNull(
            elements(document, "original-package").single().getAttribute("android:name")
                .takeIf { it.isNotBlank() }
                ?: elements(document, "original-package").single()
                    .getAttributeNS(ANDROID_NS, "name")
                    .takeIf { it.isNotBlank() },
        )

    private fun androidAttributeValues(
        document: Document,
        elementName: String,
        attributeName: String,
    ): List<String> = elements(document, elementName).mapNotNull { element ->
        androidAttribute(element, attributeName)
    }

    private fun androidAttribute(element: Element, localName: String): String? =
        element.getAttributeNS(ANDROID_NS, localName).takeIf { it.isNotBlank() }
            ?: element.attributes.asList().firstOrNull { attribute ->
                attribute.localName == localName &&
                    attribute.namespaceURI == ANDROID_NS
            }?.nodeValue
            ?: element.getAttribute("android:$localName").takeIf { it.isNotBlank() }

    private fun mappingAttributes(
        document: Document,
        mapping: GboardPackageRenameMapping,
        value: String,
    ): List<Attr> = elements(document, mapping.elementName).mapNotNull { element ->
        element.attributes.asList().firstOrNull { attribute ->
            val nameMatches = if (mapping.namespaceUri == null) {
                attribute.nodeName == mapping.attributeName
            } else {
                (
                    attribute.namespaceURI == mapping.namespaceUri &&
                        attribute.localName == mapping.attributeName
                    ) ||
                    attribute.nodeName == "android:" + mapping.attributeName
            }
            nameMatches && attribute.nodeValue == value
        } as? Attr
    }

    private fun elements(document: Document, tagName: String): List<Element> =
        document.getElementsByTagName("*").asList().filterIsInstance<Element>().filter {
            (it.localName ?: it.tagName.substringAfter(':')) == tagName
        }

    private fun org.w3c.dom.NamedNodeMap.asList(): List<org.w3c.dom.Node> =
        (0 until length).map(::item)

    private fun org.w3c.dom.NodeList.asList(): List<org.w3c.dom.Node> =
        (0 until length).map(::item)

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
            fail("Expected " + T::class.java.simpleName)
        } catch (throwable: Throwable) {
            if (throwable !is T) {
                throw throwable
            }
        }
    }

    private companion object {
        const val SOURCE_PACKAGE = "com.google.android.inputmethod.latin"
        const val FINAL_PACKAGE = "dev.jason.com.google.android.inputmethod.latin"
        const val DOUBLE_PREFIX = "dev.jason.dev.jason.com.google.android.inputmethod.latin"
        const val SETTINGS_ACTIVITY_CLASS =
            "dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity"
        const val SETTINGS_PROVIDER_CLASS =
            "dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsProvider"
        const val MANIFEST_FIXTURE =
            "/gboard/18.5.3/packagerename/AndroidManifest.xml"
        const val SETTINGS_FIXTURE = "/gboard/18.5.3/packagerename/settings.xml"
        const val SETTINGS_LEGACY_FIXTURE =
            "/gboard/18.5.3/packagerename/settings_legacy.xml"
        const val RETAINED_LITERALS_FIXTURE =
            "/gboard/18.5.3/packagerename/retained-resource-literals.xml"
        val COMPONENT_TAGS = listOf("activity", "service", "receiver", "provider")
        val EXPECTED_MAPPINGS = listOf(
            listOf("manifest", "package", SOURCE_PACKAGE, FINAL_PACKAGE),
            listOf(
                "permission",
                "android:name",
                "$SOURCE_PACKAGE.pixelbundle.RECEIVER",
                "$FINAL_PACKAGE.pixelbundle.RECEIVER",
            ),
            listOf(
                "permission",
                "android:name",
                "$SOURCE_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                "$FINAL_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
            ),
            listOf(
                "meta-data",
                "android:name",
                "com.google.android.gms.phenotype.registration.binarypb:$SOURCE_PACKAGE",
                "com.google.android.gms.phenotype.registration.binarypb:$FINAL_PACKAGE",
            ),
            listOf(
                "meta-data",
                "android:name",
                "com.google.android.gms.phenotype.registration.xml:$SOURCE_PACKAGE",
                "com.google.android.gms.phenotype.registration.xml:$FINAL_PACKAGE",
            ),
            listOf("provider", "android:authorities", SOURCE_PACKAGE, FINAL_PACKAGE),
            listOf(
                "data",
                "android:host",
                "deeplink.$SOURCE_PACKAGE",
                "deeplink.$FINAL_PACKAGE",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.clipboard_content",
                "$FINAL_PACKAGE.clipboard_content",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.inputactionprovider",
                "$FINAL_PACKAGE.inputactionprovider",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.swissarmyknifefileprovider",
                "$FINAL_PACKAGE.swissarmyknifefileprovider",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.androidx-startup",
                "$FINAL_PACKAGE.androidx-startup",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.fileprovider",
                "$FINAL_PACKAGE.fileprovider",
            ),
            listOf(
                "receiver",
                "android:permission",
                "$SOURCE_PACKAGE.pixelbundle.RECEIVER",
                "$FINAL_PACKAGE.pixelbundle.RECEIVER",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.tracing",
                "$FINAL_PACKAGE.tracing",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.wdb",
                "$FINAL_PACKAGE.wdb",
            ),
            listOf(
                "provider",
                "android:authorities",
                "$SOURCE_PACKAGE.mlkitinitprovider",
                "$FINAL_PACKAGE.mlkitinitprovider",
            ),
        )
    }
}
