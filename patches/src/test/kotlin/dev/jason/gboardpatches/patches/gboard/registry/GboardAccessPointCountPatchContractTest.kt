package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.ACCESS_POINT_COUNT_FEATURE_MARKER
import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.gboardAccessPointCountFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.accesspointcount.gboardAccessPointCountFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAccessPointCountPatchContractTest {
    @Test
    fun `public patch is independently published and enabled by default`() {
        val patch = GboardPublishedPatchCatalog.morpheRegistrations.single { candidate ->
            candidate.name == "Top Toolbar Item Count"
        }

        assertSame(gboardAccessPointCountPatch, patch)
        assertTrue(patch.default)
        assertEquals(
            "自訂 Gboard 頂端工具列項目數量\n" +
                "Customize the Gboard top toolbar item count.\nPersonnaliser le nombre d’éléments de la barre d’outils supérieure de Gboard.",
            patch.description,
        )
    }

    @Test
    fun `public patch owns its settings marker and flag contribution`() {
        assertTrue(gboardAccessPointCountPatch.dependencies.any { dependency ->
            dependency === gboardPatchesSettingsPatch
        })
        assertTrue(gboardAccessPointCountPatch.dependencies.any { dependency ->
            dependency === gboardAccessPointCountFeatureMarkerPatch
        })
        assertTrue(gboardAccessPointCountPatch.dependencies.any { dependency ->
            dependency === gboardAccessPointCountFlagValuePatch
        })
        assertEquals(
            "dev.jason.gboardpatches.feature.access_point_count",
            ACCESS_POINT_COUNT_FEATURE_MARKER,
        )
    }
}
