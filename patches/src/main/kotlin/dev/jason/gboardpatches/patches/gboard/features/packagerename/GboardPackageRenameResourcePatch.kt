package dev.jason.gboardpatches.patches.gboard.features.packagerename

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.PublicXmlManager
import dev.jason.gboardpatches.patches.gboard.features.lanftp.LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX
import dev.jason.gboardpatches.patches.gboard.features.lanftp.LAN_FTP_STATUS_PROVIDER_CLASS
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.GBOARD_PATCHES_SETTINGS_ACTIVITY_CLASS
import dev.jason.gboardpatches.patches.gboard.shared.GBOARD_PATCHES_SETTINGS_PROVIDER_AUTHORITY_SUFFIX
import dev.jason.gboardpatches.patches.gboard.shared.GBOARD_PATCHES_SETTINGS_PROVIDER_CLASS
import dev.jason.gboardpatches.patches.gboard.shared.GBOARD_SETTINGS_XML_PATHS
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.elements
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PACKAGE_NAME
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_DEFAULT_PATCHED_PACKAGE_NAME
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Target package name chosen through the Package Rename patch option. The option lives on the
 * public patch; the resource patch it depends on reads the value here when it finalizes.
 */
internal object GboardPackageRenameTarget {
    @Volatile
    var packageName: String = GBOARD_DEFAULT_PATCHED_PACKAGE_NAME
}

private val PACKAGE_NAME_REGEX =
    Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
internal const val MAX_GBOARD_PACKAGE_NAME_LENGTH = 255

internal fun isValidGboardPackageName(value: String?): Boolean {
    if (value.isNullOrEmpty() || value.length > MAX_GBOARD_PACKAGE_NAME_LENGTH) return false
    return PACKAGE_NAME_REGEX.matches(value)
}

/** A custom package must be valid and differ from the official one so both can coexist. */
internal fun isValidGboardTargetPackageName(value: String?): Boolean =
    isValidGboardPackageName(value) && value != GBOARD_PACKAGE_NAME

internal val gboardPackageRenameResourcePatch = resourcePatch(
    description =
        "將套件名稱改成可共存安裝的自訂值；僅支援單一 APK 產物，不可搭配原套件名稱的 split APK。"
) {
    finalize {
        applyManifestPackageOverride(GboardPackageRenameTarget.packageName)
    }
}

context(context: ResourcePatchContext)
private fun applyManifestPackageOverride(targetPackage: String) = with(context) {
    document("AndroidManifest.xml").use { manifestDocument ->
        document(GBOARD_SETTINGS_XML_PATHS[0]).use { settingsDocument ->
            document(GBOARD_SETTINGS_XML_PATHS[1]).use { legacySettingsDocument ->
                applyGboardPackageRename(
                    manifestDocument = manifestDocument,
                    settingsDocuments = listOf(settingsDocument, legacySettingsDocument),
                    targetPackage = targetPackage,
                )
            }
        }
    }
}

internal data class GboardPackageRenameMapping(
    val elementName: String,
    val namespaceUri: String?,
    val attributeName: String,
    val originalValue: String,
    val renamedValue: String,
) {
    val qualifiedAttributeName: String
        get() = if (namespaceUri == null) attributeName else "android:$attributeName"
}

internal enum class GboardPackageRenameResult {
    RENAMED,
    ALREADY_RENAMED,
}

internal const val MAX_GBOARD_APP_DISPLAY_NAME_CODE_POINTS = 40

internal fun isValidGboardAppDisplayName(value: String?): Boolean {
    if (value.isNullOrEmpty() || value != value.trim()) return false
    if (value.codePointCount(0, value.length) > MAX_GBOARD_APP_DISPLAY_NAME_CODE_POINTS) return false
    if (value.first() == '@' || value.first() == '?') return false
    return value.none { character ->
        character.isISOControl() || character == '\u2028' || character == '\u2029'
    }
}

private const val APP_DISPLAY_NAME_RESOURCE = "gboard_patches_app_display_name"
private const val APP_DISPLAY_NAME_RESOURCE_REF = "@string/$APP_DISPLAY_NAME_RESOURCE"
internal const val APP_DISPLAY_NAME_RESOURCE_PATH = "res/values/strings.xml"

context(context: ResourcePatchContext)
internal fun applyGboardApplicationDisplayName(displayName: String) = with(context) {
    val target = this[APP_DISPLAY_NAME_RESOURCE_PATH, false]
    check(target.isFile) { "Missing existing default string resources at $APP_DISPLAY_NAME_RESOURCE_PATH" }
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val builder = factory.newDocumentBuilder()
    val labelResources = builder.parse(target)
    val otherResources = this["res", false].listFiles().orEmpty().asSequence()
        .filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
        .flatMap { it.listFiles().orEmpty().asSequence() }
        .filter { it.isFile && it.extension == "xml" && it.absoluteFile != target.absoluteFile }
        .sortedBy { it.path }
        .map { builder.parse(it) }
        .toList()
    PublicXmlManager(this["res/values/public.xml"]).use { publicIds ->
        document("AndroidManifest.xml").use { manifest ->
            applyGboardApplicationDisplayName(manifest, displayName, labelResources, otherResources, publicIds)
            val writer = StringWriter()
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "utf-8")
            }.transform(DOMSource(labelResources), StreamResult(writer))
            check(target.parentFile.isDirectory || target.parentFile.mkdirs()) {
                "Could not create default display-name resource directory"
            }
            target.writeText(writer.toString(), Charsets.UTF_8)
        }
    }
}

internal fun applyGboardApplicationDisplayName(
    manifestDocument: Document,
    displayName: String,
    labelResourceDocument: Document,
    otherValueResourceDocuments: List<Document>,
    publicIds: PublicXmlManager,
) {
    applyGboardApplicationDisplayName(
        manifestDocument, displayName, labelResourceDocument, otherValueResourceDocuments,
    )
    publicIds.createPublicId("string", APP_DISPLAY_NAME_RESOURCE)
}

internal fun applyGboardApplicationDisplayName(
    manifestDocument: Document,
    displayName: String,
    labelResourceDocument: Document,
    otherValueResourceDocuments: List<Document> = emptyList(),
) {
    require(isValidGboardAppDisplayName(displayName)) {
        "Invalid Gboard app display name"
    }
    val applications = manifestDocument.documentElement.childElements("application").toList()
    check(applications.size == 1) {
        "Expected exactly one application element, found ${applications.size}"
    }
    val label = applications.single().androidAttribute("label")
        ?: error("Gboard application is missing android:label")
    val resources = labelResourceDocument.documentElement
    check(resources.tagName == "resources") { "Expected default display-name resources root" }
    val existing = displayNameResourceEntries(labelResourceDocument)
    check(existing.size <= 1) { "Duplicate default display-name resources" }
    check(otherValueResourceDocuments.all { displayNameResourceEntries(it).isEmpty() }) {
        "Display-name resource collides outside default values/strings.xml"
    }
    if (label.value == APP_DISPLAY_NAME_RESOURCE_REF) {
        check(existing.size == 1) { "Display-name resource reference has no unique definition" }
        val entry = existing.single()
        check(entry.tagName == "string" &&
            entry.getAttribute("translatable") == "false" &&
            entry.getAttribute("formatted") == "false" &&
            entry.childElements().none() &&
            entry.textContent.startsWith('"') && entry.textContent.endsWith('"')) {
            "Existing display-name resource is not patch-owned"
        }
    } else {
        check(existing.isEmpty()) { "Display-name resource name is already occupied" }
    }

    val entry = existing.singleOrNull() ?: labelResourceDocument.createElement("string").apply {
        setAttribute("name", APP_DISPLAY_NAME_RESOURCE)
        setAttribute("translatable", "false")
        setAttribute("formatted", "false")
        resources.appendChild(this)
    }
    entry.textContent = encodeAndroidDisplayName(displayName)
    label.value = APP_DISPLAY_NAME_RESOURCE_REF
}

private fun displayNameResourceEntries(document: Document): List<Element> {
    val root = document.documentElement
    check(root.tagName == "resources") { "Expected values resource document" }
    return root.childElements().filter {
        it.tagName != "public" && it.getAttribute("name") == APP_DISPLAY_NAME_RESOURCE
    }.toList()
}

private fun encodeAndroidDisplayName(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\'' -> append("\\'")
            else -> append(character)
        }
    }
    append('"')
}

internal fun gboardPackageRenameMappings(targetPackage: String) = listOf(
    mapping("manifest", null, "package", GBOARD_PACKAGE_NAME, targetPackage),
    androidMapping(
        "permission",
        "name",
        "$GBOARD_PACKAGE_NAME.pixelbundle.RECEIVER",
        "$targetPackage.pixelbundle.RECEIVER",
    ),
    androidMapping(
        "permission",
        "name",
        "$GBOARD_PACKAGE_NAME.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "$targetPackage.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    ),
    androidMapping(
        "meta-data",
        "name",
        "com.google.android.gms.phenotype.registration.binarypb:$GBOARD_PACKAGE_NAME",
        "com.google.android.gms.phenotype.registration.binarypb:$targetPackage",
    ),
    androidMapping(
        "meta-data",
        "name",
        "com.google.android.gms.phenotype.registration.xml:$GBOARD_PACKAGE_NAME",
        "com.google.android.gms.phenotype.registration.xml:$targetPackage",
    ),
    androidMapping(
        "provider",
        "authorities",
        GBOARD_PACKAGE_NAME,
        targetPackage,
    ),
    androidMapping(
        "data",
        "host",
        "deeplink.$GBOARD_PACKAGE_NAME",
        "deeplink.$targetPackage",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.clipboard_content",
        "$targetPackage.clipboard_content",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.inputactionprovider",
        "$targetPackage.inputactionprovider",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.swissarmyknifefileprovider",
        "$targetPackage.swissarmyknifefileprovider",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.androidx-startup",
        "$targetPackage.androidx-startup",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.fileprovider",
        "$targetPackage.fileprovider",
    ),
    androidMapping(
        "receiver",
        "permission",
        "$GBOARD_PACKAGE_NAME.pixelbundle.RECEIVER",
        "$targetPackage.pixelbundle.RECEIVER",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.tracing",
        "$targetPackage.tracing",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.wdb",
        "$targetPackage.wdb",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.mlkitinitprovider",
        "$targetPackage.mlkitinitprovider",
    ),
)

internal val GBOARD_PACKAGE_RENAME_MAPPINGS =
    gboardPackageRenameMappings(GBOARD_DEFAULT_PATCHED_PACKAGE_NAME)

internal fun applyGboardPackageRename(
    manifestDocument: Document,
    settingsDocuments: List<Document>,
    targetPackage: String = GBOARD_DEFAULT_PATCHED_PACKAGE_NAME,
): GboardPackageRenameResult {
    require(isValidGboardPackageName(targetPackage)) {
        "Invalid target package name '$targetPackage'"
    }
    val renameMappings = gboardPackageRenameMappings(targetPackage)
    val rootPackage = manifestDocument.documentElement.getAttribute("package")
    val state = when (rootPackage) {
        GBOARD_PACKAGE_NAME -> PackageState.ORIGINAL
        targetPackage -> PackageState.RENAMED
        else -> error(
            "Unexpected Gboard manifest package '$rootPackage'; expected " +
                "'$GBOARD_PACKAGE_NAME' or '$targetPackage'",
        )
    }

    require(settingsDocuments.size == GBOARD_SETTINGS_XML_PATHS.size) {
        "Expected both Gboard settings documents"
    }

    val allManifestAttributes = manifestDocument.getElementsByTagName("*")
        .elements()
        .flatMap { element -> element.attributes.asSequence().map { attribute -> element to attribute } }
        .toList()
    val doublePrefixedValue = allManifestAttributes.firstOrNull { (_, attribute) ->
        attribute.nodeValue.contains(DOUBLE_PREFIX) ||
            attribute.nodeValue.contains("$targetPackage.$targetPackage")
    }
    check(doublePrefixedValue == null) {
        val (element, attribute) = checkNotNull(doublePrefixedValue)
        "Double-prefixed package value at ${element.localElementName()} " +
            "${attribute.nodeName}: ${attribute.nodeValue}"
    }

    val selectedAttributes = renameMappings.map { mapping ->
        val matchingElements = manifestDocument.getElementsByTagName("*")
            .elements()
            .filter { element -> element.localElementName() == mapping.elementName }
        val originalMatches = matchingElements.mapNotNull { element ->
            element.attribute(mapping)?.takeIf { attribute ->
                attribute.value == mapping.originalValue
            }
        }.toList()
        val renamedMatches = matchingElements.mapNotNull { element ->
            element.attribute(mapping)?.takeIf { attribute ->
                attribute.value == mapping.renamedValue
            }
        }.toList()

        val expectedMatches = when (state) {
            PackageState.ORIGINAL -> originalMatches
            PackageState.RENAMED -> renamedMatches
        }
        val oppositeMatches = when (state) {
            PackageState.ORIGINAL -> renamedMatches
            PackageState.RENAMED -> originalMatches
        }
        check(expectedMatches.size == 1 && oppositeMatches.isEmpty()) {
            "Expected exactly one ${state.label} ${mapping.elementName} " +
                "${mapping.qualifiedAttributeName} mapping from '${mapping.originalValue}' " +
                "to '${mapping.renamedValue}', found original=${originalMatches.size}, " +
                "renamed=${renamedMatches.size}"
        }
        expectedMatches.single()
    }

    val settingsIdentity = validateSettingsIdentity(
        manifestDocument = manifestDocument,
        settingsDocuments = settingsDocuments,
        state = state,
        targetPackage = targetPackage,
    )
    val lanFtpStatusProviderAuthority = validateLanFtpStatusProviderIdentity(
        manifestDocument = manifestDocument,
        state = state,
        targetPackage = targetPackage,
    )
    val allowedPackageAttributes = selectedAttributes + listOfNotNull(
        settingsIdentity.providerAuthority,
        lanFtpStatusProviderAuthority,
    )
    val unexpectedPackageAttribute = allManifestAttributes.firstOrNull { (_, attribute) ->
        attribute.nodeValue.contains(GBOARD_PACKAGE_NAME) &&
            allowedPackageAttributes.none { allowed -> allowed === attribute }
    }
    check(unexpectedPackageAttribute == null) {
        val (element, attribute) = checkNotNull(unexpectedPackageAttribute)
        "Unexpected package-derived manifest value at ${element.localElementName()} " +
            "${attribute.nodeName}: ${attribute.nodeValue}"
    }

    if (state == PackageState.RENAMED) {
        return GboardPackageRenameResult.ALREADY_RENAMED
    }

    sanitizeStandaloneSplitManifest(manifestDocument)
    selectedAttributes.zip(renameMappings).forEach { (attribute, mapping) ->
        attribute.value = mapping.renamedValue
    }
    settingsIdentity.providerAuthority?.value =
        targetPackage + GBOARD_PATCHES_SETTINGS_PROVIDER_AUTHORITY_SUFFIX
    lanFtpStatusProviderAuthority?.value =
        targetPackage + LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX
    settingsIdentity.targetPackages.forEach { attribute ->
        attribute.value = targetPackage
    }
    return GboardPackageRenameResult.RENAMED
}

private fun validateLanFtpStatusProviderIdentity(
    manifestDocument: Document,
    state: PackageState,
    targetPackage: String,
): Attr? {
    val providers = manifestDocument.getElementsByTagName("*")
        .elements()
        .filter { element ->
            element.localElementName() == "provider" &&
                element.androidAttribute("name")?.value == LAN_FTP_STATUS_PROVIDER_CLASS
        }
        .toList()
    check(providers.size <= 1) {
        "Expected at most one LAN FTP status provider, found ${providers.size}"
    }
    val authority = providers.singleOrNull()?.androidAttribute("authorities")
    check(providers.isEmpty() || authority != null) {
        "LAN FTP status provider is missing android:authorities"
    }
    if (authority != null) {
        val expectedAuthority = state.packageName(targetPackage) + LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX
        check(authority.value == expectedAuthority) {
            "Unexpected LAN FTP status provider authority '${authority.value}'; " +
                "expected '$expectedAuthority'"
        }
    }
    return authority
}

private fun sanitizeStandaloneSplitManifest(manifestDocument: Document) {
    val manifest = manifestDocument.documentElement
    listOf("requiredSplitTypes", "splitTypes").forEach { attributeName ->
        manifest.androidAttribute(attributeName)?.let(manifest::removeAttributeNode)
    }
    manifestDocument.getElementsByTagName("*")
        .elements()
        .filter { element ->
            element.localElementName() == "meta-data" &&
                element.androidAttribute("name")?.value == REQUIRED_SPLITS_METADATA
        }
        .toList()
        .forEach { element -> element.parentNode.removeChild(element) }
}

private fun validateSettingsIdentity(
    manifestDocument: Document,
    settingsDocuments: List<Document>,
    state: PackageState,
    targetPackage: String,
): SettingsIdentity {
    val settingsProviders = manifestDocument.getElementsByTagName("*")
        .elements()
        .filter { element ->
            element.localElementName() == "provider" &&
                element.androidAttribute("name")?.value == GBOARD_PATCHES_SETTINGS_PROVIDER_CLASS
        }
        .toList()
    check(settingsProviders.size <= 1) {
        "Expected at most one Gboard patches settings provider, found ${settingsProviders.size}"
    }
    val providerAuthority = settingsProviders.singleOrNull()?.androidAttribute("authorities")
    check(settingsProviders.isEmpty() || providerAuthority != null) {
        "Gboard patches settings provider is missing android:authorities"
    }

    val expectedPackage = state.packageName(targetPackage)
    if (providerAuthority != null) {
        val expectedAuthority =
            expectedPackage + GBOARD_PATCHES_SETTINGS_PROVIDER_AUTHORITY_SUFFIX
        check(providerAuthority.value == expectedAuthority) {
            "Unexpected Gboard patches settings provider authority " +
                "'${providerAuthority.value}'; expected '$expectedAuthority'"
        }
    }

    val targetPackagesByDocument = settingsDocuments.map { document ->
        val matchingIntents = document.getElementsByTagName("*")
            .elements()
            .filter { element ->
                element.localElementName() == "intent" &&
                    element.androidAttribute("targetClass")?.value ==
                    GBOARD_PATCHES_SETTINGS_ACTIVITY_CLASS
            }
            .toList()
        check(matchingIntents.size <= 1) {
            "Expected at most one Gboard patches settings intent, found ${matchingIntents.size}"
        }
        matchingIntents.singleOrNull()?.let { intent ->
            val targetPackage = intent.androidAttribute("targetPackage")
                ?: error("Gboard patches settings intent is missing android:targetPackage")
            check(targetPackage.value == expectedPackage) {
                "Unexpected Gboard patches settings targetPackage '${targetPackage.value}'; " +
                    "expected '$expectedPackage'"
            }
            targetPackage
        }
    }

    val hasProvider = providerAuthority != null
    val hasAllSettingsIntents =
        settingsDocuments.isNotEmpty() && targetPackagesByDocument.all { it != null }
    val hasAnySettingsIntent = targetPackagesByDocument.any { it != null }
    check(
        (!hasProvider && !hasAnySettingsIntent) ||
            (hasProvider && hasAllSettingsIntents),
    ) {
        "Partial Gboard patches settings identity: provider=$hasProvider, " +
            "settingsIntents=${targetPackagesByDocument.count { it != null }}/" +
            settingsDocuments.size
    }

    return SettingsIdentity(
        providerAuthority = providerAuthority,
        targetPackages = targetPackagesByDocument.filterNotNull(),
    )
}

private fun mapping(
    elementName: String,
    namespaceUri: String?,
    attributeName: String,
    originalValue: String,
    renamedValue: String,
) = GboardPackageRenameMapping(
    elementName = elementName,
    namespaceUri = namespaceUri,
    attributeName = attributeName,
    originalValue = originalValue,
    renamedValue = renamedValue,
)

private fun androidMapping(
    elementName: String,
    attributeName: String,
    originalValue: String,
    renamedValue: String,
) = mapping(
    elementName = elementName,
    namespaceUri = ANDROID_NS,
    attributeName = attributeName,
    originalValue = originalValue,
    renamedValue = renamedValue,
)

private fun Element.attribute(mapping: GboardPackageRenameMapping): Attr? =
    attributes.asSequence().filterIsInstance<Attr>().firstOrNull { attribute ->
        if (mapping.namespaceUri == null) {
            attribute.namespaceURI == null && attribute.nodeName == mapping.attributeName
        } else {
            (
                attribute.namespaceURI == mapping.namespaceUri &&
                    attribute.localName == mapping.attributeName
                ) ||
                attribute.nodeName == "android:${mapping.attributeName}"
        }
    }

private fun Element.androidAttribute(localName: String): Attr? =
    attributes.asSequence().filterIsInstance<Attr>().firstOrNull { attribute ->
        (
            attribute.namespaceURI == ANDROID_NS &&
                attribute.localName == localName
            ) ||
            attribute.nodeName == "android:$localName"
    }

private fun Element.localElementName(): String = localName ?: tagName.substringAfter(':')

private fun org.w3c.dom.NamedNodeMap.asSequence(): Sequence<org.w3c.dom.Node> =
    (0 until length).asSequence().map(::item)

private enum class PackageState(
    val label: String,
) {
    ORIGINAL("original"),
    RENAMED("renamed");

    fun packageName(targetPackage: String): String = when (this) {
        ORIGINAL -> GBOARD_PACKAGE_NAME
        RENAMED -> targetPackage
    }
}

private data class SettingsIdentity(
    val providerAuthority: Attr?,
    val targetPackages: List<Attr>,
)

private const val DOUBLE_PREFIX =
    "dev.jason.dev.jason.com.google.android.inputmethod.latin"

private const val REQUIRED_SPLITS_METADATA = "com.android.vending.splits.required"
