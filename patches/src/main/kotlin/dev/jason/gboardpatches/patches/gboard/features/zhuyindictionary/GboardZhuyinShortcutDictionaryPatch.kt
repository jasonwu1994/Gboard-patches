package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardZhuyinShortcutDictionaryBytecodePatch = bytecodePatch(
    description = "啟用注音輸入使用個人字典捷徑。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        enableZhuyinDictionaryFlag(ZhuyinDictionaryFlag.SHORTCUTS)
        patchZhuyinShortcutImport()
        patchZhuyinShortcutCandidates()
    }
}
