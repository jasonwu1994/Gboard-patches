package dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints

import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass

/** Reviewed target shape. No editor policy belongs in this version adapter. */
internal object GboardEditingAccessPointsTargets {
    private const val OWNER = "Lphr;"
    val startView = lifecycle("onStartInputView", "Landroid/view/inputmethod/EditorInfo;", "Z")
    val resetMethods = listOf(
        startView,
        lifecycle("onStartInput", "Landroid/view/inputmethod/EditorInfo;", "Z"),
        lifecycle("onFinishInputView", "Z"),
        lifecycle("onFinishInput"),
        lifecycle("onWindowHidden"),
        lifecycle("onDestroy"),
    )
    private fun lifecycle(name: String, vararg parameters: String) =
        GboardMethodTarget(OWNER, name, parameters.toList(), "V")

    private val descriptorMethods = listOf(
        GboardMethodTarget("Lmtr;", "c", emptyList(), "Lmtm;") to true,
        GboardMethodTarget("Lmtm;", "l", listOf("Ljava/lang/String;"), "V") to false,
        GboardMethodTarget("Lmtm;", "i", listOf("I"), "V") to false,
        GboardMethodTarget("Lmtm;", "q", listOf("Ljava/lang/Runnable;"), "V") to false,
        GboardMethodTarget("Lmtm;", "a", emptyList(), "Lmtr;") to false,
        GboardMethodTarget("Lmwv;", "g", listOf("Lmtr;", "Z"), "V") to false,
        GboardMethodTarget("Lwpa;", "n", listOf("Ljava/util/Collection;"), "Lwpa;") to true,
    )

    fun validate(context: BytecodePatchContext) {
        var owner: String? = OWNER
        val seen = mutableSetOf<String>()
        while (owner != null && owner != "Landroid/inputmethodservice/InputMethodService;" &&
            seen.add(owner)) {
            owner = context.mutableClass(owner).superclass
        }
        check(owner == "Landroid/inputmethodservice/InputMethodService;") {
            "Editing buttons lifecycle owner must extend InputMethodService"
        }
        (descriptorMethods + resetMethods.map { it to false }).forEach { (target, isStatic) ->
            val method = target.resolve(context)
            check(AccessFlags.STATIC.isSet(method.accessFlags) == isStatic) {
                "Unexpected static shape: ${target.reference}"
            }
            check(method.implementation != null) { "Missing body: ${target.reference}" }
        }
        listOf("d").forEach { name ->
            val field = GboardFieldTarget("Lmtm;", name, "Ljava/lang/String;").resolve(context)
            check(!AccessFlags.STATIC.isSet(field.accessFlags)) { "Unexpected builder field: $name" }
        }
    }
}
