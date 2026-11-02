package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.closeproactivesuggestions.gboardCloseProactiveSuggestionsFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.closeproactivesuggestions.gboardCloseProactiveSuggestionsFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.quickinsert.gboardQuickInsertFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.quickinsert.gboardQuickInsertFlagValuePatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardRuntimePatchesContractTest {
    @Test
    fun `remaining features are independently published and enabled by default`() {
        val registrations = GboardPublishedPatchCatalog.morpheRegistrations
        assertTrue(registrations.none { it.name == "Hyperspeed Typing Animation" })
        val close = registrations.single { patch ->
            patch.name == "Close Proactive Suggestions"
        }
        val quick = registrations.single { patch -> patch.name == "Quick Insert" }

        assertSame(gboardCloseProactiveSuggestionsPatch, close)
        assertSame(gboardQuickInsertPatch, quick)

        val generated = generatedPublishedPatches().associateBy { patch ->
            patch["name"].asString
        }
        assertTrue(generated.getValue(close.name!!)["use"].asBoolean)
        assertTrue(generated.getValue(quick.name!!)["use"].asBoolean)
    }

    @Test
    fun `each public patch owns only its feature marker and runtime contributions`() {
        assertTrue(gboardCloseProactiveSuggestionsPatch.dependencies.any { dependency ->
            dependency === gboardCloseProactiveSuggestionsFeatureMarkerPatch
        })
        assertTrue(gboardCloseProactiveSuggestionsPatch.dependencies.any { dependency ->
            dependency === gboardCloseProactiveSuggestionsFlagValuePatch
        })


        assertTrue(gboardQuickInsertPatch.dependencies.any { dependency ->
            dependency === gboardQuickInsertFeatureMarkerPatch
        })
        assertTrue(gboardQuickInsertPatch.dependencies.any { dependency ->
            dependency === gboardQuickInsertFlagValuePatch
        })
    }

    @Test
    fun `public descriptions match the requested bilingual product copy`() {
        assertEquals(
            "在主動建議列顯示關閉按鈕\n" +
                "Show a dismiss button in the proactive suggestions bar.",
            gboardCloseProactiveSuggestionsPatch.description,
        )
        assertEquals(
            "啟用快速插入面板與工具列入口\n" +
                "Enable the Quick Insert panel and toolbar access point.",
            gboardQuickInsertPatch.description,
        )
    }
}
