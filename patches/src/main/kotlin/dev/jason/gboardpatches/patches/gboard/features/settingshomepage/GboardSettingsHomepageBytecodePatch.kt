package dev.jason.gboardpatches.patches.gboard.features.settingshomepage

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReferenceInClass
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val SETTINGS_ACTIVITY_CLASS =
    "Lcom/google/android/apps/inputmethod/latin/preference/SettingsActivity;"
private val SETTINGS_HOMEPAGE_RUNTIME_CALL =
    RuntimeCallId.SETTINGS_HOMEPAGE_RUNTIME_APPLY_SETTINGS_HOMEPAGE_POLICY
private val SETTINGS_HOMEPAGE_RUNTIME_ABI = RuntimeAbiCatalog.abi(SETTINGS_HOMEPAGE_RUNTIME_CALL)
private val SETTINGS_HOMEPAGE_RUNTIME_CLASS = SETTINGS_HOMEPAGE_RUNTIME_ABI.owner
private val SETTINGS_HOMEPAGE_RUNTIME_DESCRIPTOR = SETTINGS_HOMEPAGE_RUNTIME_ABI.reference
private const val TARGET_METHOD_NAME = "onCreate"
private val TARGET_PARAMETER_TYPES = listOf("Landroid/os/Bundle;")
private const val TARGET_RETURN_TYPE = "V"
private const val TARGET_DESCRIPTOR =
    "$SETTINGS_ACTIVITY_CLASS->onCreate(Landroid/os/Bundle;)V"
private const val STOCK_REGISTER_COUNT = 7
private const val STOCK_RECEIVER_REGISTER = 5
private val TARGET_ACCESS_FLAGS = AccessFlags.PROTECTED.value or AccessFlags.FINAL.value

internal val gboardSettingsHomepageBytecodePatch = bytecodePatch(
    description = "允許覆蓋 Gboard 設定頁新舊版樣式選擇"
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val target = selectSettingsActivityOnCreate(
            mutableClass(SETTINGS_ACTIVITY_CLASS).methods.toList(),
        )
        target.applySettingsHomepageOverride()
    }
}

internal fun selectSettingsActivityOnCreate(methods: List<MutableMethod>): MutableMethod {
    val matches = methods.filter { method ->
        method.definingClass == SETTINGS_ACTIVITY_CLASS &&
            method.name == TARGET_METHOD_NAME &&
            method.parameterTypes == TARGET_PARAMETER_TYPES &&
            method.returnType == TARGET_RETURN_TYPE &&
            method.accessFlags == TARGET_ACCESS_FLAGS
    }
    check(matches.size == 1) {
        "Expected exactly one protected final instance void $TARGET_DESCRIPTOR target"
    }
    return matches.single()
}

internal fun MutableMethod.applySettingsHomepageOverride(): MutableMethod {
    requireExactTarget()
    return applyVerified(
        VerifiedTransformationPlan(
            targetName = TARGET_DESCRIPTOR,
            classify = MutableMethod::classifySettingsHomepageOverride,
            mutate = { method ->
                method.addInstructions(
                    0,
                    RuntimeCallEmitter.invoke(SETTINGS_HOMEPAGE_RUNTIME_CALL, "p0"),
                )
                method
            },
        ),
    )
}

private fun MutableMethod.classifySettingsHomepageOverride(): VerifiedTransformationState {
    val instructions = implementation?.instructions
        ?: error("No instructions in $TARGET_DESCRIPTOR")
    val runtimeReferences = instructions.filter { instruction ->
        instruction.isMethodReferenceInClass(SETTINGS_HOMEPAGE_RUNTIME_CLASS)
    }
    return when {
        runtimeReferences.isEmpty() -> {
            validateStockBody()
            VerifiedTransformationState.STOCK
        }
        runtimeReferences.size == 1 &&
            runtimeReferences.single().isMethodReference(
                SETTINGS_HOMEPAGE_RUNTIME_DESCRIPTOR,
            ) -> {
            validateCompletedPatch()
            VerifiedTransformationState.PATCHED
        }
        else -> VerifiedTransformationState.MALFORMED
    }
}

private fun MutableMethod.requireExactTarget() {
    check(
        definingClass == SETTINGS_ACTIVITY_CLASS &&
            name == TARGET_METHOD_NAME &&
            parameterTypes == TARGET_PARAMETER_TYPES &&
            returnType == TARGET_RETURN_TYPE &&
            accessFlags == TARGET_ACCESS_FLAGS,
    ) {
        "Refusing non-target Settings Homepage method " +
            definingClass + "->" + name + "(" + parameterTypes.joinToString("") + ")" +
            returnType
    }
}

private fun MutableMethod.validateStockBody() {
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    check(implementation.registerCount == STOCK_REGISTER_COUNT) {
        "Unexpected register count in $TARGET_DESCRIPTOR: ${implementation.registerCount}"
    }
    check(implementation.tryBlocks.isEmpty()) {
        "Unexpected try blocks in exact $TARGET_DESCRIPTOR"
    }
    val instructions = implementation.instructions
    check(instructions.none { instruction ->
        instruction.isMethodReferenceInClass(SETTINGS_HOMEPAGE_RUNTIME_CLASS)
    }) {
        "$TARGET_DESCRIPTOR contains an orphan Settings Homepage delegate"
    }
    validateControlFlow(instructions)
}

private fun MutableMethod.validateCompletedPatch() {
    requireExactTarget()
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    check(implementation.registerCount == STOCK_REGISTER_COUNT)
    check(implementation.tryBlocks.isEmpty())
    val instructions = implementation.instructions
    check(instructions.firstOrNull().isExactEntryDelegate()) {
        "$TARGET_DESCRIPTOR entry delegate is missing, misplaced, or malformed"
    }
    check(instructions.count { instruction ->
        instruction.isMethodReference(SETTINGS_HOMEPAGE_RUNTIME_DESCRIPTOR)
    } == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one Settings Homepage delegate"
    }
    check(instructions.count { instruction ->
        instruction.isMethodReferenceInClass(SETTINGS_HOMEPAGE_RUNTIME_CLASS)
    } == 1) {
        "$TARGET_DESCRIPTOR contains an orphan Settings Homepage runtime reference"
    }

    val stockInstructions = instructions.drop(1)
    validateControlFlow(stockInstructions)
}

private fun validateControlFlow(instructions: List<Instruction>) {
    check(instructions.firstOrNull()?.isMethodReference(
        "$SETTINGS_ACTIVITY_CLASS->getIntent()Landroid/content/Intent;",
    ) == true) {
        "$TARGET_DESCRIPTOR must retain getIntent as its first stock instruction"
    }
    check(instructions.count { instruction ->
        instruction.isMethodReference("Lfce;->onCreate(Landroid/os/Bundle;)V")
    } == 1) {
        "$TARGET_DESCRIPTOR must retain its exact super onCreate call"
    }
    val returns = instructions.indices.filter { index ->
        instructions[index].isOpcode("RETURN_VOID")
    }
    check(returns == listOf(instructions.lastIndex)) {
        "$TARGET_DESCRIPTOR must retain its single final RETURN_VOID"
    }
}

private fun Instruction?.isExactEntryDelegate(): Boolean =
    this?.isInvoke(
        "INVOKE_STATIC",
        SETTINGS_HOMEPAGE_RUNTIME_DESCRIPTOR,
        STOCK_RECEIVER_REGISTER,
    ) == true
