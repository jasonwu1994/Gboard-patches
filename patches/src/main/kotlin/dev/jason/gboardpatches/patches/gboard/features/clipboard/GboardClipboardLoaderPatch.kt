package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private val LOADER_RUNTIME_CALL = RuntimeCallId.CLIPBOARD_RUNTIME_MAYBE_BUILD_LOADER_RESULT

internal val gboardClipboardLoaderPatch = bytecodePatch(
    description = "調整 clipboard visible list 重組與 TTL cutoff"
) {
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val mutableMethod = findMutableMethodOrThrow(
            classType = CLIPBOARD_LOADER_CALLABLE_CLASS,
            name = "call",
            returnType = "Ljava/lang/Object;",
            parameterTypes = emptyList()
        )
        mutableMethod.applyClipboardLoaderDelegate()
    }
}

internal fun MutableMethod.applyClipboardLoaderDelegate() {
    requireExactClipboardTarget(
        CLIPBOARD_LOADER_CALLABLE_CLASS,
        "call",
        "Ljava/lang/Object;",
        emptyList(),
    )
    applyVerified(
        VerifiedTransformationPlan(
            targetName = "$CLIPBOARD_LOADER_CALLABLE_CLASS->call()Ljava/lang/Object;",
            classify = MutableMethod::classifyClipboardLoader,
            mutate = { method ->
                method.addInstructions(0, LOADER_DELEGATE)
                method
            },
        ),
    )
}

private fun MutableMethod.classifyClipboardLoader(): VerifiedTransformationState {
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val delegateCount = instructions.count { it.isMethodReference(LOADER_METHOD_DESCRIPTOR) }
    val completed = instructions.size >= 4 &&
        instructions[0].isExactRangeInvoke(LOADER_METHOD_DESCRIPTOR, p0Register(), 1) &&
        instructions[1].isOpcode("MOVE_RESULT_OBJECT") &&
        (instructions[1] as? OneRegisterInstruction)?.registerA == 0 &&
        instructions[2].isOpcode("IF_EQZ") &&
        (instructions[2] as? OneRegisterInstruction)?.registerA == 0 &&
        instructions.hasExactBranchTarget(2, 4) &&
        instructions[3].isOpcode("RETURN_OBJECT") &&
        (instructions[3] as? OneRegisterInstruction)?.registerA == 0
    return when (instructions.clipboardRuntimeReferenceCount()) {
        0 -> VerifiedTransformationState.STOCK
        1 -> if (delegateCount == 1 && completed) {
            VerifiedTransformationState.PATCHED
        } else {
            VerifiedTransformationState.MALFORMED
        }
        else -> VerifiedTransformationState.MALFORMED
    }
}

private val LOADER_DELEGATE = """
    ${RuntimeCallEmitter.invoke(LOADER_RUNTIME_CALL, "p0 .. p0")}

    move-result-object v0

    if-eqz v0, :jasondev_continue

    return-object v0

    :jasondev_continue
""".trimIndent()

private val LOADER_METHOD_DESCRIPTOR = RuntimeAbiCatalog.abi(LOADER_RUNTIME_CALL).reference
