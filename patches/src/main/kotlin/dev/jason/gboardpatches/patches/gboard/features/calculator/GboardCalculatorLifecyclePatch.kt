package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardCalculatorLifecyclePatch = bytecodePatch(
    description = "在 input session lifecycle 加入 calculator 薄 delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardCalculatorExtensionPatch)

    execute {
        OnStartInputViewFingerprint.only().method.applyVoidExitLifecycleDelegate(
            RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_VIEW_STARTED,
            "p0 .. p0",
        )

        val updateSelection = OnUpdateSelectionFingerprint.only().method
        // The delegate passes p3 and p4 with a non-range invoke, which only reaches v0 to v15.
        val registerCount = updateSelection.implementation!!.registerCount
        check(registerCount - UPDATE_SELECTION_PARAMETER_REGISTERS + 4 < 16) {
            "Unexpected register count in ${updateSelection.definingClass}->onUpdateSelection: " +
                registerCount
        }
        mutableClassDefBy(updateSelection.definingClass).addHelperMethodIfMissing(
            name = SELECTION_HELPER_NAME,
            parameterTypes = listOf("I", "I"),
            returnType = "V",
            accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
            registerCount = SELECTION_HELPER_REGISTER_COUNT,
            body = selectionHelperBody(),
        )
        updateSelection.applyCalculatorSelectionEntryDelegate()

        OnWindowHiddenFingerprint.only().method.applyVoidExitLifecycleDelegate(
            RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_WINDOW_HIDDEN,
            "",
        )

        applyCalculatorCandidateHooks()
    }
}

private fun MutableMethod.applyCalculatorSelectionEntryDelegate() {
    val helperReference = "$definingClass->$SELECTION_HELPER_NAME(II)V"
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(helperReference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(helperReference)) {
            "Malformed calculator selection delegate in $definingClass->$name"
        }
        return
    }
    addInstructions(
        0,
        "invoke-direct {p0, p3, p4}, $helperReference",
    )
}

private fun selectionHelperBody(): String = """
    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.CALCULATOR_RUNTIME_ON_SELECTION_UPDATED,
        "p0, p1, p2",
    )}

    return-void
""".trimIndent()

// this and six ints.
private const val UPDATE_SELECTION_PARAMETER_REGISTERS = 7
private const val SELECTION_HELPER_REGISTER_COUNT = 3
private const val SELECTION_HELPER_NAME = "gboardPatchesCalculatorSelection"
