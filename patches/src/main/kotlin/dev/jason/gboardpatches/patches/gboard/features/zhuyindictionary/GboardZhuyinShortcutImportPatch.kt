package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val ENTRY = "Lcom/google/android/apps/inputmethod/libs/hmm/MutableDictionaryAccessor\$Entry;"
private const val STOCK_TOKENIZER = "Lggy;->av(Ljava/lang/String;)[Ljava/lang/String;"
private val TOKENIZER = RuntimeCallId.ZHUYIN_DICTIONARY_RUNTIME_TOKENIZE_SHORTCUT

internal fun BytecodePatchContext.patchZhuyinShortcutImport() {
    val target = mutableClass("Lfyx;").methods.single {
        it.name == "a" && it.parameterTypes == listOf(
            "Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "I",
        ) && it.returnType == ENTRY
    }
    target.applyZhuyinShortcutTokenizer()
}

internal fun MutableMethod.applyZhuyinShortcutTokenizer() {
    check(definingClass == "Lfyx;" && name == "a" && returnType == ENTRY &&
        parameterTypes == listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;", "I")) {
        "Unexpected shortcut importer target"
    }
    val body = implementation ?: error("Missing shortcut importer body")
    check(body.registerCount == 16) { "Unexpected shortcut importer register layout" }
    val instructions = body.instructions
    // p0 is recycled as the lower-cased shortcut in the stock importer.
    val shortcutRegister = body.registerCount - 5
    val runtimeReference = RuntimeAbiCatalog.abi(TOKENIZER).reference
    val calls = instructions.indices.filter {
        instructions[it].isInvoke("INVOKE_STATIC", STOCK_TOKENIZER, shortcutRegister) ||
            instructions[it].isInvoke("INVOKE_STATIC", runtimeReference, shortcutRegister)
    }
    check(calls.size == 2) { "Expected Chinese and English shortcut tokenizer branches" }
    check(instructions[calls[1]].isInvoke("INVOKE_STATIC", STOCK_TOKENIZER, shortcutRegister)) {
        "English shortcut tokenizer must remain stock"
    }
    // Only the first call belongs to mode 1 (Traditional Chinese); the second is English.
    if (instructions[calls[0]].isInvoke("INVOKE_STATIC", runtimeReference, shortcutRegister)) return
    check(calls[0] >= 2 &&
        instructions[calls[0] - 1].isRegisterOperation("MOVE_RESULT_OBJECT", shortcutRegister) &&
        instructions[calls[0] - 2].isMethodReference(
            "Ljava/lang/String;->toLowerCase(Ljava/util/Locale;)Ljava/lang/String;",
        )) {
        "Expected lower-cased Chinese shortcut before tokenizer"
    }
    replaceInstruction(calls[0], RuntimeCallEmitter.invoke(TOKENIZER, "p0"))
}
