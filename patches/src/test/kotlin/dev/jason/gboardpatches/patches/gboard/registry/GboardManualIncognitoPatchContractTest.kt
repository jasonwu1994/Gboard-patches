package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoAccessPointPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.manualincognito.gboardManualIncognitoPolicyPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardManualIncognitoPatchContractTest {
    @Test
    fun publicPatchOwnsComplete1777PortAndKeepsSettingsMasterOffByDefault() {
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
                gboardManualIncognitoAccessPointPatch,
            ),
            patch.dependencies.toList(),
        )
        assertEquals(
            "17.7.7.932364120-release-arm64-v8a",
            patch.compatibility!!.single().targets.single().version,
        )
    }
}
