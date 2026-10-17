package dev.jason.gboardpatches.patches.gboard.features.calculator

import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardCalculator1803Targets {
    val onStartInputView = GboardMethodTarget(
        classType = "Loup;",
        name = "onStartInputView",
        parameterTypes = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returnType = "V",
    )
    val onUpdateSelection = GboardMethodTarget(
        classType = "Loup;",
        name = "onUpdateSelection",
        parameterTypes = listOf("I", "I", "I", "I", "I", "I"),
        returnType = "V",
    )
    val onWindowHidden = GboardMethodTarget(
        classType = "Loup;",
        name = "onWindowHidden",
        parameterTypes = emptyList(),
        returnType = "V",
    )

    // Candidate pipeline, used to show results as real Gboard suggestions.
    // LatinCandidatesViewController.appendTextCandidates(List, Candidate, boolean)
    val appendTextCandidates = GboardMethodTarget(
        classType = "Leso;",
        name = "b",
        parameterTypes = listOf("Ljava/util/List;", "Lojt;", "Z"),
        returnType = "V",
    )

    // LatinCandidatesViewController.textCandidatesUpdated(boolean): false clears the strip and
    // brings back the toolbar.
    val candidatesUpdated = GboardMethodTarget(
        classType = "Leso;",
        name = "a",
        parameterTypes = listOf("Z"),
        returnType = "I",
    )

    // LatinFixedCountCandidatesHolderView.setCandidates(List): binds a batch into the strip
    // and sets each slot's width.
    val fixedStripSetCandidates = GboardMethodTarget(
        classType = "Lcom/google/android/apps/inputmethod/latin/keyboard/widget/LatinFixedCountCandidatesHolderView;",
        name = "k",
        parameterTypes = listOf("Ljava/util/List;"),
        returnType = "V",
    )

    // LatinFixedCountCandidatesHolderView.resizeSlots(): sets each slot's width again when the
    // strip changes size, for example when a foldable is unfolded.
    val fixedStripResize = GboardMethodTarget(
        classType = "Lcom/google/android/apps/inputmethod/latin/keyboard/widget/LatinFixedCountCandidatesHolderView;",
        name = "N",
        parameterTypes = emptyList(),
        returnType = "V",
    )

    // GoogleInputMethodService event dispatch (InputBundle.consumeEvent inlined), which
    // handles CANDIDATE_SELECT (-10002) for suggestion taps.
    val consumeEvent = GboardMethodTarget(
        classType = "Loup;",
        name = "au",
        parameterTypes = listOf("Lnur;"),
        returnType = "Z",
    )

    // Set by Gboard after the first letter or backspace; while set, suggestions replace the toolbar.
    const val CONTROLLER_PRIORITY_FIELD = "v:Z"
    // Set when the next batch should clear the strip instead of being appended to it.
    const val CONTROLLER_CLEAR_PENDING_FIELD = "o:Z"
    const val CANDIDATE_BUILDER_TYPE = "Lojq;"
    const val CANDIDATE_BUILDER_TEXT_FIELD = "a:Ljava/lang/CharSequence;"
    const val CANDIDATE_BUILDER_BUILD_METHOD = "a()Lojt;"
    const val EVENT_TYPE = "Lnur;"
    const val EVENT_KEY_DATA_METHOD = "g()Lpnu;"
    const val KEY_DATA_TYPE = "Lpnu;"
    const val KEY_DATA_CODE_FIELD = "c:I"
    const val KEY_DATA_PAYLOAD_FIELD = "e:Ljava/lang/Object;"
    const val CANDIDATE_DATA_TYPE = "Lsst;"
    const val CANDIDATE_DATA_CANDIDATE_FIELD = "c:Lojt;"
}
