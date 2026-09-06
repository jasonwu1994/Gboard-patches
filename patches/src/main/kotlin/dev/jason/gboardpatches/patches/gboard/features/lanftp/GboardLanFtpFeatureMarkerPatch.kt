package dev.jason.gboardpatches.patches.gboard.features.lanftp

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardLanFtpFeatureMarkerPatch = resourcePatch(
    description = "標記 LAN FTP Server feature 已被打入 target APK。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(LAN_FTP_FEATURE_MARKER)
    }
}

internal const val LAN_FTP_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.lan_ftp_server"
