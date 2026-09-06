package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCalculatorPatchRegistryTest {
    @Test
    fun calculatorIsAnIndependentDefaultEnabledPublicPatch() {
        assertEquals("Simple Calculator", gboardSimpleCalculatorPatch.name)
        assertTrue(gboardSimpleCalculatorPatch.default)
        assertTrue(
            gboardSimpleCalculatorPatch.description.orEmpty().contains(
                "scrollable equation plus its answer",
            ),
        )
        assertEquals(
            setOf("simple_calculator.lifecycle"),
            GboardTargetAdmission.admittedContributionIdsByFeature["simple_calculator"],
        )
    }
}
