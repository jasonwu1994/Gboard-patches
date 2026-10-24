package dev.jason.gboardpatches.patches.gboard.features.duckemail

import dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints.GboardEditingAccessPoints1803Targets

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardDuckEmailLifecyclePatch = bytecodePatch(
    description = "Attach Duck email to the current input session lifecycle.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        GboardEditingAccessPoints1803Targets.validate(this)
        GboardEditingAccessPoints1803Targets.resetMethods.forEach { target ->
            findMutableMethodOrThrow(target).applyDuckEmailSessionReset()
        }
        findMutableMethodOrThrow(GboardEditingAccessPoints1803Targets.startView)
            .applyVoidExitLifecycleDelegate(
                RuntimeCallId.DUCK_EMAIL_START, "p0 .. p0",
            )
    }
}

internal fun MutableMethod.applyDuckEmailSessionReset() {
    val call = RuntimeCallId.DUCK_EMAIL_END
    val reference = RuntimeAbiCatalog.abi(call).reference
    val instructions = implementation?.instructions ?: error("Missing lifecycle implementation")
    val existing = instructions.count { it.isMethodReference(reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate editing session reset" }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(call, ""))
}
