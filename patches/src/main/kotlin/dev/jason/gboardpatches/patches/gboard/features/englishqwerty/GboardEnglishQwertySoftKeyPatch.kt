package dev.jason.gboardpatches.patches.gboard.features.englishqwerty

import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardSoftKeyFamilyFeature
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.gboardSoftKeyFamilyFeaturePatch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val SOFT_KEY_VIEW_CLASS =
    "Lcom/google/android/libraries/inputmethod/widgets/SoftKeyView;"
private val ENGLISH_UPPERCASE_RUNTIME_CLASS = RuntimeAbiCatalog.abi(
    RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED,
).owner
private val softKeyMetadataType: String
    get() = GboardVersionBindings.softKeyMetadataType.descriptor

internal val gboardEnglishQwertySoftKeyPatch = gboardSoftKeyFamilyFeaturePatch(
    description = "在 18.5.4 English QWERTY bind time 注入缺少的上滑大小寫 action。",
    feature = GboardSoftKeyFamilyFeature.ENGLISH_QWERTY,
)

internal val GBOARD_ENGLISH_SOFT_KEY_HELPER_NAMES = setOf(
    "jasondevToggleAsciiCase",
    "jasondevResolvePrimaryLabel",
    "jasondevResolvePressPayload",
    "jasondevIsEnglishQwertyKeyId",
    "jasondevPatchIncomingMetadata",
)

internal fun MutableClass.installGboardEnglishQwertySoftKeyHelpers() {
    addHelperMethodIfMissing(
        name = "jasondevToggleAsciiCase",
        parameterTypes = listOf("Ljava/lang/String;"),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 3,
        body = TOGGLE_ASCII_CASE_BODY
    )
    addHelperMethodIfMissing(
        name = "jasondevResolvePrimaryLabel",
        parameterTypes = listOf(softKeyMetadataType),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 6,
        body = RESOLVE_PRIMARY_LABEL_BODY
    )
    addHelperMethodIfMissing(
        name = "jasondevResolvePressPayload",
        parameterTypes = listOf(softKeyMetadataType),
        returnType = "Ljava/lang/String;",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 7,
        body = RESOLVE_PRESS_PAYLOAD_BODY
    )
    addHelperMethodIfMissing(
        name = "jasondevIsEnglishQwertyKeyId",
        parameterTypes = listOf("I"),
        returnType = "Z",
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        registerCount = 2,
        body = IS_ENGLISH_QWERTY_KEY_ID_BODY
    )
    addHelperMethodIfMissing(
        name = "jasondevPatchIncomingMetadata",
        parameterTypes = listOf(softKeyMetadataType),
        returnType = softKeyMetadataType,
        accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        registerCount = 16,
        body = PATCH_INCOMING_METADATA_BODY
    )
}

private val TOGGLE_ASCII_CASE_BODY = """
    if-eqz p0, :cond_invalid

    invoke-virtual {p0}, Ljava/lang/String;->length()I

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_invalid

    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Ljava/lang/String;->charAt(I)C

    move-result v0

    const/16 v1, 0x61

    if-lt v0, v1, :cond_uppercase

    const/16 v1, 0x7a

    if-gt v0, v1, :cond_uppercase

    add-int/lit8 v0, v0, -0x20

    int-to-char v0, v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(C)Ljava/lang/String;

    move-result-object p0

    return-object p0

    :cond_uppercase
    const/16 v1, 0x41

    if-lt v0, v1, :cond_invalid

    const/16 v1, 0x5a

    if-gt v0, v1, :cond_invalid

    add-int/lit8 v0, v0, 0x20

    int-to-char v0, v0

    invoke-static {v0}, Ljava/lang/String;->valueOf(C)Ljava/lang/String;

    move-result-object p0

    return-object p0

    :cond_invalid
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val RESOLVE_PRIMARY_LABEL_BODY = """
    if-eqz p0, :cond_none

    iget-object p0, p0, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->i:Lwns;

    if-eqz p0, :cond_none

    invoke-interface {p0}, Ljava/util/List;->size()I

    move-result v0

    const/4 v1, 0x0

    :goto_0
    if-ge v1, v0, :cond_none

    invoke-interface {p0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v2

    if-eqz v2, :cond_next

    invoke-virtual {v2}, Ljava/lang/Object;->toString()Ljava/lang/String;

    move-result-object v2

    if-eqz v2, :cond_next

    invoke-virtual {v2}, Ljava/lang/String;->length()I

    move-result v3

    if-lez v3, :cond_next

    return-object v2

    :cond_next
    add-int/lit8 v1, v1, 0x1

    goto :goto_0

    :cond_none
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val RESOLVE_PRESS_PAYLOAD_BODY = """
    if-eqz p0, :cond_none

    sget-object v0, Lqae;->a:Lqae;

    invoke-virtual {p0, v0}, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;

    move-result-object v0

    if-eqz v0, :cond_none

    iget-object v0, v0, Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->f:Lwns;

    if-eqz v0, :cond_none

    invoke-interface {v0}, Ljava/util/List;->size()I

    move-result v1

    const/4 v2, 0x0

    :goto_0
    if-ge v2, v1, :cond_none

    invoke-interface {v0, v2}, Ljava/util/List;->get(I)Ljava/lang/Object;

    move-result-object v3

    check-cast v3, Lqbc;

    if-eqz v3, :cond_next

    iget-object v3, v3, Lqbc;->e:Ljava/lang/Object;

    instance-of v4, v3, Ljava/lang/CharSequence;

    if-eqz v4, :cond_next

    check-cast v3, Ljava/lang/CharSequence;

    invoke-interface {v3}, Ljava/lang/CharSequence;->toString()Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :cond_next

    invoke-virtual {v3}, Ljava/lang/String;->length()I

    move-result v4

    if-lez v4, :cond_next

    return-object v3

    :cond_next
    add-int/lit8 v2, v2, 0x1

    goto :goto_0

    :cond_none
    const/4 p0, 0x0

    return-object p0
""".trimIndent()

private val IS_ENGLISH_QWERTY_KEY_ID_BODY = """
    sparse-switch p0, :sswitch_data_0

    const/4 p0, 0x0

    return p0

    :sswitch_0
    const/4 p0, 0x1

    return p0

    :sswitch_data_0
    .sparse-switch
        0x7f0b17f7 -> :sswitch_0
        0x7f0b1806 -> :sswitch_0
        0x7f0b1808 -> :sswitch_0
        0x7f0b180f -> :sswitch_0
        0x7f0b1813 -> :sswitch_0
        0x7f0b1826 -> :sswitch_0
        0x7f0b1828 -> :sswitch_0
        0x7f0b1833 -> :sswitch_0
        0x7f0b1838 -> :sswitch_0
        0x7f0b1849 -> :sswitch_0
        0x7f0b184b -> :sswitch_0
        0x7f0b184f -> :sswitch_0
        0x7f0b1853 -> :sswitch_0
        0x7f0b1857 -> :sswitch_0
        0x7f0b1860 -> :sswitch_0
        0x7f0b1875 -> :sswitch_0
        0x7f0b1877 -> :sswitch_0
        0x7f0b1878 -> :sswitch_0
        0x7f0b187d -> :sswitch_0
        0x7f0b1886 -> :sswitch_0
        0x7f0b1890 -> :sswitch_0
        0x7f0b18a3 -> :sswitch_0
        0x7f0b18a5 -> :sswitch_0
        0x7f0b18a8 -> :sswitch_0
        0x7f0b18a9 -> :sswitch_0
        0x7f0b18ad -> :sswitch_0
        0x7f0b18d4 -> :sswitch_0
        0x7f0b18e4 -> :sswitch_0
        0x7f0b18e9 -> :sswitch_0
        0x7f0b18f7 -> :sswitch_0
        0x7f0b1905 -> :sswitch_0
        0x7f0b191b -> :sswitch_0
        0x7f0b191f -> :sswitch_0
        0x7f0b192e -> :sswitch_0
        0x7f0b1934 -> :sswitch_0
        0x7f0b1945 -> :sswitch_0
        0x7f0b1949 -> :sswitch_0
        0x7f0b1951 -> :sswitch_0
        0x7f0b195e -> :sswitch_0
        0x7f0b1965 -> :sswitch_0
        0x7f0b1971 -> :sswitch_0
        0x7f0b1987 -> :sswitch_0
        0x7f0b198e -> :sswitch_0
        0x7f0b1992 -> :sswitch_0
        0x7f0b199d -> :sswitch_0
        0x7f0b19c5 -> :sswitch_0
        0x7f0b19e6 -> :sswitch_0
        0x7f0b19fb -> :sswitch_0
        0x7f0b19fe -> :sswitch_0
        0x7f0b1a03 -> :sswitch_0
        0x7f0b1a06 -> :sswitch_0
        0x7f0b1a0d -> :sswitch_0
    .end sparse-switch
""".trimIndent()

private val PATCH_INCOMING_METADATA_BODY = """
    if-eqz p1, :cond_return_original

    :try_start_0
    ${RuntimeCallEmitter.invoke(RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED, "")}

    move-result v0

    if-eqz v0, :cond_return_original_safe

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA,
        "p1",
    )}

    move-result v0

    if-nez v0, :cond_return_original_safe

    iget v0, p1, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->f:I

    invoke-static {v0}, $SOFT_KEY_VIEW_CLASS->jasondevIsEnglishQwertyKeyId(I)Z

    move-result v0

    if-eqz v0, :cond_return_original_safe

    sget-object v0, Lqae;->c:Lqae;

    invoke-virtual {p1, v0}, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;

    move-result-object v0

    if-nez v0, :cond_return_original_safe

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_GET_CACHED_PATCHED_METADATA,
        "p1",
    )}

    move-result-object v0

    if-eqz v0, :cond_cache_miss

    check-cast v0, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    return-object v0

    :cond_cache_miss

    invoke-static {p1}, $SOFT_KEY_VIEW_CLASS->jasondevResolvePrimaryLabel(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;)Ljava/lang/String;

    move-result-object v1

    invoke-static {p1}, $SOFT_KEY_VIEW_CLASS->jasondevResolvePressPayload(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;)Ljava/lang/String;

    move-result-object v2

    if-nez v1, :cond_have_base_label

    move-object v1, v2

    :cond_have_base_label
    invoke-static {v1}, $SOFT_KEY_VIEW_CLASS->jasondevToggleAsciiCase(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v3

    if-eqz v3, :cond_return_original_safe

    if-eqz v2, :cond_build_action

    invoke-static {v2}, $SOFT_KEY_VIEW_CLASS->jasondevToggleAsciiCase(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    if-eqz v0, :cond_return_original_safe

    :cond_build_action
    new-instance v4, Lqcv;

    invoke-direct {v4}, Lqcv;-><init>()V

    invoke-virtual {v4, p1}, Lqcv;->k(Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;)V

    new-instance v5, Lqaf;

    invoke-direct {v5}, Lqaf;-><init>()V

    sget-object v6, Lqae;->c:Lqae;

    iput-object v6, v5, Lqaf;->a:Lqae;

    const/4 v6, 0x1

    new-array v7, v6, [Ljava/lang/String;

    const/4 v6, 0x0

    aput-object v3, v7, v6

    invoke-virtual {v5, v7}, Lqaf;->t([Ljava/lang/String;)V

    const v7, -0x2719

    sget-object v8, Lqbb;->c:Lqbb;

    invoke-virtual {v5, v7, v8, v3}, Lqaf;->s(ILqbb;Ljava/lang/Object;)V

    invoke-virtual {v5}, Lqaf;->c()Lcom/google/android/libraries/inputmethod/metadata/ActionDef;

    move-result-object v5

    if-eqz v5, :cond_return_original_safe

    invoke-virtual {v4, v5}, Lqcv;->t(Lcom/google/android/libraries/inputmethod/metadata/ActionDef;)V

    invoke-virtual {v4}, Lqcv;->b()Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    move-result-object v0

    check-cast v0, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    if-eqz v0, :cond_return_original_safe

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_CACHE_PATCHED_METADATA,
        "p1, v0",
    )}

    move-result-object v0

    check-cast v0, Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;

    if-eqz v0, :cond_return_original_safe

    return-object v0

    :cond_return_original_safe
    return-object p1
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    :cond_return_original
    return-object p1

    :catch_0
    return-object p1
""".trimIndent()
