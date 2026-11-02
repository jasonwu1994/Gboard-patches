package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
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

private val COLUMN_RUNTIME_CALL = RuntimeCallId.CLIPBOARD_RUNTIME_RESOLVE_COLUMN_COUNT_OVERRIDE

internal val gboardClipboardColumnCountPatch = bytecodePatch(
    description = "移植 clipboard 欄數 override。"
) {
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        installClipboard1853GridAdapter(this)
    }
}

internal fun MutableMethod.applyClipboardColumnCountDelegate() {
    requireExactClipboardTarget(CLIPBOARD_KEYBOARD_CLASS, "l", "I", emptyList())
    applyVerified(
        VerifiedTransformationPlan(
            targetName = "$CLIPBOARD_KEYBOARD_CLASS->l()I",
            classify = MutableMethod::classifyClipboardColumnCount,
            mutate = { method ->
                method.addInstructions(0, COLUMN_COUNT_DELEGATE)
                method
            },
        ),
    )
}

private fun MutableMethod.classifyClipboardColumnCount(): VerifiedTransformationState {
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val p0 = p0Register()
    val delegateCount = instructions.count { it.isMethodReference(COLUMN_METHOD_DESCRIPTOR) }
    val invoke = instructions.getOrNull(1) as? FiveRegisterInstruction
    val completed = instructions.size >= 8 &&
        instructions[0].isOpcode("MOVE_OBJECT") &&
        (instructions[0] as? TwoRegisterInstruction)?.registerA == 0 &&
        (instructions[0] as? TwoRegisterInstruction)?.registerB == p0 &&
        instructions[1].isOpcode("INVOKE_STATIC") &&
        instructions[1].isMethodReference(COLUMN_METHOD_DESCRIPTOR) &&
        invoke?.registerCount == 1 && invoke.registerC == 0 &&
        instructions[2].isOpcode("MOVE_RESULT_OBJECT") &&
        (instructions[2] as? OneRegisterInstruction)?.registerA == p0 &&
        instructions[3].isOpcode("IF_EQZ") &&
        (instructions[3] as? OneRegisterInstruction)?.registerA == p0 &&
        instructions.hasExactBranchTarget(3, 7) &&
        instructions[4].isMethodReference(INTEGER_VALUE_DESCRIPTOR) &&
        instructions[5].isOpcode("MOVE_RESULT") &&
        (instructions[5] as? OneRegisterInstruction)?.registerA == p0 &&
        instructions[6].isOpcode("RETURN") &&
        (instructions[6] as? OneRegisterInstruction)?.registerA == p0 &&
        instructions[7].isOpcode("MOVE_OBJECT") &&
        (instructions[7] as? TwoRegisterInstruction)?.registerA == p0 &&
        (instructions[7] as? TwoRegisterInstruction)?.registerB == 0
    return when (val runtimeReferenceCount = instructions.clipboardRuntimeReferenceCount()) {
        0 -> VerifiedTransformationState.STOCK
        1 -> if (delegateCount == 1 && completed) {
            VerifiedTransformationState.PATCHED
        } else {
            VerifiedTransformationState.MALFORMED
        }
        else -> VerifiedTransformationState.MALFORMED
    }
}

private val COLUMN_COUNT_DELEGATE = """
    move-object v0, p0

    ${RuntimeCallEmitter.invoke(COLUMN_RUNTIME_CALL, "v0")}

    move-result-object p0

    if-eqz p0, :jasondev_continue

    invoke-virtual {p0}, Ljava/lang/Integer;->intValue()I

    move-result p0

    return p0

    :jasondev_continue
    move-object p0, v0
""".trimIndent()

private val COLUMN_METHOD_DESCRIPTOR = RuntimeAbiCatalog.abi(COLUMN_RUNTIME_CALL).reference
private const val INTEGER_VALUE_DESCRIPTOR = "Ljava/lang/Integer;->intValue()I"
