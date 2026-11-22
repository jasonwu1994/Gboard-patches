package dev.jason.gboardpatches.patches.gboard.features.shiftdelete

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardShiftDeleteBytecodePatch = bytecodePatch(
    description = "在 IME 輸入事件入口替換 Shift 按住時的 Delete 事件，並在鍵盤根 View 的觸控流清除殘留的 Shift。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(
            classType = INPUT_SERVICE_CLASS,
            name = INPUT_EVENT_NAME,
            returnType = INPUT_EVENT_RETURN,
            parameterTypes = listOf(INPUT_EVENT_TYPE),
        ).applyShiftDeleteInputRemap()

        findMutableMethodOrThrow(
            classType = INPUT_VIEW_CLASS,
            name = DISPATCH_TOUCH_EVENT_NAME,
            returnType = DISPATCH_TOUCH_EVENT_RETURN,
            parameterTypes = listOf(MOTION_EVENT_TYPE),
        ).applyShiftDeleteTouchObserver()
    }
}

/**
 * The entry owns both the keyboard's own handling and the fallback output, so the event is
 * replaced once, before either of them reads it.
 */
internal fun MutableMethod.applyShiftDeleteInputRemap() {
    val abi = RuntimeAbiCatalog.abi(RuntimeCallId.SHIFT_DELETE_RUNTIME_REMAP_INPUT)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions.first().isMethodReference(abi.reference)) {
            "Malformed Shift delete input remap in $definingClass->$name"
        }
        return
    }
    addInstructions(
        0,
        """
            ${RuntimeCallEmitter.invoke(RuntimeCallId.SHIFT_DELETE_RUNTIME_REMAP_INPUT, "p0 .. p1")}

            move-result-object p1

            check-cast p1, ${parameterTypes.single()}
        """.trimIndent(),
    )
}

internal fun MutableMethod.applyShiftDeleteTouchObserver() {
    val abi = RuntimeAbiCatalog.abi(RuntimeCallId.SHIFT_DELETE_RUNTIME_ON_TOUCH_EVENT)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions.first().isMethodReference(abi.reference)) {
            "Malformed Shift delete touch observer in $definingClass->$name"
        }
        return
    }
    addInstructions(
        0,
        RuntimeCallEmitter.invoke(RuntimeCallId.SHIFT_DELETE_RUNTIME_ON_TOUCH_EVENT, "p1"),
    )
}

private const val INPUT_SERVICE_CLASS = "Lphr;"
private const val INPUT_EVENT_NAME = "dG"
private const val INPUT_EVENT_TYPE = "Lohe;"
private const val INPUT_EVENT_RETURN = "V"
private const val INPUT_VIEW_CLASS =
    "Lcom/google/android/libraries/inputmethod/inputview/InputView;"
private const val DISPATCH_TOUCH_EVENT_NAME = "dispatchTouchEvent"
private const val MOTION_EVENT_TYPE = "Landroid/view/MotionEvent;"
private const val DISPATCH_TOUCH_EVENT_RETURN = "Z"
