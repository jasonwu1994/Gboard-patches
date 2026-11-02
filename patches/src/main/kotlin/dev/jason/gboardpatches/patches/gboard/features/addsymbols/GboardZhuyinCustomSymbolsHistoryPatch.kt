package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val EMOTICON_ITEM_CLICK_CONSUMER_CLASS = "Ljhc;"
private const val EMOTICON_HISTORY_MANAGER_CLASS = "Lgqt;"
private const val EMOTICON_KEYBOARD_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/search/emoticon/EmoticonKeyboardM2;"
private const val EMOTICON_HISTORY_FIELD = "b"

internal val gboardZhuyinCustomSymbolsHistoryPatch = bytecodePatch(
    description = "移植 add-symbols 的 recent/history namespace 隔離。",
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        val clickMethod = findMutableMethodOrThrow(
            classType = EMOTICON_ITEM_CLICK_CONSUMER_CLASS,
            name = "accept",
            returnType = "V",
            parameterTypes = listOf("Ljava/lang/Object;"),
        )
        clickMethod.applyZhuyinCustomSymbolsHistoryDelegate()
    }
}

internal fun MutableMethod.applyZhuyinCustomSymbolsHistoryDelegate(): MutableMethod {
    val implementation = checkNotNull(implementation) {
        "iyd.accept() has no implementation"
    }
    val instructions = implementation.instructions
    val historyWriteIndices = instructions.indices.filter { index ->
        val methodReference = (instructions[index] as? ReferenceInstruction)
            ?.reference as? MethodReference
        val fieldReference = if (index > 0) {
            (instructions[index - 1] as? ReferenceInstruction)?.reference as? FieldReference
        } else {
            null
        }
        methodReference?.definingClass == EMOTICON_HISTORY_MANAGER_CLASS &&
            methodReference.name == "b" &&
            methodReference.parameterTypes.map(CharSequence::toString) ==
            listOf("Ljava/lang/String;") &&
            methodReference.returnType == "V" &&
            fieldReference?.definingClass == EMOTICON_KEYBOARD_CLASS &&
            fieldReference.name == EMOTICON_HISTORY_FIELD &&
            fieldReference.type == EMOTICON_HISTORY_MANAGER_CLASS
    }
    check(historyWriteIndices.size == 1) {
        "Expected one EmoticonKeyboardM2 history write, found ${historyWriteIndices.size}"
    }
    val historyWriteIndex = historyWriteIndices.single()
    val historyWrite = instructions[historyWriteIndex] as? FiveRegisterInstruction
        ?: error("EmoticonKeyboardM2 history write is not a five-register invoke")
    val historyManagerRegister = smaliRegisterName(
        historyWrite.registerC,
        implementation.registerCount,
        parameterRegisterCount = 2,
    )
    val symbolRegister = smaliRegisterName(
        historyWrite.registerD,
        implementation.registerCount,
        parameterRegisterCount = 2,
    )
    check(historyWrite.registerC != 6 && historyWrite.registerD != 6) {
        "History redirect scratch v6 aliases an input"
    }
    addInstructionsWithLabels(
        historyWriteIndex,
        HISTORY_WRITE_DELEGATE.format(historyManagerRegister, symbolRegister),
        ExternalLabel("jasondev_history_handled", instructions[historyWriteIndex + 1]),
    )
    return this
}

private val HISTORY_WRITE_DELEGATE = """
    move-object/from16 v6, p0

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ADD_SYMBOLS_RUNTIME_INTERCEPT_HISTORY_WRITE,
        "v6, %s, %s",
    )}

    move-result p1

    if-nez p1, :jasondev_history_handled
""".trimIndent()

private fun smaliRegisterName(
    register: Int,
    registerCount: Int,
    parameterRegisterCount: Int,
): String {
    val firstParameterRegister = registerCount - parameterRegisterCount
    return if (register < firstParameterRegister) {
        "v$register"
    } else {
        "p${register - firstParameterRegister}"
    }
}
