package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.*
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val GRID_ACCESS = "Ldev/jason/gboardpatches/extension/clipboard/GboardClipboardGridAccess;"
internal fun installClipboard1853GridAdapter(context: BytecodePatchContext) {
    val keyboard = context.mutableClass(CLIPBOARD_KEYBOARD_CLASS)
    val columns = keyboard.methods.single { it.name == "l" && it.parameterTypes.isEmpty() && it.returnType == "I" }
    val layout = GboardVersionBindings.clipboardLayoutBuilt.resolve(context)
    val adapterField = GboardVersionBindings.clipboardKeyboardAdapter
    val recyclerField = GboardVersionBindings.clipboardAdapterRecycler
    val managerField = GboardVersionBindings.clipboardRecyclerManager
    val spanField = GboardVersionBindings.clipboardSpanCount
    listOf(adapterField, recyclerField, managerField, spanField).forEach { it.resolve(context) }
    GboardVersionBindings.clipboardSetSpan.resolve(context)
    GboardVersionBindings.clipboardGridManagerConstructor.resolve(context)
    val access = context.mutableClass(GRID_ACCESS)
    val recyclerHelper = "jasondevClipboardRecycler"
    keyboard.addHelperMethodIfMissing(recyclerHelper, emptyList(), "Landroid/view/View;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, 2, """
            iget-object v0, p0, ${adapterField.reference}
            if-eqz v0, :grid_missing
            iget-object v0, v0, ${recyclerField.reference}
            return-object v0
            :grid_missing
            const/4 v0, 0x0
            return-object v0
        """.trimIndent())
    val managerHelper = "jasondevClipboardManager"
    context.mutableClass(managerField.ownerDescriptor).addHelperMethodIfMissing(managerHelper, emptyList(), "Ljava/lang/Object;",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, 2,
        "iget-object v0, p0, ${managerField.reference}\nreturn-object v0")
    val spanHelper = "jasondevClipboardSpan"
    context.mutableClass(spanField.ownerDescriptor).addHelperMethodIfMissing(spanHelper, emptyList(), "I",
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, 2,
        "iget v0, p0, ${spanField.reference}\nreturn v0")
    access.replaceGridStub("recycler", 3, """
        check-cast p1, $CLIPBOARD_KEYBOARD_CLASS
        invoke-virtual {p1}, $CLIPBOARD_KEYBOARD_CLASS->$recyclerHelper()Landroid/view/View;
        move-result-object v0
        return-object v0
    """.trimIndent())
    access.replaceGridStub("manager", 3, """
        check-cast p1, ${managerField.ownerDescriptor}
        invoke-virtual {p1}, ${managerField.ownerDescriptor}->$managerHelper()Ljava/lang/Object;
        move-result-object v0
        return-object v0
    """.trimIndent())
    access.replaceGridStub("count", 3, """
        check-cast p1, ${spanField.ownerDescriptor}
        invoke-virtual {p1}, ${spanField.ownerDescriptor}->$spanHelper()I
        move-result v0
        return v0
    """.trimIndent())
    access.replaceGridStub("setCount", 3, """
        check-cast p1, ${spanField.ownerDescriptor}
        invoke-virtual {p1, p2}, ${GboardVersionBindings.clipboardSetSpan.reference}
        return-void
    """.trimIndent())
    access.replaceGridStub("isManager", 3, """
        instance-of v0, p1, ${GboardVersionBindings.clipboardGridManagerConstructor.ownerDescriptor}
        return v0
    """.trimIndent())
    installClipboard1853ColumnWrapper(keyboard, columns)
    installClipboard1853LayoutWrapper(keyboard, layout)
}
private fun MutableClass.replaceGridStub(name: String, registers: Int, body: String) {
    val stub = methods.single { it.name == name }
    methods.remove(stub)
    addHelperMethodIfMissing(stub.name, stub.parameterTypes.map { it.toString() }, stub.returnType, stub.accessFlags, registers, body)
}
private fun MutableClass.copyClipboardStock(target: MutableMethod, helper: String) {
    check(methods.none { it.name == helper }) { "Clipboard stock helper collision" }
    methods.add(ImmutableMethod(target.definingClass, helper, target.parameters, target.returnType,
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, target.annotations, target.hiddenApiRestrictions,
        target.implementation).toMutable())
    methods.remove(target)
}
internal fun installClipboard1853ColumnWrapper(owner: MutableClass, target: MutableMethod) {
    target.requireExactClipboardTarget(CLIPBOARD_KEYBOARD_CLASS, "l", "I", emptyList())
    val helper = "jasondevClipboardStockColumns"
    owner.copyClipboardStock(target, helper)
    owner.addHelperMethodIfMissing(target.name, target.parameterTypes.map { it.toString() }, target.returnType, target.accessFlags, 2, """
        invoke-direct {p0}, ${target.definingClass}->$helper()I
        move-result v0
        ${RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_RUNTIME_RESOLVE_COLUMN_COUNT, "p0, v0")}
        move-result v0
        return v0
    """.trimIndent())
}
internal fun installClipboard1853LayoutWrapper(owner: MutableClass, target: MutableMethod) {
    val helper = "jasondevClipboardStockLayout"
    owner.copyClipboardStock(target, helper)
    owner.addHelperMethodIfMissing(target.name, target.parameterTypes.map { it.toString() }, target.returnType, target.accessFlags, 4, """
        invoke-direct {p0, p1, p2}, ${target.definingClass}->$helper(Landroid/view/inputmethod/EditorInfo;Ljava/lang/Object;)V
        ${RuntimeCallEmitter.invoke(RuntimeCallId.CLIPBOARD_RUNTIME_AFTER_LAYOUT, "p0")}
        return-void
    """.trimIndent())
}
