package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import java.util.logging.Logger

/**
 * Shows calculator results as real Gboard suggestions.
 *
 * - LatinCandidatesViewController.appendTextCandidates: when the cursor is in a calculation,
 *   the equation and result are built with Gboard's own candidate builder and replace the
 *   incoming batch, so Gboard lays out, themes and animates them itself. The strip is cleared
 *   first so they are never appended to earlier batches, and the controller's after-backspace
 *   flag is raised so they replace the toolbar.
 * - LatinCandidatesViewController.textCandidatesUpdated(false): while the text is still a
 *   calculation, the calculator pair is kept instead of the strip being cleared.
 * - GoogleInputMethodService event dispatch: a CANDIDATE_SELECT event for one of those
 *   candidates is handed to the calculator, which replaces the calculation, instead of Gboard
 *   committing the candidate text.
 *
 * Optional, skipped with a warning if a Gboard update changes them:
 * - LatinFixedCountCandidatesHolderView.setCandidates and resizeSlots: after Gboard sizes the
 *   slots, the equation and result slots are resized to share the whole strip, the result
 *   taking the width its text needs.
 * - LatinCandidatesViewController activation: each keyboard view (letters, symbols, folded,
 *   unfolded) has its own controller, which starts with the toolbar showing. When one is shown,
 *   the calculator shows its pair there again.
 *
 * Gboard's code is found by fingerprint (see GboardCalculatorTargets.kt), and the extension
 * recognises its candidates by identity, so neither needs obfuscated names.
 */
internal fun BytecodePatchContext.applyCalculatorCandidateHooks() {
    val t = resolveCandidateTargets()

    t.controller.addHelperMethodIfMissing(
        name = CANDIDATES_HELPER_NAME,
        parameterTypes = listOf("Ljava/util/List;"),
        returnType = "Ljava/util/List;",
        accessFlags = HELPER_ACCESS_FLAGS,
        registerCount = CANDIDATES_HELPER_REGISTER_COUNT,
        body = candidatesHelperBody(t),
    )
    t.appendTextCandidates.applyCandidatesEntryDelegate(t)

    // Lets the calculator ask for a refresh once the editor has applied a keystroke: Gboard
    // asks for suggestions before that, so it would otherwise see the previous text. The
    // controller becomes a Runnable, so the extension can call it without reflection.
    if (RUNNABLE !in t.controller.interfaces) {
        t.controller.interfaces.add(RUNNABLE)
    }
    t.controller.addHelperMethodIfMissing(
        name = "run",
        parameterTypes = emptyList(),
        returnType = "V",
        accessFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        registerCount = REFRESH_HELPER_REGISTER_COUNT,
        body = refreshHelperBody(t),
    )

    // When Gboard has no suggestions for a keystroke, textCandidatesUpdated(false) clears the
    // strip and shows the toolbar. While the text is a calculation, keep the calculator pair.
    t.candidatesUpdated.addEarlyReturn(
        guard = "if-nez p1, :$CONTINUE_LABEL",
        call = RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_KEEP_ON_CLEAR, ""),
        reference = RuntimeAbiCatalog.abi(RuntimeCallId.CALCULATOR_CANDIDATES_KEEP_ON_CLEAR).reference,
        returnValue = 0,
    )

    val tapHelper = "${t.consumeEvent.definingClass}->$TAP_HELPER_NAME(${t.eventType})Z"
    mutableClassDefBy(t.consumeEvent.definingClass).addHelperMethodIfMissing(
        name = TAP_HELPER_NAME,
        parameterTypes = listOf(t.eventType),
        returnType = "Z",
        accessFlags = HELPER_ACCESS_FLAGS,
        registerCount = TAP_HELPER_REGISTER_COUNT,
        body = tapHelperBody(t),
    )
    t.consumeEvent.addEarlyReturn(
        call = "invoke-direct/range {p0 .. p1}, $tapHelper",
        reference = tapHelper,
        returnValue = 1,
    )

    // Whenever Gboard sizes the slots, for a new batch or a new strip width, let the calculator
    // resize its two slots. Without this the pair keeps Gboard's slot widths.
    optional("fitting the pair to the strip") {
        val strip = classDefBy(FIXED_STRIP)
        listOf(FixedStripSetCandidatesFingerprint, FixedStripResizeFingerprint)
            .map { it.only(strip).method }
            .forEach {
                it.applyVoidExitLifecycleDelegate(RuntimeCallId.CALCULATOR_CANDIDATES_AFTER_BOUND, "p0 .. p0")
            }
    }

    // Gboard does not ask for suggestions again when it shows another keyboard view, such as the
    // letters after the symbols or the unfolded layout, so the new controller would show the
    // toolbar until the next keystroke. Its activation is the only no-argument method that
    // clears the priority flag.
    optional("showing the pair on another keyboard view") {
        fun Instruction.clearsPriority() =
            opcode == Opcode.IPUT_BOOLEAN && isFieldReference(t.priorityField)
        val activation = t.controller.methods.single { method ->
            method.returnType == "V" && method.parameterTypes.isEmpty() &&
                method.implementation?.instructions?.any { it.clearsPriority() } == true
        }
        val activated = RuntimeAbiCatalog.abi(RuntimeCallId.CALCULATOR_CANDIDATES_ON_ACTIVATED)
        if (!activation.alreadyDelegates(activated.reference)) {
            activation.addInstructions(
                activation.implementation!!.instructions.indexOfFirst { it.clearsPriority() } + 1,
                RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_ON_ACTIVATED, "p0 .. p0"),
            )
        }
    }
}

/**
 * Runs a hook the calculator can work without. If a Gboard update changes the code it needs,
 * the hook is skipped with a warning instead of failing the whole patch. Returns its result, or
 * null if it was skipped.
 */
private fun <T> optional(feature: String, hook: () -> T): T? = try {
    hook()
} catch (failure: Exception) {
    // The root logger, which Morphe shows in the patch log.
    Logger.getLogger("").warning(
        "Simple Calculator: skipped $feature, which this Gboard version does not support: " +
            failure.message,
    )
    null
}

/** The fingerprint's only match, failing if a Gboard update adds a lookalike method. */
context(context: BytecodePatchContext)
internal fun Fingerprint.only(classDef: ClassDef? = null): Match = with(context) {
    (if (classDef == null) matchAll(1..1) else matchAll(classDef, 1..1)).single()
}

/** Gboard classes and members the hooks use, found by fingerprint. */
private class CandidateTargets(
    val controller: MutableClass,
    val appendTextCandidates: MutableMethod,
    val candidatesUpdated: MutableMethod,
    val consumeEvent: MutableMethod,
    /** Makes the next batch clear the strip instead of being appended to it. */
    val clearPendingField: String,
    /** Set by Gboard after the first letter or backspace; while set, suggestions replace the toolbar. */
    val priorityField: String,
    val builderType: String,
    val builderTextField: String,
    val buildMethod: String,
    val eventType: String,
    val keyDataMethod: String,
    val keyCodeField: String,
    val keyPayloadField: String,
    /** CANDIDATE_SELECT carries this, wrapping the candidate. */
    val candidateDataType: String,
    val candidateDataCandidateField: String,
)

/** The builder field the Candidate(Builder) constructor copies into [candidateField]. */
private fun Map<String, String>.builderField(candidateField: FieldReference) =
    this[candidateField.toString()] ?: throw IllegalStateException("No builder field for $candidateField")

private fun BytecodePatchContext.resolveCandidateTargets(): CandidateTargets {
    val append = AppendTextCandidatesFingerprint.only()
    val candidateType = append.originalMethod.parameterTypes[1].toString()
    val consumeEvent = ConsumeEventFingerprint.only()
    val eventType = consumeEvent.originalMethod.parameterTypes.single().toString()
    val updated = TextCandidatesUpdatedFingerprint.only()
    val key = CandidatesViewControllerKeyFingerprint(eventType).only().instructionMatches

    val build = CandidateBuildFingerprint(candidateType).only()
    val builderType = build.originalClassDef.type
    val builderFields = copiedFields(
        CandidateFromBuilderFingerprint(candidateType, builderType).only().originalMethod,
    )
    val candidateText = CandidateToStringFingerprint(candidateType).only().instructionMatches[0]
        .instruction.fieldReference()
    val candidateDataType = CandidateSelectDataFingerprint(candidateType).only().instructionMatches[0]
        .instruction.methodReference().returnType
    val candidateDataCandidate = classDefBy(candidateDataType).fields.single { it.type == candidateType }

    return CandidateTargets(
        controller = append.classDef,
        appendTextCandidates = append.method,
        candidatesUpdated = updated.method,
        consumeEvent = consumeEvent.method,
        clearPendingField = updated.instructionMatches[1].instruction.fieldReference().toString(),
        priorityField = key[6].instruction.fieldReference().toString(),
        builderType = builderType,
        builderTextField = builderFields.builderField(candidateText),
        buildMethod = build.originalMethod.reference(),
        eventType = eventType,
        keyDataMethod = key[0].instruction.methodReference().toString(),
        keyCodeField = key[2].instruction.fieldReference().toString(),
        keyPayloadField = key[3].instruction.fieldReference().toString(),
        candidateDataType = candidateDataType,
        candidateDataCandidateField = "$candidateDataType->${candidateDataCandidate.name}:$candidateType",
    )
}

/** Maps each candidate field to the builder field the Candidate(Builder) constructor copies into it. */
private fun copiedFields(constructor: Method): Map<String, String> =
    constructor.implementation!!.instructions.zipWithNext().mapNotNull { (read, write) ->
        val from = (read as? ReferenceInstruction)?.reference as? FieldReference
        val to = (write as? ReferenceInstruction)?.reference as? FieldReference
        // A field read (which sets a register) followed by a write into the candidate.
        if (from == null || to == null || !read.opcode.setsRegister() || write.opcode.setsRegister() ||
            to.definingClass != constructor.definingClass
        ) {
            null
        } else {
            to.toString() to from.toString()
        }
    }.toMap()

private fun Instruction.fieldReference() = (this as ReferenceInstruction).reference as FieldReference
private fun Instruction.methodReference() = (this as ReferenceInstruction).reference as MethodReference
private fun Method.reference() = "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"

private fun MutableMethod.applyCandidatesEntryDelegate(t: CandidateTargets) {
    val helper = "${t.controller.type}->$CANDIDATES_HELPER_NAME(Ljava/util/List;)Ljava/util/List;"
    if (alreadyDelegates(helper)) {
        return
    }
    addInstructions(
        0,
        """
            invoke-direct/range {p0 .. p1}, $helper
            move-result-object p1
        """.trimIndent(),
    )
}

/**
 * At the start of the method, runs [call] and returns [returnValue] when it returns true;
 * otherwise, or when [guard] jumps to the continue label, Gboard's own code runs as before.
 */
private fun MutableMethod.addEarlyReturn(
    guard: String = "",
    call: String,
    reference: String,
    returnValue: Int,
) {
    if (alreadyDelegates(reference)) {
        return
    }
    // v0 must be a local, not a parameter.
    val parameterRegisters = parameterTypes.sumOf { if (it.toString() == "J" || it.toString() == "D") 2L else 1L } + 1
    check(implementation!!.registerCount > parameterRegisters) {
        "No free register in $definingClass->$name"
    }
    addInstructions(0, "nop")
    val continuation = implementation!!.instructions[0]
    addInstructionsWithLabels(
        0,
        """
            $guard
            $call
            move-result v0
            if-eqz v0, :$CONTINUE_LABEL
            const/4 v0, $returnValue
            return v0
        """.trimIndent(),
        ExternalLabel(CONTINUE_LABEL, continuation),
    )
}

private fun MutableMethod.alreadyDelegates(helperReference: String): Boolean {
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(helperReference) }
    check(existing <= 1) { "Malformed calculator candidate delegate in $definingClass->$name" }
    return existing == 1
}

// p0 = controller, p1 = a batch of Gboard's candidates. Returns the batch to show.
private fun candidatesHelperBody(t: CandidateTargets): String = """
    ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_TEXTS, "p0")}
    move-result-object v0
    if-nez v0, :calculator
    return-object p1

    :calculator
    const/4 v1, 0x0
    aget-object v1, v0, v1
    ${buildCandidate(t, text = "v1", into = "v2")}
    const/4 v1, 0x1
    aget-object v1, v0, v1
    ${buildCandidate(t, text = "v1", into = "v3")}

    # Clear the strip first, so the pair replaces earlier batches instead of being appended,
    # and let it replace the toolbar like after a backspace, also on the symbols page.
    const/4 v0, 0x1
    iput-boolean v0, p0, ${t.clearPendingField}
    iput-boolean v0, p0, ${t.priorityField}

    ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_SHOW, "v2, v3")}
    move-result-object v0
    return-object v0
""".trimIndent()

// Builds a Gboard text candidate with Gboard's own builder.
private fun buildCandidate(t: CandidateTargets, text: String, into: String): String = """
    new-instance $into, ${t.builderType}
    invoke-direct {$into}, ${t.builderType}-><init>()V
    iput-object $text, $into, ${t.builderTextField}
    invoke-virtual {$into}, ${t.buildMethod}
    move-result-object $into
""".trimIndent()

// p0 = controller. Sends an empty batch through appendTextCandidates; the entry hook replaces
// it with the calculator pair for the current text.
private fun refreshHelperBody(t: CandidateTargets): String = """
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    const/4 v1, 0x0
    const/4 v2, 0x0
    invoke-virtual {p0, v0, v1, v2}, ${t.appendTextCandidates.reference()}
    return-void
""".trimIndent()

// p0 = input method service, p1 = event. Returns true when the calculator handled the tap.
private fun tapHelperBody(t: CandidateTargets): String = """
    if-eqz p1, :not_calculator
    invoke-virtual {p1}, ${t.keyDataMethod}
    move-result-object v0
    if-eqz v0, :not_calculator
    iget v1, v0, ${t.keyCodeField}
    const/16 v2, $CANDIDATE_SELECT_CODE
    if-ne v1, v2, :not_calculator
    iget-object v0, v0, ${t.keyPayloadField}
    instance-of v1, v0, ${t.candidateDataType}
    if-eqz v1, :not_calculator
    check-cast v0, ${t.candidateDataType}
    iget-object v0, v0, ${t.candidateDataCandidateField}

    ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_ON_SELECTED, "v0")}
    move-result v0
    return v0

    :not_calculator
    const/4 v0, 0x0
    return v0
""".trimIndent()

private const val CONTINUE_LABEL = "gboard_patches_calculator_continue"
private val HELPER_ACCESS_FLAGS = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value
private const val CANDIDATES_HELPER_NAME = "gboardPatchesCalculatorCandidates"
private const val RUNNABLE = "Ljava/lang/Runnable;"
// Locals v0..v2 plus this.
private const val REFRESH_HELPER_REGISTER_COUNT = 4
private const val TAP_HELPER_NAME = "gboardPatchesCalculatorCandidateTap"
// Locals v0..v3 plus this and the list.
private const val CANDIDATES_HELPER_REGISTER_COUNT = 6
// Locals v0..v2 plus this and the event.
private const val TAP_HELPER_REGISTER_COUNT = 5
