package dev.jason.gboardpatches.patches.gboard.features.clipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val KEYBOARD = CLIPBOARD_KEYBOARD_CLASS
private const val ADAPTER = CLIPBOARD_ADAPTER_CLASS
private const val ADAPTER_BASE = "Ljt;"
private const val CLIP = "Lfpk;"
private const val KEYBOARD_BASE = "Lcom/google/android/libraries/inputmethod/keyboard/impl/Keyboard;"
private const val HEADER = "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;"
private const val RECYCLER = "Landroid/support/v7/widget/RecyclerView;"
private const val PANEL_KIND = "Lqcl;"

internal val gboardClipboardSelectAllPatch = bytecodePatch(
    description = "Adds the optional Select All control to clipboard edit mode."
) {
    dependsOn(gboardClipboardColumnCountPatch)

    execute {
        validateSelectAllBindings()
        GboardVersionBindings.clipboardLayoutBuilt.resolve(this).installSelectAllOpenHooks()
        wrapClipboardSelectAll(GboardVersionBindings.clipboardSelectAllClose, "Close",
            before = call(RuntimeCallId.CLIPBOARD_SELECT_ALL_BEFORE_OPEN_OR_CLOSE, "p0 .. p0"))
        wrapClipboardSelectAll(GboardVersionBindings.clipboardSelectAllState, "State",
            after = call(RuntimeCallId.CLIPBOARD_SELECT_ALL_AFTER_STATE, "p0 .. p1"))
        wrapClipboardSelectAll(GboardVersionBindings.clipboardSelectAllData, "Data",
            after = call(RuntimeCallId.CLIPBOARD_SELECT_ALL_AFTER_DATA, "p0 .. p0"))
        wrapClipboardSelectAll(GboardVersionBindings.clipboardSelectAllTouch, "Touch",
            before = """
                ${call(RuntimeCallId.CLIPBOARD_SELECT_ALL_INTERCEPT_TOUCH, "p0 .. p1")}
                move-result v0
                if-eqz v0, :stock
                return v0
                :stock
            """.trimIndent())
    }
}

/** Keeps the layout callback and select-all lifecycle in the same wrapper. */
internal fun MutableMethod.installSelectAllOpenHooks() {
    val instructions = checkNotNull(implementation).instructions
    val afterLayout = RuntimeAbiCatalog.abi(RuntimeCallId.CLIPBOARD_RUNTIME_AFTER_LAYOUT).reference
    check(instructions.size == 3 && instructions[1].isMethodReference(afterLayout) &&
        instructions[2].isOpcode("RETURN_VOID")) { "Unexpected Clipboard layout wrapper" }
    addInstructions(2, call(RuntimeCallId.CLIPBOARD_SELECT_ALL_AFTER_OPEN, "p0 .. p0"))
    addInstructions(0, call(RuntimeCallId.CLIPBOARD_SELECT_ALL_BEFORE_OPEN_OR_CLOSE, "p0 .. p0"))
}

private fun BytecodePatchContext.validateSelectAllBindings() {
    requireClipboardSuperclass(KEYBOARD, KEYBOARD_BASE)
    requireClipboardSuperclass(ADAPTER, ADAPTER_BASE)
    requireClipboardSuperclass(RECYCLER, "Landroid/view/ViewGroup;")
    requireClipboardSuperclass(HEADER, "Landroid/widget/FrameLayout;")

    requireClipboardField(KEYBOARD, "c", ADAPTER)
    requireClipboardField(KEYBOARD, "o", "Z")
    requireClipboardField(ADAPTER, "j", RECYCLER)
    requireClipboardField(ADAPTER, "w", KEYBOARD)
    requireClipboardField(ADAPTER, "n", "Ljava/util/List;")
    requireClipboardField(ADAPTER, "g", "Landroid/util/SparseArray;")
    requireClipboardField(ADAPTER, "f", "Z")
    requireClipboardField(ADAPTER, "h", "I")
    requireClipboardField(RECYCLER, "k", ADAPTER_BASE)
    requireClipboardField(PANEL_KIND, "a", PANEL_KIND, isStatic = true)
    requireClipboardField(PANEL_KIND, "b", PANEL_KIND, isStatic = true)
    requireClipboardMethod(KEYBOARD_BASE, "eN", "Landroid/view/View;", listOf(PANEL_KIND))
    requireClipboardMethod(KEYBOARD, "K", "V")
    requireClipboardMethod(ADAPTER_BASE, "gC", "V")
    requireClipboardMethod(CLIP, "g", "I")
    requireClipboardMethod(CLIP, "n", "Z")
    requireClipboardMethod(CLIP, "o", "Z")

    val reset = GboardVersionBindings.clipboardSelectAllReset.resolve(this)
        .requireInstanceImplementation().instructions
    val writes = mapOf(
        "$ADAPTER->f:Z" to "IPUT_BOOLEAN",
        "$ADAPTER->h:I" to "IPUT",
        "$ADAPTER->v:Lwpa;" to "IPUT_OBJECT",
        "$ADAPTER->g:Landroid/util/SparseArray;" to "IGET_OBJECT",
    )
    check(writes.all { (field, opcode) ->
        reset.any { it.isOpcode(opcode) && it.isFieldReference(field) }
    } && reset.any { it.isMethodReference("Landroid/util/SparseArray;->clear()V") }) {
        "Clipboard selection reset must clear edit state, pinned count, selection, and pending indices."
    }
}

private fun BytecodePatchContext.requireClipboardSuperclass(owner: String, expected: String) {
    // Follow the reviewed stock hierarchy until it reaches the expected Android or target class.
    var parent = mutableClass(owner).superclass
    val seen = mutableSetOf(owner)
    while (parent != expected && parent != null && parent != "Ljava/lang/Object;" &&
        seen.add(parent)) {
        parent = mutableClass(parent).superclass
    }
    check(parent == expected) { "Clipboard edit binding $owner no longer inherits from $expected" }
}

private fun BytecodePatchContext.requireClipboardField(
    owner: String,
    name: String,
    type: String,
    isStatic: Boolean = false,
) {
    check(mutableClass(owner).fields.any {
        it.name == name && it.type == type && AccessFlags.STATIC.isSet(it.accessFlags) == isStatic
    }) { "Missing Clipboard edit binding $owner->$name:$type" }
}

private fun BytecodePatchContext.requireClipboardMethod(
    owner: String,
    name: String,
    returnType: String,
    parameters: List<String> = emptyList(),
) {
    findMutableMethodOrThrow(owner, name, returnType, parameters).requireInstanceImplementation()
}

private fun MutableMethod.requireInstanceImplementation() = checkNotNull(implementation)
    .also { check(!AccessFlags.STATIC.isSet(accessFlags)) { "Expected instance method $this" } }

private fun BytecodePatchContext.wrapClipboardSelectAll(
    binding: GboardMethodTarget,
    label: String,
    before: String = "",
    after: String = "",
) {
    val clazz = binding.ownerClass(this)
    val target = binding.resolve(clazz)
    val stock = target.requireInstanceImplementation()
    val owner = binding.ownerDescriptor
    val parameters = binding.parameterTypes
    val returnType = binding.returnType
    val helper = "jasondevClipboardStock$label"
    check(clazz.methods.none { it.name == helper }) { "Clipboard edit method collision: $helper" }

    clazz.methods.add(ImmutableMethod(
        owner, helper, target.parameters, returnType,
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        target.annotations, target.hiddenApiRestrictions, stock,
    ).toMutable())
    clazz.methods.remove(target)
    val registers = "p0 .. p${parameters.size}"
    val returnsValue = returnType == "Z"
    val finish = if (returnsValue) "move-result v0\nreturn v0" else "return-void"
    clazz.addHelperMethodIfMissing(binding.name, parameters, returnType, target.accessFlags,
        parameters.size + 1 + if (returnsValue) 1 else 0,
        """
            $before
            invoke-direct/range {$registers}, $owner->$helper${binding.descriptor}
            $after
            $finish
        """.trimIndent())
}

private fun call(id: RuntimeCallId, registers: String) = RuntimeCallEmitter.invoke(id, registers)
