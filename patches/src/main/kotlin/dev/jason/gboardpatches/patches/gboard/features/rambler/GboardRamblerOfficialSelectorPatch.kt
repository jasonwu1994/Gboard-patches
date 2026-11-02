package dev.jason.gboardpatches.patches.gboard.features.rambler

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private const val VOICE = "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;"
private const val SELECTOR = "Lcom/google/android/libraries/inputmethod/preferencewidgets/CustomSelectorWithWidgetPreference;"

internal val gboardRambler1803OfficialSelectorPatch = bytecodePatch(
    description = "1853官方語音選擇與例外安全的Rambler capability scope。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        val cleanupClasses = mutableListOf<ClassDef>()
        classDefForEach { cleanupClasses.add(it) }
        GboardMethodTarget("Lflb;", "i", listOf("Z"), "V").resolve(this)
            .preservePendingRamblerResetCleanup(cleanupClasses)
        val targets = listOf(
            GboardMethodTarget(VOICE, "aC", emptyList(), "I"),
            GboardMethodTarget(VOICE, "ac", emptyList(), "V"),
            GboardMethodTarget(VOICE, "f", listOf("Landroid/os/Bundle;"), "V"),
            GboardMethodTarget(VOICE, "aE", listOf("Z", "Lqvt;", SELECTOR, SELECTOR), "V"),
            GboardMethodTarget("Lnca;", "a", listOf("Landroid/content/Context;"), "Z"),
            GboardMethodTarget("Lfhv;", "hU", emptyList(), "V"),
        )
        val methods = targets.map { it.resolve(this) }
        methods.forEachIndexed { i, method ->
            val owner = mutableClass(method.definingClass)
            val helper = "morpheRamblerStock${method.name}"
            check(owner.methods.none { it.name == helper }) { "Duplicate Rambler scope wrapper" }
            val static = method.accessFlags and AccessFlags.STATIC.value != 0
            check(static == (i == 3 || i == 4))
            val renamed = ImmutableMethod(method.definingClass, helper, method.parameters, method.returnType,
                AccessFlags.PRIVATE.value or (if (static) AccessFlags.STATIC.value else 0),
                method.annotations, method.hiddenApiRestrictions, method.implementation).toMutable()
            check(owner.methods.remove(method) && owner.methods.add(renamed))
            val words = method.parameterTypes.size + if (static) 0 else 1
            owner.addHelperMethodIfMissing(method.name, method.parameterTypes.map { it.toString() }, method.returnType,
                method.accessFlags, words + 2, rambler1853WrapperBody(
                    "${method.definingClass}->$helper${targets[i].descriptor}", static, words,
                    method.returnType, i))
        }
        GboardMethodTarget("Lkaf;", "h", listOf("Ljava/lang/CharSequence;"), "V").resolve(this)
        GboardMethodTarget("Lflz;", "J", emptyList(), "V").resolve(this)
            .clearRamblerResetHeaderWithoutShowing()
    }
}

internal fun rambler1853WrapperBody(reference: String, static: Boolean, words: Int, returnType: String,
    role: Int): String {
    val enter = when (role) {
        0, 1, 2 -> RuntimeCallId.RAMBLER_RUNTIME_ENTER_VOICE_SETTINGS_SCOPE
        5 -> RuntimeCallId.RAMBLER_RUNTIME_ENTER_DEFAULT_SELECTION_SUPPRESSION
        else -> null
    }
    val exit = when (role) {
        0, 1, 2 -> RuntimeCallId.RAMBLER_RUNTIME_EXIT_VOICE_SETTINGS_SCOPE
        5 -> RuntimeCallId.RAMBLER_RUNTIME_EXIT_DEFAULT_SELECTION_SUPPRESSION
        else -> null
    }
    val invoke = "invoke-${if (static) "static" else "direct"}/range {p0 .. p${words - 1}}, $reference"
    val result = if (returnType == "V") "" else "move-result v0"
    val observe = when (role) {
        3 -> RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_UPDATE_OFFICIAL_SELECTION, "p0")
        4 -> RuntimeCallEmitter.invoke(RuntimeCallId.RAMBLER_RUNTIME_UPDATE_OFFICIAL_SELECTION, "v0")
        else -> ""
    }
    val returnInstruction = if (returnType == "V") "return-void" else "return v0"
    if (enter == null || exit == null) return "$invoke\n$result\n$observe\n$returnInstruction"
    return """
        ${RuntimeCallEmitter.invoke(enter, "")}
        :start
        $invoke
        $result
        :end
        ${RuntimeCallEmitter.invoke(exit, "")}
        $returnInstruction
        .catch Ljava/lang/Throwable; {:start .. :end} :failed
        :failed
        move-exception v1
        ${RuntimeCallEmitter.invoke(exit, "")}
        throw v1
    """.trimIndent()
}
