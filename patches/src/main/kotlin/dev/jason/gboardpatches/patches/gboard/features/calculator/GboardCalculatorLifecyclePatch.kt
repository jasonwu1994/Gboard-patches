package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardCalculatorLifecyclePatch = bytecodePatch(
    description = "在 18.5.4 input session lifecycle 加入 calculator 薄 delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardCalculatorExtensionPatch)

    execute {
        val inputView = GboardCalculatorTargets.inputView.resolve(this)
        check(
            isCalculatorHostCompatible(inputView.type) { type ->
                runCatching { mutableClass(type).superclass }.getOrNull()
            },
        ) {
            "Calculator host ${inputView.type} must inherit from $FRAME_LAYOUT_TYPE"
        }

        findMutableMethodOrThrow(GboardCalculatorTargets.onStartInputView)
            .applyVoidExitLifecycleDelegate(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_VIEW_STARTED,
                "p0 .. p0",
            )

        val updateSelection =
            findMutableMethodOrThrow(GboardCalculatorTargets.onUpdateSelection)
        check(updateSelection.implementation?.registerCount == UPDATE_SELECTION_REGISTER_COUNT) {
            "Unexpected register count in ${GboardCalculatorTargets.onUpdateSelection.reference}: " +
                updateSelection.implementation?.registerCount
        }
        GboardCalculatorTargets.onUpdateSelection.ownerClass(this).addHelperMethodIfMissing(
            name = SELECTION_HELPER_NAME,
            parameterTypes = listOf("I", "I"),
            returnType = "V",
            accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
            registerCount = SELECTION_HELPER_REGISTER_COUNT,
            body = selectionHelperBody(),
        )
        updateSelection.applyCalculatorSelectionEntryDelegate()

        findMutableMethodOrThrow(GboardCalculatorTargets.onWindowHidden)
            .applyVoidExitLifecycleDelegate(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_WINDOW_HIDDEN,
                "",
            )
    }
}

internal fun isCalculatorHostCompatible(
    startType: String,
    superclassOf: (String) -> String?,
): Boolean {
    var current: String? = startType
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        if (current == FRAME_LAYOUT_TYPE) {
            return true
        }
        current = superclassOf(current)
    }
    return false
}

private fun MutableMethod.applyCalculatorSelectionEntryDelegate() {
    val helperReference =
        "${GboardCalculatorTargets.onUpdateSelection.ownerDescriptor}" +
            "->$SELECTION_HELPER_NAME(II)V"
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
    iget-object v0, p0, ${GboardCalculatorTargets.inputView.reference}

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.CALCULATOR_RUNTIME_ON_SELECTION_UPDATED,
        "p0, v0, p1, p2",
    )}

    return-void
""".trimIndent()

private const val UPDATE_SELECTION_REGISTER_COUNT = 8
private const val SELECTION_HELPER_REGISTER_COUNT = 4
private const val SELECTION_HELPER_NAME = "gboardPatchesCalculatorSelection"
private const val FRAME_LAYOUT_TYPE = "Landroid/widget/FrameLayout;"
