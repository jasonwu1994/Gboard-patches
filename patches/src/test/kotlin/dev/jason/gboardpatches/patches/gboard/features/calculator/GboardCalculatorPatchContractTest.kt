package dev.jason.gboardpatches.patches.gboard.features.calculator

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCalculatorPatchContractTest {
    @Test
    fun targetSpecificDetailsStayInTheDedicatedTargetAndDelegateFiles() {
        val target = source("GboardCalculatorTargets.kt")
        val lifecycle = source("GboardCalculatorLifecyclePatch.kt")

        // Gboard's code is found by fingerprint, without obfuscated class names.
        assertFalse(Regex("\"L[a-z]{1,4};\"").containsMatchIn(target))
        assertTrue(target.contains("\"onStartInputView\""))
        assertTrue(target.contains("\"onUpdateSelection\""))
        assertTrue(target.contains("\"onWindowHidden\""))
        assertTrue(lifecycle.contains("SELECTION_HELPER_NAME"))
        assertTrue(lifecycle.contains("p0, p3, p4"))

        val portableRoot = repositoryRoot().resolve(
            "extensions/calculator/src/main/java/dev/jason/gboardpatches/extension/calculator",
        )
        Files.list(portableRoot).use { files ->
            files.filter { Files.isRegularFile(it) }.forEach { file ->
                val contents = Files.readString(file, StandardCharsets.UTF_8)
                assertFalse(file.toString(), contents.contains("Lphr;"))
                assertFalse(file.toString(), contents.contains("onUpdateSelection"))
            }
        }
    }

    @Test
    fun runtimeAbiAndAdmissionContainTheCompleteLifecycle() {
        assertEquals(
            setOf("simple_calculator.lifecycle"),
            GboardTargetAdmission.admittedContributionIdsByFeature["simple_calculator"],
        )
        assertEquals(
            "(Ljava/lang/Object;)V",
            RuntimeAbiCatalog.abi(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_VIEW_STARTED,
            ).descriptor,
        )
        assertTrue(
            RuntimeAbiCatalog.abi(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_VIEW_STARTED,
            ).owner.endsWith("/GboardCalculatorLifecycleRuntime;"),
        )
        assertEquals(
            "(Ljava/lang/Object;II)V",
            RuntimeAbiCatalog.abi(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_SELECTION_UPDATED,
            ).descriptor,
        )
        assertEquals(
            "()V",
            RuntimeAbiCatalog.abi(
                RuntimeCallId.CALCULATOR_RUNTIME_ON_INPUT_WINDOW_HIDDEN,
            ).descriptor,
        )
    }

    private fun source(name: String): String = Files.readString(
        repositoryRoot().resolve(
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                "features/calculator/$name",
        ),
        StandardCharsets.UTF_8,
    )

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (!Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.parent ?: error("Repository root not found")
        }
        return current
    }
}
