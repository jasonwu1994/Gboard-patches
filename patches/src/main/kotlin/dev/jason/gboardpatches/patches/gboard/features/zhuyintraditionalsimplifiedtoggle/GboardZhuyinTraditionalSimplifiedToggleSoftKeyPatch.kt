package dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle

import dev.jason.gboardpatches.patches.gboard.shared.GboardSoftKeyFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.afterDelegate
import dev.jason.gboardpatches.patches.gboard.shared.beforeDelegate
import dev.jason.gboardpatches.patches.gboard.shared.gboardSoftKeyFamilyFeaturePatch

internal val ZHUYIN_TOGGLE_PATCH_INCOMING_METADATA_DELEGATE by lazy {
    GboardSoftKeyFamilyFeature.ZHUYIN_TOGGLE.beforeDelegate()
}
internal val ZHUYIN_TOGGLE_AFTER_BIND_DELEGATE by lazy {
    GboardSoftKeyFamilyFeature.ZHUYIN_TOGGLE.afterDelegate()
}

internal val gboardZhuyinTraditionalSimplifiedToggleSoftKeyPatch =
    gboardSoftKeyFamilyFeaturePatch(
        description = "修正注音 ㄥ 上滑繁簡切換鍵的 SoftKeyView bind-time metadata。",
        feature = GboardSoftKeyFamilyFeature.ZHUYIN_TOGGLE,
    )

internal val gboardZhuyinSimplifiedCandidateButtonSoftKeyPatch =
    gboardSoftKeyFamilyFeaturePatch(
        description = "Replace the Zhuyin candidate button according to its independent Patches setting.",
        feature = GboardSoftKeyFamilyFeature.ZHUYIN_SIMPLIFIED_CANDIDATE_BUTTON,
    )
