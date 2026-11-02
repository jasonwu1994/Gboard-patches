package dev.jason.gboardpatches.patches.gboard.features.keypresshaptics

import dev.jason.gboardpatches.patches.gboard.shared.GboardFlagFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardFlagFamilyFeaturePatch

internal val gboardKeypressHapticsFlagValuePatch = gboardFlagFamilyFeaturePatch(
    description = "Enable Gboard's native primitive-click path through its existing flag reader.",
    feature = GboardFlagFamilyFeature.KEYPRESS_HAPTICS,
)
