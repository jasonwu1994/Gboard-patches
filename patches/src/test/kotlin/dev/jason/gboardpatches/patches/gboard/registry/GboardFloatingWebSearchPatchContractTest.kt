package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.websearch.gboardFloatingWebSearchFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.websearch.gboardFloatingWebSearchManifestPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardFloatingWebSearchPatchContractTest {
    @Test
    fun publicPatchIsIndependentDefaultOnAndOwnsTheRequiredClosure() {
        val patch = GboardPublishedPatchCatalog.morpheRegistrations.single {
            it.name == "Floating Web Search"
        }
        assertSame(gboardFloatingWebSearchPatch, patch)
        assertTrue(patch.default)
        assertEquals(
            "直接從 Gboard 開啟懸浮網頁，快速搜尋需要的資訊。\n" +
                "Open a floating web page directly from Gboard to quickly search for the information you need.",
            patch.description,
        )
        assertTrue(patch.dependencies.any { it === gboardPatchesSettingsPatch })
        assertTrue(patch.dependencies.any { it === gboardFloatingWebSearchFeatureMarkerPatch })
        assertTrue(patch.dependencies.any { it === gboardFloatingWebSearchManifestPatch })
        assertTrue(patch.dependencies.any { it === gboardAccessPointContributions1803Patch })
        val publicNames = GboardPublishedPatchCatalog.morpheRegistrations
            .mapNotNull { it.name }
            .toSet()
        assertTrue(patch.dependencies.mapNotNull { it.name }.none { it in publicNames })
    }

    @Test
    fun manifestContractAddsOnlyMarkerAndBrowserVisibilityQueries() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/websearch/GboardFloatingWebSearchManifestPatch.kt",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(source.contains("android.support.customtabs.action.CustomTabsService"))
        assertTrue(source.contains("android.intent.action.VIEW"))
        assertTrue(source.contains("android.intent.category.BROWSABLE"))
        assertTrue(source.contains("HTTPS_SCHEME = \"https\""))
        assertFalse(source.contains("WebView"))
        assertFalse(source.contains("ensureActivity"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }
}
