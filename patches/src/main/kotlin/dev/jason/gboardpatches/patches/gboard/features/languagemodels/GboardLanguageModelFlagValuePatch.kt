package dev.jason.gboardpatches.patches.gboard.features.languagemodels

import dev.jason.gboardpatches.patches.gboard.shared.GboardFlagFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardFlagFamilyFeaturePatch

internal val gboardLanguageModelFlagValuePatch = gboardFlagFamilyFeaturePatch(
    description = "Select typed dictionary and prediction resource flags.",
    feature = GboardFlagFamilyFeature.LANGUAGE_MODELS,
)
