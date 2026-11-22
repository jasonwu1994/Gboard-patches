package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardShiftDeletePatchRegistryTest {
    @Test
    fun shiftDeleteIsAnIndependentDefaultEnabledPublicPatch() {
        assertEquals("Shift + Backspace Forward Delete", gboardShiftDeletePatch.name)
        assertTrue(gboardShiftDeletePatch.default)
        assertTrue(
            gboardShiftDeletePatch.description.orEmpty().contains(
                "按住 Shift 再按退格鍵，會刪除游標右邊的字，作用等同電腦鍵盤的 Delete 鍵。",
            ),
        )
        assertTrue(
            gboardShiftDeletePatch.description.orEmpty().contains(
                "Hold Shift and press Backspace to delete the character after the cursor, like the Delete key on a computer keyboard.",
            ),
        )
        assertEquals(
            setOf("shift_backspace_forward_delete.dispatch"),
            GboardTargetAdmission.admittedContributionIdsByFeature["shift_backspace_forward_delete"],
        )
    }
}
