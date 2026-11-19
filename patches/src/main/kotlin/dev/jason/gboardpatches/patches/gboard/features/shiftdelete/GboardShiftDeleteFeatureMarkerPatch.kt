package dev.jason.gboardpatches.patches.gboard.features.shiftdelete

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardShiftDeleteFeatureMarkerPatch = resourcePatch(
    description = "標記 Shift + Backspace Forward Delete feature 已被打入 target APK，共用 settings UI 過濾。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(SHIFT_DELETE_FEATURE_MARKER_NAME)
    }
}

private const val SHIFT_DELETE_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.shift_forward_delete"
