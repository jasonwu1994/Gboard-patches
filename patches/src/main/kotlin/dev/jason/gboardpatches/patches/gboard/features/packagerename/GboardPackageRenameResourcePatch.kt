package dev.jason.gboardpatches.patches.gboard.features.packagerename

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
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
import dev.jason.gboardpatches.patches.shared.Constants.GBOARD_PATCHED_PACKAGE_NAME
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

internal val gboardPackageRenameResourcePatch = resourcePatch(
    description =
        "將套件名稱改成可共存安裝的自訂值；僅支援單一 APK 產物，不可搭配原套件名稱的 split APK。"
) {
    finalize {
        applyManifestPackageOverride()
    }
}

context(context: ResourcePatchContext)
private fun applyManifestPackageOverride() = with(context) {
    document("AndroidManifest.xml").use { manifestDocument ->
        document(GBOARD_SETTINGS_XML_PATHS[0]).use { settingsDocument ->
            document(GBOARD_SETTINGS_XML_PATHS[1]).use { legacySettingsDocument ->
                applyGboardPackageRename(
                    manifestDocument = manifestDocument,
                    settingsDocuments = listOf(settingsDocument, legacySettingsDocument),
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

internal fun applyGboardApplicationDisplayName(
    manifestDocument: Document,
    displayName: String,
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
    label.value = displayName
}

internal val GBOARD_PACKAGE_RENAME_MAPPINGS = listOf(
    mapping("manifest", null, "package", GBOARD_PACKAGE_NAME, GBOARD_PATCHED_PACKAGE_NAME),
    androidMapping(
        "permission",
        "name",
        "$GBOARD_PACKAGE_NAME.pixelbundle.RECEIVER",
        "$GBOARD_PATCHED_PACKAGE_NAME.pixelbundle.RECEIVER",
    ),
    androidMapping(
        "permission",
        "name",
        "$GBOARD_PACKAGE_NAME.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "$GBOARD_PATCHED_PACKAGE_NAME.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    ),
    androidMapping(
        "meta-data",
        "name",
        "com.google.android.gms.phenotype.registration.binarypb:$GBOARD_PACKAGE_NAME",
        "com.google.android.gms.phenotype.registration.binarypb:$GBOARD_PATCHED_PACKAGE_NAME",
    ),
    androidMapping(
        "meta-data",
        "name",
        "com.google.android.gms.phenotype.registration.xml:$GBOARD_PACKAGE_NAME",
        "com.google.android.gms.phenotype.registration.xml:$GBOARD_PATCHED_PACKAGE_NAME",
    ),
    androidMapping(
        "provider",
        "authorities",
        GBOARD_PACKAGE_NAME,
        GBOARD_PATCHED_PACKAGE_NAME,
    ),
    androidMapping(
        "data",
        "host",
        "deeplink.$GBOARD_PACKAGE_NAME",
        "deeplink.$GBOARD_PATCHED_PACKAGE_NAME",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.clipboard_content",
        "$GBOARD_PATCHED_PACKAGE_NAME.clipboard_content",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.inputactionprovider",
        "$GBOARD_PATCHED_PACKAGE_NAME.inputactionprovider",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.swissarmyknifefileprovider",
        "$GBOARD_PATCHED_PACKAGE_NAME.swissarmyknifefileprovider",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.fileprovider",
        "$GBOARD_PATCHED_PACKAGE_NAME.fileprovider",
    ),
    androidMapping(
        "receiver",
        "permission",
        "$GBOARD_PACKAGE_NAME.pixelbundle.RECEIVER",
        "$GBOARD_PATCHED_PACKAGE_NAME.pixelbundle.RECEIVER",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.tracing",
        "$GBOARD_PATCHED_PACKAGE_NAME.tracing",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.wdb",
        "$GBOARD_PATCHED_PACKAGE_NAME.wdb",
    ),
    androidMapping(
        "provider",
        "authorities",
        "$GBOARD_PACKAGE_NAME.mlkitinitprovider",
        "$GBOARD_PATCHED_PACKAGE_NAME.mlkitinitprovider",
    ),
)

internal fun applyGboardPackageRename(
    manifestDocument: Document,
    settingsDocuments: List<Document>,
): GboardPackageRenameResult {
    val rootPackage = manifestDocument.documentElement.getAttribute("package")
    val state = when (rootPackage) {
        GBOARD_PACKAGE_NAME -> PackageState.ORIGINAL
        GBOARD_PATCHED_PACKAGE_NAME -> PackageState.RENAMED
        else -> error(
            "Unexpected Gboard manifest package '$rootPackage'; expected " +
                "'$GBOARD_PACKAGE_NAME' or '$GBOARD_PATCHED_PACKAGE_NAME'",
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
        attribute.nodeValue.contains(DOUBLE_PREFIX)
    }
    check(doublePrefixedValue == null) {
        val (element, attribute) = checkNotNull(doublePrefixedValue)
        "Double-prefixed package value at ${element.localElementName()} " +
            "${attribute.nodeName}: ${attribute.nodeValue}"
    }

    val selectedAttributes = GBOARD_PACKAGE_RENAME_MAPPINGS.map { mapping ->
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
    )
    val lanFtpStatusProviderAuthority = validateLanFtpStatusProviderIdentity(
        manifestDocument = manifestDocument,
        state = state,
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
    selectedAttributes.zip(GBOARD_PACKAGE_RENAME_MAPPINGS).forEach { (attribute, mapping) ->
        attribute.value = mapping.renamedValue
    }
    settingsIdentity.providerAuthority?.value =
        GBOARD_PATCHED_PACKAGE_NAME + GBOARD_PATCHES_SETTINGS_PROVIDER_AUTHORITY_SUFFIX
    lanFtpStatusProviderAuthority?.value =
        GBOARD_PATCHED_PACKAGE_NAME + LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX
    settingsIdentity.targetPackages.forEach { attribute ->
        attribute.value = GBOARD_PATCHED_PACKAGE_NAME
    }
    return GboardPackageRenameResult.RENAMED
}

private fun validateLanFtpStatusProviderIdentity(
    manifestDocument: Document,
    state: PackageState,
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
        val expectedAuthority = state.packageName + LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX
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

    val expectedPackage = state.packageName
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
    val packageName: String,
    val label: String,
) {
    ORIGINAL(GBOARD_PACKAGE_NAME, "original"),
    RENAMED(GBOARD_PATCHED_PACKAGE_NAME, "renamed"),
}

private data class SettingsIdentity(
    val providerAuthority: Attr?,
    val targetPackages: List<Attr>,
)

private const val DOUBLE_PREFIX =
    "dev.jason.dev.jason.com.google.android.inputmethod.latin"

private const val REQUIRED_SPLITS_METADATA = "com.android.vending.splits.required"
