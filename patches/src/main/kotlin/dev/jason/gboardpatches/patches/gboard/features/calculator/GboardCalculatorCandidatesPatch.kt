package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.applyVoidExitLifecycleDelegate
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

/**
 * Shows calculator results as real Gboard suggestions.
 *
 * - LatinCandidatesViewController.appendTextCandidates: when the cursor is in a calculation,
 *   the equation and result are built with Gboard's own candidate builder and replace the
 *   incoming batch, so Gboard lays out, themes and animates them itself. The strip is cleared
 *   first so they are never appended to earlier batches, and the controller's after-backspace
 *   flag is raised so they replace the toolbar.
 * - LatinFixedCountCandidatesHolderView.setCandidates and resizeSlots: after Gboard sizes the
 *   slots, the equation and result slots are resized to share the whole strip, the result
 *   taking the width its text needs.
 * - LatinCandidatesViewController.textCandidatesUpdated(false): while the text is still a
 *   calculation, the calculator pair is kept instead of the strip being cleared.
 * - GoogleInputMethodService event dispatch: a CANDIDATE_SELECT event for one of those
 *   candidates is handed to the calculator, which replaces the calculation, instead of Gboard
 *   committing the candidate text.
 *
 * The extension recognises its candidates by identity, so it needs no obfuscated names.
 */
internal fun BytecodePatchContext.applyCalculatorCandidateHooks() {
    val targets = GboardCalculator1803Targets
    targets.appendTextCandidates.ownerClass(this).addHelperMethodIfMissing(
        name = CANDIDATES_HELPER_NAME,
        parameterTypes = listOf("Ljava/util/List;"),
        returnType = "Ljava/util/List;",
        accessFlags = HELPER_ACCESS_FLAGS,
        registerCount = CANDIDATES_HELPER_REGISTER_COUNT,
        body = candidatesHelperBody(),
    )
    findMutableMethodOrThrow(targets.appendTextCandidates).applyCandidatesEntryDelegate()

    // Lets the calculator ask for a refresh once the editor has applied a keystroke: Gboard
    // asks for suggestions before that, so it would otherwise see the previous text. The
    // controller becomes a Runnable, so the extension can call it without reflection.
    val controller = targets.appendTextCandidates.ownerClass(this)
    if (RUNNABLE !in controller.interfaces) {
        controller.interfaces.add(RUNNABLE)
    }
    controller.addHelperMethodIfMissing(
        name = "run",
        parameterTypes = emptyList(),
        returnType = "V",
        accessFlags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
        registerCount = REFRESH_HELPER_REGISTER_COUNT,
        body = refreshHelperBody(),
    )

    // Whenever Gboard sizes the slots, for a new batch or a new strip width, let the calculator
    // resize its two slots.
    listOf(targets.fixedStripSetCandidates, targets.fixedStripResize).forEach { target ->
        findMutableMethodOrThrow(target).applyVoidExitLifecycleDelegate(
            RuntimeCallId.CALCULATOR_CANDIDATES_AFTER_BOUND,
            "p0 .. p0",
        )
    }

    // When Gboard has no suggestions for a keystroke, textCandidatesUpdated(false) clears the
    // strip and shows the toolbar. While the text is a calculation, keep the calculator pair.
    findMutableMethodOrThrow(targets.candidatesUpdated).addEarlyReturn(
        guard = "if-nez p1, :$CONTINUE_LABEL",
        call = RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_KEEP_ON_CLEAR, ""),
        reference = RuntimeAbiCatalog.abi(RuntimeCallId.CALCULATOR_CANDIDATES_KEEP_ON_CLEAR).reference,
        returnValue = 0,
    )

    targets.consumeEvent.ownerClass(this).addHelperMethodIfMissing(
        name = TAP_HELPER_NAME,
        parameterTypes = listOf(targets.EVENT_TYPE),
        returnType = "Z",
        accessFlags = HELPER_ACCESS_FLAGS,
        registerCount = TAP_HELPER_REGISTER_COUNT,
        body = tapHelperBody(),
    )
    val tapHelper = "${targets.consumeEvent.ownerDescriptor}->$TAP_HELPER_NAME(${targets.EVENT_TYPE})Z"
    findMutableMethodOrThrow(targets.consumeEvent).addEarlyReturn(
        call = "invoke-direct/range {p0 .. p1}, $tapHelper",
        reference = tapHelper,
        returnValue = 1,
    )
}

private fun MutableMethod.applyCandidatesEntryDelegate() {
    val helper = "${GboardCalculator1803Targets.appendTextCandidates.ownerDescriptor}->" +
        "$CANDIDATES_HELPER_NAME(Ljava/util/List;)Ljava/util/List;"
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
private fun candidatesHelperBody(): String {
    val t = GboardCalculator1803Targets
    val controller = t.appendTextCandidates.ownerDescriptor
    return """
        ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_TEXTS, "p0")}
        move-result-object v0
        if-nez v0, :calculator
        return-object p1

        :calculator
        const/4 v1, 0x0
        aget-object v1, v0, v1
        ${buildCandidate(text = "v1", into = "v2")}
        const/4 v1, 0x1
        aget-object v1, v0, v1
        ${buildCandidate(text = "v1", into = "v3")}

        # Clear the strip first, so the pair replaces earlier batches instead of being appended,
        # and let it replace the toolbar like after a backspace, also on the symbols page.
        const/4 v0, 0x1
        iput-boolean v0, p0, $controller->${t.CONTROLLER_CLEAR_PENDING_FIELD}
        iput-boolean v0, p0, $controller->${t.CONTROLLER_PRIORITY_FIELD}

        ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_SHOW, "v2, v3")}
        move-result-object v0
        return-object v0
    """.trimIndent()
}

// Builds a Gboard text candidate with Gboard's own builder.
private fun buildCandidate(text: String, into: String): String {
    val t = GboardCalculator1803Targets
    val builder = t.CANDIDATE_BUILDER_TYPE
    return """
        new-instance $into, $builder
        invoke-direct {$into}, $builder-><init>()V
        iput-object $text, $into, $builder->${t.CANDIDATE_BUILDER_TEXT_FIELD}
        invoke-virtual {$into}, $builder->${t.CANDIDATE_BUILDER_BUILD_METHOD}
        move-result-object $into
    """.trimIndent()
}

// p0 = controller. Sends an empty batch through appendTextCandidates; the entry hook replaces
// it with the calculator pair for the current text.
private fun refreshHelperBody(): String = """
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    const/4 v1, 0x0
    const/4 v2, 0x0
    invoke-virtual {p0, v0, v1, v2}, ${GboardCalculator1803Targets.appendTextCandidates.reference}
    return-void
""".trimIndent()

// p0 = input method service, p1 = event. Returns true when the calculator handled the tap.
private fun tapHelperBody(): String {
    val t = GboardCalculator1803Targets
    return """
        if-eqz p1, :not_calculator
        invoke-virtual {p1}, ${t.EVENT_TYPE}->${t.EVENT_KEY_DATA_METHOD}
        move-result-object v0
        if-eqz v0, :not_calculator
        iget v1, v0, ${t.KEY_DATA_TYPE}->${t.KEY_DATA_CODE_FIELD}
        const/16 v2, $CANDIDATE_SELECT_CODE
        if-ne v1, v2, :not_calculator
        iget-object v0, v0, ${t.KEY_DATA_TYPE}->${t.KEY_DATA_PAYLOAD_FIELD}
        instance-of v1, v0, ${t.CANDIDATE_DATA_TYPE}
        if-eqz v1, :not_calculator
        check-cast v0, ${t.CANDIDATE_DATA_TYPE}
        iget-object v0, v0, ${t.CANDIDATE_DATA_TYPE}->${t.CANDIDATE_DATA_CANDIDATE_FIELD}

        ${RuntimeCallEmitter.invoke(RuntimeCallId.CALCULATOR_CANDIDATES_ON_SELECTED, "v0")}
        move-result v0
        return v0

        :not_calculator
        const/4 v0, 0x0
        return v0
    """.trimIndent()
}

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
// CANDIDATE_SELECT key code, sent when a suggestion is tapped.
private const val CANDIDATE_SELECT_CODE = -10002
