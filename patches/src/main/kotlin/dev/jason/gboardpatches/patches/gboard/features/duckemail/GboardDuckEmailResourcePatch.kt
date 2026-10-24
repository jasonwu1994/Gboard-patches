package dev.jason.gboardpatches.patches.gboard.features.duckemail

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardDuckEmailResourcePatch = resourcePatch(
    description = "Add Duck email feature marker, network permission and toolbar icon.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    finalize {
        applyFeatureMarker("dev.jason.gboardpatches.feature.duck_email")
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            val permission = "android.permission.INTERNET"
            if (manifest.childElements("uses-permission").none {
                it.getAttributeNS(ANDROID_NS, "name") == permission ||
                    it.getAttribute("android:name") == permission
            }) {
                val node = document.createElement("uses-permission")
                node.setAttributeNS(ANDROID_NS, "android:name", permission)
                manifest.insertBefore(node, manifest.firstChild)
            }
        }
        val icon = this["res/drawable/ic_duck_email.xml", false]
        icon.parentFile.mkdirs()
        icon.writeText(
            """
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="24dp" android:height="24dp" android:viewportWidth="24"
                android:viewportHeight="24">
                <path android:fillColor="@android:color/transparent"
                    android:strokeColor="#FF202124" android:strokeWidth="2"
                    android:strokeLineJoin="round" android:strokeLineCap="round"
                    android:pathData="M4,5 L20,5 Q21,5 21,6 L21,18 Q21,19 20,19 L4,19 Q3,19 3,18 L3,6 Q3,5 4,5 M3,6 L12,13 L21,6"/>
            </vector>
            """.trimIndent(),
        )
    }
}
