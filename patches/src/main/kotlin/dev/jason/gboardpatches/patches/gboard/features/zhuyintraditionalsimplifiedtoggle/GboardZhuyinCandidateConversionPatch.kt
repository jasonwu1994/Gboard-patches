package dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val CHINESE_DECODER =
    "Lcom/google/android/apps/inputmethod/libs/chinese/ime/hmm/AbstractHmmChineseDecodeProcessor;"

internal val gboardZhuyinCandidateConversionPatch = bytecodePatch(
    description = "Update the active Zhuyin converter while preserving composition and the candidate view.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val owner = mutableClass(CHINESE_DECODER)
        check(owner.fields.any { it.name == "j" && it.type == "Ljpj;" }) {
            "18.0.3 Chinese decoder conversion binding changed"
        }
        val ready = RuntimeCallId.ZHUYIN_CANDIDATE_CONVERSION_READY
        GboardMethodTarget(CHINESE_DECODER, "k", listOf("Landroid/view/inputmethod/EditorInfo;", "Z"), "V")
            .resolve(owner).applyVoidExitLifecycleDelegate(ready, "p0 .. p0")
        GboardMethodTarget(CHINESE_DECODER, "l", emptyList(), "V")
            .resolve(owner).applyVoidExitLifecycleDelegate(
                RuntimeCallId.ZHUYIN_CANDIDATE_CONVERSION_FINISHED, "p0 .. p0",
            )
        val publish = GboardMethodTarget(CHINESE_DECODER, "P", listOf("J"), "V").resolve(owner)
        val reference = RuntimeAbiCatalog.abi(ready).reference
        val instructions = checkNotNull(publish.implementation).instructions
        val indices = instructions.indices.filter { instructions[it].isMethodReference(reference) }
        if (indices.isEmpty()) {
            publish.addInstructions(0, RuntimeCallEmitter.invoke(ready, "p0 .. p0"))
        } else {
            check(indices == listOf(0)) { "Malformed Zhuyin conversion delegate" }
        }
    }
}
