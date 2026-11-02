package dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints

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

internal val gboardEditingAccessPointsLifecyclePatch = bytecodePatch(
    description = "接入 Toolbar Editing Buttons input session lifecycle。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        GboardEditingAccessPointsTargets.validate(this)
        GboardEditingAccessPointsTargets.resetMethods.forEach { target ->
            findMutableMethodOrThrow(target).applyEditingSessionReset()
        }
        findMutableMethodOrThrow(GboardEditingAccessPointsTargets.startView)
            .applyVoidExitLifecycleDelegate(
                RuntimeCallId.EDITING_ACCESS_POINTS_START, "p0 .. p0",
            )
    }
}

internal fun MutableMethod.applyEditingSessionReset() {
    val call = RuntimeCallId.EDITING_ACCESS_POINTS_END
    val reference = RuntimeAbiCatalog.abi(call).reference
    val instructions = implementation?.instructions ?: error("Missing lifecycle implementation")
    val existing = instructions.count { it.isMethodReference(reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate editing session reset" }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(call, ""))
}
