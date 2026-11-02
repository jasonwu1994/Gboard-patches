package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints.gboardEditingAccessPointsLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.editingaccesspoints.gboardEditingAccessPointsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import org.junit.Assert.*
import org.junit.Test

class GboardEditingAccessPointsPatchContractTest {
    @Test fun standalonePatchHasNoOtherPublicPatchDependency() {
        val patch = GboardPublishedPatchCatalog.morpheRegistrations.single {
            it.name == "Toolbar Editing Buttons"
        }
        assertSame(gboardEditingAccessPointsPatch, patch)
        assertTrue(patch.default)
        assertTrue(patch.dependencies.contains(gboardEditingAccessPointsLifecyclePatch))
        assertTrue(patch.dependencies.contains(gboardEditingAccessPointsFeatureMarkerPatch))
        assertTrue(patch.dependencies.contains(gboardAccessPointContributions1803Patch))
        val publicNames = GboardPublishedPatchCatalog.morpheRegistrations.mapNotNull { it.name }
        assertTrue(patch.dependencies.none { it.name in publicNames })
        assertEquals(setOf("editing_access_points.runtime"),
            GboardTargetAdmission.admittedContributionIdsByFeature["editing_access_points"])
    }

    @Test fun editorAbiDoesNotExposeObfuscatedTypes() {
        assertEquals("(Ljava/lang/Object;)V",
            RuntimeAbiCatalog.abi(RuntimeCallId.EDITING_ACCESS_POINTS_START).descriptor)
        assertEquals("()V",
            RuntimeAbiCatalog.abi(RuntimeCallId.EDITING_ACCESS_POINTS_END).descriptor)
    }
}
