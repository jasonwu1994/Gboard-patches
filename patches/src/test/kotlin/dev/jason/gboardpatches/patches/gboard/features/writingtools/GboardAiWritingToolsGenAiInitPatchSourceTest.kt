package dev.jason.gboardpatches.patches.gboard.features.writingtools

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAiWritingToolsGenAiInitPatchSourceTest {
    @Test
    fun `formal 1803 promotes only the transient unknown client at the GenAI init call site`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/writingtools/GboardAiWritingToolsFlagValuePatch.kt",
            ),
        )

        assertTrue(source.contains("classType = \"Lifn;\""))
        assertTrue(source.contains("name = \"c\""))
        assertTrue(source.contains("Lidr;->j:Lknm;"))
        assertTrue(source.contains("Lunb;->cH(Lknm;)Z"))
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_APPLY_GEN_AI_INIT_CLIENT_TYPE",
            ),
        )
        assertTrue(source.contains("gboardAiWritingTools1803GenAiInitPatch"))
    }

    @Test
    fun `formal 1803 also constructs SmartEdit while the client type is transient unknown`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/writingtools/GboardAiWritingToolsFlagValuePatch.kt",
            ),
        )

        assertTrue(source.contains("name = \"d\""))
        assertTrue(source.contains("Lunb;->cG(Lknm;)Z"))
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_APPLY_SMART_EDIT_INIT_CLIENT_TYPE",
            ),
        )
        assertTrue(source.contains("gboardAiWritingTools1803SmartEditInitPatch"))
    }

    @Test
    fun `formal 1803 retries GenAI manager after Smart Dictation client refresh`() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/writingtools/GboardAiWritingToolsFlagValuePatch.kt",
            ),
        )

        assertTrue(source.contains("name = \"e\""))
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_REMEMBER_GEN_AI_INIT_CALL",
            ),
        )
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_BEGIN_GEN_AI_REFRESH",
            ),
        )
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_OBSERVE_GEN_AI_REFRESH_CLIENT_TYPE",
            ),
        )
        assertTrue(
            source.contains(
                "AI_WRITING_TOOLS_VOICE_COMMAND_RUNTIME_FINISH_GEN_AI_REFRESH",
            ),
        )
        assertTrue(source.contains("gboardAiWritingTools1803GenAiRefreshPatch"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }
}
