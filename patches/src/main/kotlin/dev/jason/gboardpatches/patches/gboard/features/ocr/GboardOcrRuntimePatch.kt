package dev.jason.gboardpatches.patches.gboard.features.ocr

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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


internal val gboardOcrRuntimePatch = bytecodePatch(
    description = "覆寫 OCR backend、optional module request 與 non-Latin thin recognizer init。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        GboardOcrPatchTargets.preflight(this)
        findMutableMethodOrThrow(GboardOcrPatchTargets.configLabel)
            .applyOcrObjectReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_CONFIG_LABEL)
        findMutableMethodOrThrow(GboardOcrPatchTargets.languageHint)
            .applyOcrObjectReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_LANGUAGE_HINT)
        findMutableMethodOrThrow(GboardOcrPatchTargets.loggingLibrary)
            .applyOcrObjectReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_LOGGING_LIBRARY)
        findMutableMethodOrThrow(GboardOcrPatchTargets.optionalModuleName)
            .applyOcrObjectReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_OPTIONAL_MODULE_NAME)
        findMutableMethodOrThrow(GboardOcrPatchTargets.moduleId)
            .applyOcrObjectReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_MODULE_ID)
        findMutableMethodOrThrow(GboardOcrPatchTargets.moduleAvailability)
            .applyOcrBooleanReturnDelegate(RuntimeCallId.OCR_RUNTIME_APPLY_MODULE_AVAILABILITY)
        findMutableMethodOrThrow(GboardOcrPatchTargets.optionalModuleBuilder)
            .applyOcrObjectReturnDelegate(
                RuntimeCallId.OCR_RUNTIME_APPLY_OPTIONAL_MODULE_REQUEST,
                castType = "[Llfy;",
            )
        findMutableMethodOrThrow(GboardOcrPatchTargets.moduleManagerRequest)
            .applyOcrObjectReturnDelegate(
                RuntimeCallId.OCR_RUNTIME_APPLY_OPTIONAL_MODULE_REQUEST,
                castType = "[Llfy;",
            )
        findMutableMethodOrThrow(GboardOcrPatchTargets.thinRecognizerInit)
            .applyOcrVoidReturnDelegate(RuntimeCallId.OCR_RUNTIME_AFTER_THIN_INIT)
    }
}

internal fun MutableMethod.applyOcrObjectReturnDelegate(
    call: RuntimeCallId,
    castType: String? = null,
) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_OBJECT") }
    check(returns.isNotEmpty()) { "No RETURN_OBJECT in $definingClass->$name" }

    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            val resultRegister = (instructions[returnIndex] as OneRegisterInstruction).registerA
            val castOffset = if (castType == null) 0 else 1
            instructions.getOrNull(returnIndex - 2 - castOffset)
                ?.isInvoke("INVOKE_STATIC", abi.reference, resultRegister) == true &&
                instructions.getOrNull(returnIndex - 1 - castOffset)
                    ?.isRegisterOperation("MOVE_RESULT_OBJECT", resultRegister) == true &&
                (castType == null || instructions.getOrNull(returnIndex - 1)
                    ?.isRegisterOperation("CHECK_CAST", resultRegister) == true)
        }) { "Malformed OCR object delegate in $definingClass->$name" }
        return
    }

    returns.asReversed().forEach { returnIndex ->
        val resultRegister = (instructions[returnIndex] as OneRegisterInstruction).registerA
        val cast = castType?.let { "\n\ncheck-cast v$resultRegister, $it" }.orEmpty()
        addInstructions(
            returnIndex,
            """
                ${RuntimeCallEmitter.invoke(call, "v$resultRegister")}

                move-result-object v$resultRegister$cast
            """.trimIndent(),
        )
    }
}

internal fun MutableMethod.applyOcrBooleanReturnDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN") }
    check(returns.isNotEmpty()) { "No RETURN in $definingClass->$name" }

    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            val resultRegister = (instructions[returnIndex] as OneRegisterInstruction).registerA
            instructions.getOrNull(returnIndex - 2)
                ?.isInvoke("INVOKE_STATIC", abi.reference, resultRegister) == true &&
                instructions.getOrNull(returnIndex - 1)
                    ?.isRegisterOperation("MOVE_RESULT", resultRegister) == true
        }) { "Malformed OCR boolean delegate in $definingClass->$name" }
        return
    }

    returns.asReversed().forEach { returnIndex ->
        val resultRegister = (instructions[returnIndex] as OneRegisterInstruction).registerA
        addInstructions(
            returnIndex,
            """
                ${RuntimeCallEmitter.invoke(call, "v$resultRegister")}

                move-result v$resultRegister
            """.trimIndent(),
        )
    }
}

internal fun MutableMethod.applyOcrVoidReturnDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.isNotEmpty()) { "No RETURN_VOID in $definingClass->$name" }

    val receiverRegister = implementation!!.registerCount - 1
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == returns.size && returns.all { returnIndex ->
            instructions.getOrNull(returnIndex - 1)
                ?.isInvoke("INVOKE_STATIC", abi.reference, receiverRegister) == true
        }) { "Malformed OCR void delegate in $definingClass->$name" }
        return
    }

    returns.asReversed().forEach { returnIndex ->
        addInstructions(returnIndex, RuntimeCallEmitter.invoke(call, "p0"))
    }
}
