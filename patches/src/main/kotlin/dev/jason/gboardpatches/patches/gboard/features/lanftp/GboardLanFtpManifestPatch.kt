package dev.jason.gboardpatches.patches.gboard.features.lanftp

import app.morphe.patcher.patch.ResourcePatchContext
import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.ANDROID_NS
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestComponent
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestIntentFilter
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestMetaData
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.removeManifestAndroidAttribute
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document

internal val gboardLanFtpManifestPatch = resourcePatch(
    description = "注入 LAN FTP Server 所需 permissions、components 與 Tile drawable。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(gboardPatchesSettingsPatch)

    finalize {
        applyLanFtpManifest()
        copyLanFtpTileDrawable()
        copyLanFtpStatusMessages()
        copyLanFtpBackupRules()
    }
}

context(context: ResourcePatchContext)
private fun applyLanFtpManifest() = with(context) {
    document("AndroidManifest.xml").use(::applyGboardLanFtpManifest)
}

internal fun applyGboardLanFtpManifest(document: Document) {
    val manifest = document.documentElement
    val application = manifest.childElements("application").singleOrNull()
        ?: error("Expected exactly one application element in AndroidManifest.xml")
    val packageName = manifest.getAttribute("package")
    check(packageName.isNotBlank()) { "AndroidManifest.xml is missing its package name" }

    requireAvailableBackupPolicy(
        application,
        "fullBackupContent",
        LAN_FTP_FULL_BACKUP_POLICY,
    )
    requireAvailableBackupPolicy(
        application,
        "dataExtractionRules",
        LAN_FTP_DATA_EXTRACTION_POLICY,
    )

    LAN_FTP_PERMISSIONS.forEach { permissionName ->
        ensureManifestUsesPermission(document, manifest, permissionName)
    }

    application.setManifestAndroidAttribute(
        "fullBackupContent",
        LAN_FTP_FULL_BACKUP_POLICY,
    )
    application.setManifestAndroidAttribute(
        "dataExtractionRules",
        LAN_FTP_DATA_EXTRACTION_POLICY,
    )

    ensureManifestComponent(document, application, "service", LAN_FTP_SERVICE_CLASS).apply {
        setManifestAndroidAttribute("exported", "false")
        setManifestAndroidAttribute("foregroundServiceType", "connectedDevice")
        setManifestAndroidAttribute("process", LAN_FTP_PROCESS)
        removeManifestAndroidAttribute("isolatedProcess")
    }

    ensureManifestComponent(document, application, "provider", LAN_FTP_STATUS_PROVIDER_CLASS).apply {
        setManifestAndroidAttribute(
            "authorities",
            packageName + LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX,
        )
        setManifestAndroidAttribute("exported", "false")
        setManifestAndroidAttribute("process", LAN_FTP_PROCESS)
    }

    ensureManifestComponent(
        document,
        application,
        "activity",
        TILE_PREFERENCES_ACTIVITY_CLASS,
    ).apply {
        setManifestAndroidAttribute("exported", "true")
        setManifestAndroidAttribute("permission", BIND_QUICK_SETTINGS_TILE_PERMISSION)
        ensureManifestIntentFilter(document, this, ACTION_QS_TILE_PREFERENCES, CATEGORY_DEFAULT)
    }

    ensureManifestComponent(document, application, "service", LAN_FTP_TILE_SERVICE_CLASS).apply {
        setManifestAndroidAttribute("label", "FTP Server")
        setManifestAndroidAttribute("icon", "@drawable/ic_lan_ftp_tile")
        setManifestAndroidAttribute("permission", BIND_QUICK_SETTINGS_TILE_PERMISSION)
        setManifestAndroidAttribute("exported", "true")
        ensureManifestIntentFilter(document, this, ACTION_QS_TILE)
        ensureManifestMetaData(document, this, "android.service.quicksettings.ACTIVE_TILE", "true")
        ensureManifestMetaData(
            document,
            this,
            "android.service.quicksettings.TOGGLEABLE_TILE",
            "true",
        )
        ensureManifestMetaData(
            document,
            this,
            TILE_NAVIGATION_PATH_META_DATA,
            LAN_FTP_NAVIGATION_PATH,
        )
    }
}

private fun requireAvailableBackupPolicy(
    application: org.w3c.dom.Element,
    attribute: String,
    expectedValue: String,
) {
    val existingValue = application.getAttributeNS(ANDROID_NS, attribute).ifBlank {
        application.getAttribute("android:$attribute")
    }.trim()
    check(existingValue.isBlank() || existingValue == expectedValue) {
        "Cannot install LAN FTP because AndroidManifest application android:$attribute " +
            "already points to '$existingValue'; refusing to overwrite the host backup policy"
    }
}

context(context: ResourcePatchContext)
private fun copyLanFtpTileDrawable() = with(context) {
    val resourcePath = "$LAN_FTP_RESOURCE_ROOT/drawable/ic_lan_ftp_tile.xml"
    val bytes = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)?.use { stream ->
        stream.readBytes()
    } ?: error("LAN FTP tile drawable resource \"$resourcePath\" not found")
    val targetFile = this["res/drawable/ic_lan_ftp_tile.xml", false]
    targetFile.parentFile?.mkdirs()
    targetFile.outputStream().use { output -> output.write(bytes) }
}

context(context: ResourcePatchContext)
private fun copyLanFtpStatusMessages() = with(context) {
    val resourcePath = "$LAN_FTP_RESOURCE_ROOT/FtpStatus.properties"
    val bytes = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)?.use { stream ->
        stream.readBytes()
    } ?: error("LAN FTP status message resource \"$resourcePath\" not found")
    val targetFile = this["org/apache/ftpserver/message/FtpStatus.properties", false]
    targetFile.parentFile?.mkdirs()
    targetFile.outputStream().use { output -> output.write(bytes) }
}

context(context: ResourcePatchContext)
private fun copyLanFtpBackupRules() = with(context) {
    for (name in listOf(
        "gboard_patches_lan_ftp_backup_rules.xml",
        "gboard_patches_lan_ftp_data_extraction_rules.xml",
    )) {
        val resourcePath = "$LAN_FTP_RESOURCE_ROOT/xml/$name"
        val bytes = object {}.javaClass.classLoader.getResourceAsStream(resourcePath)?.use { stream ->
            stream.readBytes()
        } ?: error("LAN FTP backup resource \"$resourcePath\" not found")
        val targetFile = this["res/xml/$name", false]
        targetFile.parentFile?.mkdirs()
        targetFile.outputStream().use { output -> output.write(bytes) }
    }
}

private val LAN_FTP_PERMISSIONS = listOf(
    "android.permission.ACCESS_LOCAL_NETWORK",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE",
    "android.permission.CHANGE_NETWORK_STATE",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
    "android.permission.INTERNET",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
    "android.permission.WAKE_LOCK",
)

internal const val LAN_FTP_SERVICE_CLASS =
    "dev.jason.gboardpatches.extension.lanftp.android.LanFtpService"
internal const val LAN_FTP_STATUS_PROVIDER_CLASS =
    "dev.jason.gboardpatches.extension.lanftp.android.LanFtpStatusProvider"
internal const val LAN_FTP_TILE_SERVICE_CLASS =
    "dev.jason.gboardpatches.extension.lanftp.android.LanFtpTileService"
internal const val TILE_PREFERENCES_ACTIVITY_CLASS =
    "dev.jason.gboardpatches.extension.settings.GboardTilePreferencesActivity"
internal const val LAN_FTP_STATUS_PROVIDER_AUTHORITY_SUFFIX = ".lan_ftp_status"
internal const val LAN_FTP_PROCESS = ":lan_file_transfer"
private const val ACTION_QS_TILE = "android.service.quicksettings.action.QS_TILE"
private const val ACTION_QS_TILE_PREFERENCES =
    "android.service.quicksettings.action.QS_TILE_PREFERENCES"
private const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
private const val BIND_QUICK_SETTINGS_TILE_PERMISSION =
    "android.permission.BIND_QUICK_SETTINGS_TILE"
private const val TILE_NAVIGATION_PATH_META_DATA =
    "dev.jason.gboardpatches.tile.NAVIGATION_PATH"
private const val LAN_FTP_NAVIGATION_PATH =
    "dev.jason.gboardpatches.extension.lanftp.settings.GboardLanFtpSettingsFeature"
private const val LAN_FTP_RESOURCE_ROOT = "lan-ftp-res"
private const val LAN_FTP_FULL_BACKUP_POLICY =
    "@xml/gboard_patches_lan_ftp_backup_rules"
private const val LAN_FTP_DATA_EXTRACTION_POLICY =
    "@xml/gboard_patches_lan_ftp_data_extraction_rules"
