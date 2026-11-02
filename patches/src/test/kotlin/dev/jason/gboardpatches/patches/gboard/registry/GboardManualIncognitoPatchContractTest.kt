package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoPolicyPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardManualIncognitoPatchContractTest {
    @Test
    fun publicPatchOwnsComplete1803PortAndKeepsSettingsMasterOffByDefault() {
        val patch = gboardManualIncognitoModePatch
        assertEquals("Incognito Mode Toggle", patch.name)
        assertEquals(
            "在 Access Point 工具列新增無痕模式切換按鈕，並可設定無痕模式下是否啟用剪貼簿與語音輸入\n" +
                "Add an Incognito toggle to the Access Point toolbar and configure clipboard and voice typing availability while Incognito mode is active.",
            patch.description,
        )
        assertTrue(patch.default)
        assertEquals(
            listOf(
                gboardPatchesSettingsPatch,
                gboardManualIncognitoFeatureMarkerPatch,
                gboardManualIncognitoLifecyclePatch,
                gboardManualIncognitoPolicyPatch,
                gboardAccessPointContributions1803Patch,
            ),
            patch.dependencies.toList(),
        )
        assertEquals(
            "18.5.4.993608717-release-arm64-v8a",
            patch.compatibility!!.single().targets.single().version,
        )
    }
}
