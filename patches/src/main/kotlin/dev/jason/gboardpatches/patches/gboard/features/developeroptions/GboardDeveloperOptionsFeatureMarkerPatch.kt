package dev.jason.gboardpatches.patches.gboard.features.developeroptions

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Element

internal val gboardDeveloperOptionsFeatureMarkerPatch = resourcePatch(
    description = "標記 Developer options feature 已被打入 target APK"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(DEVELOPER_OPTIONS_FEATURE_MARKER_NAME)
        ensurePackageQueries()
    }
}

context(context: ResourcePatchContext)
private fun ensurePackageQueries() = with(context) {
    document("AndroidManifest.xml").use { document ->
        val manifest = document.documentElement
        val queries = manifest.childElements("queries").firstOrNull()
            ?: document.createElement("queries").also { createdQueries ->
                val application = manifest.childElements("application").firstOrNull()
                if (application == null) {
                    manifest.appendChild(createdQueries)
                } else {
                    manifest.insertBefore(createdQueries, application)
                }
            }

        SUPPORTED_GBOARD_PACKAGES.forEach { packageName ->
            val packageQuery = queries.childElements("package").firstOrNull {
                it.androidAttribute("name") == packageName
            } ?: document.createElement("package").also { createdPackage ->
                queries.appendChild(createdPackage)
            }
            packageQuery.setAndroidAttribute("name", packageName)
        }
    }
}

private fun Element.androidAttribute(localName: String): String? {
    val namespaced = getAttributeNS(ANDROID_NS, localName)
    if (namespaced.isNotBlank()) {
        return namespaced
    }
    return getAttribute("android:$localName").takeIf { it.isNotBlank() }
}

private fun Element.setAndroidAttribute(localName: String, value: String) {
    setAttributeNS(ANDROID_NS, "android:$localName", value)
}

private const val DEVELOPER_OPTIONS_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.developer_options"
private val SUPPORTED_GBOARD_PACKAGES = listOf(
    "com.google.android.inputmethod.latin",
    "com.google.android.inputmethod.latin.jason.dev",
    "com.touchtype.swiftkey"
)
