package dev.jason.gboardpatches.patches.gboard.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbi
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.util.WeakHashMap

internal enum class GboardFlagFamilyArgumentShape {
    FLAG_NAME_AND_RESULT,
    RECEIVER_AND_RESULT,
}

internal enum class GboardFlagFamilyFeature(
    val compositionOrder: Int,
    val runtimeCall: RuntimeCallId,
    val argumentShape: GboardFlagFamilyArgumentShape,
) {
    LANGUAGE_MODELS(
        5,
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT,
    ),
    DEVICE_INTELLIGENCE(
        10,
        RuntimeCallId.DEVICE_INTELLIGENCE_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    GRAMMAR_CHECKER(
        20,
        RuntimeCallId.GRAMMAR_CHECKER_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    INLINE_SUGGESTIONS(
        30,
        RuntimeCallId.INLINE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    KEY_SHAPE_SELECTION(
        40,
        RuntimeCallId.KEY_SHAPE_SELECTION_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ACCESS_POINTS_MENU_STYLE(
        100,
        RuntimeCallId.ACCESS_POINTS_MENU_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ADVANCED_VOICE_TYPING(
        200,
        RuntimeCallId.ADVANCED_VOICE_RUNTIME_AFTER_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT,
    ),
    AI_WRITING_TOOLS(
        300,
        RuntimeCallId.AI_WRITING_TOOLS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT,
    ),
    CHANGE_EMOJI_SIZE(
        400,
        RuntimeCallId.EMOJI_SIZE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    CLIPBOARD_CUSTOM_CHARACTER_LIMIT(
        500,
        RuntimeCallId.CLIPBOARD_CONTENT_LIMIT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ENABLE_ACCESSIBILITY_LAYOUT(
        600,
        RuntimeCallId.ACCESSIBILITY_LAYOUT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ENABLE_CURSOR_TRACKPAD_MODE(
        700,
        RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ENABLE_OCR_SCAN_TEXT(
        800,
        RuntimeCallId.OCR_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    USE_BLUETOOTH_MICROPHONE(
        900,
        RuntimeCallId.BLUETOOTH_MICROPHONE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT,
    ),
    CLOSE_PROACTIVE_SUGGESTIONS(
        1000,
        RuntimeCallId.CLOSE_PROACTIVE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    QUICK_INSERT(
        1200,
        RuntimeCallId.QUICK_INSERT_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ACCESS_POINT_COUNT(
        1300,
        RuntimeCallId.ACCESS_POINT_COUNT_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    KEYPRESS_HAPTICS(
        1400,
        RuntimeCallId.KEYPRESS_HAPTICS_RUNTIME_APPLY_FLAG_VALUE,
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT,
    ),
    ;

    val spec: GboardFlagFamilyFeatureSpec
        get() = GboardFlagFamilyFeatureSpec(this, runtimeCall, argumentShape)
}

internal data class GboardFlagFamilyFeatureSpec(
    val feature: GboardFlagFamilyFeature,
    val runtimeCall: RuntimeCallId,
    val argumentShape: GboardFlagFamilyArgumentShape,
)

internal fun interface GboardFlagFamilyTransformation {
    fun apply(method: MutableMethod, selectedFeatures: Collection<GboardFlagFamilyFeatureSpec>)
}

internal val gboardFlagFamilyTransformation = GboardFlagFamilyTransformation { method, specs ->
    if (specs.isEmpty()) return@GboardFlagFamilyTransformation

    val selected = specs.associateBy { spec -> spec.feature }
    check(selected.size == specs.size) { "Duplicate flag-family feature intent" }
    val ordered = selected.values.sortedBy { spec -> spec.feature.compositionOrder }
    check(ordered.zipWithNext().all { (left, right) ->
        left.feature.compositionOrder < right.feature.compositionOrder
    }) { "Duplicate flag-family composition order" }

    val plan = GboardFlagFamilyTransformationPlan.preflight(method, ordered)
    if (plan.state == GboardFlagFamilyTransformationState.PATCHED) {
        return@GboardFlagFamilyTransformation
    }
    plan.apply(method)
    GboardFlagFamilyTransformationPlan.preflight(method, ordered).also { verified ->
        check(verified.state == GboardFlagFamilyTransformationState.PATCHED) {
            "Flag-family transformation did not produce the selected exact call chain"
        }
    }
}

private val gboardFlagFamilyComposerPatch = bytecodePatch(
    description = "Compose selected Gboard flag getter policies in one verified transformation.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute { }

    finalize {
        val selected = GboardFlagFamilyFeatureSelections.take(this)
        if (selected.isEmpty()) return@finalize

        mutableFieldOrThrow(GboardVersionBindings.flagNameField)
        gboardFlagFamilyTransformation.apply(
            findMutableMethodOrThrow(GboardVersionBindings.flagBoolGetter),
            selected,
        )
    }
}

internal fun gboardFlagFamilyFeaturePatch(
    description: String,
    feature: GboardFlagFamilyFeature,
) = bytecodePatch(description = description) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardFlagFamilyComposerPatch)

    execute {
        GboardFlagFamilyFeatureSelections.add(this, feature.spec)
    }
}

internal class GboardFlagFamilySelectionStore<K : Any> {
    private val selectedByContext = WeakHashMap<
        K,
        MutableMap<GboardFlagFamilyFeature, GboardFlagFamilyFeatureSpec>,
    >()

    @Synchronized
    fun add(context: K, spec: GboardFlagFamilyFeatureSpec) {
        val selected = selectedByContext.getOrPut(context) { linkedMapOf() }
        val existing = selected.putIfAbsent(spec.feature, spec)
        check(existing == null || existing == spec) {
            "Conflicting flag-family feature spec for ${spec.feature}"
        }
    }

    @Synchronized
    fun take(context: K): Collection<GboardFlagFamilyFeatureSpec> =
        selectedByContext.remove(context)?.values.orEmpty()
}

private object GboardFlagFamilyFeatureSelections {
    private val store = GboardFlagFamilySelectionStore<BytecodePatchContext>()

    fun add(context: BytecodePatchContext, spec: GboardFlagFamilyFeatureSpec) =
        store.add(context, spec)

    fun take(context: BytecodePatchContext): Collection<GboardFlagFamilyFeatureSpec> =
        store.take(context)
}

private enum class GboardFlagFamilyTransformationState {
    STOCK,
    PATCHED,
}

private data class GboardFlagFamilyTransformationPlan(
    val state: GboardFlagFamilyTransformationState,
    val returns: List<GboardFlagFamilyReturnPlan>,
    val receiverPreservationIndex: Int,
) {
    fun apply(method: MutableMethod) {
        check(state == GboardFlagFamilyTransformationState.STOCK)
        returns.asReversed().forEach { returnPlan ->
            method.addInstructions(returnPlan.returnIndex, returnPlan.delegate)
        }
        method.addInstructions(
            receiverPreservationIndex,
            "move-object v$RECEIVER_SCRATCH_REGISTER, p0",
        )
        method.protectGuardedFlagCalls()
    }

    companion object {
        fun preflight(
            method: MutableMethod,
            selected: List<GboardFlagFamilyFeatureSpec>,
        ): GboardFlagFamilyTransformationPlan {
            check(method.parameterTypes.isEmpty()) {
                "Flag-family composition requires the zero-argument flag getter"
            }
            selected.forEach(GboardFlagFamilyFeatureSpec::verifyRuntimeAbi)

            val instructions = method.implementation?.instructions
                ?: error("No instructions available in the flag getter")
            val receiverRegister = method.implementation!!.registerCount - 1
            check(
                method.implementation!!.registerCount == FORMAL_18_0_3_REGISTER_COUNT &&
                    receiverRegister == FORMAL_18_0_3_RECEIVER_REGISTER
            ) {
                "Unexpected 18.0.3 flag getter register layout: " +
                    "count=${method.implementation!!.registerCount} receiver=v$receiverRegister"
            }

            val returnIndices = method.returnInstructionIndices().filter { index ->
                instructions[index].opcode.name.normalized() == "RETURN_OBJECT"
            }
            check(returnIndices.size == 1) {
                "18.0.3 flag getter must expose exactly one RETURN_OBJECT"
            }
            val resultRegistersByReturn = returnIndices.associateWith { returnIndex ->
                val resultRegister = (instructions[returnIndex] as? OneRegisterInstruction)?.registerA
                    ?: error("RETURN_OBJECT at $returnIndex does not expose registerA")
                check(resultRegister <= MAX_MOVE_RESULT_OBJECT_REGISTER) {
                    "RETURN_OBJECT at $returnIndex cannot be encoded by move-result-object"
                }
                resultRegister
            }
            check(resultRegistersByReturn.values.single() == receiverRegister) {
                "18.0.3 flag getter must materialize its result in p0"
            }
            val needsFlagName = selected.any {
                it.argumentShape == GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT
            }
            val registers = GboardFlagFamilyRegisterPlan(
                receiver = RECEIVER_SCRATCH_REGISTER,
                flagNameScratch = FLAG_NAME_SCRATCH_REGISTER.takeIf { needsFlagName },
            )

            if (GboardVersionBindings.targetVersion == "18.5.4") {
                val snapshots = instructions.filter { instruction ->
                    val load = instruction as? TwoRegisterInstruction
                    val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                    instruction.opcode.name.normalized() == "IGET_OBJECT" &&
                        load?.registerA == 0 && load.registerB == receiverRegister &&
                        field?.definingClass == "Loki;" && field.name == "f" && field.type == "Lokr;"
                }
                check(snapshots.size == 1) { "18.5.4 flag snapshot carrier drift" }
            }

            val resultMaterializationIndex = instructions.indices.singleOrNull { index ->
                instructions[index].isFormal1803ResultMaterialization(receiverRegister)
            } ?: error("Could not resolve exact 18.0.3 nxw#g result materialization")
            check(resultMaterializationIndex < returnIndices.single()) {
                "18.0.3 flag result materialization must precede RETURN_OBJECT"
            }

            // Materialize every instruction string before the first method mutation. In
            // particular, RuntimeCallEmitter validates invoke-35c/range encodability here for
            // every return x selected contribution instead of during the reverse apply loop.
            val returnPlans = returnIndices.map { returnIndex ->
                val resultRegister = resultRegistersByReturn.getValue(returnIndex)
                GboardFlagFamilyReturnPlan(
                    returnIndex = returnIndex,
                    delegate = buildFlagFamilyDelegate(selected, resultRegister, registers),
                )
            }

            val receiverMoves = instructions.indices.filter { index ->
                instructions[index].isReceiverPreservationMove(receiverRegister)
            }
            val familyCallIndices = instructions.indices.filter { index ->
                instructions[index].isFlagFamilyRuntimeReference()
            }

            val state = when {
                receiverMoves.isEmpty() && familyCallIndices.isEmpty() ->
                    GboardFlagFamilyTransformationState.STOCK.also {
                        instructions.requireFormal1803ScratchLiveness(
                            resultMaterializationIndex = resultMaterializationIndex,
                            preservationIndex = null,
                        )
                    }
                receiverMoves == listOf(resultMaterializationIndex - 1) &&
                    instructions.hasExactSelectedCallChains(
                        returnIndices = returnIndices,
                        resultRegistersByReturn = resultRegistersByReturn,
                        selected = selected,
                        familyCallIndices = familyCallIndices,
                        registers = registers,
                        resultMaterializationIndex = resultMaterializationIndex,
                    ) && method.hasGuardedFlagCalls() -> GboardFlagFamilyTransformationState.PATCHED
                else -> error(
                    "Malformed or extraneous flag-family transformation before mutation: " +
                        "receiverMoves=$receiverMoves, familyCalls=$familyCallIndices, " +
                        "returns=$returnIndices",
                )
            }

            return GboardFlagFamilyTransformationPlan(
                state = state,
                returns = returnPlans,
                receiverPreservationIndex = resultMaterializationIndex,
            )
        }
    }
}

private data class GboardFlagFamilyReturnPlan(
    val returnIndex: Int,
    val delegate: String,
)

/** Ignore an extension linkage/initialization failure without clobbering the three live registers. */
internal fun MutableMethod.protectGuardedFlagCalls() {
    val code = checkNotNull(implementation)
    val calls = code.instructions.indices.filter { index ->
        code.instructions[index].runtimeCallId() in GUARDED_FLAG_FAMILY_CALLS
    }
    calls.forEach { index ->
        check(code.instructions[index + 1].opcode.name.normalized() == "MOVE_RESULT_OBJECT")
        val resume = code.newLabelForIndex(index + 2)
        code.addCatch("Ljava/lang/Throwable;", code.newLabelForIndex(index), resume, resume)
    }
}

private fun MutableMethod.hasGuardedFlagCalls(): Boolean {
    val code = checkNotNull(implementation)
    val instructions = code.instructions
    return instructions.indices.filter { index ->
        instructions[index].runtimeCallId() in GUARDED_FLAG_FAMILY_CALLS
    }.all { index ->
        val start = instructions.take(index).sumOf { it.codeUnits }
        val end = start + instructions.drop(index).take(2).sumOf { it.codeUnits }
        code.tryBlocks.count { block ->
            block.startCodeAddress == start && block.codeUnitCount == end - start &&
                block.exceptionHandlers.singleOrNull()?.let { handler ->
                    handler.exceptionType == "Ljava/lang/Throwable;" && handler.handlerCodeAddress == end
                } == true
        } == 1
    }
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .isReceiverPreservationMove(receiverRegister: Int): Boolean {
    return opcode.name.normalized() in setOf("MOVE_OBJECT", "MOVE_OBJECT_FROM16") &&
        this is TwoRegisterInstruction &&
        registerA == RECEIVER_SCRATCH_REGISTER &&
        registerB == receiverRegister
}

private fun GboardFlagFamilyFeatureSpec.verifyRuntimeAbi() {
    val abi = RuntimeAbiCatalog.abi(runtimeCall)
    val expectedParameters = when (argumentShape) {
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT ->
            listOf("Ljava/lang/String;", "Ljava/lang/Object;")
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT ->
            listOf("Ljava/lang/Object;", "Ljava/lang/Object;")
    }
    check(abi.parameters == expectedParameters && abi.returnType == "Ljava/lang/Object;") {
        "Flag-family Runtime ABI shape mismatch for $runtimeCall: ${abi.reference}"
    }
}

private fun GboardFlagFamilyFeatureSpec.delegate(
    resultRegister: Int,
    registers: GboardFlagFamilyRegisterPlan,
): String {
    val firstArgument = when (argumentShape) {
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT ->
            "v${checkNotNull(registers.flagNameScratch)}"
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT -> "v${registers.receiver}"
    }
    return emitFlagFamilyRuntimeCall(runtimeCall, "$firstArgument, v$resultRegister") +
        "\n\nmove-result-object v$resultRegister"
}

private fun buildFlagFamilyDelegate(
    selected: List<GboardFlagFamilyFeatureSpec>,
    resultRegister: Int,
    registers: GboardFlagFamilyRegisterPlan,
): String = buildString {
    registers.flagNameScratch?.let { scratch ->
        append("iget-object v$scratch, v${registers.receiver}, ")
        append(GboardVersionBindings.flagNameField.reference)
        append("\n\n")
    }
    append(selected.joinToString("\n\n") { spec -> spec.delegate(resultRegister, registers) })
}

private fun List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>
    .hasExactSelectedCallChains(
        returnIndices: List<Int>,
        resultRegistersByReturn: Map<Int, Int>,
        selected: List<GboardFlagFamilyFeatureSpec>,
        familyCallIndices: List<Int>,
        registers: GboardFlagFamilyRegisterPlan,
        resultMaterializationIndex: Int,
    ): Boolean {
    val callsBySegment = returnIndices.mapIndexed { ordinal, returnIndex ->
        val segmentStart = if (ordinal == 0) 0 else returnIndices[ordinal - 1] + 1
        familyCallIndices.filter { index -> index in segmentStart until returnIndex }
    }
    if (familyCallIndices.any { index -> index > returnIndices.last() }) return false
    if (callsBySegment.flatten() != familyCallIndices) return false

    val flagLoads = indices.filter { index -> get(index).isExactFlagNameLoad(registers) }
    val expectedFlagLoads = if (registers.flagNameScratch == null) {
        emptyList()
    } else {
        listOf(resultMaterializationIndex + 1)
    }
    if (flagLoads != expectedFlagLoads) return false

    requireFormal1803ScratchLiveness(
        resultMaterializationIndex = resultMaterializationIndex,
        preservationIndex = resultMaterializationIndex - 1,
    )

    return callsBySegment.zip(returnIndices).all { (callIndices, returnIndex) ->
        if (callIndices.size != selected.size) return@all false
        val resultRegister = resultRegistersByReturn.getValue(returnIndex)
        callIndices.zip(selected).all { (invokeIndex, spec) ->
            hasExactSelectedDelegate(invokeIndex, resultRegister, spec, registers)
        }
    }
}

private fun List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>
    .hasExactSelectedDelegate(
        invokeIndex: Int,
        resultRegister: Int,
        spec: GboardFlagFamilyFeatureSpec,
        registers: GboardFlagFamilyRegisterPlan,
    ): Boolean {
    val invokeInstruction = getOrNull(invokeIndex) ?: return false
    val invoke = invokeInstruction as? FiveRegisterInstruction ?: return false
    val expectedFirstArgument = when (spec.argumentShape) {
        GboardFlagFamilyArgumentShape.FLAG_NAME_AND_RESULT ->
            checkNotNull(registers.flagNameScratch)
        GboardFlagFamilyArgumentShape.RECEIVER_AND_RESULT -> registers.receiver
    }
    if (
        invokeInstruction.opcode.name.normalized() != "INVOKE_STATIC" ||
        invoke.registerCount != 2 ||
        invoke.registerC != expectedFirstArgument ||
        invoke.registerD != resultRegister ||
        invokeInstruction.runtimeCallId() != spec.runtimeCall
    ) {
        return false
    }

    val moveResult = getOrNull(invokeIndex + 1) as? OneRegisterInstruction ?: return false
    if (
        moveResult.opcode.name.normalized() != "MOVE_RESULT_OBJECT" ||
        moveResult.registerA != resultRegister
    ) {
        return false
    }

    return true
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .isFormal1803ResultMaterialization(receiverRegister: Int): Boolean {
    val load = this as? TwoRegisterInstruction ?: return false
    val field = (load as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return opcode.name.normalized() == "IGET_OBJECT" &&
        load.registerA == receiverRegister &&
        load.registerB == FLAG_NAME_SCRATCH_REGISTER &&
        field.definingClass == when (GboardVersionBindings.targetVersion) {
            "18.0.3" -> "Lnyf;"
            "18.5.4" -> "Lokr;"
            else -> error("Unsupported Gboard flag stock contract for ${GboardVersionBindings.targetVersion}")
        } && field.name == when (GboardVersionBindings.targetVersion) {
            "18.5.4" -> "a"
            else -> "c"
        } &&
        field.type == "Ljava/lang/Object;"
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .isExactFlagNameLoad(registers: GboardFlagFamilyRegisterPlan): Boolean {
    val scratch = registers.flagNameScratch ?: return false
    val load = this as? TwoRegisterInstruction ?: return false
    val field = (load as? ReferenceInstruction)?.reference as? FieldReference ?: return false
    return opcode.name.normalized() == "IGET_OBJECT" &&
        load.registerA == scratch && load.registerB == registers.receiver &&
        GboardVersionBindings.flagNameField.matches(field)
}

private fun List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>
    .requireFormal1803ScratchLiveness(
        resultMaterializationIndex: Int,
        preservationIndex: Int?,
    ) {
    val receiverWrites = (0 until resultMaterializationIndex).filter { index ->
        (get(index) as? OneRegisterInstruction)?.registerA == RECEIVER_SCRATCH_REGISTER
    }
    check(receiverWrites == listOfNotNull(preservationIndex)) {
        "18.0.3 receiver scratch v$RECEIVER_SCRATCH_REGISTER liveness drift: $receiverWrites"
    }
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.runtimeCallId():
    RuntimeCallId? {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return null
    return ALL_FLAG_FAMILY_RUNTIME_CALLS.firstOrNull { call ->
        reference.matches(RuntimeAbiCatalog.abi(call))
    }
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction
    .isFlagFamilyRuntimeReference(): Boolean {
    val reference = (this as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return ALL_FLAG_FAMILY_RUNTIME_CALLS.any { call ->
        val abi = RuntimeAbiCatalog.abi(call)
        reference.definingClass == abi.owner && reference.name == abi.name
    }
}

private fun MethodReference.matches(abi: RuntimeAbi): Boolean =
    definingClass == abi.owner &&
        name == abi.name &&
        parameterTypes == abi.parameters &&
        returnType == abi.returnType

private fun String.normalized(): String = uppercase().replace('-', '_').replace('/', '_')

private fun emitFlagFamilyRuntimeCall(call: RuntimeCallId, registers: String): String = when (call) {
    RuntimeCallId.KEYPRESS_HAPTICS_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.KEYPRESS_HAPTICS_RUNTIME_APPLY_FLAG_VALUE, registers,
    )
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.LANGUAGE_MODELS_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.ACCESS_POINT_COUNT_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.ACCESS_POINT_COUNT_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.ACCESSIBILITY_LAYOUT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.ACCESSIBILITY_LAYOUT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.ACCESS_POINTS_MENU_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.ACCESS_POINTS_MENU_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.ADVANCED_VOICE_RUNTIME_AFTER_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.ADVANCED_VOICE_RUNTIME_AFTER_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.AI_WRITING_TOOLS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.AI_WRITING_TOOLS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.BLUETOOTH_MICROPHONE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.BLUETOOTH_MICROPHONE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.CLIPBOARD_CONTENT_LIMIT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.CLIPBOARD_CONTENT_LIMIT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.CLOSE_PROACTIVE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.CLOSE_PROACTIVE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE ->
        RuntimeCallEmitter.invoke(
            RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
            registers,
        )
    RuntimeCallId.DEVICE_INTELLIGENCE_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.DEVICE_INTELLIGENCE_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.EMOJI_SIZE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.EMOJI_SIZE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.FEATURE_FLAGS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.FEATURE_FLAGS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.GRAMMAR_CHECKER_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.GRAMMAR_CHECKER_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.INLINE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.INLINE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.KEY_SHAPE_SELECTION_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.KEY_SHAPE_SELECTION_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.OCR_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.OCR_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    RuntimeCallId.QUICK_INSERT_RUNTIME_APPLY_FLAG_VALUE -> RuntimeCallEmitter.invoke(
        RuntimeCallId.QUICK_INSERT_RUNTIME_APPLY_FLAG_VALUE,
        registers,
    )
    else -> error("Runtime call $call does not belong to the flag family")
}

private data class GboardFlagFamilyRegisterPlan(
    val receiver: Int,
    val flagNameScratch: Int?,
)

private const val FLAG_NAME_SCRATCH_REGISTER = 0
private const val RECEIVER_SCRATCH_REGISTER = 1
private const val FORMAL_18_0_3_RECEIVER_REGISTER = 2
private const val FORMAL_18_0_3_REGISTER_COUNT = 3
private const val MAX_MOVE_RESULT_OBJECT_REGISTER = 255

private val GUARDED_FLAG_FAMILY_CALLS = setOf(
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.KEYPRESS_HAPTICS_RUNTIME_APPLY_FLAG_VALUE,
)

private val ALL_FLAG_FAMILY_RUNTIME_CALLS: Set<RuntimeCallId> = setOf(
    RuntimeCallId.KEYPRESS_HAPTICS_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.LANGUAGE_MODELS_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.ACCESS_POINT_COUNT_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.ACCESSIBILITY_LAYOUT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.ACCESS_POINTS_MENU_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.ADVANCED_VOICE_RUNTIME_AFTER_FLAG_VALUE,
    RuntimeCallId.AI_WRITING_TOOLS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.BLUETOOTH_MICROPHONE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.CLIPBOARD_CONTENT_LIMIT_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.CLOSE_PROACTIVE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.CURSOR_TRACKPAD_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.DEVICE_INTELLIGENCE_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.EMOJI_SIZE_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.FEATURE_FLAGS_RUNTIME_APPLY_OVERRIDDEN_FLAG_VALUE,
    RuntimeCallId.GRAMMAR_CHECKER_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.INLINE_SUGGESTIONS_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.KEY_SHAPE_SELECTION_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.OCR_RUNTIME_APPLY_FLAG_VALUE,
    RuntimeCallId.QUICK_INSERT_RUNTIME_APPLY_FLAG_VALUE,
)
