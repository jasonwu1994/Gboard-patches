package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val PROVIDER_RECEIVER_WRAPPER_CLASS = "Lplg;"
private const val METRICS_UTILS_CLASS = "Lihe;"
private const val FOOTER_TAB_CLICK_CONSUMER_CLASS = "Lgyt;"
private const val KEYBOARD_WRAPPER_CLASS = "Lpbw;"
private const val SCROLLABLE_NAVIGATION_VIEW_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/expression/navbar/ScrollableNavigationView;"
private val providerRequestKeyboardType: String
    get() = GboardVersionBindings.keyboardDefinitionType.descriptor
private val providerRequestReceiverType: String
    get() = GboardVersionBindings.keyboardCompletionCallbackType.descriptor

internal val gboardZhuyinCustomSymbolsRoutingPatch = bytecodePatch(
    description = "移植 add-symbols 的 provider / routing / tab identity 主線。"
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        patchExtensionManager()
        patchProviderRequest()
        patchProviderWrapper()
        patchMetricsAlias()
        patchNavigationIdentity()
        patchFooterTabClick()
        patchKeyboardReady()
    }
}

context(context: BytecodePatchContext)
private fun patchExtensionManager() = with(context) {
    val mutableMethod =
        findMutableMethodOrThrow(GboardVersionBindings.keyboardExtensionManager)
    mutableMethod.addInstructions(0, EXTENSION_MANAGER_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchProviderRequest() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(GboardVersionBindings.keyboardProviderRequest)
    mutableMethod.addInstructions(0, PROVIDER_REQUEST_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchProviderWrapper() = with(context) {
    val onKeyboardCreated = findMutableMethodOrThrow(
        classType = PROVIDER_RECEIVER_WRAPPER_CLASS,
        name = "a",
        returnType = "V",
        parameterTypes = listOf(
            "Lqch;",
            "Lpky;",
            "Lcom/google/android/libraries/inputmethod/metadata/KeyboardDef;",
        )
    )
    onKeyboardCreated.addInstructions(0, PROVIDER_WRAPPER_TYPE_DELEGATE)

    val onBeforeKeyboardCreated = findMutableMethodOrThrow(
        classType = PROVIDER_RECEIVER_WRAPPER_CLASS,
        name = "b",
        returnType = "Lpky;",
        parameterTypes = listOf(
            "Lqch;",
            "Lcom/google/android/libraries/inputmethod/metadata/KeyboardDef;",
        )
    )
    onBeforeKeyboardCreated.addInstructions(0, PROVIDER_WRAPPER_TYPE_DELEGATE)

    val canHandle = findMutableMethodOrThrow(
        classType = PROVIDER_RECEIVER_WRAPPER_CLASS,
        name = "c",
        returnType = "Z",
        parameterTypes = listOf("Lqch;")
    )
    canHandle.addInstructions(0, PROVIDER_WRAPPER_TYPE_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchMetricsAlias() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = METRICS_UTILS_CLASS,
        name = "b",
        returnType = "Lxgs;",
        parameterTypes = listOf("Lqch;")
    )
    mutableMethod.addInstructions(0, METRICS_ALIAS_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchNavigationIdentity() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = SCROLLABLE_NAVIGATION_VIEW_CLASS,
        name = "a",
        returnType = "V",
        parameterTypes = listOf(
            "Landroid/view/inputmethod/EditorInfo;",
            "Lqch;",
            "Z",
            "Ljava/util/function/Consumer;",
            "Landroid/os/Parcelable;",
            "Ljava/util/function/Supplier;",
            "Ljava/util/function/Supplier;"
        )
    )
    mutableMethod.addInstructions(0, NAVIGATION_IDENTITY_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchFooterTabClick() = with(context) {
    val consumerClass = mutableClass(FOOTER_TAB_CLICK_CONSUMER_CLASS)
    val mutableMethod = findMutableMethodOrThrow(
        classType = FOOTER_TAB_CLICK_CONSUMER_CLASS,
        name = "accept",
        returnType = "V",
        parameterTypes = listOf("Ljava/lang/Object;")
    )
    validateFooterTabClickConsumerOrThrow(consumerClass.fields.map { it.type }, mutableMethod)
    mutableMethod.addInstructions(0, FOOTER_TAB_CLICK_DELEGATE)
}

context(context: BytecodePatchContext)
private fun patchKeyboardReady() = with(context) {
    val mutableMethod = findMutableMethodOrThrow(
        classType = KEYBOARD_WRAPPER_CLASS,
        name = "a",
        returnType = "V",
        parameterTypes = listOf(
            "Lpky;",
            "Lcom/google/android/libraries/inputmethod/metadata/KeyboardDef;",
            "Lqch;",
        )
    )
    mutableMethod.addInstructions(0, KEYBOARD_READY_DELEGATE)
}

private val EXTENSION_MANAGER_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ENSURE_EXTENSION_PROVIDER_MAPPING, "p0, p1")}
""".trimIndent()

private val PROVIDER_REQUEST_DELEGATE by lazy { """
    move-object v2, p4

    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_REWRITE_PROVIDER_REQUEST_TYPE, "p4")}

    move-result-object p4

    check-cast p4, $providerRequestKeyboardType

    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_BRIDGE_PROVIDER_RECEIVER, "p0, v2, p7")}

    move-result-object p7

    check-cast p7, $providerRequestReceiverType
""".trimIndent() }

private val PROVIDER_WRAPPER_TYPE_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_REWRITE_PROVIDER_WRAPPER_KEYBOARD_TYPE, "p1")}

    move-result-object p1

    check-cast p1, Lqch;
""".trimIndent()

private val METRICS_ALIAS_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_REWRITE_METRICS_KEYBOARD_TYPE, "p0")}

    move-result-object p0

    check-cast p0, Lqch;
""".trimIndent()

private val NAVIGATION_IDENTITY_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_REWRITE_NAVIGATION_KEYBOARD_TYPE, "p2")}

    move-result-object p2

    check-cast p2, Lqch;
""".trimIndent()

private val FOOTER_TAB_CLICK_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_EXPRESSION_CORPUS_FOOTER_TAB_CLICK, "p0, p1")}
""".trimIndent()

private val KEYBOARD_READY_DELEGATE = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ADD_SYMBOLS_RUNTIME_ON_KEYBOARD_READY, "p0 .. p3")}
""".trimIndent()

internal data class FooterTabClickConsumerShape(
    val scrollableNavigationFieldCount: Int,
    val acceptReadsScrollableNavigationField: Boolean,
    val acceptCallsScrollableNavigationView: Boolean
)

internal fun isValidFooterTabClickConsumerShape(shape: FooterTabClickConsumerShape): Boolean =
    shape.scrollableNavigationFieldCount == 1 &&
        shape.acceptReadsScrollableNavigationField &&
        shape.acceptCallsScrollableNavigationView

private fun validateFooterTabClickConsumerOrThrow(
    fieldTypes: List<String>,
    method: MutableMethod
) {
    val shape = FooterTabClickConsumerShape(
        scrollableNavigationFieldCount = fieldTypes.count { it == SCROLLABLE_NAVIGATION_VIEW_CLASS },
        acceptReadsScrollableNavigationField =
            method.referencesFieldType(
                definingClass = FOOTER_TAB_CLICK_CONSUMER_CLASS,
                fieldType = SCROLLABLE_NAVIGATION_VIEW_CLASS
            ),
        acceptCallsScrollableNavigationView = method.referencesMethodOwner(
            SCROLLABLE_NAVIGATION_VIEW_CLASS
        )
    )
    check(isValidFooterTabClickConsumerShape(shape)) {
        "Footer tab click consumer drifted: $shape"
    }
}

private fun MutableMethod.referencesFieldType(
    definingClass: String,
    fieldType: String
): Boolean {
    val instructions = implementation?.instructions ?: return false
    return instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? FieldReference
            ?: return@any false
        reference.definingClass == definingClass && reference.type == fieldType
    }
}

private fun MutableMethod.referencesMethodOwner(definingClass: String): Boolean {
    val instructions = implementation?.instructions ?: return false
    return instructions.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
            ?: return@any false
        reference.definingClass == definingClass
    }
}
