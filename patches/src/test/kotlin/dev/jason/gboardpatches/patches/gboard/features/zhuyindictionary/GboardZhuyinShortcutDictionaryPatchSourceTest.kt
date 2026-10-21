package dev.jason.gboardpatches.patches.gboard.features.zhuyindictionary

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardZhuyinShortcutDictionaryPatchSourceTest {
    @Test
    fun `shortcut feature enables Zhuyin shortcuts dictionary flag`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/zhuyindictionary/GboardZhuyinShortcutDictionaryPatch.kt",
            ),
            StandardCharsets.UTF_8,
        )
        val implementation = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/zhuyindictionary/GboardZhuyinDictionaryFlagPatch.kt",
            ),
            StandardCharsets.UTF_8,
        )

        assertTrue(source.contains("ZhuyinDictionaryFlag.SHORTCUTS"))
        assertTrue(implementation.contains("SHORTCUTS(3, \"shortcuts dictionary\")"))
        assertTrue(implementation.contains("Lhbd;->h:[Z"))
        assertTrue(implementation.contains("shortcuts dictionary"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }
}
