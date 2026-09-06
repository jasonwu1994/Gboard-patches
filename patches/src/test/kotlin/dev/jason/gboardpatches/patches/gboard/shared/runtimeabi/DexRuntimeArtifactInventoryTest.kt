package dev.jason.gboardpatches.patches.gboard.shared.runtimeabi

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DexRuntimeArtifactInventoryTest {
    @Test
    fun `verifies the authoritative contract against freshly built extension artifacts`() {
        val outputDirectories = requireNotNull(
            System.getProperty("gboard.runtimeAbiOutputDirectories"),
        ).split(File.pathSeparator).map(::File)
        outputDirectories.forEach { outputDirectory ->
            require(outputDirectory.isDirectory) {
                "Extension output directory not found: $outputDirectory"
            }
        }
        val artifacts = outputDirectories.flatMap { outputDirectory ->
            outputDirectory.walkTopDown()
                .filter { file -> file.isFile && file.extension == "rve" }
                .toList()
        }
        require(artifacts.isNotEmpty()) { "No .rve artifacts found under $outputDirectories" }
        val inventories = artifacts.map(DexRuntimeArtifactInventory::read)
        val compositeInventory = RuntimeArtifactInventory { owner, name ->
            inventories.flatMap { inventory -> inventory.methods(owner, name) }
        }

        val mismatches = RuntimeAbiVerifier.verify(
            calls = RuntimeCallId.entries,
            inventory = compositeInventory,
        )

        assertEquals(emptyList<RuntimeAbiMismatch>(), mismatches)
    }
}
