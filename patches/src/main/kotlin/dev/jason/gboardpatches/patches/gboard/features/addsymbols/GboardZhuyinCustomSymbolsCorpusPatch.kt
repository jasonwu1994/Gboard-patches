package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardExpressionCorpusPatchState
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.indexOfFirstMoveResultAfter
import dev.jason.gboardpatches.patches.gboard.shared.invokeRegisters
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.methodCallIndices
import dev.jason.gboardpatches.patches.gboard.shared.requireGboardExpressionCorpusPatchState
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val EXPRESSION_CORPUS_MANAGER_CLASS = "Lgyo;"

internal val gboardZhuyinCustomSymbolsCorpusPatch = bytecodePatch(
    description = "在 expression corpus list 追加自訂 symbol tab。"
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        val mutableMethod = findMutableMethodOrThrow(
            classType = EXPRESSION_CORPUS_MANAGER_CLASS,
            name = "a",
            returnType = "Lwns;",
            parameterTypes = listOf("Landroid/view/inputmethod/EditorInfo;", "Z")
        )
        mutableMethod.applyZhuyinCustomSymbolsCorpusDelegate()
    }
}

internal fun MutableMethod.applyZhuyinCustomSymbolsCorpusDelegate(): MutableMethod {
    check(
        definingClass == EXPRESSION_CORPUS_MANAGER_CLASS &&
            name == "a" &&
            returnType == "Lwns;" &&
            parameterTypes == listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
    ) {
        "Refusing non-target Custom Symbols corpus method " +
            "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
    }
    val state = requireGboardExpressionCorpusPatchState()
    val instructions = implementation?.instructions
        ?: error("No instructions available in gri.a")
    val collectCallIndices = methodCallIndices(
        definingClass = "Lj$/util/stream/Stream;",
        name = "collect",
        returnType = "Ljava/lang/Object;",
        parameterTypes = listOf("Lj$/util/stream/Collector;")
    )
    check(collectCallIndices.size == 2) {
        "Expected exactly two Stream.collect calls in gri.a"
    }
    val customReferences = instructions.count {
        it.isMethodReference(CUSTOM_SYMBOLS_RUNTIME_DESCRIPTOR)
    }
    if (state == GboardExpressionCorpusPatchState.CUSTOM_ONLY ||
        state == GboardExpressionCorpusPatchState.COMPOSED
    ) {
        check(customReferences == 1) {
            "Completed Custom Symbols state is missing its exact runtime delegate"
        }
        validateCustomSymbolsCorpusDelegate()
        return this
    }
    check(customReferences == 0) {
        "Stock/Tab-only expression corpus state contains an orphan Custom Symbols delegate"
    }

    val collectCallIndex = collectCallIndices.last()
    val moveResultIndex = indexOfFirstMoveResultAfter(collectCallIndex)
    check(moveResultIndex >= 0) { "Could not resolve final collect() move-result in gri.a" }
    val resultRegister = returnRegisterAt(moveResultIndex)
    check(instructions.getOrNull(moveResultIndex + 1)?.isOpcode("CHECK_CAST") == true) {
        "Final collect() result in gri.a is not cast to vvw"
    }
    addInstructions(moveResultIndex + 2, buildCorpusAppendDelegate(resultRegister))
    validateCustomSymbolsCorpusDelegate()
    return this
}

private fun MutableMethod.validateCustomSymbolsCorpusDelegate() {
    val instructions = implementation?.instructions ?: error("No instructions available in gri.a")
    val collectCallIndices = methodCallIndices(
        definingClass = "Lj$/util/stream/Stream;",
        name = "collect",
        returnType = "Ljava/lang/Object;",
        parameterTypes = listOf("Lj$/util/stream/Collector;")
    )
    check(collectCallIndices.size == 2) {
        "Expected exactly two Stream.collect calls in gri.a"
    }
    val moveResultIndex = indexOfFirstMoveResultAfter(collectCallIndices.last())
    check(moveResultIndex >= 0) { "Could not resolve final collect() move-result in gri.a" }
    val expectedResultRegister = returnRegisterAt(moveResultIndex)
    val customIndex = instructions.indexOfFirst {
        it.isMethodReference(CUSTOM_SYMBOLS_RUNTIME_DESCRIPTOR)
    }
    check(customIndex >= 0 && instructions.count {
        it.isMethodReference(CUSTOM_SYMBOLS_RUNTIME_DESCRIPTOR)
    } == 1) {
        "Custom Symbols corpus delegate is duplicated or orphaned"
    }
    val resultRegister = instructions[customIndex]
        .invokeRegisters(CUSTOM_SYMBOLS_RUNTIME_DESCRIPTOR)
        ?.singleOrNull()
        ?: error("Custom Symbols corpus delegate has an unexpected invoke format")
    check(
        customIndex == moveResultIndex + 2 &&
            instructions.getOrNull(moveResultIndex + 1)?.isRegisterOperation(
                "CHECK_CAST", expectedResultRegister,
            ) == true &&
            resultRegister == expectedResultRegister &&
            instructions.getOrNull(customIndex + 1)?.isRegisterOperation(
                "MOVE_RESULT_OBJECT", resultRegister,
            ) == true &&
            instructions.getOrNull(customIndex + 2)?.isRegisterOperation(
                "CHECK_CAST", resultRegister,
            ) == true,
    ) {
        "Custom Symbols corpus delegate is incomplete or malformed"
    }
}

private fun buildCorpusAppendDelegate(register: Int): String = """
    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ADD_SYMBOLS_RUNTIME_APPEND_CUSTOM_CORPUS_ITEM,
        "v$register",
    )}

    move-result-object v$register

    check-cast v$register, Lwns;
""".trimIndent()

private val CUSTOM_SYMBOLS_RUNTIME_DESCRIPTOR = RuntimeAbiCatalog.abi(
    RuntimeCallId.ADD_SYMBOLS_RUNTIME_APPEND_CUSTOM_CORPUS_ITEM,
).reference
