package dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle

import dev.jason.gboardpatches.patches.gboard.shared.GboardGestureFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.gboardGestureFamilyFeaturePatch

internal val gboardZhuyinSimplifiedCandidateButtonGesturePatch =
    gboardGestureFamilyFeaturePatch(
        description = "Handle the Simplified Chinese toggle in the expanded Zhuyin candidate view.",
        feature = GboardGestureFamilyFeature.ZHUYIN_SIMPLIFIED_CANDIDATE_BUTTON,
    )
