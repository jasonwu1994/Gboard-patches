package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.instructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val BASE_RECYCLER_ADAPTER_CLASS = "Ljt;"
private const val EMOTICON_RECYCLER_ADAPTER_CLASS = "Ljiu;"

internal val gboardZhuyinCustomSymbolsRecyclerPatch = bytecodePatch(
    description = "移植 add-symbols 的 custom recycler bind rendering。"
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        patchConstructor()
        patchViewType()
        patchCreateViewHolder()
        patchBindViewHolder()
    }
}

context(context: BytecodePatchContext)
private fun patchConstructor() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_RECYCLER_ADAPTER_CLASS,
        name = "<init>",
        returnType = "V",
        parameterTypes = listOf(
            "Landroid/content/Context;",
            "Ljjn;",
            "Ljava/util/function/Consumer;",
            "I",
            "I"
        )
    )
    val returnIndices = mutableMethod.instructionIndices("RETURN_VOID")
    check(returnIndices.isNotEmpty()) {
        "Could not resolve return-void instructions in izu.<init>()"
    }
    returnIndices.sortedDescending().forEach { returnIndex ->
        mutableMethod.addInstructions(returnIndex, CONSTRUCTOR_DELEGATE)
    }
}

context(context: BytecodePatchContext)
private fun patchBindViewHolder() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_RECYCLER_ADAPTER_CLASS,
        name = "p",
        returnType = "V",
        parameterTypes = listOf("Lkr;", "I")
    )
    mutableMethod.addInstructions(0, BIND_VIEW_HOLDER_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchViewType() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = BASE_RECYCLER_ADAPTER_CLASS,
        name = "gW",
        returnType = "I",
        parameterTypes = listOf("I")
    )
    mutableMethod.addInstructions(0, VIEW_TYPE_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchCreateViewHolder() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_RECYCLER_ADAPTER_CLASS,
        name = "d",
        returnType = "Lkr;",
        parameterTypes = listOf("Landroid/view/ViewGroup;", "I")
    )
    mutableMethod.addInstructions(0, CREATE_VIEW_HOLDER_DELEGATE)
}

private val CONSTRUCTOR_DELEGATE = """
    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_EMOTICON_RECYCLER_ADAPTER_CONSTRUCTED,
        "p0, p3",
    )}
""".trimIndent()

private val BIND_VIEW_HOLDER_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_BIND_CUSTOM_VIEW_HOLDER, "p0, p1, p2")}

    move-result v0

    if-eqz v0, :jasondev_continue

    return-void

    :jasondev_continue
""".trimIndent()

private val VIEW_TYPE_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_RESOLVE_CUSTOM_VIEW_TYPE, "p0, p1")}

    move-result p0

    const/4 p1, -0x1

    if-eq p0, p1, :jasondev_continue

    return p0

    :jasondev_continue
""".trimIndent()

private val CREATE_VIEW_HOLDER_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_CREATE_CUSTOM_VIEW_HOLDER, "p0, p1, p2")}

    move-result-object v0

    if-eqz v0, :jasondev_continue

    check-cast v0, Lkr;

    return-object v0

    :jasondev_continue
""".trimIndent()
