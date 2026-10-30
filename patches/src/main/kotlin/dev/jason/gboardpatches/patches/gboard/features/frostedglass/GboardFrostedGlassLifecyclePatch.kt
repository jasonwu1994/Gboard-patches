package dev.jason.gboardpatches.patches.gboard.features.frostedglass

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.methodCallIndices
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardFrostedGlassLifecyclePatch = bytecodePatch(description = "在 Gboard IME Window lifecycle 加入 Frosted Glass runtime delegate。") {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)
    execute {
        // Resolve admitted bindings only when this contribution executes. A sparse build
        // must still be able to load the patch class when Frosted Glass is unselected.
        val onConfigureWindow = GboardVersionBindings.frostedConfigure
        val onWindowShown = GboardVersionBindings.frostedShown
        val onWindowHidden = GboardVersionBindings.frostedHidden
        val modelType = GboardVersionBindings.frostedNavParamsConstructor.ownerDescriptor
        val applyNavBar = GboardVersionBindings.frostedNavApply
        findMutableMethodOrThrow(onWindowShown).addInstructions(0, RuntimeCallEmitter.invoke(RuntimeCallId.FROSTED_GLASS_RUNTIME_ON_INPUT_VIEW_STARTED, "p0 .. p0"))
        findMutableMethodOrThrow(onWindowHidden).applyVoidExitLifecycleDelegate(RuntimeCallId.FROSTED_GLASS_RUNTIME_ON_INPUT_WINDOW_HIDDEN, "p0")
        val configure = findMutableMethodOrThrow(onConfigureWindow)
        val patched = configure.applyFrostedGlassConfigureDelegate()
        if (patched !== configure) {
            val methods = mutableClass(onConfigureWindow.ownerDescriptor).methods
            check(methods.remove(configure) && methods.add(patched)) {
                "Could not replace expanded $FROSTED_CONFIGURE_DESCRIPTOR"
            }
        }
        val nav = applyNavBar.resolve(this)
        check(nav.accessFlags and AccessFlags.STATIC.value == 0 && nav.returnType == "V"
            && nav.parameterTypes == listOf("Landroid/view/Window;", modelType))
        GboardVersionBindings.frostedNavParamsConstructor.resolve(this)
        listOf(GboardVersionBindings.frostedNavColor, GboardVersionBindings.frostedNavDivider,
            GboardVersionBindings.frostedNavBody, GboardVersionBindings.frostedNavHeader,
            GboardVersionBindings.frostedNavLight).forEach { binding ->
            val field = binding.resolve(this)
            check(field.accessFlags and AccessFlags.FINAL.value != 0
                && field.accessFlags and AccessFlags.STATIC.value == 0)
        }
        nav.applySeamlessNavBarDelegates(modelType)
    }
}

/** Replaces the applied model at entry and lets the runtime re-style the Window at exit. */
internal fun MutableMethod.applySeamlessNavBarDelegates(
    modelType: String = GboardVersionBindings.frostedNavParamsConstructor.ownerDescriptor,
) {
    val implementation = implementation ?: error("Navigation method has no implementation")
    val instructions = implementation.instructions.toList()
    val entry = RuntimeAbiCatalog.abi(RuntimeCallId.FROSTED_GLASS_RUNTIME_ON_APPLY_NAV_BAR_ENTRY).reference
    if (instructions.any { it.isMethodReference(entry) }) return
    val modelRegister = implementation.registerCount - 1
    check(instructions.any { it.opcode == Opcode.RETURN_VOID }) { "Navigation method has no void exit" }
    check(instructions.none {
        it is OneRegisterInstruction && it.opcode.setsRegister() &&
            (it.registerA == modelRegister || it.opcode.setsWideRegister() && it.registerA + 1 == modelRegister)
    }) { "Navigation model register is overwritten; exit delegate would receive an invalid model" }
    addInstructions(0, navBarEntryDelegate(modelType))
    applyVoidExitLifecycleDelegate(RuntimeCallId.FROSTED_GLASS_RUNTIME_ON_APPLY_NAV_BAR_EXIT, "p2")
}

private fun navBarEntryDelegate(modelType: String) = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.FROSTED_GLASS_RUNTIME_ON_APPLY_NAV_BAR_ENTRY, "p1, p0, p2")}
    move-result-object p2
    check-cast p2, $modelType
""".trimIndent()

private fun MutableMethod.applyFrostedGlassConfigureDelegate(): MutableMethod {
    val implementation = implementation ?: error("$FROSTED_CONFIGURE_DESCRIPTOR has no implementation")
    if (implementation.registerCount == FROSTED_CONFIGURE_PATCHED_REGISTERS) {
        check(implementation.instructions.count { it.isMethodReference(FROSTED_CONFIGURE_RUNTIME_DESCRIPTOR) } == 1)
        return this
    }
    check(implementation.registerCount == FROSTED_CONFIGURE_STOCK_REGISTERS) {
        "Unexpected register count in $FROSTED_CONFIGURE_DESCRIPTOR: ${implementation.registerCount}"
    }
    val expanded = ImmutableMethod(
        definingClass, name, parameters, returnType, accessFlags, annotations, hiddenApiRestrictions,
        ImmutableMethodImplementation(
            FROSTED_CONFIGURE_PATCHED_REGISTERS,
            implementation.instructions, implementation.tryBlocks, implementation.debugItems,
        ),
    ).toMutable()
    expanded.addInstructions(0, FROSTED_CONFIGURE_ENTRY_COPIES)
    val layoutCalls = expanded.methodCallIndices("Landroid/view/Window;", "setLayout", "V", listOf("I", "I"))
    check(layoutCalls.size == 1) { "Expected exactly one Window.setLayout in $FROSTED_CONFIGURE_DESCRIPTOR" }
    expanded.addInstructions(layoutCalls.single() + 1, FROSTED_CONFIGURE_RUNTIME_DELEGATE)
    return expanded
}

private const val FROSTED_CONFIGURE_STOCK_REGISTERS = 11
private const val FROSTED_CONFIGURE_PATCHED_REGISTERS = 17
private const val FROSTED_CONFIGURE_DESCRIPTOR = "Lphr;->onConfigureWindow(Landroid/view/Window;ZZ)V"
private val FROSTED_CONFIGURE_RUNTIME_DESCRIPTOR =
    RuntimeAbiCatalog.abi(RuntimeCallId.FROSTED_GLASS_RUNTIME_AFTER_CONFIGURE_WINDOW).reference
private val FROSTED_CONFIGURE_ENTRY_COPIES = """
    move-object/from16 v7, p0
    move-object/from16 v8, p1
    move/from16 v9, p2
    move/from16 v10, p3
    move/from16 v11, p2
    move/from16 v12, p3
""".trimIndent()
private val FROSTED_CONFIGURE_RUNTIME_DELEGATE =
    RuntimeCallEmitter.invoke(RuntimeCallId.FROSTED_GLASS_RUNTIME_AFTER_CONFIGURE_WINDOW, "p1, v11, v12")
