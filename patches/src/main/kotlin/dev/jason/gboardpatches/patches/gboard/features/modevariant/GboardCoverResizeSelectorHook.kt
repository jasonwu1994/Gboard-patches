/*
 * COVER_RESIZE_1803: opt-in, controlled replacement of only the cover normal-keyboard
 * saved variant string at the exact Gboard 18.0.3 Lpfq.d READ/SAVE path.
 * Original mode/family/IME remains intact. See runtime for strict guards.
 */
package dev.jason.gboardpatches.patches.gboard.features.modevariant

import app.morphe.patcher.patch.BytecodePatchContext
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private val IME_PARAMS = listOf("Landroid/view/inputmethod/EditorInfo;", "Z")
private val READ_PARAMS = listOf(
    "Landroid/content/Context;", "Lqhy;", "I", "Lnmm;", "Z"
)
private val VARIANT_PARAMS = listOf("Lnmm;", "Z", "Z")
private const val SELECTOR = "Lpfq;->d(Lnmm;ZZ)Ljava/lang/String;"
private const val WRITE_MODE = "Lcdl;->t(ILjava/lang/String;)V"

private fun BytecodePatchContext.exact(
    owner: String, name: String, args: List<String>, result: String,
    static: Boolean
): GboardMethodTarget {
    val cls = classDefByOrNull { it.type == owner }
        ?: error("COVER_RESIZE_1803_NO_CLASS: " + owner)
    val matches = cls.methods.filter { method ->
        method.name == name &&
            method.parameterTypes.map { it.toString() } == args &&
            method.returnType == result &&
            method.implementation != null &&
            ((method.accessFlags and AccessFlags.STATIC.value) != 0) == static
    }
    check(matches.size == 1) {
        "COVER_RESIZE_1803_METHOD_DRIFT: " + owner + name + " matches=" + matches.size
    }
    return GboardMethodTarget(owner, name, args, result)
}

private fun BytecodePatchContext.imeInputView(): GboardMethodTarget {
    var owner: String? = "Lcom/android/inputmethod/latin/LatinIME;"
    val visited = mutableSetOf<String>()
    while (owner != null && visited.size < 16) {
        val type = owner
        check(visited.add(type)) { "COVER_RESIZE_1803 cyclic IME superclass" }
        val cls = classDefByOrNull { it.type == type }
            ?: error("COVER_RESIZE_1803 cannot resolve IME superclass: " + type)
        val methods = cls.methods.filter {
            it.name == "onStartInputView" &&
                it.parameterTypes.map { p -> p.toString() } == IME_PARAMS &&
                it.returnType == "V" && it.implementation != null
        }
        check(methods.size <= 1) { "COVER_RESIZE_1803 ambiguous onStartInputView" }
        if (methods.size == 1) {
            return GboardMethodTarget(type, "onStartInputView", IME_PARAMS, "V")
        }
        owner = cls.superclass
    }
    error("COVER_RESIZE_1803 InputMethodService callback not found")
}

internal val gboardCoverResizeSelectorHook = bytecodePatch(
    description = "Opt-in, guarded cover Resize variant-key selector for Gboard 18.0.3."
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val targetRead = exact(
            "Lpdq;", "a", READ_PARAMS, "Lpfh;", static = true
        )
        val targetSave = exact(
            "Lpez;", "ai", emptyList(), "V", static = false
        )
        val targetVariant = exact(
            "Lpfq;", "d", VARIANT_PARAMS, "Ljava/lang/String;", static = true
        )

        // Prove this is specifically the same map selector used by both paths.
        val read = findMutableMethodOrThrow(targetRead)
        val save = findMutableMethodOrThrow(targetSave)
        check(read.implementation?.instructions?.any {
            it.isMethodReference(SELECTOR)
        } == true) { "COVER_RESIZE_1803 READ does not call intended variant selector" }
        check(save.implementation?.instructions?.any {
            it.isMethodReference(SELECTOR)
        } == true) { "COVER_RESIZE_1803 SAVE does not call intended variant selector" }
        check(save.implementation?.instructions?.any {
            it.isMethodReference(WRITE_MODE)
        } == true) { "COVER_RESIZE_1803 SAVE does not reference mode preference write" }

        val variant = findMutableMethodOrThrow(targetVariant)
        val original = variant.implementation?.instructions
            ?: error("COVER_RESIZE_1803 selector has no native implementation")
        check(original.none {
            it.isMethodReference(RuntimeAbiCatalog.abi(
                RuntimeCallId.MODE_VARIANT_ROUTE_RESULT
            ).reference)
        }) { "COVER_RESIZE_1803 duplicate selector hook" }
        val returns = original.indices.filter {
            original[it].opcode == Opcode.RETURN_OBJECT
        }
        check(returns.size in 1..4) {
            "COVER_RESIZE_1803 selector return count changed to " + returns.size
        }
        // Return a String from the bounded runtime gate. Keep native return-object
        // instruction and its register unchanged. No new locals/parameters.
        returns.asReversed().forEach { at ->
            val register = (original[at] as? OneRegisterInstruction)?.registerA
                ?: error("COVER_RESIZE_1803 return-object missing register")
            check(register in 0..255) { "COVER_RESIZE_1803 return register out of range" }
            variant.addInstructions(
                at,
                RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_ROUTE_RESULT,
                    "v" + register + " .. v" + register) +
                    "\nmove-result-object v" + register
            )
        }
        variant.addInstructions(
            0,
            RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_FAMILY, "p0 .. p0")
        )

        check(read.implementation?.instructions?.none {
            it.isMethodReference(RuntimeAbiCatalog.abi(
                RuntimeCallId.MODE_VARIANT_BEGIN_READ
            ).reference)
        } == true) { "COVER_RESIZE_1803 duplicate read marker" }
        read.addInstructions(
            0,
            RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_BEGIN_READ, "p2 .. p2")
        )

        check(save.implementation?.instructions?.none {
            it.isMethodReference(RuntimeAbiCatalog.abi(
                RuntimeCallId.MODE_VARIANT_BEGIN_SAVE
            ).reference)
        } == true) { "COVER_RESIZE_1803 duplicate save marker" }
        // COVER_RESIZE_1803: a SAVE must also prove its native keyboard mode is 1.
        // Lpez.ai calls Lpez.i() twice; only its first result is used to
        // select the map before Lpfq.d. Observe the existing result register.
        val saveCode = save.implementation?.instructions
            ?: error("COVER_RESIZE_1803 SAVE implementation disappeared")
        val nativeModeCalls = saveCode.indices.filter {
            saveCode[it].isMethodReference("Lpez;->i()I")
        }
        check(nativeModeCalls.size == 2) {
            "COVER_RESIZE_1803 SAVE mode accessor call count: " + nativeModeCalls.size
        }
        val firstModeCall = nativeModeCalls.first()
        check(firstModeCall + 1 < saveCode.size &&
              saveCode[firstModeCall + 1].opcode == Opcode.MOVE_RESULT) {
            "COVER_RESIZE_1803 SAVE mode accessor did not return directly into a register"
        }
        val nativeModeReg =
            (saveCode[firstModeCall + 1] as? OneRegisterInstruction)?.registerA
                ?: error("COVER_RESIZE_1803 SAVE mode result register missing")
        check(nativeModeReg in 0..255) { "COVER_RESIZE_1803 SAVE mode register overflow" }
        save.addInstructions(
            firstModeCall + 2,
            RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_SAVE_KEYBOARD_MODE,
                "v" + nativeModeReg + " .. v" + nativeModeReg)
        )

        save.addInstructions(
            0,
            RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_BEGIN_SAVE, "")
        )

        // Bind trusted main/cover identity from the actual IME service.
        val ime = findMutableMethodOrThrow(imeInputView())
        check(ime.implementation?.instructions?.none {
            it.isMethodReference(RuntimeAbiCatalog.abi(
                RuntimeCallId.MODE_VARIANT_BIND_IME
            ).reference)
        } == true) { "COVER_RESIZE_1803 duplicate IME observer" }
        ime.addInstructions(
            0,
            RuntimeCallEmitter.invoke(RuntimeCallId.MODE_VARIANT_BIND_IME, "p0 .. p0")
        )
    }
}
