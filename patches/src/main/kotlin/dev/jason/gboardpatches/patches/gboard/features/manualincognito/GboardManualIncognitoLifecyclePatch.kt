package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardManualIncognitoLifecyclePatch = bytecodePatch(
    description = "在 17.7.7 input session lifecycle 與 incognito predicate 加入薄 delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.onStartInput)
            .applyManualIncognitoEntryDelegate(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_ON_INPUT_STARTING,
            )
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.onStartInputView)
            .applyManualIncognitoEntryDelegate(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_ON_INPUT_VIEW_STARTING,
            )
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.onWindowHidden)
            .applyManualIncognitoVoidExitDelegate(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_ON_INPUT_WINDOW_HIDDEN,
            )
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.incognitoPredicate)
            .applyManualIncognitoBooleanReturnDelegate(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_APPLY_INCOGNITO_PREDICATE,
            )
    }
}

internal fun MutableMethod.applyManualIncognitoEntryDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(abi.reference)) {
            "Malformed manual incognito entry delegate in $definingClass->$name"
        }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(call, "p0 .. p1"))
}

internal fun MutableMethod.applyManualIncognitoVoidExitDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)
                ?.isMethodReference(abi.reference) == true
        }) { "Malformed manual incognito exit delegate in $definingClass->$name" }
        return
    }
    returns.asReversed().forEach { returnIndex ->
        addInstructions(returnIndex, RuntimeCallEmitter.invoke(call, "p0"))
    }
}

internal fun MutableMethod.applyManualIncognitoBooleanReturnDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN") }
    check(returns.isNotEmpty()) { "No boolean RETURN in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            val register = (instructions[returnIndex] as OneRegisterInstruction).registerA
            instructions.getOrNull(returnIndex - 2)
                ?.isInvoke("INVOKE_STATIC", abi.reference, register) == true &&
                instructions.getOrNull(returnIndex - 1)
                    ?.isRegisterOperation("MOVE_RESULT", register) == true
        }) { "Malformed manual incognito boolean delegate in $definingClass->$name" }
        return
    }
    returns.asReversed().forEach { returnIndex ->
        val register = (instructions[returnIndex] as OneRegisterInstruction).registerA
        addInstructions(
            returnIndex,
            """
                ${RuntimeCallEmitter.invoke(call, "v$register")}

                move-result v$register
            """.trimIndent(),
        )
    }
}
