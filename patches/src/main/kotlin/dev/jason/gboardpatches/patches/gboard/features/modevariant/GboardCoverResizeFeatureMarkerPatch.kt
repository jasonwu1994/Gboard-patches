package dev.jason.gboardpatches.patches.gboard.features.modevariant

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal const val COVER_RESIZE_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.cover_resize_profiles"

internal val gboardCoverResizeFeatureMarkerPatch = resourcePatch(
    description = "Mark independent cover Resize support in the patched Gboard APK.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(COVER_RESIZE_FEATURE_MARKER)
    }
}
