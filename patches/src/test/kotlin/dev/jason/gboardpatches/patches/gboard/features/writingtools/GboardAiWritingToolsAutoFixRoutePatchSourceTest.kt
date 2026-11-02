package dev.jason.gboardpatches.patches.gboard.features.writingtools

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAiWritingToolsAutoFixRoutePatchSourceTest {
    @Test
    fun `formal 1803 routes only auto fix through the stock writing tools v2 builder`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/writingtools/GboardAiWritingToolsFlagValuePatch.kt",
            ),
        )

        assertTrue(source.contains("classType = \"Lrzb;\""))
        assertTrue(source.contains("name = \"v\""))
        assertTrue(source.contains("returnType = \"Lvvw;\""))
        assertTrue(source.contains("Lzxi;->c:Lzxi;"))
        assertTrue(source.contains("Lvvl;-><init>(I)V"))
        assertTrue(source.contains("Lvvr;->g()Lvvw;"))
        assertTrue(source.contains("Lvvr;->h(Ljava/lang/Object;)V"))
        assertTrue(source.contains("builderInitIndices.single() + 1"))
        assertTrue(!source.contains("freezeIndices.single(),"))
        assertTrue(source.contains("classType = \"Lgwf;\""))
        assertTrue(source.contains("name = \"a\""))
        assertTrue(source.contains("parameterTypes = listOf(\"Lgwo;\")"))
        assertTrue(source.contains("returnType = \"Llyg;\""))
        assertTrue(source.contains("Lzxi;->c:Lzxi;"))
        assertTrue(!source.contains("Lgwo;->f:Z"))
        assertTrue(!source.contains("Lgwo;->g:Lzxh;"))
        assertTrue(!source.contains("Lzxh;->i:Lzxh;"))
        assertTrue(source.contains("Llyg;->e:Llyg;"))
        assertEquals(5, Regex("findMutableMethodOrThrow\\(").findAll(source).count())
        assertTrue(!source.contains("runCatching"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }
}
