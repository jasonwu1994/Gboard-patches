package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.patch.bytecodePatch
import dev.jason.gboardpatches.patches.gboard.shared.mergeBundledExtension

internal val gboardCalculatorExtensionPatch = bytecodePatch(
    description = "將 Simple Calculator 專屬 runtime extension 併入 target APK。",
) {
    execute {
        mergeBundledExtension(this, "extensions/gboard-calculator.rve")
    }
}
