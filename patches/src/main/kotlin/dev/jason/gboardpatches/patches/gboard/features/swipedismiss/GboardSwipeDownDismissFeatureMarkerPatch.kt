package dev.jason.gboardpatches.patches.gboard.features.swipedismiss

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardSwipeDownDismissFeatureMarkerPatch = resourcePatch(
    description = "標記 Swipe Down to Dismiss Keyboard feature 已被打入 target APK",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(SWIPE_DOWN_DISMISS_FEATURE_MARKER)
    }
}

internal const val SWIPE_DOWN_DISMISS_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.swipe_down_dismiss_keyboard"
