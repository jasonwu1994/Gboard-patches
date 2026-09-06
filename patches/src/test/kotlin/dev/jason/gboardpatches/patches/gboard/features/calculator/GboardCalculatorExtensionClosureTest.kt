package dev.jason.gboardpatches.patches.gboard.features.calculator

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCalculatorExtensionClosureTest {
    @Test
    fun sharedExtensionExcludesCalculatorRuntimeAndFeatureExtensionOwnsIt() {
        val artifacts = requireNotNull(
            System.getProperty("gboard.runtimeAbiOutputDirectories"),
        ).split(File.pathSeparator)
            .map(::File)
            .flatMap { directory ->
                directory.walkTopDown()
                    .filter { file -> file.isFile && file.extension == "rve" }
                    .toList()
            }
        val shared = artifacts.single { it.name == "gboard-patches.rve" }
        val calculator = artifacts.single { it.name == "gboard-calculator.rve" }
        val runtimeDescriptor =
            "Ldev/jason/gboardpatches/extension/calculator/GboardCalculatorRuntime;"
        val sharedText = shared.readBytes().toString(Charsets.ISO_8859_1)
        val calculatorText = calculator.readBytes().toString(Charsets.ISO_8859_1)

        assertFalse(sharedText.contains(runtimeDescriptor))
        assertTrue(calculatorText.contains(runtimeDescriptor))
    }
}
