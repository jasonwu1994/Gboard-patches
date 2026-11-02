package dev.jason.gboardpatches.patches.gboard.features.symbolfooter

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import dev.jason.gboardpatches.patches.gboard.shared.GboardExpressionCorpusPatchState
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.requireGboardExpressionCorpusPatchState
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val EXPRESSION_CORPUS_MANAGER_CLASS = "Lgyo;"

internal val gboardSymbolFooterOrderBytecodePatch = bytecodePatch(
    description = "依使用者設定重排 expression footer corpus list。"
) {
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val methods = mutableClass(EXPRESSION_CORPUS_MANAGER_CLASS).methods.filter {
            it.name == TARGET_METHOD_NAME &&
                it.returnType == TARGET_RETURN_TYPE &&
                it.parameterTypes == TARGET_PARAMETERS
        }
        check(methods.size == 1) {
            "Expected exactly one $TARGET_DESCRIPTOR target"
        }
        val mutableMethod = methods.single()
        val patchedMethod = mutableMethod.applySymbolFooterOrderDelegate()
        if (patchedMethod !== mutableMethod) {
            val ownerMethods = mutableClass(EXPRESSION_CORPUS_MANAGER_CLASS).methods
            check(ownerMethods.remove(mutableMethod) && ownerMethods.add(patchedMethod)) {
                "Could not replace expanded $TARGET_DESCRIPTOR"
            }
        }
    }
}

internal fun MutableMethod.applySymbolFooterOrderDelegate(): MutableMethod {
    requireExactTarget()
    return applyVerified(
        VerifiedTransformationPlan(
            targetName = TARGET_DESCRIPTOR,
            classify = MutableMethod::classifySymbolFooterOrder,
            mutate = { stock ->
                stock.expandRegisters().also { expanded ->
                    expanded.addInstructions(0, ENTRY_PARAMETER_COPIES)
                    expanded.addInstructions(
                        expanded.singleReturnIndex(),
                        buildReorderDelegate(LEGACY_P0_REGISTER),
                    )
                }
            },
        ),
    )
}

private fun MutableMethod.classifySymbolFooterOrder(): VerifiedTransformationState =
    when (requireGboardExpressionCorpusPatchState()) {
        GboardExpressionCorpusPatchState.STOCK,
        GboardExpressionCorpusPatchState.CUSTOM_ONLY -> {
            val registerCount = implementation?.registerCount
                ?: error("No instructions in $TARGET_DESCRIPTOR")
            check(registerCount == STOCK_REGISTER_COUNT) {
                "Unexpected register count in $TARGET_DESCRIPTOR: $registerCount"
            }
            validateStockShape()
            VerifiedTransformationState.STOCK
        }
        GboardExpressionCorpusPatchState.TAB_ONLY,
        GboardExpressionCorpusPatchState.COMPOSED -> {
            validateCompletedPatch()
            VerifiedTransformationState.PATCHED
        }
    }

private fun MutableMethod.requireExactTarget() {
    check(
        definingClass == EXPRESSION_CORPUS_MANAGER_CLASS &&
            name == TARGET_METHOD_NAME &&
            returnType == TARGET_RETURN_TYPE &&
            parameterTypes == TARGET_PARAMETERS,
    ) {
        "Refusing non-target Symbol Footer method " +
            "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
    }
}

private fun MutableMethod.validateStockShape() {
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    val instructions = implementation.instructions
    check(singleReturnIndex() == instructions.lastIndex) {
        "$TARGET_DESCRIPTOR must end at its single RETURN_OBJECT"
    }
    check((instructions.last() as OneRegisterInstruction).registerA == LEGACY_P0_REGISTER) {
        "$TARGET_DESCRIPTOR must return legacy p0/v$LEGACY_P0_REGISTER"
    }
    check(instructions.count { it.isMethodReference(FINAL_COLLECT_DESCRIPTOR) } == 2) {
        "$TARGET_DESCRIPTOR must contain exactly two Stream.collect calls"
    }
    check(instructions.count { it.isMethodReference(RECEIVER_LAST_USE_DESCRIPTOR) } == 1) {
        "$TARGET_DESCRIPTOR must contain the exact final original-receiver use"
    }
    check(instructions.count { it.isMethodReference(FOOTER_RUNTIME_DESCRIPTOR) } == 0) {
        "$TARGET_DESCRIPTOR contains an orphan Symbol Footer delegate"
    }
}

private fun MutableMethod.validateCompletedPatch() {
    requireExactTarget()
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    check(implementation.registerCount == PATCHED_REGISTER_COUNT) {
        "Malformed expanded register state in $TARGET_DESCRIPTOR"
    }
    val instructions = implementation.instructions
    check(
        instructions.getOrNull(0).isExactMove(
            "MOVE_OBJECT_FROM16", LEGACY_P0_REGISTER, PATCHED_P0_REGISTER,
        ) &&
            instructions.getOrNull(1).isExactMove(
                "MOVE_OBJECT_FROM16", LEGACY_P1_REGISTER, PATCHED_P1_REGISTER,
            ) &&
            instructions.getOrNull(2).isExactMove(
                "MOVE_FROM16", LEGACY_P2_REGISTER, PATCHED_P2_REGISTER,
            ),
    ) {
        "$TARGET_DESCRIPTOR entry parameter copies are missing or malformed"
    }
    val returnIndex = singleReturnIndex()
    check((instructions[returnIndex] as OneRegisterInstruction).registerA == LEGACY_P0_REGISTER)
    check(
        instructions.size > 3 &&
            instructions[returnIndex - 3].isExactFooterInvoke() &&
            instructions[returnIndex - 2].isExactOneRegister(
                "MOVE_RESULT_OBJECT", LEGACY_P0_REGISTER,
            ) &&
            instructions[returnIndex - 1].isExactOneRegister(
                "CHECK_CAST", LEGACY_P0_REGISTER,
            ),
    ) {
        "$TARGET_DESCRIPTOR final Symbol Footer delegate is missing or malformed"
    }
    check(instructions.count { it.isMethodReference(FOOTER_RUNTIME_DESCRIPTOR) } == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one Symbol Footer delegate"
    }
    check(instructions.count { it.isMethodReference(FINAL_COLLECT_DESCRIPTOR) } == 2)
    check(instructions.count { it.isMethodReference(RECEIVER_LAST_USE_DESCRIPTOR) } == 1)
}

private fun MutableMethod.expandRegisters(): MutableMethod {
    val stock = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    return ImmutableMethod(
        definingClass,
        name,
        parameters,
        returnType,
        accessFlags,
        annotations,
        hiddenApiRestrictions,
        ImmutableMethodImplementation(
            PATCHED_REGISTER_COUNT,
            stock.instructions,
            stock.tryBlocks,
            stock.debugItems,
        ),
    ).toMutable()
}

private fun MutableMethod.singleReturnIndex(): Int {
    val instructions = implementation?.instructions ?: error("No instructions in $TARGET_DESCRIPTOR")
    val returns = returnInstructionIndices().filter {
        instructions[it].isOpcode("RETURN_OBJECT")
    }
    check(returns.size == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one RETURN_OBJECT"
    }
    return returns.single()
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?.isExactMove(
    opcode: String,
    destination: Int,
    source: Int,
): Boolean =
    this?.isOpcode(opcode) == true &&
        this is TwoRegisterInstruction &&
        registerA == destination &&
        registerB == source

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?.isExactOneRegister(
    opcode: String,
    register: Int,
): Boolean =
    this?.isRegisterOperation(opcode, register) == true

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?.isExactFooterInvoke(): Boolean =
    this?.isInvoke(
        "INVOKE_STATIC",
        FOOTER_RUNTIME_DESCRIPTOR,
        PATCHED_P0_REGISTER,
        LEGACY_P0_REGISTER,
    ) == true

private fun buildReorderDelegate(register: Int): String = """
    ${RuntimeCallEmitter.invoke(FOOTER_RUNTIME_CALL, "p0, v$register")}

    move-result-object v$register

    check-cast v$register, $TARGET_RETURN_TYPE
""".trimIndent()

private val ENTRY_PARAMETER_COPIES = """
    move-object/from16 v$LEGACY_P0_REGISTER, p0
    move-object/from16 v$LEGACY_P1_REGISTER, p1
    move/from16 v$LEGACY_P2_REGISTER, p2
""".trimIndent()

private const val TARGET_METHOD_NAME = "a"
private const val TARGET_RETURN_TYPE = "Lwns;"
private val TARGET_PARAMETERS = listOf("Landroid/view/inputmethod/EditorInfo;", "Z")
private const val TARGET_DESCRIPTOR =
    "Lgyo;->a(Landroid/view/inputmethod/EditorInfo;Z)Lwns;"
private val FOOTER_RUNTIME_CALL =
    RuntimeCallId.SYMBOL_FOOTER_ORDER_RUNTIME_REORDER_EXPRESSION_CORPUS_LIST
private val FOOTER_RUNTIME_DESCRIPTOR = RuntimeAbiCatalog.abi(FOOTER_RUNTIME_CALL).reference
private const val FINAL_COLLECT_DESCRIPTOR =
    "Lj$/util/stream/Stream;->collect" +
        "(Lj$/util/stream/Collector;)Ljava/lang/Object;"
private const val RECEIVER_LAST_USE_DESCRIPTOR =
    "Lgym;-><init>(Lgyo;Landroid/view/inputmethod/EditorInfo;Z)V"
private const val STOCK_REGISTER_COUNT = 14
private const val PATCHED_REGISTER_COUNT = 17
private const val LEGACY_P0_REGISTER = 11
private const val LEGACY_P1_REGISTER = 12
private const val LEGACY_P2_REGISTER = 13
private const val PATCHED_P0_REGISTER = 14
private const val PATCHED_P1_REGISTER = 15
private const val PATCHED_P2_REGISTER = 16
