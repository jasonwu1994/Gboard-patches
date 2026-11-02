package dev.jason.gboardpatches.patches.gboard.features.calculator

import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardCalculatorTargets {
    val inputView = GboardFieldTarget(
        classType = "Lphr;",
        name = "i",
        type = "Lcom/google/android/libraries/inputmethod/inputview/InputView;",
    )
    val onStartInputView = GboardMethodTarget(
        classType = "Lphr;",
        name = "onStartInputView",
        parameterTypes = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returnType = "V",
    )
    val onUpdateSelection = GboardMethodTarget(
        classType = "Lphr;",
        name = "onUpdateSelection",
        parameterTypes = listOf("I", "I", "I", "I", "I", "I"),
        returnType = "V",
    )
    val onWindowHidden = GboardMethodTarget(
        classType = "Lphr;",
        name = "onWindowHidden",
        parameterTypes = emptyList(),
        returnType = "V",
    )
}
