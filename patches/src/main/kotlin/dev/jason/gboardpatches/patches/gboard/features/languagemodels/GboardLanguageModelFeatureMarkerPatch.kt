package dev.jason.gboardpatches.patches.gboard.features.languagemodels

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardLanguageModelFeatureMarkerPatch = resourcePatch(
    description = "Expose dictionary and prediction settings for the selected patch.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    finalize { applyFeatureMarker("dev.jason.gboardpatches.feature.language_models") }
}
