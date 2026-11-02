package dev.jason.gboardpatches.patches.gboard.shared

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableField
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardVersionBindings
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import java.util.WeakHashMap

internal fun interface GboardPointerOwnerTransformation {
    fun apply(
        ownerClass: MutableClass,
        selectedFeatures: Collection<GboardPointerOwnerFeatureSpec>,
    )
}

internal enum class GboardPointerOwnerFeature {
    ENGLISH_QWERTY,
    LONG_PRESS_QUICK_ACTIONS,
    TOP_ROW_SWIPE,
    ZHUYIN_SLIDE,
}

internal data class GboardPointerOwnerFeatureSpec(
    val feature: GboardPointerOwnerFeature,
    val transformation: GboardPointerOwnerTransformationAdapter,
)

internal val GBOARD_POINTER_OWNER_COMPOSITION_ORDER = listOf(
    GboardPointerOwnerFeature.ENGLISH_QWERTY,
    GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS,
    GboardPointerOwnerFeature.TOP_ROW_SWIPE,
    GboardPointerOwnerFeature.ZHUYIN_SLIDE,
)

internal fun interface GboardPointerOwnerTransformationAdapter {
    fun apply(context: GboardPointerOwnerTransformationContext)
}

internal val gboardPointerOwnerTransformation = GboardPointerOwnerTransformation { ownerClass, specs ->
    if (specs.isEmpty()) return@GboardPointerOwnerTransformation
    val selected = specs.validatePointerOwnerSpecs()
    val plan = GboardPointerOwnerTransformationPlan.preflight(ownerClass, selected)
    plan.commit()
}

private data class GboardPointerOwnerTransformationPlan(
    val ownerClass: MutableClass,
    val committedFields: List<MutableField>?,
    val committedMethods: List<MutableMethod>?,
) {
    fun commit() {
        val fields = committedFields ?: return
        val methods = checkNotNull(committedMethods)
        ownerClass.fields.clear()
        ownerClass.fields.addAll(fields)
        ownerClass.methods.clear()
        ownerClass.methods.addAll(methods)
    }

    companion object {
        fun preflight(
            ownerClass: MutableClass,
            selected: Map<GboardPointerOwnerFeature, GboardPointerOwnerFeatureSpec>,
        ): GboardPointerOwnerTransformationPlan {
            ownerClass.validatePointerOwnerBindings(selected.keys)
            selected.keys.verifyPointerOwnerRuntimeAbis()
            val live = GboardPointerOwnerTransformationContext.from(ownerClass)
            if (live.hasAnyTransformationState()) {
                live.verifyCompleteTransformation(selected.keys)
                live.verifyExactNormalizedLifecycleState(selected.keys)
                return GboardPointerOwnerTransformationPlan(ownerClass, null, null)
            }
            live.pointerOwnerMethod.requireSupportedPointerOwnerStock()
            live.requireExactSelectedLifecycleStock(selected.keys)

            val candidate = MutableClass(ownerClass)
            val candidateContext = GboardPointerOwnerTransformationContext.from(candidate)
            GBOARD_POINTER_OWNER_COMPOSITION_ORDER.forEach { feature ->
                selected[feature]?.transformation?.apply(candidateContext)
            }
            candidateContext.verifyCompleteTransformation(selected.keys)
            candidateContext.verifyExactNormalizedLifecycleState(selected.keys)

            return GboardPointerOwnerTransformationPlan(
                ownerClass = ownerClass,
                committedFields = candidate.fields.toList(),
                committedMethods = candidate.methods.toList(),
            )
        }
    }
}

private fun Collection<GboardPointerOwnerFeatureSpec>.validatePointerOwnerSpecs():
    Map<GboardPointerOwnerFeature, GboardPointerOwnerFeatureSpec> {
    check(
        GBOARD_POINTER_OWNER_COMPOSITION_ORDER.size ==
            GBOARD_POINTER_OWNER_COMPOSITION_ORDER.distinct().size &&
            GBOARD_POINTER_OWNER_COMPOSITION_ORDER.toSet() ==
            GboardPointerOwnerFeature.entries.toSet()
    ) { "Invalid canonical pointer-owner composition order" }
    val grouped = groupBy(GboardPointerOwnerFeatureSpec::feature)
    grouped.forEach { (feature, matching) ->
        check(matching.map(GboardPointerOwnerFeatureSpec::transformation).distinct().size == 1) {
            "Conflicting pointer-owner feature spec for $feature"
        }
        check(matching.size == 1) { "Duplicate pointer-owner feature intent for $feature" }
    }
    return GBOARD_POINTER_OWNER_COMPOSITION_ORDER.mapNotNull { feature ->
        grouped[feature]?.singleOrNull()?.let { spec -> feature to spec }
    }.toMap(LinkedHashMap())
}

private fun MutableMethod.requireSupportedPointerOwnerStock() {
    check(GboardVersionBindings.targetVersion in setOf("18.0.3", "18.5.4")) {
        "Unsupported Gboard pointer-owner stock contract for ${GboardVersionBindings.targetVersion}"
    }
    requireExact1803PointerOwnerStockShape()
}

private fun MutableMethod.requireExact1803PointerOwnerStockShape() {
    val methodImplementation = implementation
        ?: error("No instructions in formal 18.0.3 pointer-owner target")
    val instructions = methodImplementation.instructions
    val finishIndex = indexOfFirstMethodCall(
        GboardVersionBindings.pointerFinish.ownerDescriptor,
        GboardVersionBindings.pointerFinish.name,
        GboardVersionBindings.pointerFinish.returnType,
        GboardVersionBindings.pointerFinish.parameterTypes,
    )
    val preResetIndex = indexOfFirstMethodCall(
        GboardVersionBindings.pointerPreReset.ownerDescriptor,
        GboardVersionBindings.pointerPreReset.name,
        GboardVersionBindings.pointerPreReset.returnType,
        GboardVersionBindings.pointerPreReset.parameterTypes,
    )
    check(
        accessFlags == AccessFlags.FINAL.value &&
            methodImplementation.registerCount == GboardPointerOwnerRegisterContract.stockRegisterCount &&
            finishIndex >= 0 && preResetIndex == finishIndex + 1 &&
            returnInstructionIndices().isNotEmpty() &&
            instructions.count { instruction ->
                instruction.methodReference()?.let(GboardVersionBindings.pointerFinish::matches) == true
            } == 1 &&
            instructions.count { instruction ->
                instruction.methodReference()?.let(GboardVersionBindings.pointerPreReset::matches) == true
            } == 1
    ) {
        "18.0.3 pointer-owner semantic shape drift in $definingClass->$name: " +
            "finish=$finishIndex preReset=$preResetIndex registers=${methodImplementation.registerCount}"
    }
}

private fun GboardPointerOwnerTransformationContext.requireExactSelectedLifecycleStock(
    selected: Set<GboardPointerOwnerFeature>,
) {
    if (
        GboardPointerOwnerFeature.TOP_ROW_SWIPE in selected ||
        GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected
    ) {
        pointerFinishMethod.requireSupportedPointerLifecycleStock(PointerLifecycleRole.FINISH)
    }
    if (selected.hasPointerCleanupFeature()) {
        pointerCancelMethod.requireSupportedPointerLifecycleStock(PointerLifecycleRole.CANCEL)
        pointerResetMethod.requireSupportedPointerLifecycleStock(PointerLifecycleRole.RESET)
    }
}

private enum class PointerLifecycleRole(val label: String) {
    FINISH("pointer finish"),
    CANCEL("pointer cancel"),
    RESET("pointer reset"),
}

private fun MutableMethod.requireSupportedPointerLifecycleStock(
    role: PointerLifecycleRole,
) {
    check(GboardVersionBindings.targetVersion in setOf("18.0.3", "18.5.4")) {
        "Unsupported Gboard ${role.label} stock contract for ${GboardVersionBindings.targetVersion}"
    }
    requireExact1803PointerLifecycleShape(role)
}

private fun MutableMethod.requireExact1803PointerLifecycleShape(role: PointerLifecycleRole) {
    val instructions = implementation?.instructions
        ?: error("No instructions in formal 18.0.3 ${role.label} target")
    check(accessFlags == (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value)) {
        "18.0.3 ${role.label} access drift in $definingClass->$name"
    }
    when (role) {
        PointerLifecycleRole.FINISH -> {
            val dispatches = instructions.mapNotNull { it.methodReference() }.count { reference ->
                reference.definingClass == when (GboardVersionBindings.targetVersion) {
                    "18.5.4" -> "Lqio;"
                    else -> "Lpvj;"
                } && reference.name == "f" &&
                    reference.parameterTypes == GboardVersionBindings.gestureDispatch.parameterTypes &&
                    reference.returnType == GboardVersionBindings.gestureDispatch.returnType
            }
            check(parameterTypes == listOf("J", "I") && dispatches == 1) {
                "18.0.3 pointer finish dispatch shape drift in $definingClass->$name"
            }
        }
        PointerLifecycleRole.CANCEL -> {
            check(parameterTypes == listOf("J") && returnInstructionIndices().isNotEmpty()) {
                "Pointer cancel shape drift in $definingClass->$name"
            }
            if (GboardVersionBindings.targetVersion == "18.5.4") {
                val first = instructions.firstOrNull() as? TwoRegisterInstruction
                check(implementation!!.registerCount == 4 && instructions.size == 8 &&
                    instructions[0].opcode == Opcode.IGET_OBJECT &&
                    first?.registerA == 0 && first.registerB == 1 &&
                    instructions[0].isFieldReference("Lqin;->q:Lqsl;") &&
                    instructions[1].opcode == Opcode.IF_EQZ &&
                    instructions[2].isMethodReference("Lqsl;->a(J)V") &&
                    instructions[3].isFieldReference("Lqin;->r:Lqio;") &&
                    instructions[4].isMethodReference("Lqio;->k()V") &&
                    instructions[5].isLiteralWrite(2, 0) &&
                    instructions[6].isFieldReference("Lqin;->O:Z") &&
                    instructions[7].opcode == Opcode.RETURN_VOID) {
                    "18.5.4 cancel timestamp/delegate/reset sequence drift"
                }
            }
        }
        PointerLifecycleRole.RESET -> {
            val cancelIndex = instructions.indexOfFirst { instruction ->
                instruction.methodReference()?.let(GboardVersionBindings.pointerCancel::matches) == true
            }
            val preResetIndex = instructions.indexOfFirst { instruction ->
                instruction.methodReference()?.let(GboardVersionBindings.pointerPreReset::matches) == true
            }
            check(
                parameterTypes.isEmpty() && cancelIndex >= 0 &&
                    preResetIndex == cancelIndex + 1 &&
                    instructions.lastOrNull()?.opcode == Opcode.RETURN_VOID
            ) { "18.0.3 pointer reset shape drift in $definingClass->$name" }
        }
    }
}

private fun Set<GboardPointerOwnerFeature>.hasPointerCleanupFeature(): Boolean =
    any { feature ->
        feature == GboardPointerOwnerFeature.ENGLISH_QWERTY ||
            feature == GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS ||
            feature == GboardPointerOwnerFeature.TOP_ROW_SWIPE ||
            feature == GboardPointerOwnerFeature.ZHUYIN_SLIDE
    }

private fun MutableClass.validatePointerOwnerBindings(
    selected: Set<GboardPointerOwnerFeature>,
) {
    val owner = resolveExactPointerMethod(
        GboardVersionBindings.pointerOwner,
        expectedAccessFlags = AccessFlags.FINAL.value,
    )
    check(owner.implementation?.registerCount in setOf(
        GboardPointerOwnerRegisterContract.stockRegisterCount,
        GboardPointerOwnerRegisterContract.expandedRegisterCount,
    )) { "Unexpected pointer-owner register contract" }
    check(
        GboardVersionBindings.pointerOwner.parameterTypes.firstOrNull() ==
            GboardVersionBindings.softKeyViewType.descriptor
    ) { "Pointer owner SoftKeyView binding relation drift" }

    val needsLifecycle = selected.isNotEmpty()
    if (!needsLifecycle) return
    resolveExactPointerMethod(
        GboardVersionBindings.pointerFinish,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
    )
    resolveExactPointerMethod(
        GboardVersionBindings.pointerPreReset,
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
    )
    resolveExactPointerMethod(
        GboardVersionBindings.pointerCancel,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
    )
    resolveExactPointerMethod(
        GboardVersionBindings.pointerReset,
        AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
    )
}

private fun MutableClass.resolveExactPointerMethod(
    binding: GboardMethodTarget,
    expectedAccessFlags: Int,
): MutableMethod {
    val exact = methods.filter(binding::matches)
    check(exact.size <= 1) { "Ambiguous pointer binding ${binding.reference}" }
    val target = exact.singleOrNull()
    if (target == null) {
        val nearMisses = methods.filter { method ->
            method.definingClass == binding.ownerDescriptor && method.name == binding.name
        }
        check(nearMisses.isEmpty()) {
            "Pointer binding prototype drift: expected ${binding.reference}"
        }
        error("Could not find ${binding.reference}")
    }
    check(target.accessFlags == expectedAccessFlags && target.implementation != null) {
        "Pointer binding access/implementation drift: ${binding.reference}"
    }
    return target
}

private fun Set<GboardPointerOwnerFeature>.verifyPointerOwnerRuntimeAbis() {
    val calls = flatMapTo(linkedSetOf()) { feature ->
        POINTER_RUNTIME_CALLS_BY_FEATURE.getValue(feature)
    }
    calls.forEach { expected ->
        val actual = RuntimeAbiCatalog.abi(expected.call)
        check(actual.parameters == expected.parameters && actual.returnType == expected.returnType) {
            "Pointer Runtime ABI drift for ${expected.call}: ${actual.reference}"
        }
    }
}

private data class PointerRuntimeAbiExpectation(
    val call: RuntimeCallId,
    val parameters: List<String>,
    val returnType: String,
)

private fun pointerRuntimeAbi(
    call: RuntimeCallId,
    parameters: List<String>,
    returnType: String,
) = PointerRuntimeAbiExpectation(call, parameters, returnType)

private val POINTER_RUNTIME_CALLS_BY_FEATURE = mapOf(
    GboardPointerOwnerFeature.ENGLISH_QWERTY to listOf(
        pointerRuntimeAbi(
            RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_ENABLED,
            emptyList(),
            "Z",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.ENGLISH_UPPERCASE_TOGGLE_RUNTIME_IS_PATCHED_METADATA,
            listOf("Ljava/lang/Object;"),
            "Z",
        ),
    ),
    GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS to listOf(
        pointerRuntimeAbi(
            RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_ENSURE_LONG_PRESS_SCHEDULED,
            listOf("Ljava/lang/Object;", "Landroid/view/View;"),
            "V",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_FINISH,
            listOf("Ljava/lang/Object;"),
            "V",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_CANCEL,
            listOf("Ljava/lang/Object;"),
            "V",
        ),
    ),
    GboardPointerOwnerFeature.TOP_ROW_SWIPE to listOf(
        pointerRuntimeAbi(
            RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_MAYBE_ARM_AND_RESOLVE_TOP_ROW_OWNER,
            listOf(
                "Ljava/lang/Object;",
                "Ljava/lang/Object;",
                "Ljava/lang/Object;",
                "F",
                "F",
            ),
            "Ljava/lang/Object;",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_FINISH_SWIPE_SESSION,
            listOf("Ljava/lang/Object;"),
            "V",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_CLEAR_SWIPE_SESSION,
            listOf("Ljava/lang/Object;"),
            "V",
        ),
    ),
    GboardPointerOwnerFeature.ZHUYIN_SLIDE to listOf(
        pointerRuntimeAbi(
            RuntimeCallId.ZHUYIN_SLIDE_RUNTIME_MAYBE_CAPTURE_AND_SHOULD_SUPPRESS_RETARGET,
            listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "F", "F"),
            "Z",
        ),
        pointerRuntimeAbi(
            RuntimeCallId.ZHUYIN_SLIDE_RUNTIME_CLEAR_POINTER_STATE,
            listOf("Ljava/lang/Object;"),
            "V",
        ),
    ),
)

private val gboardPointerOwnerComposerPatch = bytecodePatch(
    description = "Compose the admitted Gboard pointer-owner transformations in a verified order.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute { }

    finalize {
        val selected = GboardPointerOwnerFeatureSelections.take(this)
        if (selected.isEmpty()) return@finalize
        gboardPointerOwnerTransformation.apply(
            GboardVersionBindings.pointerOwner.ownerClass(this),
            selected,
        )
    }
}

internal fun gboardPointerOwnerFeaturePatch(
    description: String,
    spec: GboardPointerOwnerFeatureSpec,
) = bytecodePatch(description = description) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPointerOwnerComposerPatch)

    execute {
        GboardPointerOwnerFeatureSelections.add(this, spec)
    }
}

internal class GboardPointerOwnerFeatureSelectionStore<K : Any> {
    private val selectedByContext = WeakHashMap<
        K,
        MutableMap<GboardPointerOwnerFeature, GboardPointerOwnerFeatureSpec>
    >()

    @Synchronized
    fun add(context: K, spec: GboardPointerOwnerFeatureSpec) {
        val selected = selectedByContext.getOrPut(context) { linkedMapOf() }
        val existing = selected.putIfAbsent(spec.feature, spec)
        check(existing == null || existing == spec) {
            "Conflicting pointer-owner feature spec for ${spec.feature}"
        }
    }

    @Synchronized
    fun take(context: K): Collection<GboardPointerOwnerFeatureSpec> =
        selectedByContext.remove(context)?.values.orEmpty()
}

private object GboardPointerOwnerFeatureSelections {
    private val store = GboardPointerOwnerFeatureSelectionStore<BytecodePatchContext>()

    fun add(context: BytecodePatchContext, spec: GboardPointerOwnerFeatureSpec) =
        store.add(context, spec)

    fun take(context: BytecodePatchContext): Collection<GboardPointerOwnerFeatureSpec> =
        store.take(context)
}

internal class GboardPointerOwnerTransformationContext private constructor(
    val ownerClass: MutableClass,
    pointerOwnerMethod: MutableMethod,
) {
    var pointerOwnerMethod: MutableMethod = pointerOwnerMethod
        private set

    val pointerFinishMethod: MutableMethod by lazy {
        GboardVersionBindings.pointerFinish.resolve(ownerClass)
    }
    val pointerCancelMethod: MutableMethod by lazy {
        GboardVersionBindings.pointerCancel.resolve(ownerClass)
    }
    val pointerResetMethod: MutableMethod by lazy {
        GboardVersionBindings.pointerReset.resolve(ownerClass)
    }

    fun replacePointerOwnerMethod(replacement: MutableMethod) {
        if (replacement === pointerOwnerMethod) return
        check(ownerClass.methods.remove(pointerOwnerMethod) && ownerClass.methods.add(replacement)) {
            "Could not replace expanded pointer owner method"
        }
        pointerOwnerMethod = replacement
    }

    companion object {
        fun from(ownerClass: MutableClass): GboardPointerOwnerTransformationContext =
            GboardPointerOwnerTransformationContext(
                ownerClass = ownerClass,
                pointerOwnerMethod = GboardVersionBindings.pointerOwner.resolve(ownerClass),
            )
    }
}

private fun GboardPointerOwnerTransformationContext.hasAnyTransformationState(): Boolean =
    ownerClass.fields.any { field -> field.name == ENGLISH_ANCHOR_FIELD_NAME } ||
        ownerClass.methods.any { method -> method.name in ENGLISH_HELPER_METHOD_NAMES } ||
        ownerClass.methods.any { method ->
            ALL_TRANSFORMATION_CALLS.any { descriptor -> method.countCalls(descriptor) > 0 }
        }

private fun GboardPointerOwnerTransformationContext.verifyCompleteTransformation(
    selected: Set<GboardPointerOwnerFeature>,
) {
    val hasLongPress = GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected
    val expectedRegisterCount = if (hasLongPress) {
        GboardPointerOwnerRegisterContract.expandedRegisterCount
    } else {
        GboardPointerOwnerRegisterContract.stockRegisterCount
    }
    check(pointerOwnerMethod.implementation?.registerCount == expectedRegisterCount) {
        "Incomplete Gboard pointer-owner transformation: unexpected register count"
    }
    val hasEnglish = GboardPointerOwnerFeature.ENGLISH_QWERTY in selected
    val expectedEnglishMemberCount = if (hasEnglish) 1 else 0
    check(ownerClass.fields.count { field -> field.name == ENGLISH_ANCHOR_FIELD_NAME } ==
        expectedEnglishMemberCount &&
        ownerClass.fields.count { field ->
            field.name == ENGLISH_ANCHOR_FIELD_NAME &&
                field.type == SOFT_KEY_VIEW_TYPE &&
                field.accessFlags == AccessFlags.PRIVATE.value
        } == expectedEnglishMemberCount) {
        "Incomplete Gboard pointer-owner transformation: English anchor field"
    }
    ENGLISH_HELPER_METHODS.forEach { helper ->
        val matching = ownerClass.methods.filter { method ->
            method.name == helper.name &&
                method.parameterTypes == helper.parameterTypes &&
                method.returnType == helper.returnType
        }
        check(ownerClass.methods.count { method -> method.name == helper.name } ==
            expectedEnglishMemberCount && matching.size == expectedEnglishMemberCount) {
            "Incomplete Gboard pointer-owner transformation: English helper ${helper.name}"
        }
        if (hasEnglish) {
            val method = matching.single()
            check(method.accessFlags == helper.accessFlags) {
                "Malformed Gboard English helper access flags: ${helper.name}"
            }
            val actual = method.gboardStructuralFingerprint()
            check(actual == helper.fingerprint) {
                "Malformed Gboard English helper implementation: ${helper.name}: " +
                    "$actual != ${helper.fingerprint}"
            }
        }
    }
    verifyPointerOwnerCalls(selected)
    if (hasLongPress) verifyLongPressEntryCopies()
    verifyCleanupCalls(selected)
    pointerOwnerMethod.requireExactPatchedPointerOwnerFingerprint()
}

private fun MutableMethod.requireExactPatchedPointerOwnerFingerprint() {
    check(GboardVersionBindings.targetVersion in setOf("18.0.3", "18.5.4")) {
        "Unsupported patched pointer-owner contract for ${GboardVersionBindings.targetVersion}"
    }
    // Exact descriptors, semantic stock checks, selected call order, and
    // register invariants are verified before this point.
}

private fun GboardPointerOwnerTransformationContext.verifyLongPressEntryCopies() {
    val instructions = pointerOwnerMethod.implementation!!.instructions
    val expected = listOf(
        Triple(Opcode.MOVE_OBJECT_FROM16, 14, 21),
        Triple(Opcode.MOVE_OBJECT_FROM16, 15, 22),
        Triple(Opcode.MOVE_FROM16, 16, 23),
        Triple(Opcode.MOVE_FROM16, 17, 24),
        Triple(Opcode.MOVE_WIDE_FROM16, 18, 25),
        Triple(Opcode.MOVE_FROM16, 20, 27),
    )
    check(expected.withIndex().all { (index, copy) ->
        val instruction = instructions.getOrNull(index) as? TwoRegisterInstruction
            ?: return@all false
        instruction.opcode == copy.first &&
            instruction.registerA == copy.second &&
            instruction.registerB == copy.third
    }) { "Malformed Gboard Long-press pointer-owner entry copies" }
}

private fun GboardPointerOwnerTransformationContext.verifyPointerOwnerCalls(
    selected: Set<GboardPointerOwnerFeature>,
) {
    val ownerCalls = pointerOwnerMethod.transformationCallIndices()
    val expectedCallCounts = mapOf(
        ENGLISH_OWNER_CALL to if (GboardPointerOwnerFeature.ENGLISH_QWERTY in selected) 1 else 0,
        LONG_PRESS_OWNER_CALL to if (GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected) {
            1 + if (GboardPointerOwnerFeature.ENGLISH_QWERTY in selected) 1 else 0
        } else 0,
        TOP_ROW_OWNER_CALL to if (GboardPointerOwnerFeature.TOP_ROW_SWIPE in selected) 1 else 0,
        ZHUYIN_OWNER_CALL to if (GboardPointerOwnerFeature.ZHUYIN_SLIDE in selected) 1 else 0,
    )
    expectedCallCounts.forEach { (descriptor, count) ->
        check(ownerCalls.count { (_, actual) -> actual == descriptor } == count) {
            "Malformed Gboard pointer-owner call count for ${descriptor.render()}"
        }
    }
    val rIndex = GboardVersionBindings.pointerFinish.let { binding ->
        pointerOwnerMethod.indexOfFirstMethodCall(
            binding.ownerDescriptor,
            binding.name,
            binding.returnType,
            binding.parameterTypes,
        )
    }
    val acIndex = GboardVersionBindings.pointerPreReset.let { binding ->
        pointerOwnerMethod.indexOfFirstMethodCall(
            binding.ownerDescriptor,
            binding.name,
            binding.returnType,
            binding.parameterTypes,
        )
    }
    check(rIndex >= 0 && acIndex > rIndex) { "Malformed Gboard pointer-owner anchors" }
    val englishIndex = ownerCalls.singleIndexOrNull(ENGLISH_OWNER_CALL)
    val zhuyinIndex = ownerCalls.singleIndexOrNull(ZHUYIN_OWNER_CALL)
    check(englishIndex == null || englishIndex < rIndex)
    check(zhuyinIndex == null || zhuyinIndex < rIndex)
    check(englishIndex == null || zhuyinIndex == null || englishIndex < zhuyinIndex)
    val topRowIndex = ownerCalls.singleIndexOrNull(TOP_ROW_OWNER_CALL)
    check(topRowIndex == null || topRowIndex == acIndex + 1)
    ownerCalls.filter { (_, descriptor) -> descriptor == LONG_PRESS_OWNER_CALL }
        .forEach { (index, _) ->
            check(pointerOwnerMethod.implementation!!.instructions[index] is RegisterRangeInstruction)
            val invoke = pointerOwnerMethod.implementation!!.instructions[index] as RegisterRangeInstruction
            check(pointerOwnerMethod.implementation!!.instructions[index].opcode ==
                Opcode.INVOKE_STATIC_RANGE)
            check(invoke.startRegister == GboardPointerOwnerRegisterContract.expandedP0Register)
            check(invoke.registerCount == 2)
            check(pointerOwnerMethod.implementation!!.instructions[index + 1].opcode == Opcode.RETURN_VOID)
        }
    listOfNotNull(englishIndex, zhuyinIndex, topRowIndex).forEach { index ->
        val invoke = pointerOwnerMethod.implementation!!.instructions[index] as FiveRegisterInstruction
        val expected = when (index) {
            topRowIndex -> listOf(14, 15, 3, 0, 1)
            else -> listOf(14, 15, 0, 1)
        }
        val expectedOpcode = if (index == englishIndex) {
            Opcode.INVOKE_DIRECT
        } else {
            Opcode.INVOKE_STATIC
        }
        check(
            pointerOwnerMethod.implementation!!.instructions[index].opcode == expectedOpcode &&
                invoke.registers() == expected
        ) { "Malformed pointer-owner opcode or register wiring" }
    }
}

private fun GboardPointerOwnerTransformationContext.verifyCleanupCalls(
    selected: Set<GboardPointerOwnerFeature>,
) {
    val hasEnglish = GboardPointerOwnerFeature.ENGLISH_QWERTY in selected
    val hasLongPress = GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected
    val hasTopRow = GboardPointerOwnerFeature.TOP_ROW_SWIPE in selected
    val hasZhuyin = GboardPointerOwnerFeature.ZHUYIN_SLIDE in selected
    val allMethods = ownerClass.methods
    check(allMethods.sumOf { method -> method.countCalls(TOP_ROW_FINISH_CALL) } ==
        if (hasTopRow) pointerFinishMethod.returnInstructionIndices().size else 0)
    if (hasTopRow) {
        pointerFinishMethod.verifyCallImmediatelyBeforeReturns(TOP_ROW_FINISH_CALL)
    }
    check(allMethods.sumOf { method -> method.countCalls(LONG_PRESS_FINISH_CALL) } ==
        if (hasLongPress) pointerFinishMethod.returnInstructionIndices().size else 0)
    check(allMethods.sumOf { method -> method.countCalls(LONG_PRESS_CANCEL_CALL) } ==
        if (hasLongPress) 2 else 0)
    mapOf(
        ENGLISH_CLEAR_CALL to hasEnglish,
        ZHUYIN_CLEAR_CALL to hasZhuyin,
    ).forEach { (descriptor, enabled) ->
        check(allMethods.sumOf { method -> method.countCalls(descriptor) } == if (enabled) 2 else 0)
    }
    check(allMethods.sumOf { method -> method.countCalls(TOP_ROW_CLEAR_CALL) } == if (hasTopRow) {
        pointerCancelMethod.returnInstructionIndices().size + 1
    } else 0)
    if (!hasEnglish && !hasLongPress && !hasTopRow && !hasZhuyin) return

    if (hasTopRow) {
        pointerCancelMethod.verifyCallImmediatelyBeforeReturns(TOP_ROW_CLEAR_CALL)
    }
    val expectedCancelEntry = selected.expectedPointerCancelEntryCalls()
    val expectedCancelOrder = expectedCancelEntry +
        listOfNotNull(TOP_ROW_CLEAR_CALL.takeIf { hasTopRow })
    val expectedResetOrder = selected.expectedPointerResetEntryCalls()
    val expectedFinishOrder = listOfNotNull(
        LONG_PRESS_FINISH_CALL.takeIf { hasLongPress },
        TOP_ROW_FINISH_CALL.takeIf { hasTopRow },
    )
    pointerFinishMethod.verifyCallsImmediatelyBeforeReturns(expectedFinishOrder)
    pointerCancelMethod.verifyEntryCalls(expectedCancelEntry)
    pointerResetMethod.verifyEntryCalls(expectedResetOrder)
    check(pointerCancelMethod.transformationCallIndices().map { it.second } == expectedCancelOrder)
    check(pointerResetMethod.transformationCallIndices().map { it.second } == expectedResetOrder)
}

private fun Set<GboardPointerOwnerFeature>.expectedPointerCancelEntryCalls(): List<MethodCall> =
    buildList {
        if (GboardPointerOwnerFeature.ZHUYIN_SLIDE in this@expectedPointerCancelEntryCalls) {
            add(ZHUYIN_CLEAR_CALL)
        }
        if (GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in
            this@expectedPointerCancelEntryCalls
        ) {
            add(LONG_PRESS_CANCEL_CALL)
        }
        if (GboardPointerOwnerFeature.ENGLISH_QWERTY in this@expectedPointerCancelEntryCalls) {
            add(ENGLISH_CLEAR_CALL)
        }
    }

private fun Set<GboardPointerOwnerFeature>.expectedPointerResetEntryCalls(): List<MethodCall> =
    buildList {
        if (GboardPointerOwnerFeature.ZHUYIN_SLIDE in this@expectedPointerResetEntryCalls) {
            add(ZHUYIN_CLEAR_CALL)
        }
        if (GboardPointerOwnerFeature.TOP_ROW_SWIPE in this@expectedPointerResetEntryCalls) {
            add(TOP_ROW_CLEAR_CALL)
        }
        if (GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in
            this@expectedPointerResetEntryCalls
        ) {
            add(LONG_PRESS_CANCEL_CALL)
        }
        if (GboardPointerOwnerFeature.ENGLISH_QWERTY in this@expectedPointerResetEntryCalls) {
            add(ENGLISH_CLEAR_CALL)
        }
    }

private fun GboardPointerOwnerTransformationContext.verifyExactNormalizedLifecycleState(
    selected: Set<GboardPointerOwnerFeature>,
) {
    if (!selected.hasPointerCleanupFeature()) return

    val normalizedOwner = MutableClass(ownerClass)
    val normalized = GboardPointerOwnerTransformationContext.from(normalizedOwner)
    if (GboardPointerOwnerFeature.TOP_ROW_SWIPE in selected) {
        normalized.pointerFinishMethod.removeCallsImmediatelyBeforeReturns(TOP_ROW_FINISH_CALL)
        normalized.pointerCancelMethod.removeCallsImmediatelyBeforeReturns(TOP_ROW_CLEAR_CALL)
    }
    if (GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected) {
        normalized.pointerFinishMethod.removeCallsImmediatelyBeforeReturns(
            LONG_PRESS_FINISH_CALL,
        )
    }
    normalized.pointerCancelMethod.removeEntryCalls(
        selected.expectedPointerCancelEntryCalls(),
    )
    normalized.pointerResetMethod.removeEntryCalls(
        selected.expectedPointerResetEntryCalls(),
    )
    val normalizedLifecycle = buildList {
        if (
            GboardPointerOwnerFeature.TOP_ROW_SWIPE in selected ||
            GboardPointerOwnerFeature.LONG_PRESS_QUICK_ACTIONS in selected
        ) {
            add(normalized.pointerFinishMethod)
        }
        add(normalized.pointerCancelMethod)
        add(normalized.pointerResetMethod)
    }
    check(normalizedLifecycle.all { method -> method.transformationCallIndices().isEmpty() }) {
        "Extraneous Pointer-family call after lifecycle normalization"
    }
    normalized.requireExactSelectedLifecycleStock(selected)
}

private fun MutableMethod.verifyCallImmediatelyBeforeReturns(descriptor: MethodCall) {
    val instructions = implementation!!.instructions
    val receiver = receiverRegister()
    check(returnInstructionIndices().all { returnIndex ->
        instructions.getOrNull(returnIndex - 1)
            .isExactPointerManagedInvoke(descriptor, receiver)
    }) {
        "Cleanup ${descriptor.render()} must immediately precede every return in $definingClass->$name"
    }
}

private fun MutableMethod.verifyCallsImmediatelyBeforeReturns(expected: List<MethodCall>) {
    if (expected.isEmpty()) return
    val instructions = implementation!!.instructions
    val receiver = receiverRegister()
    check(returnInstructionIndices().all { returnIndex ->
        expected.withIndex().all { (offset, descriptor) ->
            instructions.getOrNull(returnIndex - expected.size + offset)
                .isExactPointerManagedInvoke(descriptor, receiver)
        }
    }) { "Malformed ordered pointer-finish cleanup chain in $definingClass->$name" }
}

private fun MutableMethod.verifyEntryCalls(expected: List<MethodCall>) {
    val instructions = implementation!!.instructions
    val receiver = receiverRegister()
    check(expected.withIndex().all { (index, descriptor) ->
        instructions.getOrNull(index).isExactPointerManagedInvoke(descriptor, receiver)
    }) { "Malformed entry cleanup placement in $definingClass->$name" }
}

private fun MutableMethod.removeCallsImmediatelyBeforeReturns(descriptor: MethodCall) {
    val receiver = receiverRegister()
    returnInstructionIndices().asReversed().forEach { returnIndex ->
        check(
            implementation!!.instructions.getOrNull(returnIndex - 1)
                .isExactPointerManagedInvoke(descriptor, receiver),
        ) { "Malformed cleanup ${descriptor.render()} before normalization" }
        implementation!!.removeInstruction(returnIndex - 1)
    }
}

private fun MutableMethod.removeEntryCalls(expected: List<MethodCall>) {
    val receiver = receiverRegister()
    expected.forEach { descriptor ->
        check(
            implementation!!.instructions.firstOrNull()
                .isExactPointerManagedInvoke(descriptor, receiver),
        ) { "Malformed entry cleanup ${descriptor.render()} before normalization" }
        implementation!!.removeInstruction(0)
    }
}

private fun MutableMethod.receiverRegister(): Int {
    val parameterWords = parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 }
    return implementation!!.registerCount - parameterWords - 1
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?
    .isExactPointerManagedInvoke(descriptor: MethodCall, receiverRegister: Int): Boolean {
    val invoke = this as? FiveRegisterInstruction ?: return false
    val expectedOpcode = if (descriptor == ENGLISH_CLEAR_CALL) {
        Opcode.INVOKE_DIRECT
    } else {
        Opcode.INVOKE_STATIC
    }
    return opcode == expectedOpcode &&
        invoke.registerCount == 1 &&
        invoke.registerC == receiverRegister &&
        methodReference()?.matches(descriptor) == true
}

private fun MutableMethod.countCalls(descriptor: MethodCall): Int =
    implementation?.instructions.orEmpty().count { instruction ->
        instruction.methodReference()?.matches(descriptor) == true
    }

private fun MutableMethod.transformationCallIndices(): List<Pair<Int, MethodCall>> =
    implementation?.instructions.orEmpty().mapIndexedNotNull { index, instruction ->
        val reference = instruction.methodReference() ?: return@mapIndexedNotNull null
        val descriptor = ALL_TRANSFORMATION_CALLS.firstOrNull(reference::matches)
            ?: return@mapIndexedNotNull null
        index to descriptor
    }

private fun List<Pair<Int, MethodCall>>.singleIndexOrNull(descriptor: MethodCall): Int? =
    singleOrNull { (_, actual) -> actual == descriptor }?.first

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction?.methodReference(): MethodReference? =
    (this as? ReferenceInstruction)?.reference as? MethodReference

private fun FiveRegisterInstruction.registers(): List<Int> =
    listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)

private const val ENGLISH_ANCHOR_FIELD_NAME = "jasondevEnglishAnchorKey"
private val SOFT_KEY_VIEW_TYPE: String
    get() = GboardVersionBindings.softKeyViewType.descriptor
private data class HelperDescriptor(
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val accessFlags: Int,
    val fingerprint: String,
)
private val ENGLISH_HELPER_METHODS by lazy { setOf(
    HelperDescriptor(
        "jasondevShouldSuppressEnglishRetarget",
        listOf(SOFT_KEY_VIEW_TYPE, "F", "F"),
        "Z",
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        when (GboardVersionBindings.targetVersion) {
            "18.5.4" -> "9ef19d4d34512bd19e259d766bc6c82c358a55410564691f84a6602c37f4aecc"
            else -> "376f69ad84e0010204d729ce09f4092848775e875721797b3f6528723cc8b770"
        },
    ),
    HelperDescriptor(
        "jasondevClearEnglishAnchor",
        emptyList(),
        "V",
        AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
        when (GboardVersionBindings.targetVersion) {
            "18.5.4" -> "72efbe3504490610a371883613263f90d684fe901ea27b9bcf6d3a3fa9691e45"
            else -> "4e12d6280912adf42d09797a759925f47bba89c612b5b3ae4d15b6b2895afd8d"
        },
    ),
) }
private val ENGLISH_HELPER_METHOD_NAMES by lazy {
    ENGLISH_HELPER_METHODS.mapTo(mutableSetOf()) { it.name }
}

private data class MethodCall(
    val definingClass: String,
    val name: String,
    val parameterTypes: List<String>,
    val returnType: String,
) {
    fun render(): String = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
}

private fun RuntimeCallId.methodCall(): MethodCall = RuntimeAbiCatalog.abi(this).let { abi ->
    MethodCall(abi.owner, abi.name, abi.parameters, abi.returnType)
}

private fun MethodReference.matches(call: MethodCall): Boolean =
    definingClass == call.definingClass &&
        name == call.name &&
        parameterTypes == call.parameterTypes &&
        returnType == call.returnType

private val ENGLISH_OWNER_CALL by lazy { MethodCall(
    GboardVersionBindings.pointerOwner.ownerDescriptor,
    "jasondevShouldSuppressEnglishRetarget",
    listOf(SOFT_KEY_VIEW_TYPE, "F", "F"),
    "Z",
)}
private val LONG_PRESS_OWNER_CALL =
    RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_MAYBE_ENSURE_LONG_PRESS_SCHEDULED.methodCall()
private val TOP_ROW_OWNER_CALL =
    RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_MAYBE_ARM_AND_RESOLVE_TOP_ROW_OWNER.methodCall()
private val ZHUYIN_OWNER_CALL =
    RuntimeCallId.ZHUYIN_SLIDE_RUNTIME_MAYBE_CAPTURE_AND_SHOULD_SUPPRESS_RETARGET.methodCall()
private val ENGLISH_CLEAR_CALL by lazy { MethodCall(
    GboardVersionBindings.pointerOwner.ownerDescriptor,
    "jasondevClearEnglishAnchor",
    emptyList(),
    "V",
)}
private val TOP_ROW_CLEAR_CALL = RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_CLEAR_SWIPE_SESSION.methodCall()
private val TOP_ROW_FINISH_CALL = RuntimeCallId.TOP_ROW_SWIPE_RUNTIME_FINISH_SWIPE_SESSION.methodCall()
private val LONG_PRESS_CANCEL_CALL =
    RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_CANCEL.methodCall()
private val LONG_PRESS_FINISH_CALL =
    RuntimeCallId.LONG_PRESS_QUICK_ACTIONS_RUNTIME_ON_GLOBE_POINTER_FINISH.methodCall()
private val ZHUYIN_CLEAR_CALL = RuntimeCallId.ZHUYIN_SLIDE_RUNTIME_CLEAR_POINTER_STATE.methodCall()
private val ALL_TRANSFORMATION_CALLS by lazy { listOf(
    ENGLISH_OWNER_CALL,
    LONG_PRESS_OWNER_CALL,
    TOP_ROW_OWNER_CALL,
    ZHUYIN_OWNER_CALL,
    ENGLISH_CLEAR_CALL,
    TOP_ROW_CLEAR_CALL,
    TOP_ROW_FINISH_CALL,
    LONG_PRESS_CANCEL_CALL,
    LONG_PRESS_FINISH_CALL,
    ZHUYIN_CLEAR_CALL,
)}
