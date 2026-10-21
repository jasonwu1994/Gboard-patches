package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

internal const val ZHUYIN_PROCESSOR =
    "Lcom/google/android/apps/inputmethod/libs/zhuyin/ZhuyinHmmDecodeProcessor;"
internal const val HMM_PROCESSOR =
    "Lcom/google/android/apps/inputmethod/libs/chinese/ime/hmm/AbstractHmmChineseDecodeProcessor;"
private const val STOCK_SELECT = "gboardZhuyinStockSelect"

internal fun BytecodePatchContext.patchZhuyinShortcutCandidates() {
    mutableClass("Lfyx;").methods.single { it.name == "a" }.captureShortcutEntries()
    val listener = mutableClass("Lhch;")
    listener.methods.single { it.name == "g" && it.parameterTypes.isEmpty() }
        .hookShortcutImport(RuntimeCallId.ZHUYIN_SHORTCUTS_BEGIN_IMPORT, false)
    listener.methods.single { it.name == "d" && it.parameterTypes == listOf("[Ljava/lang/Object;") }
        .hookShortcutRow(RuntimeCallId.ZHUYIN_SHORTCUTS_RECORD_ROW)
    listener.methods.single { it.name == "c" && it.parameterTypes.isEmpty() }
        .hookShortcutImport(RuntimeCallId.ZHUYIN_SHORTCUTS_FINISH_IMPORT, true)
    mutableClass(HMM_PROCESSOR).methods.single { it.name == "B" && it.parameterTypes.isEmpty() }
        .hookShortcutCandidates()
    mutableClass(ZHUYIN_PROCESSOR).wrapShortcutSelection()
}

private fun MutableMethod.hasCall(call: RuntimeCallId) = implementation!!.instructions.any {
    it.isMethodReference(RuntimeAbiCatalog.abi(call).reference)
}

internal fun MutableMethod.captureShortcutEntries() {
    val call = RuntimeCallId.ZHUYIN_SHORTCUTS_RECORD_ENTRY
    if (hasCall(call)) return
    val body = implementation ?: error("Shortcut importer has no body")
    check(definingClass == "Lfyx;" && name == "a" && body.registerCount == 16)
    check(body.instructions[0].isFieldReference("Lfyx;->a:I") &&
        body.instructions[0].isRegisterOperation("IGET", 11, 11))
    addInstructions(1, RuntimeCallEmitter.invoke(call, "p0, p1, p2, p3"))
}

internal fun MutableMethod.hookShortcutRow(call: RuntimeCallId) {
    check(definingClass == "Lhch;" && parameterTypes == listOf("[Ljava/lang/Object;") && returnType == "V")
    check(name == "d")
    if (hasCall(call)) return
    addInstructions(0, RuntimeCallEmitter.invoke(call, "p0, p1"))
}

internal fun MutableMethod.hookShortcutImport(call: RuntimeCallId, finish: Boolean) {
    check(definingClass == "Lhch;" && parameterTypes.isEmpty() && returnType == "V")
    check(name == if (finish) "c" else "g")
    if (!finish && hasCall(call)) return
    if (finish) {
        val reference = RuntimeAbiCatalog.abi(call).reference
        val existing = implementation!!.instructions.indices.filter {
            implementation!!.instructions[it].isMethodReference(reference)
        }
        check(existing.size <= 1) { "Duplicate dictionary import completion hooks" }
        // Upgrade older patches whose branch labels could skip the completion call.
        existing.singleOrNull()?.let { removeInstruction(it) }
    }
    val instructions = implementation!!.instructions
    val insertion = if (finish) {
        instructions.indices.single { instructions[it].opcode == Opcode.RETURN_VOID }
    } else 0
    if (finish) {
        // Preserve labels on the return location so all branches run the completion hook.
        replaceInstruction(insertion, RuntimeCallEmitter.invoke(call, "p0"))
        addInstructions(insertion + 1, "return-void")
    } else {
        addInstructions(insertion, RuntimeCallEmitter.invoke(call, "p0"))
    }
}

internal fun MutableMethod.hookShortcutCandidates() {
    val call = RuntimeCallId.ZHUYIN_SHORTCUTS_CANDIDATES
    if (hasCall(call)) return
    val body = implementation ?: error("Candidate iterator has no body")
    check(definingClass == HMM_PROCESSOR && name == "B" && parameterTypes.isEmpty() &&
        returnType == "Ljava/util/Iterator;" && body.registerCount == 4)
    check(body.instructions.count { it.isMethodReference("Lhbw;->f()Ljava/util/Iterator;") } == 1)
    addInstructionsWithLabels(0, """
        ${RuntimeCallEmitter.invoke(call, "p0")}
        move-result-object v0
        if-eqz v0, :stock
        return-object v0
    """.trimIndent(), ExternalLabel("stock", body.instructions.first()))
}

internal fun MutableClass.wrapShortcutSelection() {
    val call = RuntimeCallId.ZHUYIN_SHORTCUTS_COMMIT
    check(type == ZHUYIN_PROCESSOR)
    val target = methods.single { it.name == "Z" && it.parameterTypes == listOf("Lojt;", "Z") }
    if (target.hasCall(call)) {
        check(methods.count { it.name == STOCK_SELECT } == 1)
        return
    }
    check(methods.none { it.name == STOCK_SELECT } && target.returnType == "Z")
    check(target.implementation!!.instructions.count {
        it.isMethodReference("Lcom/google/android/apps/inputmethod/libs/chinese/ime/hmm/" +
            "AbstractHmmChineseDecodeProcessor;->Z(Lojt;Z)Z")
    } == 1)
    val helper = ImmutableMethod(
        type, STOCK_SELECT, target.parameters, target.returnType,
        (target.accessFlags and AccessFlags.PROTECTED.value.inv() and AccessFlags.PUBLIC.value.inv()) or
            AccessFlags.PRIVATE.value,
        target.annotations, target.hiddenApiRestrictions, target.implementation,
    ).toMutable()
    val wrapper = ImmutableMethod(
        type, target.name, target.parameters, target.returnType, target.accessFlags,
        target.annotations, target.hiddenApiRestrictions, MutableMethodImplementation(4),
    ).toMutable().apply {
        addInstructions(0, """
            ${RuntimeCallEmitter.invoke(call, "p0, p1")}
            move-result v0
            if-eqz v0, :stock
            const/4 v0, 0x1
            return v0
            :stock
            invoke-direct {p0, p1, p2}, $type->$STOCK_SELECT(Lojt;Z)Z
            move-result v0
            return v0
        """.trimIndent())
    }
    methods.remove(target)
    methods.add(helper)
    methods.add(wrapper)
}
