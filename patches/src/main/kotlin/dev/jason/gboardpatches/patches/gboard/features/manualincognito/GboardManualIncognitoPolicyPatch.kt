package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardManualIncognitoPolicyPatch = bytecodePatch(
    description = "在 17.7.7 clipboard 與 voice incognito gate 加入 scoped value delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.clipboardListener)
            .applyManualIncognitoAfterBooleanCall(
                owner = "Locw;",
                name = "f",
                call = RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_APPLY_CLIPBOARD_POLICY,
            )
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.voiceEligibility)
            .applyManualIncognitoBooleanParameterDelegate(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_APPLY_VOICE_POLICY,
                parameterRegister = "p2",
            )
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.dictationEligibility)
            .applyManualIncognitoAfterBooleanFieldRead(
                fieldReference = "Ledx;->v:Z",
                call = RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_APPLY_DICTATION_FLAG,
            )
    }
}

internal fun MutableMethod.applyManualIncognitoAfterBooleanCall(
    owner: String,
    name: String,
    call: RuntimeCallId,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate manual incognito clipboard delegates" }
        return
    }
    val invokeIndex = instructions.indices.singleOrNull { index ->
        val reference = (instructions[index] as? ReferenceInstruction)
            ?.reference as? MethodReference
        reference?.definingClass == owner &&
            reference.name == name &&
            reference.parameterTypes.isEmpty() &&
            reference.returnType == "Z"
    } ?: error("Could not find exact $owner->$name()Z call in $definingClass->${this.name}")
    val moveResultIndex = invokeIndex + 1
    val register = (instructions.getOrNull(moveResultIndex) as? OneRegisterInstruction)
        ?.registerA
        ?: error("Expected result register after $owner->$name()Z")
    check(instructions[moveResultIndex].isRegisterOperation("MOVE_RESULT", register)) {
        "Expected MOVE_RESULT after $owner->$name()Z in $definingClass->${this.name}"
    }
    addInstructions(
        moveResultIndex + 1,
        """
            ${RuntimeCallEmitter.invoke(call, "v$register")}

            move-result v$register
        """.trimIndent(),
    )
}

internal fun MutableMethod.applyManualIncognitoBooleanParameterDelegate(
    call: RuntimeCallId,
    parameterRegister: String,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(abi.reference)) {
            "Malformed manual incognito parameter delegate in $definingClass->$name"
        }
        return
    }
    addInstructions(
        0,
        """
            ${RuntimeCallEmitter.invoke(call, parameterRegister)}

            move-result $parameterRegister
        """.trimIndent(),
    )
}

internal fun MutableMethod.applyManualIncognitoAfterBooleanFieldRead(
    fieldReference: String,
    call: RuntimeCallId,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate manual incognito dictation delegates" }
        return
    }
    val fieldIndex = instructions.indices.singleOrNull { index ->
        val field = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
        field?.toString() == fieldReference &&
            instructions[index].isOpcode("IGET_BOOLEAN")
    } ?: error("Could not find exact $fieldReference read in $definingClass->$name")
    val register = (instructions[fieldIndex] as OneRegisterInstruction).registerA
    addInstructions(
        fieldIndex + 1,
        """
            ${RuntimeCallEmitter.invoke(call, "v$register")}

            move-result v$register
        """.trimIndent(),
    )
}
