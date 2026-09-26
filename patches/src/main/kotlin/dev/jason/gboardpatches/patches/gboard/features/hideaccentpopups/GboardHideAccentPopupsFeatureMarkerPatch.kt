package dev.jason.gboardpatches.patches.gboard.features.hideaccentpopups

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardHideAccentPopupsFeatureMarkerPatch = resourcePatch(
    description = "標記 Hide Accented Key Popups feature 已被打入 target APK，共用 settings UI 過濾",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(HIDE_ACCENT_POPUPS_FEATURE_MARKER_NAME)
    }
}

private const val HIDE_ACCENT_POPUPS_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.hide_accent_popups"
