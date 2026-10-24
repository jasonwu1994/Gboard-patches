package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isLiteralWrite
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass

private const val ZHUYIN_ENGINE_CLASS = "Lkkb;"
private const val ZHUYIN_ENGINE_INIT = "$ZHUYIN_ENGINE_CLASS->c()V"
private const val BASE_ENGINE_INIT = "Lfhm;->c()V"
private const val DICTIONARY_FLAGS = "Lhbd;->h:[Z"
private const val SETTINGS_PROVIDER = "Lkkb;->g:Lqhy;"
private const val SETTING_KEY_READ = "Lcdl;->x(IZ)Z"
private const val MODE_FLAG = "Lkkb;->c:Z"
private const val MODE_CALLBACK = "Lkkb;->t:Lqhx;"
private const val SETTINGS_CALLBACK = "Lqhy;->Z(Lqhx;I)V"
private const val ZH_TW_MIXED_INPUT_SETTING_KEY = 0x7f1409c8L

internal enum class ZhuyinDictionaryFlag(val index: Int, val label: String) {
    SHORTCUTS(3, "shortcuts dictionary"),
}

internal fun BytecodePatchContext.enableZhuyinDictionaryFlag(flag: ZhuyinDictionaryFlag) {
    val ownerClass = mutableClass(ZHUYIN_ENGINE_CLASS)
    val matches = ownerClass.methods.filter { method ->
        method.definingClass == ZHUYIN_ENGINE_CLASS &&
            method.name == "c" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    }
    check(matches.size == 1) { "Expected exactly one $ZHUYIN_ENGINE_INIT target" }

    val candidateClass = MutableClass(ownerClass)
    val candidateTarget = candidateClass.methods.single { method ->
        method.definingClass == ZHUYIN_ENGINE_CLASS &&
            method.name == "c" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    }
    candidateTarget.applyZhuyinDictionaryFlag(flag)

    ownerClass.methods.clear()
    ownerClass.methods.addAll(candidateClass.methods)
    check(ownerClass.methods.count { method ->
        method.definingClass == ZHUYIN_ENGINE_CLASS &&
            method.name == "c" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == "V"
    } == 1) {
        "Could not commit $ZHUYIN_ENGINE_INIT"
    }
}

internal fun MutableMethod.applyZhuyinDictionaryFlag(
    flag: ZhuyinDictionaryFlag,
): MutableMethod = applyVerified(
    VerifiedTransformationPlan(
        targetName = "$ZHUYIN_ENGINE_INIT (${flag.label})",
        classify = { method -> method.classifyZhuyinDictionaryFlag(flag) },
        mutate = { method ->
            val instructions = method.implementation?.instructions
                ?: error("No instructions available in $ZHUYIN_ENGINE_INIT")
            val superCall = instructions.indexOfFirst {
                it.isInvoke("INVOKE_SUPER", BASE_ENGINE_INIT, method.thisRegister())
            }
            check(superCall >= 0) { "Missing $BASE_ENGINE_INIT in $ZHUYIN_ENGINE_INIT" }
            method.addInstructions(
                superCall + 1,
                buildString {
                    appendLine("iget-object v0, p0, $DICTIONARY_FLAGS")
                    appendLine("const/4 v1, 0x${flag.index.toString(16)}")
                    appendLine("const/4 v2, 0x1")
                    append("aput-boolean v2, v0, v1")
                },
            )
            method
        },
    ),
)

private fun MutableMethod.classifyZhuyinDictionaryFlag(
    flag: ZhuyinDictionaryFlag,
): VerifiedTransformationState {
    requireExactZhuyinEngineInit()
    val instructions = implementation?.instructions
        ?: error("No instructions available in $ZHUYIN_ENGINE_INIT")
    val baseInstructions = instructions.withoutZhuyinDictionaryFlagOverrides()
    validateZhuyinEngineInitStockBody(baseInstructions)
    val overrides = instructions.zhuyinDictionaryFlagOverrides()
    return if (overrides.count { it == flag.index } == 1) {
        VerifiedTransformationState.PATCHED
    } else {
        VerifiedTransformationState.STOCK
    }
}

private fun MutableMethod.requireExactZhuyinEngineInit() {
    check(
        definingClass == ZHUYIN_ENGINE_CLASS &&
            name == "c" &&
            parameterTypes.isEmpty() &&
            returnType == "V",
    ) { "Unexpected target; expected $ZHUYIN_ENGINE_INIT" }
}

private fun MutableMethod.thisRegister(): Int {
    val registerCount = implementation?.registerCount
        ?: error("No instructions available in $ZHUYIN_ENGINE_INIT")
    check(registerCount == 4) {
        "Unexpected register count in $ZHUYIN_ENGINE_INIT: $registerCount"
    }
    return registerCount - 1
}

private fun List<Instruction>.withoutZhuyinDictionaryFlagOverrides(): List<Instruction> {
    val overrides = zhuyinDictionaryFlagOverrideRanges()
    return filterIndexed { index, _ -> overrides.none { index in it.first } }
}

private fun List<Instruction>.zhuyinDictionaryFlagOverrides(): List<Int> =
    zhuyinDictionaryFlagOverrideRanges().map { it.second }

private fun List<Instruction>.zhuyinDictionaryFlagOverrideRanges(): List<Pair<IntRange, Int>> {
    val thisRegister = 3
    val starts = indices.filter { index -> this[index].isFieldReference(DICTIONARY_FLAGS) }
    val ranges = mutableListOf<Pair<IntRange, Int>>()
    starts.forEach { start ->
        val fieldRead = getOrNull(start)
        val flagIndexInstruction = getOrNull(start + 1)
        val enabledInstruction = getOrNull(start + 2)
        val arrayWrite = getOrNull(start + 3)
        val destinationRegister = (fieldRead as? TwoRegisterInstruction)?.registerA
        val arrayRegister = (fieldRead as? TwoRegisterInstruction)?.registerB
        val flagRegister = (flagIndexInstruction as? OneRegisterInstruction)?.registerA
        val flagIndex = (flagIndexInstruction as? NarrowLiteralInstruction)?.narrowLiteral
        val enabledRegister = (enabledInstruction as? OneRegisterInstruction)?.registerA

        check(
            fieldRead?.isOpcode("IGET_OBJECT") == true &&
                destinationRegister == 0 && arrayRegister == thisRegister &&
                flagIndexInstruction?.isOpcode("CONST_4") == true &&
                flagRegister == 1 && flagIndex != null && flagIndex in 2..3 &&
                enabledInstruction?.isOpcode("CONST_4") == true &&
                enabledRegister == 2 && enabledInstruction?.isLiteralWrite(2, 1) == true &&
                arrayWrite?.isRegisterOperation("APUT_BOOLEAN", 2, 0, 1) == true,
        ) { "Malformed dictionary flag override in $ZHUYIN_ENGINE_INIT" }
        ranges += (start..start + 3) to flagIndex!!
    }

    check(ranges.map { it.second }.distinct().size == ranges.size) {
        "Duplicate dictionary flag override in $ZHUYIN_ENGINE_INIT"
    }
    return ranges
}

private fun validateZhuyinEngineInitStockBody(instructions: List<Instruction>) {
    check(instructions.size == 10) {
        "Unexpected instruction count in $ZHUYIN_ENGINE_INIT: ${instructions.size}"
    }
    val thisRegister = 3
    check(instructions[0].isInvoke("INVOKE_SUPER", BASE_ENGINE_INIT, thisRegister))
    check(
        instructions[1].isOpcode("IGET_OBJECT") &&
            instructions[1].isFieldReference(SETTINGS_PROVIDER) &&
            instructions[1].isRegisterOperation("IGET_OBJECT", 0, thisRegister),
    )
    check(instructions[2].isOpcode("CONST_4") && instructions[2].isLiteralWrite(1, 0))
    check(instructions[3].isOpcode("CONST") &&
        instructions[3].isLiteralWrite(2, ZH_TW_MIXED_INPUT_SETTING_KEY))
    check(instructions[4].isInvoke("INVOKE_VIRTUAL", SETTING_KEY_READ, 0, 2, 1))
    check(instructions[5].isRegisterOperation("MOVE_RESULT", 1))
    check(
        instructions[6].isOpcode("IPUT_BOOLEAN") &&
            instructions[6].isFieldReference(MODE_FLAG) &&
            instructions[6].isRegisterOperation("IPUT_BOOLEAN", 1, thisRegister),
    )
    check(
        instructions[7].isOpcode("IGET_OBJECT") &&
            instructions[7].isFieldReference(MODE_CALLBACK) &&
            instructions[7].isRegisterOperation("IGET_OBJECT", thisRegister, thisRegister),
    )
    check(instructions[8].isInvoke("INVOKE_VIRTUAL", SETTINGS_CALLBACK, 0, thisRegister, 2))
    check(instructions[9].isOpcode("RETURN_VOID"))
}
