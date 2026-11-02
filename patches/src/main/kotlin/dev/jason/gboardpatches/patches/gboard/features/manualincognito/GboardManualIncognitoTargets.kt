package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardManualIncognitoTargets {
    val onStartInput = method(
        owner = "Lphr;",
        name = "onStartInput",
        parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returns = "V",
    )
    val onStartInputView = method(
        owner = "Lphr;",
        name = "onStartInputView",
        parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returns = "V",
    )
    val onWindowHidden = method("Lphr;", "onWindowHidden", emptyList(), "V")
    val incognitoPredicate = method("Lewx;", "I", emptyList(), "Z")
    val clipboardListener = method("Lfqx;", "onPrimaryClipChanged", emptyList(), "V")
    val voiceEligibility = method(
        owner = "Lvec;",
        name = "aQ",
        parameters = listOf(
            "Landroid/content/Context;",
            "Landroid/view/inputmethod/EditorInfo;",
            "Z",
        ),
        returns = "Z",
    )
    val dictationEligibility = method(
        owner = "Lstb;",
        name = "b",
        parameters = listOf("Leta;", "Z", "Z"),
        returns = "Lkxa;",
    )
    private fun method(
        owner: String,
        name: String,
        parameters: List<String>,
        returns: String,
    ) = GboardMethodTarget(owner, name, parameters, returns)
}
