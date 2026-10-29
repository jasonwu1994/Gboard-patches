package dev.jason.gboardpatches.patches.gboard.features.splitkeyboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

/** Wrap only the two reviewed call sites; shared helpers and native mode persistence stay stock. */
internal fun MutableMethod.applySplitKeyboardEligibilityOverrides(): MutableMethod {
    check(GboardSplitKeyboardTargets.splitEligibility.matches(this) &&
        AccessFlags.PRIVATE.isSet(accessFlags) && AccessFlags.FINAL.isSet(accessFlags) &&
        !AccessFlags.STATIC.isSet(accessFlags)) { "Unexpected Split eligibility method shape" }
    val code = implementation ?: error("Split eligibility has no code")
    val instructions = code.instructions.toList()
    val deviceAbi = RuntimeAbiCatalog.abi(RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_APPLY_DEVICE_ELIGIBILITY)
    val widthAbi = RuntimeAbiCatalog.abi(RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_APPLY_WIDTH_ELIGIBILITY)
    fun uniqueCall(reference: String): Int {
        val matches = instructions.indices.filter { instructions[it].isMethodReference(reference) }
        check(matches.size == 1) { "Expected exactly one Split call site for $reference" }
        return matches.single()
    }
    val device = uniqueCall(GboardSplitKeyboardTargets.splitDeviceEligibility.reference)
    val width = uniqueCall(GboardSplitKeyboardTargets.splitWidthEligibility.reference)
    check(device < width) { "Split device gate must precede width gate" }
    check(instructions[device].opcode == Opcode.INVOKE_STATIC &&
        (instructions[device] as FiveRegisterInstruction).registerCount == 0 &&
        instructions[width].opcode == Opcode.INVOKE_STATIC &&
        (instructions[width] as FiveRegisterInstruction).registerCount == 3) {
        "Unexpected native Split gate invocation shape"
    }
    fun resultRegister(index: Int): Int {
        val result = instructions.getOrNull(index + 1)
        check(result?.opcode == Opcode.MOVE_RESULT) { "Split gate requires an adjacent Boolean result" }
        return (result as OneRegisterInstruction).registerA
    }
    val deviceResult = resultRegister(device)
    val widthResult = resultRegister(width)
    val widthArgument = (instructions[width] as FiveRegisterInstruction).registerD
    check(widthResult != widthArgument) { "Native Split width result overwrites the available width" }
    val deviceOverrides = instructions.count { it.isMethodReference(deviceAbi.reference) }
    val widthOverrides = instructions.count { it.isMethodReference(widthAbi.reference) }
    if (deviceOverrides != 0 || widthOverrides != 0) {
        val entry = instructions.first() as? TwoRegisterInstruction
        check(deviceOverrides == 1 && widthOverrides == 1 &&
            uniqueCall(deviceAbi.reference) == device + 3 &&
            uniqueCall(widthAbi.reference) == width + 2 &&
            instructions.first().opcode == Opcode.MOVE_OBJECT_FROM16 &&
            entry != null && entry.registerA == code.registerCount - 4 &&
            entry.registerB == code.registerCount - 1) {
            "Malformed existing Split eligibility overrides"
        }
        return this
    }

    // Keep all original physical registers, including receiver uses and its final Boolean reuse.
    // The new receiver is copied back to the old parameter slot before any stock instruction runs.
    val originalRegisters = code.registerCount
    check(originalRegisters in 1..14 && deviceResult <= 15 && widthResult <= 15 && widthArgument <= 15) {
        "Split eligibility register layout requires a new reviewed adapter"
    }
    val originalDevice = originalRegisters
    val admittedDevice = originalRegisters + 1
    val widthDelegate = """
        ${RuntimeCallEmitter.invoke(RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_APPLY_WIDTH_ELIGIBILITY,
            "v$widthResult, v$originalDevice, v$admittedDevice, v$widthArgument")}
        move-result v$widthResult
    """.trimIndent()
    val deviceDelegate = """
        move/from16 v$originalDevice, v$deviceResult
        ${RuntimeCallEmitter.invoke(RuntimeCallId.SPLIT_KEYBOARD_RUNTIME_APPLY_DEVICE_ELIGIBILITY, "v$deviceResult")}
        move-result v$deviceResult
        move/from16 v$admittedDevice, v$deviceResult
    """.trimIndent()

    // Preflight is complete before changing any bytecode; descending insertions keep native indices.
    val expanded = ImmutableMethod(
        definingClass, name, parameters, returnType, accessFlags, annotations, hiddenApiRestrictions,
        ImmutableMethodImplementation(
            originalRegisters + 3, code.instructions, code.tryBlocks, code.debugItems,
        ),
    ).toMutable()
    expanded.addInstructions(width + 2, widthDelegate)
    expanded.addInstructions(device + 2, deviceDelegate)
    expanded.addInstructions(0, "move-object/from16 v${originalRegisters - 1}, p0")
    return expanded
}
