package dev.jason.gboardpatches.patches.gboard.features.developeroptions

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/**
 * Marks Developer options as patched in. The settings page only opens this app's own Gboard
 * settings, which is always visible to itself, so no package visibility entries are added.
 */
internal val gboardDeveloperOptionsFeatureMarkerPatch = resourcePatch(
    description = "標記 Developer options feature 已被打入 target APK"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(DEVELOPER_OPTIONS_FEATURE_MARKER_NAME)
    }
}

private const val DEVELOPER_OPTIONS_FEATURE_MARKER_NAME =
    "dev.jason.gboardpatches.feature.developer_options"
