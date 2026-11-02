package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.indexOfFirstFieldAccess
import dev.jason.gboardpatches.patches.gboard.shared.instructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val EMOTICON_KEYBOARD_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/search/emoticon/EmoticonKeyboardM2;"
private const val EMOTICON_HEADER_CALLBACK_CLASS = "Lgnv;"
private const val HEADER_VIEW_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/expression/header/ConstraintHeaderViewImpl;"
private const val VIEW_UTIL_CLASS = "Ltmk;"

internal val gboardZhuyinCustomSymbolsEmoticonStatePatch = bytecodePatch(
    description = "移植 add-symbols 的 emoticon host / state isolation / header 主線。"
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        patchConstructor()
        patchBodyReady()
        patchCategoryNameLookup()
        patchSelectedIndex()
        patchCategoryChange()
        patchCategoryBind()
        patchHeaderCallback()
        patchHeaderStartEdgeGuard()
        patchViewUtilTransformGuard()
    }
}

context(context: BytecodePatchContext)
private fun patchConstructor() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "<init>",
        returnType = "V",
        parameterTypes = listOf(
            "Landroid/content/Context;",
            "Lpkz;",
            "Lcom/google/android/libraries/inputmethod/metadata/KeyboardDef;",
            "Lcom/google/android/libraries/inputmethod/metadata/ImeDef;",
            "Lqch;"
        )
    )
    val returnIndices = mutableMethod.instructionIndices("RETURN_VOID")
    check(returnIndices.isNotEmpty()) {
        "Could not resolve return-void instructions in EmoticonKeyboardM2.<init>()"
    }
    returnIndices.sortedDescending().forEach { returnIndex ->
        mutableMethod.addInstructions(returnIndex, CONSTRUCTOR_DELEGATE)
    }
}

context(context: BytecodePatchContext)
private fun patchBodyReady() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "eR",
        returnType = "V",
        parameterTypes = listOf(
            "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyboardView;",
            "Lcom/google/android/libraries/inputmethod/metadata/KeyboardViewDef;"
        )
    )
    val bodyReadyFieldWriteIndex = mutableMethod.indexOfFirstFieldAccess(
        definingClass = EMOTICON_KEYBOARD_CLASS,
        name = "q",
        type = "Landroid/view/ViewGroup;",
        opcodeName = "IPUT_OBJECT",
    )
    check(bodyReadyFieldWriteIndex >= 0) {
        "Could not resolve EmoticonKeyboardM2.q assignment in eL()"
    }
    val implementation = checkNotNull(mutableMethod.implementation) {
        "EmoticonKeyboardM2.eL() has no implementation"
    }
    val fieldWrite = implementation.instructions[bodyReadyFieldWriteIndex]
        as? TwoRegisterInstruction
        ?: error("EmoticonKeyboardM2.q assignment does not expose object register")
    val receiverRegister = implementation.registerCount - 3
    check(fieldWrite.registerB == receiverRegister) {
        "EmoticonKeyboardM2.q assignment receiver is not p0"
    }
    mutableMethod.addInstructions(bodyReadyFieldWriteIndex + 1, BODY_READY_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchCategoryNameLookup() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "l",
        returnType = "Ljava/lang/String;",
        parameterTypes = listOf("I")
    )
    mutableMethod.addInstructions(0, CATEGORY_NAME_LOOKUP_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchSelectedIndex() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "k",
        returnType = "I",
        parameterTypes = listOf("Lwns;")
    )
    mutableMethod.addInstructions(0, SELECTED_INDEX_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchCategoryChange() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "E",
        returnType = "V",
        parameterTypes = listOf("I", "I")
    )
    mutableMethod.addInstructions(0, CATEGORY_CHANGE_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchCategoryBind() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_KEYBOARD_CLASS,
        name = "D",
        returnType = "V",
        parameterTypes = listOf(
            "Lcom/google/android/apps/inputmethod/libs/search/emoticon/EmoticonRecyclerView;",
            "Ljava/lang/String;"
        )
    )
    mutableMethod.addInstructions(0, CATEGORY_BIND_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchHeaderCallback() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = EMOTICON_HEADER_CALLBACK_CLASS,
        name = "accept",
        returnType = "V",
        parameterTypes = listOf("Ljava/lang/Object;")
    )
    val headerCallbackIndex = mutableMethod.indexOfMethodCallOrThrow(
        definingClass = EMOTICON_KEYBOARD_CLASS,
        name = "E",
        returnType = "V",
        parameterTypes = listOf("I", "I"),
    )
    val implementation = checkNotNull(mutableMethod.implementation) {
        "fpu.accept() has no implementation"
    }
    val headerCallback = implementation.instructions[headerCallbackIndex]
        as? FiveRegisterInstruction
        ?: error("EmoticonKeyboardM2.F() call does not expose receiver register")
    val headerReceiverRegister = smaliRegisterName(
        headerCallback.registerC,
        implementation.registerCount,
        parameterRegisterCount = 2,
    )
    mutableMethod.addInstructions(
        headerCallbackIndex + 1,
        HEADER_CALLBACK_DELEGATE.format(headerReceiverRegister),
    )
}

context(context: BytecodePatchContext)
private fun patchHeaderStartEdgeGuard() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = HEADER_VIEW_CLASS,
        name = "n",
        returnType = "V",
        parameterTypes = listOf("Lgph;")
    )
    mutableMethod.addInstructions(0, HEADER_START_EDGE_GUARD_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchViewUtilTransformGuard() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = VIEW_UTIL_CLASS,
        name = "j",
        returnType = "Landroid/view/View;",
        parameterTypes = listOf(
            "Landroid/graphics/Matrix;",
            "Landroid/view/View;",
            "Landroid/view/View;"
        )
    )
    mutableMethod.addInstructions(0, VIEW_UTIL_TRANSFORM_GUARD_DELEGATE)
}

private val CONSTRUCTOR_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_EMOTICON_KEYBOARD_CONSTRUCTED, "p0")}
""".trimIndent()

private val BODY_READY_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_EMOTICON_BODY_READY, "p0")}
""".trimIndent()

private val CATEGORY_NAME_LOOKUP_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_OVERRIDE_CATEGORY_NAME_FROM_INDEX, "p0, p1")}

    move-result-object v0

    if-eqz v0, :jasondev_continue

    return-object v0

    :jasondev_continue
""".trimIndent()

private val SELECTED_INDEX_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_RESOLVE_SELECTED_CATEGORY_INDEX, "p0, p1")}

    move-result v0

    const/4 v1, -0x1

    if-eq v0, v1, :jasondev_continue

    return v0

    :jasondev_continue
""".trimIndent()

private val CATEGORY_CHANGE_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_HANDLE_CATEGORY_CHANGE, "p0, p1, p2")}

    move-result v0

    if-eqz v0, :jasondev_continue

    return-void

    :jasondev_continue
""".trimIndent()

private val CATEGORY_BIND_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_HANDLE_CATEGORY_BIND, "p0, p1, p2")}

    move-result v0

    if-eqz v0, :jasondev_continue

    return-void

    :jasondev_continue
""".trimIndent()

private val HEADER_CALLBACK_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_HEADER_CALLBACK_AFTER, "%s")}
""".trimIndent()

private val HEADER_START_EDGE_GUARD_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_SHOULD_SKIP_HEADER_START_EDGE_UPDATE, "p0")}

    move-result v0

    if-eqz v0, :jasondev_continue

    return-void

    :jasondev_continue
""".trimIndent()

private val VIEW_UTIL_TRANSFORM_GUARD_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_SHOULD_SKIP_DETACHED_VIEW_TRANSFORM, "p0, p1, p2")}

    move-result v0

    if-eqz v0, :jasondev_continue

    return-object p1

    :jasondev_continue
""".trimIndent()

private fun smaliRegisterName(
    register: Int,
    registerCount: Int,
    parameterRegisterCount: Int,
): String {
    val firstParameterRegister = registerCount - parameterRegisterCount
    return if (register < firstParameterRegister) {
        "v$register"
    } else {
        "p${register - firstParameterRegister}"
    }
}
