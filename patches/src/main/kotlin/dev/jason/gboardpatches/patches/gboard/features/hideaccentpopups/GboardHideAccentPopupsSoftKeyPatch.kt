package dev.jason.gboardpatches.patches.gboard.features.hideaccentpopups

import dev.jason.gboardpatches.patches.gboard.shared.GboardSoftKeyFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardSoftKeyFamilyFeaturePatch

internal val gboardHideAccentPopupsSoftKeyPatch = gboardSoftKeyFamilyFeaturePatch(
    description = "在 SoftKeyView bind 前移除字母鍵長按選單中的重音字母。",
    feature = GboardSoftKeyFamilyFeature.HIDE_ACCENT_POPUPS,
)
