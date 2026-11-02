package dev.jason.gboardpatches.patches.gboard.features.cursortrackpad

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardCursorTrackpadPreferencePatch = bytecodePatch(
    description = "1853游標觸控板的字串與資源偏好讀取；保留原始參數和例外。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val targets = listOf(
            GboardMethodTarget("Lqvt;", "am", listOf("Ljava/lang/String;", "Z", "Z"), "Z"),
            GboardMethodTarget("Lcht;", "x", listOf("I", "Z"), "Z"),
        )
        val methods = targets.map { it.resolve(this) }
        methods.forEach { check(it.accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value)) }
        methods.forEachIndexed { index, original ->
            val owner = mutableClass(original.definingClass)
            val helper = "morpheCursorStock${original.name}"
            check(owner.methods.none { it.name == helper }) { "Duplicate cursor preference bridge" }
            if (index == 1 && original.implementation!!.instructions.any {
                    it.isMethodReference("Lcht;->morpheWritingToolsStockRead(IZ)Z") }) {
                val returns = original.implementation!!.instructions.indices.filter { original.implementation!!.instructions[it].isOpcode("RETURN") }
                check(returns.size == 1 && original.implementation!!.registerCount == 4)
                original.addInstructions(returns.single(), """
                    ${RuntimeCallEmitter.invoke(RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_RESOURCE_BOOLEAN_PREFERENCE, "p1, v0")}
                    move-result v0
                """)
                return@forEachIndexed
            }
            val renamed = ImmutableMethod(original.definingClass, helper, original.parameters, "Z",
                AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, original.annotations,
                original.hiddenApiRestrictions, original.implementation).toMutable()
            check(owner.methods.remove(original) && owner.methods.add(renamed))
            val registers = if (index == 0) "p0, p1, p2, p3" else "p0, p1, p2"
            val call = if (index == 0) RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_BOOLEAN_PREFERENCE
                else RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_RESOURCE_BOOLEAN_PREFERENCE
            owner.addHelperMethodIfMissing(original.name, original.parameterTypes.map { it.toString() }, "Z", original.accessFlags,
                original.parameterTypes.size + 2, """
                    invoke-direct {$registers}, ${original.definingClass}->$helper${targets[index].descriptor}
                    move-result v0
                    ${RuntimeCallEmitter.invoke(call, "p1, v0")}
                    move-result v0
                    return v0
                """)
        }
    }
}
