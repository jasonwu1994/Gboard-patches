package dev.jason.gboardpatches.patches.gboard.features.webclipboard

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestComponent
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestIntentFilter
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestMetaData
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

internal val gboardWebClipboardManifestPatch = resourcePatch(
    description = "注入 Web Clipboard 所需 manifest components、permissions 與 tile drawable。"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(gboardPatchesSettingsPatch)

    finalize {
        applyWebClipboardManifest()
        copyWebClipboardTileDrawable()
    }
}

context(context: ResourcePatchContext)
private fun applyWebClipboardManifest() = with(context) {
    document("AndroidManifest.xml").use { document ->
        applyGboardWebClipboardManifest(document)
    }
}

internal fun applyGboardWebClipboardManifest(document: Document) {
    val manifest = document.documentElement
    val application = manifest.childElements("application").firstOrNull()
        ?: error("Could not find application element in AndroidManifest.xml")

    WEB_CLIPBOARD_PERMISSIONS.forEach { permissionName ->
        ensureManifestUsesPermission(document, manifest, permissionName)
    }

    ensureManifestComponent(
        document,
        application,
        "activity",
        PATCHES_SETTINGS_ACTIVITY_CLASS,
    ).setManifestAndroidAttribute("exported", "false")

    val tilePreferencesActivity = ensureManifestComponent(
        document,
        application,
        "activity",
        TILE_PREFERENCES_ACTIVITY_CLASS,
    ).apply {
        setManifestAndroidAttribute("exported", "true")
        setManifestAndroidAttribute("permission", BIND_QUICK_SETTINGS_TILE_PERMISSION)
    }
    ensureManifestIntentFilter(
        document,
        tilePreferencesActivity,
        ACTION_QS_TILE_PREFERENCES,
        CATEGORY_DEFAULT,
    )

    ensureManifestComponent(
        document,
        application,
        "service",
        CLIPBOARD_SYNC_SERVICE_CLASS,
    ).apply {
        setManifestAndroidAttribute("exported", "false")
        setManifestAndroidAttribute("foregroundServiceType", "dataSync")
    }

    val tileService = ensureManifestComponent(
        document,
        application,
        "service",
        WEB_CLIPBOARD_TILE_SERVICE_CLASS,
    ).apply { setManifestAndroidAttribute("exported", "true") }
    tileService.setManifestAndroidAttribute("label", "Web Clipboard")
    tileService.setManifestAndroidAttribute("icon", "@drawable/ic_web_clipboard_tile")
    tileService.setManifestAndroidAttribute("permission", BIND_QUICK_SETTINGS_TILE_PERMISSION)
    ensureManifestIntentFilter(document, tileService, ACTION_QS_TILE)
    ensureManifestMetaData(
        document,
        tileService,
        "android.service.quicksettings.ACTIVE_TILE",
        "true",
    )
    ensureManifestMetaData(
        document,
        tileService,
        "android.service.quicksettings.TOGGLEABLE_TILE",
        "true",
    )
    ensureManifestMetaData(
        document,
        tileService,
        TILE_NAVIGATION_PATH_META_DATA,
        WEB_CLIPBOARD_NAVIGATION_PATH,
    )

    val packageReplacedReceiver = ensureManifestComponent(
        document,
        application,
        "receiver",
        WEB_CLIPBOARD_PACKAGE_REPLACED_RECEIVER_CLASS,
    ).apply { setManifestAndroidAttribute("exported", "false") }
    ensureManifestIntentFilter(document, packageReplacedReceiver, ACTION_MY_PACKAGE_REPLACED)
}

context(context: ResourcePatchContext)
private fun copyWebClipboardTileDrawable() = with(context) {
    val resourcePath = "$WEB_CLIPBOARD_RESOURCE_ROOT/drawable/ic_web_clipboard_tile.xml"
    val bytes = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() }
        ?: error("Web Clipboard tile drawable resource \"$resourcePath\" not found")
    val targetFile = this["res/drawable/ic_web_clipboard_tile.xml", false]
    targetFile.parentFile?.mkdirs()
    targetFile.outputStream().use { it.write(bytes) }
}

private val WEB_CLIPBOARD_PERMISSIONS = listOf(
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
)

private const val PATCHES_SETTINGS_ACTIVITY_CLASS =
    "dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsActivity"
private const val TILE_PREFERENCES_ACTIVITY_CLASS =
    "dev.jason.gboardpatches.extension.settings.GboardTilePreferencesActivity"
private const val CLIPBOARD_SYNC_SERVICE_CLASS =
    "dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService"
private const val WEB_CLIPBOARD_TILE_SERVICE_CLASS =
    "dev.jason.gboardpatches.extension.webclipboard.WebClipboardTileService"
private const val WEB_CLIPBOARD_PACKAGE_REPLACED_RECEIVER_CLASS =
    "dev.jason.gboardpatches.extension.webclipboard.WebClipboardPackageReplacedReceiver"
private const val ACTION_QS_TILE = "android.service.quicksettings.action.QS_TILE"
private const val ACTION_QS_TILE_PREFERENCES =
    "android.service.quicksettings.action.QS_TILE_PREFERENCES"
private const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
private const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
private const val BIND_QUICK_SETTINGS_TILE_PERMISSION =
    "android.permission.BIND_QUICK_SETTINGS_TILE"
private const val TILE_NAVIGATION_PATH_META_DATA =
    "dev.jason.gboardpatches.tile.NAVIGATION_PATH"
private const val WEB_CLIPBOARD_NAVIGATION_PATH =
    "dev.jason.gboardpatches.extension.clipboard.GboardClipboardSettingsFeature;" +
        "dev.jason.gboardpatches.extension.clipboard.GboardWebClipboardSettingsFeature"
private const val WEB_CLIPBOARD_RESOURCE_ROOT = "web-clipboard-res"
