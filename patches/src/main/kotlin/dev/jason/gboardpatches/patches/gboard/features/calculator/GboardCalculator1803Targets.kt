package dev.jason.gboardpatches.patches.gboard.features.calculator

import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget

internal object GboardCalculator1803Targets {
    val inputView = GboardFieldTarget(
        classType = "Loup;",
        name = "j",
        type = "Lcom/google/android/libraries/inputmethod/inputview/InputView;",
    )
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
}
