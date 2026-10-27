package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

// Fingerprints for the Gboard code Simple Calculator hooks. They use Android and unobfuscated
// Gboard names, strings, key codes and short instruction patterns, never obfuscated names, so
// they keep matching when Gboard updates. Each matches exactly one method in Gboard 18.0.3 and
// 18.5.4, which the patch checks, so a lookalike in a later version fails the patch instead of
// being hooked.

private const val INPUT_METHOD_SERVICE = "Landroid/inputmethodservice/InputMethodService;"
internal const val FIXED_STRIP =
    "Lcom/google/android/apps/inputmethod/latin/keyboard/widget/LatinFixedCountCandidatesHolderView;"

// Key codes of Gboard's suggestion events.
internal const val CANDIDATE_SELECT_CODE = -10002
private const val KEYCODE_DEL = 67

/** Gboard's input method service, the only InputMethodService subclass. */
internal object OnStartInputViewFingerprint : Fingerprint(
    name = "onStartInputView",
    returnType = "V",
    parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
    custom = { _, classDef -> classDef.superclass == INPUT_METHOD_SERVICE },
)

internal object OnUpdateSelectionFingerprint : Fingerprint(
    classFingerprint = OnStartInputViewFingerprint,
    name = "onUpdateSelection",
    returnType = "V",
    parameters = listOf("I", "I", "I", "I", "I", "I"),
)

internal object OnWindowHiddenFingerprint : Fingerprint(
    classFingerprint = OnStartInputViewFingerprint,
    name = "onWindowHidden",
    returnType = "V",
    parameters = emptyList(),
)

/**
 * Event dispatch of the input method service (InputBundle.consumeEvent inlined), which handles
 * CANDIDATE_SELECT for suggestion taps. Its parameter is Gboard's event type.
 */
internal object ConsumeEventFingerprint : Fingerprint(
    classFingerprint = OnStartInputViewFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_OBJECT, type = "Landroid/view/KeyEvent;"),
        methodCall(definingClass = "Landroid/view/KeyEvent;", name = "getKeyCode"),
        methodCall(definingClass = "Landroid/view/KeyEvent;", name = "isModifierKey"),
        // Meta state mask of the shift and caps lock states.
        literal(0x60000000000L),
    ),
)

/** LatinCandidatesViewController, found by its logger tag. */
internal object CandidatesViewControllerFingerprint : Fingerprint(
    name = "<clinit>",
    filters = listOf(
        string("com/google/android/apps/inputmethod/latin/keyboard/LatinCandidatesViewController"),
    ),
)

/** LatinCandidatesViewController.appendTextCandidates(List, Candidate, boolean). */
internal object AppendTextCandidatesFingerprint : Fingerprint(
    classFingerprint = CandidatesViewControllerFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/util/List;", "L", "Z"),
)

/**
 * LatinCandidatesViewController.textCandidatesUpdated(boolean): false clears the strip and brings
 * back the toolbar. True sets the flag that makes the next batch clear the strip instead of being
 * appended to it (the second match), then asks for suggestions.
 */
internal object TextCandidatesUpdatedFingerprint : Fingerprint(
    classFingerprint = CandidatesViewControllerFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
    parameters = listOf("Z"),
    filters = listOf(
        opcode(Opcode.CONST_4),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterImmediately(),
        ),
        methodCall(
            definingClass = "this",
            parameters = listOf("Z"),
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
    ),
)

/**
 * The controller's key event handler. Matches, in order: the key data read from the event (0),
 * its key code (2) and payload (3), and the flag set after a backspace that lets suggestions
 * replace the toolbar (6).
 */
internal class CandidatesViewControllerKeyFingerprint(eventType: String) : Fingerprint(
    classFingerprint = CandidatesViewControllerFingerprint,
    returnType = "Z",
    parameters = listOf(eventType),
    filters = listOf(
        methodCall(
            definingClass = eventType,
            parameters = emptyList(),
            returnType = "L",
            opcode = Opcode.INVOKE_VIRTUAL,
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET, type = "I", location = MatchAfterWithin(5)),
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            type = "Ljava/lang/Object;",
            location = MatchAfterWithin(5),
        ),
        literal(KEYCODE_DEL),
        opcode(Opcode.IF_NE, MatchAfterImmediately()),
        fieldAccess(
            opcode = Opcode.IPUT_BOOLEAN,
            definingClass = "this",
            type = "Z",
            location = MatchAfterWithin(1),
        ),
    ),
)

/** LatinFixedCountCandidatesHolderView.setCandidates(List): binds a batch and sizes the slots. */
internal object FixedStripSetCandidatesFingerprint : Fingerprint(
    definingClass = FIXED_STRIP,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
)

/**
 * LatinFixedCountCandidatesHolderView.resizeSlots(): sizes the slots again when the strip changes
 * size, for example when a foldable is unfolded.
 */
internal object FixedStripResizeFingerprint : Fingerprint(
    definingClass = FIXED_STRIP,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        // Slot count.
        methodCall(
            definingClass = "this",
            parameters = emptyList(),
            returnType = "I",
            opcode = Opcode.INVOKE_DIRECT,
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        methodCall(
            definingClass = "this",
            parameters = listOf("L", "L", "I", "Z"),
            returnType = "V",
            opcode = Opcode.INVOKE_DIRECT,
            location = MatchAfterWithin(1),
        ),
    ),
)

/** Candidate.Builder.build(), whose class is the candidate builder. */
internal class CandidateBuildFingerprint(candidateType: String) : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = candidateType,
    parameters = emptyList(),
    strings = listOf("Image model %s is not supported"),
)

/** Candidate(Builder), which copies each builder field into the candidate. */
internal class CandidateFromBuilderFingerprint(
    candidateType: String,
    builderType: String,
) : Fingerprint(
    definingClass = candidateType,
    name = "<init>",
    parameters = listOf(builderType),
)

/** Candidate.toString(), which reads the candidate text first. */
internal class CandidateToStringFingerprint(candidateType: String) : Fingerprint(
    definingClass = candidateType,
    name = "toString",
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Ljava/lang/CharSequence;",
        ),
        methodCall(
            definingClass = "Ljava/lang/String;",
            name = "valueOf",
            location = MatchAfterImmediately(),
        ),
        string("Candidate : text = '"),
    ),
)

/**
 * CandidateViewBuilder.addActions(builder, action, index, candidate, ...), which sets a candidate
 * key's tap action.
 */
internal open class CandidateActionsFingerprint(
    candidateType: String,
    filters: List<InstructionFilter>,
) : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "I", candidateType, "Z", "I"),
    strings = listOf("getUiDataAsString"),
    filters = filters,
)

/** The data sent with CANDIDATE_SELECT, which wraps the candidate (the first match's result). */
internal class CandidateSelectDataFingerprint(candidateType: String) : CandidateActionsFingerprint(
    candidateType,
    listOf(
        methodCall(parameters = emptyList(), returnType = "L", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        literal(CANDIDATE_SELECT_CODE, location = MatchAfterWithin(1)),
    ),
)
