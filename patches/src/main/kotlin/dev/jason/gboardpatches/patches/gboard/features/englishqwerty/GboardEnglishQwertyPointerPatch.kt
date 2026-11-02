package dev.jason.gboardpatches.patches.gboard.features.englishqwerty

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerFeature
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerFeatureSpec
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerTransformationContext
import dev.jason.gboardpatches.patches.gboard.shared.GboardPointerOwnerTransformationAdapter
import dev.jason.gboardpatches.patches.gboard.shared.addFieldIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.indexOfFirstMethodCall
import dev.jason.gboardpatches.patches.gboard.shared.gboardPointerOwnerFeaturePatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private val pointerTrackerClass: String
    get() = GboardVersionBindings.pointerOwnerType.descriptor
private val SOFT_KEY_VIEW_TYPE: String
    get() = GboardVersionBindings.softKeyViewType.descriptor
private val ENGLISH_UPPERCASE_RUNTIME_CLASS = RuntimeAbiCatalog.abi(
    RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED,
).owner
private const val ENGLISH_ANCHOR_FIELD_NAME = "jasondevEnglishAnchorKey"

internal val gboardEnglishQwertyPointerTransformation =
    GboardPointerOwnerTransformationAdapter { context ->
        context.addEnglishAnchorField()
        context.addEnglishPointerHelpers()
        context.injectEnglishPointerOwner()
        context.injectEnglishPointerCleanup()
    }

private val gboardEnglishQwertyPointerSpec = GboardPointerOwnerFeatureSpec(
    feature = GboardPointerOwnerFeature.ENGLISH_QWERTY,
    transformation = gboardEnglishQwertyPointerTransformation,
)

internal val gboardEnglishQwertyPointerPatch = gboardPointerOwnerFeaturePatch(
    description = "在 18.5.4 qin reset 前固定 English 上滑起始 key 並抑制 retarget。",
    spec = gboardEnglishQwertyPointerSpec,
)

private fun GboardPointerOwnerTransformationContext.addEnglishAnchorField() {
    ownerClass.addFieldIfMissing(
        fieldName = ENGLISH_ANCHOR_FIELD_NAME,
        fieldType = SOFT_KEY_VIEW_TYPE,
        accessFlags = AccessFlags.PRIVATE.value
    )
}

private fun GboardPointerOwnerTransformationContext.addEnglishPointerHelpers() {
    ownerClass.addHelperMethodIfMissing(
        name = "jasondevShouldSuppressEnglishRetarget",
        parameterTypes = listOf(SOFT_KEY_VIEW_TYPE, "F", "F"),
        returnType = "Z",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 9,
        body = SHOULD_SUPPRESS_ENGLISH_RETARGET_BODY
    )
    ownerClass.addHelperMethodIfMissing(
        name = "jasondevClearEnglishAnchor",
        parameterTypes = emptyList(),
        returnType = "V",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 2,
        body = CLEAR_ENGLISH_POINTER_ANCHOR_BODY
    )
}

private fun GboardPointerOwnerTransformationContext.injectEnglishPointerOwner() {
    val finish = GboardVersionBindings.pointerFinish
    val rCallIndex = pointerOwnerMethod.indexOfFirstMethodCall(
        definingClass = finish.ownerDescriptor,
        name = finish.name,
        returnType = finish.returnType,
        parameterTypes = finish.parameterTypes,
    )
    val preReset = GboardVersionBindings.pointerPreReset
    val acCallIndex = pointerOwnerMethod.indexOfFirstMethodCall(
        definingClass = preReset.ownerDescriptor,
        name = preReset.name,
        returnType = preReset.returnType,
        parameterTypes = preReset.parameterTypes,
    )
    check(rCallIndex >= 0) { "Unable to find ${finish.reference} inside pointer owner" }
    check(acCallIndex >= 0) { "Unable to find ${preReset.reference} inside pointer owner" }
    check(rCallIndex < acCallIndex) {
        "Target pointer owner must finish the prior session before resetting tracker state"
    }

    pointerOwnerMethod.addInstructions(rCallIndex, "nop")
    val continuationInstruction = pointerOwnerMethod.implementation!!.instructions[rCallIndex]
    pointerOwnerMethod.addInstructionsWithLabels(
        rCallIndex,
        ENGLISH_POINTER_OWNER_PRE_RESET_DELEGATE,
        ExternalLabel(
            "jasondev_continue_english_pointer_owner",
            continuationInstruction
        )
    )
}

private fun GboardPointerOwnerTransformationContext.injectEnglishPointerCleanup() {
    pointerCancelMethod.addInstructions(0, CLEAR_ENGLISH_POINTER_ANCHOR_DELEGATE)
    pointerResetMethod.addInstructions(0, CLEAR_ENGLISH_POINTER_ANCHOR_DELEGATE)
}

private val ENGLISH_POINTER_OWNER_PRE_RESET_DELEGATE by lazy { """
    invoke-direct {p0, p1, v0, v1}, $pointerTrackerClass->jasondevShouldSuppressEnglishRetarget(${SOFT_KEY_VIEW_TYPE}FF)Z

    move-result v4

    if-eqz v4, :jasondev_continue_english_pointer_owner

    return-void
""".trimIndent() }

private val CLEAR_ENGLISH_POINTER_ANCHOR_DELEGATE by lazy { """
    invoke-direct {p0}, $pointerTrackerClass->jasondevClearEnglishAnchor()V
""".trimIndent() }

private val CLEAR_ENGLISH_POINTER_ANCHOR_BODY by lazy { """
    const/4 v0, 0x0

    iput-object v0, p0, $pointerTrackerClass->$ENGLISH_ANCHOR_FIELD_NAME:$SOFT_KEY_VIEW_TYPE

    return-void
""".trimIndent() }

private val SHOULD_SUPPRESS_ENGLISH_RETARGET_BODY by lazy { """
    :try_start_0
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED, "")}

    move-result v0

    if-nez v0, :cond_enabled

    const/4 v0, 0x0

    iput-object v0, p0, $pointerTrackerClass->jasondevEnglishAnchorKey:$SOFT_KEY_VIEW_TYPE

    goto :cond_return_false

    :cond_enabled
    iget-object v0, p0, $pointerTrackerClass->jasondevEnglishAnchorKey:$SOFT_KEY_VIEW_TYPE

    if-nez v0, :cond_have_anchor

    iget-object v1, p0, $pointerTrackerClass->m:$SOFT_KEY_VIEW_TYPE

    if-nez v1, :cond_return_false

    if-eqz p1, :cond_return_false

    iget-object v1, p1, $SOFT_KEY_VIEW_TYPE->e:Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    if-eqz v1, :cond_return_false

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA,
        "v1",
    )}

    move-result v2

    if-eqz v2, :cond_return_false

    move-object v0, p1

    iput-object v0, p0, $pointerTrackerClass->jasondevEnglishAnchorKey:$SOFT_KEY_VIEW_TYPE

    :cond_have_anchor
    iget-object v1, v0, $SOFT_KEY_VIEW_TYPE->e:Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    if-eqz v1, :cond_return_false

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA,
        "v1",
    )}

    move-result v2

    if-eqz v2, :cond_return_false

    invoke-virtual {p0}, $pointerTrackerClass->i()Lqae;

    move-result-object v2

    invoke-virtual {p0, p2, p3, v2}, $pointerTrackerClass->h(FFLqae;)Lqae;

    move-result-object v2

    sget-object v3, Lqae;->c:Lqae;

    if-ne v2, v3, :cond_return_false

    invoke-virtual {v1, v2}, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;

    move-result-object v1

    if-eqz v1, :cond_return_false

    const/4 v0, 0x1

    return v0

    :cond_return_false
    const/4 v0, 0x0

    return v0
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :catch_0
    const/4 v0, 0x0

    return v0
""".trimIndent() }
