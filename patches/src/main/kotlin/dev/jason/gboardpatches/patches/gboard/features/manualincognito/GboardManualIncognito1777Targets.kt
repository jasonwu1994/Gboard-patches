package dev.jason.gboardpatches.patches.gboard.features.manualincognito

import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardManualIncognito1777Targets {
    val onStartInput = method(
        owner = "Loau;",
        name = "onStartInput",
        parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returns = "V",
    )
    val onStartInputView = method(
        owner = "Loau;",
        name = "onStartInputView",
        parameters = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returns = "V",
    )
    val onWindowHidden = method("Loau;", "onWindowHidden", emptyList(), "V")
    val incognitoPredicate = method("Leht;", "E", emptyList(), "Z")
    val clipboardListener = method("Lewb;", "onPrimaryClipChanged", emptyList(), "V")
    val voiceEligibility = method(
        owner = "Lttb;",
        name = "cn",
        parameters = listOf(
            "Landroid/content/Context;",
            "Landroid/view/inputmethod/EditorInfo;",
            "Z",
        ),
        returns = "Z",
    )
    val dictationEligibility = method(
        owner = "Lriv;",
        name = "b",
        parameters = listOf("Ledx;", "Z", "Z"),
        returns = "Ljtu;",
    )
    val orderFactory = method(
        owner = "Llqa;",
        name = "m",
        parameters = listOf(
            "Landroid/content/Context;",
            "Labpm;",
            "Lmsw;",
            "Lvbq;",
            "Lvbq;",
            "Z",
        ),
        returns = "Llqa;",
    )
    val accessPointControllerConstructor = method(
        owner = "Llrq;",
        name = "<init>",
        parameters = listOf("Landroid/content/Context;", "Lltc;"),
        returns = "V",
    )

    private fun method(
        owner: String,
        name: String,
        parameters: List<String>,
        returns: String,
    ) = GboardMethodTarget(owner, name, parameters, returns)
}
