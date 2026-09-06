package dev.jason.gboardpatches.patches.gboard.shared

import org.w3c.dom.Document
import org.w3c.dom.Element

internal fun ensureManifestUsesPermission(
    document: Document,
    manifest: Element,
    permissionName: String,
) {
    val permission = manifest.childElements("uses-permission").firstOrNull {
        it.manifestAndroidAttribute("name") == permissionName
    } ?: document.createElement("uses-permission").also { createdPermission ->
        val application = manifest.childElements("application").firstOrNull()
        if (application == null) {
            manifest.appendChild(createdPermission)
        } else {
            manifest.insertBefore(createdPermission, application)
        }
    }
    permission.setManifestAndroidAttribute("name", permissionName)
}

internal fun ensureManifestComponent(
    document: Document,
    application: Element,
    tagName: String,
    className: String,
): Element {
    val component = application.childElements(tagName).firstOrNull {
        it.manifestAndroidAttribute("name") == className
    } ?: document.createElement(tagName).also(application::appendChild)
    component.setManifestAndroidAttribute("name", className)
    return component
}

internal fun ensureManifestIntentFilter(
    document: Document,
    component: Element,
    actionName: String,
    categoryName: String? = null,
) {
    val intentFilter = component.childElements("intent-filter").firstOrNull {
        it.childElements("action").any { action ->
            action.manifestAndroidAttribute("name") == actionName
        }
    } ?: document.createElement("intent-filter").also(component::appendChild)
    ensureNamedManifestChild(document, intentFilter, "action", actionName)
    categoryName?.let { category ->
        ensureNamedManifestChild(document, intentFilter, "category", category)
    }
}

internal fun ensureManifestMetaData(
    document: Document,
    component: Element,
    name: String,
    value: String,
) {
    val metaData = component.childElements("meta-data").firstOrNull {
        it.manifestAndroidAttribute("name") == name
    } ?: document.createElement("meta-data").also(component::appendChild)
    metaData.setManifestAndroidAttribute("name", name)
    metaData.setManifestAndroidAttribute("value", value)
}

internal fun Element.manifestAndroidAttribute(localName: String): String? {
    val namespaced = getAttributeNS(ANDROID_NS, localName)
    if (namespaced.isNotBlank()) return namespaced
    return getAttribute("android:$localName").takeIf { it.isNotBlank() }
}

internal fun Element.setManifestAndroidAttribute(localName: String, value: String) {
    setAttributeNS(ANDROID_NS, "android:$localName", value)
}

internal fun Element.removeManifestAndroidAttribute(localName: String) {
    removeAttributeNS(ANDROID_NS, localName)
    removeAttribute("android:$localName")
}

private fun ensureNamedManifestChild(
    document: Document,
    parent: Element,
    tagName: String,
    name: String,
) {
    val child = parent.childElements(tagName).firstOrNull {
        it.manifestAndroidAttribute("name") == name
    } ?: document.createElement(tagName).also(parent::appendChild)
    child.setManifestAndroidAttribute("name", name)
}
