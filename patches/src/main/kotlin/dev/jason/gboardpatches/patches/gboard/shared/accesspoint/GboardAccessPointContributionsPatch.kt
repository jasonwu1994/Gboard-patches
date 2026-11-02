package dev.jason.gboardpatches.patches.gboard.shared.accesspoint

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

internal val gboardAccessPointContributions1803Patch = bytecodePatch(
    description = "集中注入 18.5.4 synthetic Access Point catalog 與 controller delegate。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(GboardAccessPointContributionsTargets.orderFactory)
            .applyAccessPointOrderCatalogDelegate1803()
        findMutableMethodOrThrow(GboardAccessPointContributionsTargets.controllerConstructor)
            .applyAccessPointControllerDelegate1803()
    }
}

internal fun MutableMethod.applyAccessPointOrderCatalogDelegate1803() {
    val call = RuntimeCallId.ACCESS_POINT_CONTRIBUTIONS_INCLUDE_ORDER_CATALOG
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) {
            "Malformed 18.5.4 Access Point order catalog delegate"
        }
        return
    }
    addInstructions(
        0,
        """
            move-object/from16 v0, p0

            move-object/from16 v1, p3

            ${RuntimeCallEmitter.invoke(call, "v0, v1")}

            move-result-object v0

            move-object/from16 p3, v0

            check-cast p3, Lwpa;
        """.trimIndent(),
    )
}

internal fun MutableMethod.applyAccessPointControllerDelegate1803() {
    val call = RuntimeCallId.ACCESS_POINT_CONTRIBUTIONS_AFTER_CONTROLLER_CREATED
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val returns = returnInstructionIndices().filter { instructions[it].isOpcode("RETURN_VOID") }
    check(returns.size == 1) { "Expected one mwv constructor return, found ${returns.size}" }
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) { "Duplicate 18.5.4 Access Point controller delegate" }
        return
    }
    addInstructions(
        returns.single(),
        RuntimeCallEmitter.invoke(call, "p0, p1"),
    )
}
