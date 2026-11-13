package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardSwipeDismissPatchRegistryTest {
    @Test
    fun swipeDismissIsAnIndependentDefaultEnabledPublicPatch() {
        assertEquals("Swipe Down to Dismiss Keyboard", gboardSwipeDismissPatch.name)
        assertTrue(gboardSwipeDismissPatch.default)
        assertTrue(
            gboardSwipeDismissPatch.description.orEmpty().contains(
                "從建議列或工具列向下滑動以關閉鍵盤。",
            ),
        )
        assertTrue(
            gboardSwipeDismissPatch.description.orEmpty().contains(
                "Swipe down from the suggestions or toolbar to close the keyboard.\nBalayer vers le bas depuis les suggestions ou la barre d’outils pour fermer le clavier.",
            ),
        )
        assertEquals(
            setOf("swipe_down_to_dismiss_keyboard.dispatch"),
            GboardTargetAdmission.admittedContributionIdsByFeature["swipe_down_to_dismiss_keyboard"],
        )
    }
}
