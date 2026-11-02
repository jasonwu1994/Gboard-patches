package dev.jason.gboardpatches.patches.gboard.features.webclipboard

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReferenceInClass
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val LATIN_IME_SERVICE_CLASS = "Lphr;"
private const val INPUT_METHOD_SERVICE_CLASS =
    "Landroid/inputmethodservice/InputMethodService;"
private const val TARGET_METHOD_NAME = "onCreate"
private const val TARGET_RETURN_TYPE = "V"
private val TARGET_PARAMETER_TYPES = emptyList<String>()
private const val TARGET_DESCRIPTOR = "$LATIN_IME_SERVICE_CLASS->onCreate()V"
private const val SUPER_ON_CREATE_DESCRIPTOR =
    "$INPUT_METHOD_SERVICE_CLASS->onCreate()V"
private const val STOCK_REGISTER_COUNT = 12
private const val STOCK_RECEIVER_REGISTER = 11
private const val SAVED_RECEIVER_REGISTER = 10
private const val STOCK_THROW_COUNT = 3
private const val STOCK_TRY_BLOCK_COUNT = 5
private const val STOCK_CATCHALL_HANDLER_COUNT = 3
private val TARGET_CLASS_ACCESS_FLAGS = AccessFlags.PUBLIC.value
private val TARGET_METHOD_ACCESS_FLAGS =
    AccessFlags.PUBLIC.value or AccessFlags.FINAL.value

private val CAPTURE_BOOTSTRAP_RUNTIME_CALL =
    RuntimeCallId.WEB_CLIPBOARD_CAPTURE_BOOTSTRAP_AFTER_LATIN_IME_ON_CREATE
private val CAPTURE_BOOTSTRAP_RUNTIME_ABI = RuntimeAbiCatalog.abi(CAPTURE_BOOTSTRAP_RUNTIME_CALL)
private val CAPTURE_BOOTSTRAP_RUNTIME_CLASS = CAPTURE_BOOTSTRAP_RUNTIME_ABI.owner
private val CAPTURE_BOOTSTRAP_RUNTIME_DESCRIPTOR = CAPTURE_BOOTSTRAP_RUNTIME_ABI.reference

internal val gboardWebClipboardCapturePatch = bytecodePatch(
    description = "在 phr.onCreate() 正常返回前掛上 Web Clipboard capture bootstrap。"
) {
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        val owner = mutableClass(LATIN_IME_SERVICE_CLASS)
        val target = selectWebClipboardCaptureOnCreate(owner, owner.methods.toList())
        target.applyWebClipboardCaptureBootstrap()
    }
}

internal fun selectWebClipboardCaptureOnCreate(
    owner: MutableClass,
    methods: List<MutableMethod>,
): MutableMethod {
    check(
        owner.type == LATIN_IME_SERVICE_CLASS &&
            owner.accessFlags == TARGET_CLASS_ACCESS_FLAGS &&
            owner.superclass == INPUT_METHOD_SERVICE_CLASS,
    ) {
        "Expected public $LATIN_IME_SERVICE_CLASS to directly extend " +
            INPUT_METHOD_SERVICE_CLASS
    }
    val matches = methods.filter { method ->
        method.definingClass == LATIN_IME_SERVICE_CLASS &&
            method.name == TARGET_METHOD_NAME &&
            method.parameterTypes == TARGET_PARAMETER_TYPES &&
            method.returnType == TARGET_RETURN_TYPE &&
            method.accessFlags == TARGET_METHOD_ACCESS_FLAGS
    }
    check(matches.size == 1) {
        "Expected exactly one public final instance void $TARGET_DESCRIPTOR target"
    }
    return matches.single()
}

internal fun MutableMethod.applyWebClipboardCaptureBootstrap(): MutableMethod {
    requireExactTarget()
    return applyVerified(
        VerifiedTransformationPlan(
            targetName = TARGET_DESCRIPTOR,
            classify = MutableMethod::classifyWebClipboardCapture,
            mutate = { method ->
                val instructions = method.implementation?.instructions
                    ?: error("No instructions in $TARGET_DESCRIPTOR")
                method.addInstructions(
                    receiverSaveInsertionIndex(instructions),
                    RECEIVER_SAVE,
                )
                val returnIndex = singleNormalReturnIndex(
                    method.implementation?.instructions
                        ?: error("No instructions in $TARGET_DESCRIPTOR"),
                )
                method.addInstructions(returnIndex, CAPTURE_BOOTSTRAP_DELEGATE)
                method
            },
        ),
    )
}

private fun MutableMethod.classifyWebClipboardCapture(): VerifiedTransformationState {
    val instructions = implementation?.instructions
        ?: error("No instructions in $TARGET_DESCRIPTOR")
    val runtimeReferences = instructions.filter { instruction ->
        instruction.isMethodReferenceInClass(CAPTURE_BOOTSTRAP_RUNTIME_CLASS)
    }
    return when {
        runtimeReferences.isEmpty() -> {
            validateStockBody()
            VerifiedTransformationState.STOCK
        }
        runtimeReferences.size == 1 &&
            runtimeReferences.single().isMethodReference(
                CAPTURE_BOOTSTRAP_RUNTIME_DESCRIPTOR,
            ) -> {
            validateCompletedPatch()
            VerifiedTransformationState.PATCHED
        }
        else -> VerifiedTransformationState.MALFORMED
    }
}

private fun MutableMethod.requireExactTarget() {
    check(
        definingClass == LATIN_IME_SERVICE_CLASS &&
            name == TARGET_METHOD_NAME &&
            parameterTypes == TARGET_PARAMETER_TYPES &&
            returnType == TARGET_RETURN_TYPE &&
            accessFlags == TARGET_METHOD_ACCESS_FLAGS,
    ) {
        "Refusing non-target Web Clipboard method " +
            "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
    }
}

private fun MutableMethod.validateStockBody() {
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    check(implementation.registerCount == STOCK_REGISTER_COUNT) {
        "Unexpected register count in $TARGET_DESCRIPTOR: ${implementation.registerCount}"
    }
    check(implementation.instructions.none { instruction ->
        instruction.isMethodReferenceInClass(CAPTURE_BOOTSTRAP_RUNTIME_CLASS)
    }) {
        "$TARGET_DESCRIPTOR contains an orphan Web Clipboard runtime reference"
    }
    receiverSaveInsertionIndex(implementation.instructions)
    validateStockControlFlow()
}

private fun MutableMethod.validateCompletedPatch() {
    requireExactTarget()
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    check(implementation.registerCount == STOCK_REGISTER_COUNT)
    val instructions = implementation.instructions
    val returnIndex = singleNormalReturnIndex(instructions)
    val saveIndices = instructions.indices.filter { index ->
        instructions[index].isExactReceiverSave()
    }
    check(saveIndices.size == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one receiver save"
    }
    val saveIndex = saveIndices.single()
    check(instructions.getOrNull(saveIndex - 1).isExactLastSavedLocalUse()) {
        "$TARGET_DESCRIPTOR receiver save is missing or misplaced"
    }
    check(instructions.getOrNull(returnIndex - 1).isExactCaptureDelegate()) {
        "$TARGET_DESCRIPTOR delegate is missing, misplaced, or malformed"
    }
    val delegateIndex = returnIndex - 1
    check(saveIndex < delegateIndex) {
        "$TARGET_DESCRIPTOR receiver save must precede the delegate"
    }
    check(instructions.subList(saveIndex + 1, delegateIndex).none { instruction ->
        SAVED_RECEIVER_REGISTER in instruction.mentionedRegisters()
    }) {
        "$TARGET_DESCRIPTOR saved receiver local is redefined or consumed before delegate"
    }
    val firstP0Clobber = instructions.indices.firstOrNull { index ->
        instructions[index].definesRegister(STOCK_RECEIVER_REGISTER)
    } ?: error("$TARGET_DESCRIPTOR must retain its p0 clobber")
    check(saveIndex < firstP0Clobber && firstP0Clobber < delegateIndex) {
        "$TARGET_DESCRIPTOR must save the receiver before p0 is clobbered"
    }
    check(instructions.count { instruction ->
        instruction.isMethodReference(CAPTURE_BOOTSTRAP_RUNTIME_DESCRIPTOR)
    } == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one Web Clipboard delegate"
    }
    check(instructions.count { instruction ->
        instruction.isMethodReferenceInClass(CAPTURE_BOOTSTRAP_RUNTIME_CLASS)
    } == 1) {
        "$TARGET_DESCRIPTOR contains an orphan Web Clipboard runtime reference"
    }
    validateStockControlFlow()
    val saveAddress = instructions.codeAddressOf(saveIndex)
    val delegateAddress = instructions.codeAddressOf(returnIndex - 1)
    val tryBlocks = implementation.tryBlocks
    check(tryBlocks.count { tryBlock ->
        saveAddress in tryBlock.startCodeAddress until
            (tryBlock.startCodeAddress + tryBlock.codeUnitCount)
    } == 1) {
        "$TARGET_DESCRIPTOR receiver save must remain inside its single stock try region"
    }
    check(tryBlocks.none { tryBlock ->
        delegateAddress in tryBlock.startCodeAddress until
            (tryBlock.startCodeAddress + tryBlock.codeUnitCount)
    }) {
        "$TARGET_DESCRIPTOR delegate must not run on the exceptional exit"
    }
}

private fun MutableMethod.validateStockControlFlow() {
    val implementation = implementation ?: error("No instructions in $TARGET_DESCRIPTOR")
    val instructions = implementation.instructions
    val returnIndex = singleNormalReturnIndex(instructions)
    val throwIndices = instructions.indices.filter { index ->
        instructions[index].isOpcode("THROW")
    }
    check(throwIndices.size == STOCK_THROW_COUNT) {
        "$TARGET_DESCRIPTOR must retain exactly $STOCK_THROW_COUNT catchall THROW instructions"
    }
    check(
        throwIndices.count { it < returnIndex } == 2 &&
            throwIndices.count { it > returnIndex } == 1,
    ) {
        "$TARGET_DESCRIPTOR catchall THROW ordering drifted around its normal return"
    }
    check(throwIndices.all { throwIndex ->
        (instructions[throwIndex] as? OneRegisterInstruction)?.registerA ==
            STOCK_RECEIVER_REGISTER
    }) {
        "$TARGET_DESCRIPTOR catchall handlers must throw the original p0 register"
    }

    val superCalls = instructions.indices.filter { index ->
        instructions[index].isMethodReference(SUPER_ON_CREATE_DESCRIPTOR)
    }
    check(superCalls.size == 1 && superCalls.single() < returnIndex) {
        "$TARGET_DESCRIPTOR must call InputMethodService.onCreate() before normal return"
    }

    val tryBlocks = implementation.tryBlocks
    check(tryBlocks.size == STOCK_TRY_BLOCK_COUNT) {
        "$TARGET_DESCRIPTOR must retain exactly $STOCK_TRY_BLOCK_COUNT catchall try blocks"
    }
    check(tryBlocks.all { tryBlock ->
        tryBlock.exceptionHandlers.size == 1 &&
            tryBlock.exceptionHandlers.single().exceptionType == null
    }) {
        "$TARGET_DESCRIPTOR must retain only single catchall handlers"
    }
    val handlerAddresses = tryBlocks.map { tryBlock ->
        tryBlock.exceptionHandlers.single().handlerCodeAddress
    }.toSet()
    check(handlerAddresses.size == STOCK_CATCHALL_HANDLER_COUNT) {
        "$TARGET_DESCRIPTOR catchall handler target count drifted"
    }
    val moveExceptionIndices = instructions.indices.filter { index ->
        instructions[index].isOpcode("MOVE_EXCEPTION") &&
            (instructions[index] as? OneRegisterInstruction)?.registerA ==
                STOCK_RECEIVER_REGISTER
    }
    check(moveExceptionIndices.size == STOCK_CATCHALL_HANDLER_COUNT) {
        "$TARGET_DESCRIPTOR must retain exactly $STOCK_CATCHALL_HANDLER_COUNT move-exception p0 handlers"
    }
    check(
        moveExceptionIndices.map(instructions::codeAddressOf).toSet() == handlerAddresses,
    ) {
        "$TARGET_DESCRIPTOR catchall handler addresses drifted"
    }
    check(moveExceptionIndices.indices.all { handlerOrdinal ->
        val handlerIndex = moveExceptionIndices[handlerOrdinal]
        val nextHandlerIndex = moveExceptionIndices.getOrNull(handlerOrdinal + 1)
            ?: instructions.size
        throwIndices.count { it in (handlerIndex + 1) until nextHandlerIndex } == 1
    }) {
        "$TARGET_DESCRIPTOR catchall handlers must each terminate in one THROW"
    }
}

private fun MutableMethod.singleNormalReturnIndex(instructions: List<Instruction>): Int {
    val returns = instructions.indices.filter { index ->
        instructions[index].isOpcode("RETURN_VOID")
    }
    check(returns.size == 1) {
        "$TARGET_DESCRIPTOR must contain exactly one normal RETURN_VOID"
    }
    return returns.single()
}

private fun receiverSaveInsertionIndex(instructions: List<Instruction>): Int {
    val savedRegisterAccesses = instructions.indices.filter { index ->
        SAVED_RECEIVER_REGISTER in instructions[index].mentionedRegisters()
    }
    check(savedRegisterAccesses.isNotEmpty()) {
        "$TARGET_DESCRIPTOR must use v$SAVED_RECEIVER_REGISTER before receiver save"
    }
    val lastAccess = savedRegisterAccesses.last()
    check(instructions[lastAccess].isExactLastSavedLocalUse()) {
        "$TARGET_DESCRIPTOR v$SAVED_RECEIVER_REGISTER liveness anchor drifted"
    }
    val insertionIndex = lastAccess + 1
    val firstP0Clobber = instructions.indices.firstOrNull { index ->
        instructions[index].definesRegister(STOCK_RECEIVER_REGISTER)
    } ?: error("$TARGET_DESCRIPTOR must retain its p0 clobber")
    check(insertionIndex < firstP0Clobber) {
        "$TARGET_DESCRIPTOR has no proven-dead local before p0 clobber"
    }
    return insertionIndex
}

private fun List<Instruction>.codeAddressOf(index: Int): Int {
    check(index in indices)
    var address = 0
    for (current in 0 until index) {
        address += this[current].codeUnits
    }
    return address
}

private fun Instruction?.isExactCaptureDelegate(): Boolean =
    this?.isInvoke(
        "INVOKE_STATIC_RANGE",
        CAPTURE_BOOTSTRAP_RUNTIME_DESCRIPTOR,
        SAVED_RECEIVER_REGISTER,
    ) == true

private fun Instruction?.isExactReceiverSave(): Boolean =
    this?.isOpcode("MOVE_OBJECT") == true &&
        this is TwoRegisterInstruction &&
        registerA == SAVED_RECEIVER_REGISTER &&
        registerB == STOCK_RECEIVER_REGISTER

private fun Instruction?.isExactLastSavedLocalUse(): Boolean =
    this?.isOpcode("APUT_OBJECT") == true &&
        this is ThreeRegisterInstruction &&
        registerA == 1 &&
        registerB == 2 &&
        registerC == SAVED_RECEIVER_REGISTER

private fun Instruction.mentionedRegisters(): Set<Int> = when (this) {
    is RegisterRangeInstruction ->
        (startRegister until startRegister + registerCount).toSet()
    is FiveRegisterInstruction -> listOf(
        registerC,
        registerD,
        registerE,
        registerF,
        registerG,
    ).take(registerCount).toSet()
    is ThreeRegisterInstruction -> setOf(registerA, registerB, registerC)
    is TwoRegisterInstruction -> setOf(registerA, registerB)
    is OneRegisterInstruction -> setOf(registerA)
    else -> emptySet()
}

private fun Instruction.definesRegister(register: Int): Boolean {
    val opcode = normalizedOpcode()
    return when (this) {
        is TwoRegisterInstruction ->
            registerA == register && (
                opcode.startsWith("MOVE_") ||
                    opcode.startsWith("MOVE_WIDE") ||
                    opcode.startsWith("MOVE_OBJECT")
                )
        is OneRegisterInstruction ->
            registerA == register && DESTINATION_REGISTER_PREFIXES.any(opcode::startsWith)
        else -> false
    }
}

private fun Instruction.normalizedOpcode(): String =
    opcode.name.uppercase().replace('-', '_').replace('/', '_')

private const val RECEIVER_SAVE = "move-object v10, p0"
private val CAPTURE_BOOTSTRAP_DELEGATE =
    RuntimeCallEmitter.invoke(CAPTURE_BOOTSTRAP_RUNTIME_CALL, "v10 .. v10")

private val DESTINATION_REGISTER_PREFIXES = listOf(
    "CONST",
    "IGET",
    "MOVE_EXCEPTION",
    "MOVE_RESULT",
    "NEW_INSTANCE",
    "SGET",
)
