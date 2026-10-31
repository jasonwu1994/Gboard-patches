package dev.jason.gboardpatches.patches.gboard.features.splitkeyboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD


internal val gboardSplitKeyboardModePatch = bytecodePatch(
    description = "加入自動分離生命週期與目前已分離時的還原動作橋接",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val eligibility = findMutableMethodOrThrow(GboardSplitKeyboardTargets.splitEligibility)
        val rewritten = eligibility.applySplitKeyboardEligibilityOverrides()
        if (rewritten !== eligibility) {
            val owner = GboardSplitKeyboardTargets.splitEligibility.ownerClass(this)
            check(owner.methods.remove(eligibility)) { "Native Split eligibility method disappeared" }
            check(owner.methods.add(rewritten)) { "Duplicate Split eligibility method" }
        }
        findMutableMethodOrThrow(GboardSplitKeyboardTargets.modeAttach)
            .applySplitKeyboardModeLifecycleDelegate()
        findMutableMethodOrThrow(GboardSplitKeyboardTargets.modeEnvironmentReload)
            .applySplitKeyboardModeLifecycleDelegate()
        findMutableMethodOrThrow(GboardSplitKeyboardTargets.splitAction)
            .applySplitKeyboardActionInterceptor()
    }
}

internal fun MutableMethod.applySplitKeyboardModeLifecycleDelegate() {
    val abi = RuntimeAbiCatalog.abi(
        RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_AFTER_MODE_ENVIRONMENT_CHANGED,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)?.isMethodReference(abi.reference) == true
        }) { "Malformed split keyboard lifecycle delegate in $definingClass->$name" }
        return
    }
    returns.asReversed().forEach { returnIndex ->
        addInstructions(
            returnIndex,
            RuntimeCallEmitter.invoke(
                RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_AFTER_MODE_ENVIRONMENT_CHANGED,
                "p0",
            ),
        )
    }
}

internal fun MutableMethod.applySplitKeyboardActionInterceptor() {
    val abi = RuntimeAbiCatalog.abi(
        RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_INTERCEPT_SPLIT_ACTION,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions.first().isMethodReference(abi.reference)) {
            "Malformed split keyboard action interceptor in $definingClass->$name"
        }
        return
    }
    addInstructions(0, "nop")
    val continuation = implementation!!.instructions[0]
    addInstructionsWithLabels(
        0,
        """
            ${RuntimeCallEmitter.invoke(
                RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_INTERCEPT_SPLIT_ACTION,
                "p0",
            )}

            move-result v0

            if-eqz v0, :jasondev_continue_split_keyboard_action

            return-void
        """.trimIndent(),
        ExternalLabel("jasondev_continue_split_keyboard_action", continuation),
    )
}
