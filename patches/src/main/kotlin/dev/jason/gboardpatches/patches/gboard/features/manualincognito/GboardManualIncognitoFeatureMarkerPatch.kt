package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardManualIncognitoFeatureMarkerPatch = resourcePatch(
    description = "標記 Incognito Mode Toggle feature 已被打入 target APK。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(MANUAL_INCOGNITO_FEATURE_MARKER)
    }
}

internal const val MANUAL_INCOGNITO_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.manual_incognito"
