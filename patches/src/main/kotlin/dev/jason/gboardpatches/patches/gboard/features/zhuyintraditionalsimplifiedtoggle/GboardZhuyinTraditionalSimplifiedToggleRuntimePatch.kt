package dev.jason.gboardpatches.patches.gboard.features.zhuyintraditionalsimplifiedtoggle

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.gboardStructuralFingerprint
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isReference
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.gboard.features.featureflags.applyFeatureMarker
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

private val POPUP_RUNTIME_CALL =
    RuntimeCallId.ZHUYIN_TRADITIONAL_SIMPLIFIED_TOGGLE_RUNTIME_PATCH_POPUP_ACTION
private val POPUP_RUNTIME_ABI = RuntimeAbiCatalog.abi(POPUP_RUNTIME_CALL)
private val POPUP_RUNTIME_REFERENCE = POPUP_RUNTIME_ABI.reference
private const val GBOARD_18_0_3_POPUP_STOCK_FINGERPRINT =
    "03ef5a453ac6c4657b72f805b603ca29708c4307cf69bc029d2a299692f49942"
private val POPUP_TARGET_ACCESS_FLAGS = AccessFlags.PUBLIC.value

internal val gboardZhuyinSimplifiedCandidateButtonFeatureMarkerPatch = resourcePatch(
    description = "Expose the independent Zhuyin simplified candidate button setting.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyFeatureMarker(ZHUYIN_SIMPLIFIED_CANDIDATE_BUTTON_FEATURE_MARKER)
    }
}

internal const val ZHUYIN_SIMPLIFIED_CANDIDATE_BUTTON_FEATURE_MARKER =
    "dev.jason.gboardpatches.feature.zhuyin_simplified_candidate_button"

internal val gboardZhuyinTraditionalSimplifiedTogglePopupPatch = bytecodePatch(
    description = "修改 18.0.3 注音繁簡切換 popup action。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        GboardVersionBindings.zhuyinTogglePopupAction
            .ownerClass(this)
            .applyZhuyinTogglePopupActionPatch()
    }
}

internal fun MutableClass.applyZhuyinTogglePopupActionPatch() {
    val plan = ZhuyinTogglePopupTransformationPlan.preflight(this)
    plan.commit()
}

private data class ZhuyinTogglePopupTransformationPlan(
    val ownerClass: MutableClass,
    val committedMethods: Set<MutableMethod>?,
) {
    fun commit() {
        val methods = committedMethods ?: return
        ownerClass.methods.clear()
        ownerClass.methods.addAll(methods)
    }

    companion object {
        fun preflight(ownerClass: MutableClass): ZhuyinTogglePopupTransformationPlan {
            val target = ownerClass.resolvePopupTargetReadOnly()
            target.requirePopupImplementation()
            if (target.hasAnyPopupRuntimeState()) {
                target.verifyExactPatchedPopupState()
                return ZhuyinTogglePopupTransformationPlan(ownerClass, null)
            }

            target.requireExact1803PopupStockFingerprint()
            val candidate = MutableClass(ownerClass)
            val candidateTarget = GboardVersionBindings.zhuyinTogglePopupAction.resolve(candidate)
            candidateTarget.addInstructions(0, ZHUYIN_TOGGLE_POPUP_DELEGATE)
            candidateTarget.verifyExactPatchedPopupState()

            val committed = ownerClass.methods.toMutableSet().apply {
                check(remove(target)) { "Could not stage Zhuyin toggle popup target replacement" }
                add(MutableMethod(candidateTarget))
            }
            return ZhuyinTogglePopupTransformationPlan(ownerClass, committed)
        }
    }
}

private fun MutableClass.resolvePopupTargetReadOnly(): MutableMethod {
    val binding = GboardVersionBindings.zhuyinTogglePopupAction
    val exact = methods.filter(binding::matches)
    check(exact.size <= 1) { "Ambiguous popup target: ${binding.reference}" }
    exact.singleOrNull()?.let { return it }

    val nearMisses = methods.filter { method ->
        method.definingClass == binding.ownerDescriptor && method.name == binding.name
    }
    check(nearMisses.isEmpty()) {
        val actual = nearMisses.joinToString { method ->
            "(${method.parameterTypes.joinToString("")})${method.returnType}"
        }
        "Popup target prototype drift: expected ${binding.reference}, found $actual"
    }
    error("Could not find ${binding.reference}")
}

private fun MutableMethod.requirePopupImplementation() {
    check(accessFlags == POPUP_TARGET_ACCESS_FLAGS && implementation != null) {
        "Unexpected popup target access or implementation: " +
            GboardVersionBindings.zhuyinTogglePopupAction.reference
    }
}

private fun MutableMethod.hasAnyPopupRuntimeState(): Boolean =
    implementation!!.instructions.any { instruction ->
        instruction.popupRuntimeCallNearMatch()
    }

private fun MutableMethod.requireExact1803PopupStockFingerprint() {
    val actual = gboardStructuralFingerprint()
    check(actual == GBOARD_18_0_3_POPUP_STOCK_FINGERPRINT) {
        "18.0.3 popup stock body drift in $definingClass->$name: $actual"
    }
}

private fun MutableMethod.verifyExactPatchedPopupState() {
    verifyExactPopupDelegate()
    val normalized = MutableMethod(this)
    repeat(POPUP_DELEGATE_INSTRUCTION_COUNT) {
        normalized.implementation!!.removeInstruction(0)
    }
    check(normalized.implementation!!.instructions.none { instruction ->
        instruction.popupRuntimeCallNearMatch()
    }) { "Extraneous or near-miss popup runtime call after the managed delegate" }
    normalized.requireExact1803PopupStockFingerprint()

    val expected = MutableMethod(normalized)
    expected.addInstructions(0, ZHUYIN_TOGGLE_POPUP_DELEGATE)
    check(gboardStructuralFingerprint() == expected.gboardStructuralFingerprint()) {
        "Malformed popup patched state"
    }
}

private fun MutableMethod.verifyExactPopupDelegate() {
    val implementation = implementation ?: error("Popup target has no implementation")
    val layout = PopupRegisterLayout.fromBinding()
    val parameterBase = implementation.registerCount - layout.parameterWordCount
    check(parameterBase >= 0) { "Popup target register layout is smaller than its prototype" }
    val viewRegister = parameterBase + layout.parameterRegister(POPUP_VIEW_PARAMETER_INDEX)
    val actionRegister = parameterBase + layout.parameterRegister(POPUP_ACTION_PARAMETER_INDEX)
    val instructions = implementation.instructions
    val runtimeCallIndices = instructions.indices.filter { index ->
        instructions[index].isMethodReference(POPUP_RUNTIME_REFERENCE)
    }
    check(runtimeCallIndices == listOf(0)) {
        "Popup runtime delegate must appear exactly once at method entry"
    }
    check(
        instructions[0].isInvoke(
            "INVOKE_STATIC",
            POPUP_RUNTIME_REFERENCE,
            viewRegister,
            actionRegister,
        ) &&
            instructions.getOrNull(1)?.isRegisterOperation(
                "MOVE_RESULT_OBJECT",
                actionRegister,
            ) == true &&
            instructions.getOrNull(2)?.isRegisterOperation(
                "CHECK_CAST",
                actionRegister,
            ) == true &&
            instructions[2].isReference(layout.actionType)
    ) {
        "Popup runtime delegate is malformed"
    }
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .popupRuntimeCallNearMatch(): Boolean {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass == POPUP_RUNTIME_ABI.owner &&
        reference.name == POPUP_RUNTIME_ABI.name
}

private data class PopupRegisterLayout(
    val parameterWordOffsets: List<Int>,
    val parameterWordCount: Int,
    val actionType: String,
) {
    fun parameterRegister(index: Int): Int = parameterWordOffsets[index]

    companion object {
        fun fromBinding(): PopupRegisterLayout {
            val parameters = GboardVersionBindings.zhuyinTogglePopupAction.parameterTypes
            var nextWord = 1
            val offsets = parameters.map { type ->
                nextWord.also { nextWord += if (type == "J" || type == "D") 2 else 1 }
            }
            return PopupRegisterLayout(
                parameterWordOffsets = offsets,
                parameterWordCount = nextWord,
                actionType = parameters[POPUP_ACTION_PARAMETER_INDEX],
            )
        }
    }
}

private val POPUP_REGISTER_LAYOUT by lazy { PopupRegisterLayout.fromBinding() }
private val POPUP_VIEW_PARAMETER_REGISTER: Int
    get() = POPUP_REGISTER_LAYOUT.parameterRegister(POPUP_VIEW_PARAMETER_INDEX)
private val POPUP_ACTION_PARAMETER_REGISTER: Int
    get() = POPUP_REGISTER_LAYOUT.parameterRegister(POPUP_ACTION_PARAMETER_INDEX)

internal val ZHUYIN_TOGGLE_POPUP_DELEGATE by lazy { """
    ${RuntimeCallEmitter.invoke(
        POPUP_RUNTIME_CALL,
        "p$POPUP_VIEW_PARAMETER_REGISTER, p$POPUP_ACTION_PARAMETER_REGISTER",
    )}

    move-result-object p$POPUP_ACTION_PARAMETER_REGISTER

    check-cast p$POPUP_ACTION_PARAMETER_REGISTER, ${POPUP_REGISTER_LAYOUT.actionType}
""".trimIndent() }

private const val POPUP_VIEW_PARAMETER_INDEX = 1
private const val POPUP_ACTION_PARAMETER_INDEX = 4
private const val POPUP_DELEGATE_INSTRUCTION_COUNT = 3
