package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.returnInstructionIndices
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardManualIncognitoAccessPointPatch = bytecodePatch(
    description = "加入 17.7.7 manual_incognito Access Point catalog 與 descriptor delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(GboardManualIncognito1777Targets.orderFactory)
            .applyManualIncognitoOrderCatalogDelegate()
        findMutableMethodOrThrow(
            GboardManualIncognito1777Targets.accessPointControllerConstructor,
        ).applyManualIncognitoControllerDelegate()
    }
}

internal fun MutableMethod.applyManualIncognitoOrderCatalogDelegate() {
    val abi = RuntimeAbiCatalog.abi(
        RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_INCLUDE_ACCESS_POINT_CATALOG,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(abi.reference)) {
            "Malformed manual incognito order catalog delegate"
        }
        return
    }
    addInstructions(
        0,
        """
            ${RuntimeCallEmitter.invoke(
                RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_INCLUDE_ACCESS_POINT_CATALOG,
                "p3 .. p3",
            )}

            move-result-object p3

            check-cast p3, Lvbq;
        """.trimIndent(),
    )
}

internal fun MutableMethod.applyManualIncognitoControllerDelegate() {
    val abi = RuntimeAbiCatalog.abi(
        RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_AFTER_CONTROLLER_CREATED,
    )
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.size == 1) { "Expected one lrq constructor return, found ${returns.size}" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate manual incognito controller delegate" }
        return
    }
    addInstructions(
        returns.single(),
        RuntimeCallEmitter.invoke(
            RuntimeCallId.MANUAL_INCOGNITO_RUNTIME_AFTER_CONTROLLER_CREATED,
            "p0 .. p1",
        ),
    )
}
