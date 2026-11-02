package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.keypresshaptics.gboardKeypressHapticsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.keypresshaptics.gboardKeypressHapticsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Assert.*
import org.junit.Test

class GboardKeypressHapticsPatchContractTest {
    @Test fun `haptics is independently selectable without another feature dependency`() {
        val patch = gboardKeypressHapticsPatch
        assertEquals("Keypress Vibration Intensity Control", patch.name)
        assertEquals(listOf(gboardPatchesSettingsPatch, gboardKeypressHapticsFeatureMarkerPatch,
            gboardKeypressHapticsFlagValuePatch), patch.dependencies.toList())
        assertSame(patch, GboardPublishedPatchCatalog.morpheRegistrations.single {
            it.name == "Keypress Vibration Intensity Control"
        })
    }
}
