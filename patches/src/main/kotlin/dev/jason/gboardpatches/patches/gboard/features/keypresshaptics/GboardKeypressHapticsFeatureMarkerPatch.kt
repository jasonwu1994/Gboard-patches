package dev.jason.gboardpatches.patches.gboard.features.keypresshaptics

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal const val KEYPRESS_HAPTICS_FEATURE_MARKER = "dev.jason.gboardpatches.feature.keypress_haptics"

internal val gboardKeypressHapticsFeatureMarkerPatch = resourcePatch(
    description = "Mark the independently selected keypress vibration intensity feature.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    finalize { applyFeatureMarker(KEYPRESS_HAPTICS_FEATURE_MARKER) }
}
