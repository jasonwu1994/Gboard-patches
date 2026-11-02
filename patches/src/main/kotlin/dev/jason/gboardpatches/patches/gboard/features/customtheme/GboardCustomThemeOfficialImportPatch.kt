package dev.jason.gboardpatches.patches.gboard.features.customtheme

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardStructuralFingerprint
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardCustomThemeOfficialImportPatch = bytecodePatch(
    description = "在 Gboard 官方 Theme 加號加入 ZIP 主題匯入選項。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val (plusHandler, resultHandler) = validateCustomThemeTargetContract()
        plusHandler.applyCustomThemeGuard(
            RuntimeCallId.CUSTOM_THEME_RUNTIME_INTERCEPT_PLUS,
            "p0 .. p3",
            "custom_theme_plus_continue",
        )
        resultHandler.applyCustomThemeGuard(
            RuntimeCallId.CUSTOM_THEME_RUNTIME_HANDLE_ACTIVITY_RESULT,
            "p0 .. p3",
            "custom_theme_result_continue",
        )
    }
}

context(context: app.morphe.patcher.patch.BytecodePatchContext)
private fun validateCustomThemeTargetContract(): Pair<MutableMethod, MutableMethod> = with(context) {
    check(GboardVersionBindings.targetVersion == "18.5.4") {
        "Unsupported Custom Theme stock contract for ${GboardVersionBindings.targetVersion}"
    }
    val publicFinal = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value
    val publicStatic = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value
    val plusHandler = findMutableMethodOrThrow(GboardVersionBindings.customThemePlusHandler)
    val resultHandler = findMutableMethodOrThrow(GboardVersionBindings.customThemeResultHandler)
    listOf(
        plusHandler,
        resultHandler,
        findMutableMethodOrThrow(GboardVersionBindings.customThemePeerResultHandler),
        findMutableMethodOrThrow(GboardVersionBindings.customThemeLauncherLaunch),
    ).forEach { method ->
        check(method.accessFlags == publicFinal && method.implementation != null) {
            "Unexpected Custom Theme method shape for ${method.definingClass}->${method.name}"
        }
    }
    plusHandler.requireStockFingerprintUnlessPatched(
        RuntimeCallId.CUSTOM_THEME_RUNTIME_INTERCEPT_PLUS,
        CUSTOM_THEME_PLUS_1853_FINGERPRINT,
    )
    resultHandler.requireStockFingerprintUnlessPatched(
        RuntimeCallId.CUSTOM_THEME_RUNTIME_HANDLE_ACTIVITY_RESULT,
        CUSTOM_THEME_RESULT_1853_FINGERPRINT,
    )
    findMutableMethodOrThrow(GboardVersionBindings.customThemeValidator).let { method ->
        check(method.accessFlags == publicStatic && method.implementation != null) {
            "Unexpected Custom Theme validator shape for ${method.definingClass}->${method.name}"
        }
    }
    check(GboardVersionBindings.customThemeFragmentPeerField.resolve(this).accessFlags ==
        AccessFlags.PUBLIC.value) {
        "Unexpected Custom Theme fragment peer field shape"
    }
    listOf(
        GboardVersionBindings.customThemePeerContextField,
        GboardVersionBindings.customThemePeerLauncherField,
    ).forEach { binding ->
        check(binding.resolve(this).accessFlags == publicFinal) {
            "Unexpected Custom Theme peer field shape for ${binding.reference}"
        }
    }
    plusHandler to resultHandler
}

internal fun MutableMethod.requireStockFingerprintUnlessPatched(
    call: RuntimeCallId,
    expected: String,
) {
    val runtimeReference = RuntimeAbiCatalog.abi(call).reference
    if (implementation!!.instructions.any { it.isMethodReference(runtimeReference) }) return
    val actual = gboardStructuralFingerprint()
    check(actual == expected) {
        "Unexpected18.5.4 Custom Theme fingerprint for $definingClass->$name: " +
            "$actual != $expected"
    }
}

internal const val CUSTOM_THEME_PLUS_1853_FINGERPRINT =
    "60fcd2fd947ffaf1a478edc562a295ab292acb4f3ed1519551459ada44033370"
internal const val CUSTOM_THEME_RESULT_1853_FINGERPRINT =
    "e64cedb293a76233836aa0779d5f9a81156b3a41c7f77e78f830e1ee24901f48"

private fun MutableMethod.applyCustomThemeGuard(
    call: RuntimeCallId,
    registers: String,
    labelName: String,
) {
    check(accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value)) {
        "Unexpected access flags for $definingClass->$name"
    }
    val implementation = implementation ?: error("No implementation in $definingClass->$name")
    check(implementation.registerCount == 5) {
        "Unexpected register count in $definingClass->$name: ${implementation.registerCount}"
    }
    val abi = RuntimeAbiCatalog.abi(call)
    val existing = implementation.instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && implementation.instructions.first().isMethodReference(abi.reference)) {
            "Malformed Custom Theme adapter in $definingClass->$name"
        }
        return
    }
    addInstructions(0, "nop")
    val continuation = implementation.instructions[0]
    addInstructionsWithLabels(
        0,
        """
            ${RuntimeCallEmitter.invoke(call, registers)}

            move-result v0

            if-eqz v0, :$labelName

            return-void
        """.trimIndent(),
        ExternalLabel(labelName, continuation),
    )
}
