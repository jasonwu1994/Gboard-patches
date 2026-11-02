package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsInputEventPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsGesturePatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsPointerOwnerPatch
import dev.jason.gboardpatches.patches.gboard.features.longpressquickactions.gboardLongPressQuickActionsSoftKeyPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardLongPressQuickActionsPatchContractTest {
    @Test
    fun publicPatchIsIndependentAndExact1803Only() {
        val patch = gboardLongPressQuickActionsPatch
        assertEquals("Long-Press Editing Shortcuts", patch.name)
        assertEquals(LONG_PRESS_DESCRIPTION, patch.description)
        assertTrue(patch.default)

        assertEquals(
            listOf(
                gboardPatchesSettingsPatch,
                gboardLongPressQuickActionsFeatureMarkerPatch,
                gboardLongPressQuickActionsSoftKeyPatch,
                gboardLongPressQuickActionsInputEventPatch,
                gboardLongPressQuickActionsPointerOwnerPatch,
                gboardLongPressQuickActionsGesturePatch,
            ),
            patch.dependencies.toList(),
        )
        assertFalse(patch.dependencies.any { dependency ->
            dependency.toString().contains("AdvancedVoice", ignoreCase = true)
        })

        assertEquals(
            "18.5.4.993608717-release-arm64-v8a",
            patch.compatibility!!.single().targets.single().version,
        )
    }

    @Test
    fun generatedInventoryContainsExactlyOneLongPressShortcutsRow() {
        val rows = generatedPublishedPatches()
            .filter { it.get("name").asString == "Long-Press Editing Shortcuts" }

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(LONG_PRESS_DESCRIPTION, row.get("description").asString)
        assertTrue(row.get("use").asBoolean)
        assertEquals(0, row.getAsJsonArray("options").size())
        assertEquals(
            listOf("18.5.4.993608717-release-arm64-v8a"),
            row.getAsJsonObject("compatiblePackages")
                .getAsJsonArray("com.google.android.inputmethod.latin")
                .map { it.asString },
        )
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val LONG_PRESS_DESCRIPTION =
            "在英文 QWERTY 與注音鍵盤加入全選、復原、複製、剪下、貼上與重做長按快捷鍵\n" +
                "Add Select all, Undo, Copy, Cut, Paste, and Redo long-press shortcuts " +
                "to English QWERTY and Zhuyin."
    }
}
