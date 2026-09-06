package dev.jason.gboardpatches.patches.gboard.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

internal fun MutableMethod.applyVoidExitLifecycleDelegate(
    call: RuntimeCallId,
    registers: String,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)?.isMethodReference(abi.reference) == true
        }) { "Malformed void-exit lifecycle delegate in $definingClass->$name" }
        return
    }

    val invoke = RuntimeCallEmitter.invoke(call, registers)
    returns.asReversed().forEach { returnIndex ->
        addInstructions(returnIndex, invoke)
    }
}
