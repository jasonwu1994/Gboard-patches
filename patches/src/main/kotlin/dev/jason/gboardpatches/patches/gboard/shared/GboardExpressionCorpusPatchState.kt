package dev.jason.gboardpatches.patches.gboard.shared

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags

internal enum class GboardExpressionCorpusPatchState {
    STOCK,
    CUSTOM_ONLY,
    TAB_ONLY,
    COMPOSED,
}

internal fun MutableMethod.requireGboardExpressionCorpusPatchState():
    GboardExpressionCorpusPatchState {
    check(
        definingClass == EXPRESSION_CORPUS_MANAGER_CLASS &&
            name == EXPRESSION_CORPUS_MANAGER_METHOD &&
            returnType == EXPRESSION_CORPUS_MANAGER_RETURN_TYPE &&
            parameterTypes == EXPRESSION_CORPUS_MANAGER_PARAMETERS,
    ) {
        "Refusing non-target expression corpus method " +
            "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
    }
    check(accessFlags == EXPECTED_ACCESS_FLAGS) {
        "Unexpected access flags in $EXPRESSION_CORPUS_MANAGER_DESCRIPTOR: $accessFlags"
    }
    val fingerprint = gboardStructuralFingerprint()
    return when (fingerprint) {
        STOCK_FINGERPRINT -> GboardExpressionCorpusPatchState.STOCK
        CUSTOM_ONLY_FINGERPRINT -> GboardExpressionCorpusPatchState.CUSTOM_ONLY
        TAB_ONLY_FINGERPRINT -> GboardExpressionCorpusPatchState.TAB_ONLY
        COMPOSED_FINGERPRINT -> GboardExpressionCorpusPatchState.COMPOSED
        else -> error(
            "Unexpected expression corpus structure in $EXPRESSION_CORPUS_MANAGER_DESCRIPTOR: " +
                fingerprint,
        )
    }
}

private const val EXPRESSION_CORPUS_MANAGER_CLASS = "Lgyo;"
private const val EXPRESSION_CORPUS_MANAGER_METHOD = "a"
private const val EXPRESSION_CORPUS_MANAGER_RETURN_TYPE = "Lwns;"
private val EXPRESSION_CORPUS_MANAGER_PARAMETERS =
    listOf("Landroid/view/inputmethod/EditorInfo;", "Z")
private const val EXPRESSION_CORPUS_MANAGER_DESCRIPTOR =
    "Lgyo;->a(Landroid/view/inputmethod/EditorInfo;Z)Lwns;"
private val EXPECTED_ACCESS_FLAGS = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value
private const val STOCK_FINGERPRINT =
    "c071e8a7f60078eb4470e8abb45518697d7793ae17b939a5dab118432c7b94bf"
private const val CUSTOM_ONLY_FINGERPRINT =
    "74b1e0d9ce128bc5d2635e6d12400ba8f3b13f740a1306b3874b978833017ad0"
private const val TAB_ONLY_FINGERPRINT =
    "bdcdf808949e414c9efb3c210601571d6a13350eb6c42ef4b675eb20135a5e8f"
private const val COMPOSED_FINGERPRINT =
    "31eca2eb752633ad1e09cfab1d90f5772462c2a69211e9990d2ba9517bcf004d"
