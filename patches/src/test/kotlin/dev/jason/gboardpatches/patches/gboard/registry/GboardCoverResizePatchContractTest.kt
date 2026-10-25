package dev.jason.gboardpatches.patches.gboard.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardCoverResizePatchContractTest {
    @Test
    fun originalPatchesArePreservedAndCoverResizeIsOptIn() {
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations
        val resize = registrations.single { it.name == "Independent cover/main Resize profiles" }
        assertEquals(45, registrations.size)
        assertEquals(44, registrations.count { it !== resize })
        assertFalse(resize.use)
        assertTrue(registrations.any { it.name == "Package Rename" })
    }
}
