package dev.jason.gboardpatches.patches.gboard.registry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardDeveloperOptionsPatchContractSourceTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun publicPatchIsActiveWhileMarkerImplementationRemainsAvailable() {
        val registry = readSource(REGISTRY_PATH)
        val activeRegistry = registry.replace(Regex("(?s)/\\*.*?\\*/"), "")

        assertTrue(registry.contains("val gboardDeveloperOptionsPatch = gboardPublicResourcePatch("))
        assertTrue(activeRegistry.contains("val gboardDeveloperOptionsPatch = gboardPublicResourcePatch("))
        assertEquals(1, registry.countOccurrences("name = \"Developer options\""))
        assertTrue(registry.contains("gboardPatchesSettingsPatch"))
        assertTrue(registry.contains("gboardDeveloperOptionsFeatureMarkerPatch"))

        val markerPath = repositoryRoot.resolve(MARKER_PATH)
        assertTrue("Developer options feature marker source missing", Files.isRegularFile(markerPath))
        val marker = readSource(MARKER_PATH)
        assertTrue(marker.contains("gboardDeveloperOptionsFeatureMarkerPatch"))
        assertTrue(marker.contains("applyFeatureMarker(DEVELOPER_OPTIONS_FEATURE_MARKER_NAME)"))
        assertTrue(marker.contains("dev.jason.gboardpatches.feature.developer_options"))
        assertTrue(marker.contains("ensurePackageQueries()"))
        SUPPORTED_PACKAGES.forEach { packageName ->
            assertTrue("Marker must query $packageName", marker.contains("\"$packageName\""))
        }

        val sharedSettingsPatch = readSource(SETTINGS_PATCH_PATH)
        SUPPORTED_PACKAGES.forEach { packageName ->
            assertFalse(
                "Package visibility must not be globally injected by the shared settings patch",
                sharedSettingsPatch.contains(packageName),
            )
        }
    }

    @Test
    fun extensionRegistersMarkerGatedDirectRootAction() {
        val availability = readSource(AVAILABILITY_PATH)
        val featureRegistry = readSource(FEATURE_REGISTRY_PATH)
        val featurePath = repositoryRoot.resolve(FEATURE_PATH)
        val contract = readSource(CONTRACT_PATH)
        val activity = readSource(ACTIVITY_PATH)
        val orchestrator = readSource(ORCHESTRATOR_PATH)

        assertTrue(availability.contains("FEATURE_DEVELOPER_OPTIONS"))
        assertTrue(availability.contains("dev.jason.gboardpatches.feature.developer_options"))
        assertTrue(featureRegistry.contains("new GboardMoreSettingsGroupFeature("))
        assertTrue(readSource(FEATURE_REGISTRY_PATH.replace(
            "GboardPatchesSettingsFeatureRegistry.java", "GboardMoreSettingsGroupFeature.java"))
            .contains("new GboardDeveloperOptionsSettingsFeature("))
        assertTrue("Developer options settings feature missing", Files.isRegularFile(featurePath))

        val feature = readSource(FEATURE_PATH)
        assertTrue(feature.contains("FEATURE_DEVELOPER_OPTIONS"))
        assertTrue(
            feature.contains(
                "public void openRootEntry(GboardPatchesSettingsContract.FeatureHost host)",
            ),
        )
        assertTrue(
            feature.contains(
                "GboardPatchesSettingsContract.openTargetSettingsHeader(" +
                    "host, TARGET_HEADER_KEY_RESOURCE_ID)",
            ),
        )
        assertTrue(feature.contains("0x7f140a6d"))
        assertFalse(feature.contains("openFeature("))
        assertFalse(feature.contains("DeveloperSettingsFragment"))
        assertFalse(feature.contains(":android:show_fragment"))

        assertTrue(contract.contains("default void openRootEntry(FeatureHost host)"))
        assertTrue(contract.contains("void submit(Intent intent);"))
        assertTrue(
            contract.contains(
                "public static void openTargetSettingsHeader(" +
                    "FeatureHost host, int headerKeyResourceId)",
            ),
        )
        assertTrue(
            contract.contains(
                "submit(host, adapter -> adapter.openTargetSettingsHeader(headerKeyResourceId))",
            ),
        )
        assertTrue(contract.contains("default void openTargetSettingsHeader(int headerKeyResourceId)"))
        assertTrue(activity.contains("() -> feature.openRootEntry(host)"))
        assertTrue(activity.contains("GboardPatchesSettingsOrchestrator.Event.featureIntent(intent)"))
        assertTrue(activity.contains("case EXECUTE_INTENT:"))
        assertTrue(activity.contains("intent.apply(this);"))
        assertTrue(orchestrator.contains("case FEATURE_INTENT:"))
        assertTrue(orchestrator.contains("EffectKind.EXECUTE_INTENT"))
        assertFalse(activity.contains("() -> host.openFeature(feature)"))
    }

    @Test
    fun activityUsesCurrentPackageExact1853HeaderNavigationAndGuardsUnverifiedPackages() {
        val activity = readSource(ACTIVITY_PATH)

        assertTrue(activity.contains("ENTER_PREF_HEADER"))
        assertTrue(activity.contains("18.5.4.993608717-release-arm64-v8a"))
        assertTrue(activity.contains("targetPackageVersionName(packageName)"))
        assertTrue(activity.contains("SUPPORTED_DEVELOPER_OPTIONS_TARGET_VERSION.equals("))
        assertTrue(activity.contains("intent.putExtra(ENTER_PREF_HEADER_EXTRA, headerKeyResourceId)"))
        assertTrue(activity.contains("new ComponentName(packageName, GBOARD_SETTINGS_ACTIVITY_CLASS)"))
        assertTrue(activity.contains("String currentPackage = getPackageName();"))
        assertTrue(activity.contains("isSupportedTargetPackage(currentPackage)"))
        assertTrue(activity.contains("showUnsupportedDeveloperOptionsPackageDialog("))
        assertTrue(activity.contains("tryLaunchUnverifiedDeveloperOptions("))
        assertTrue(activity.contains("catch (Throwable throwable)"))
        assertTrue(activity.contains("getPackageInfo(packageName, 0)"))
        SUPPORTED_PACKAGES.forEach { packageName ->
            assertTrue("Activity must support $packageName", activity.contains("\"$packageName\""))
        }

        assertFalse(activity.contains(":android:show_fragment"))
        assertFalse(activity.contains("DeveloperSettingsFragment"))
        assertFalse(activity.contains("versionName.startsWith("))
        assertFalse(activity.contains("17.0.10"))
        assertFalse(activity.contains("fallbackFragment"))
    }

    private fun readSource(relativePath: String): String = String(
        Files.readAllBytes(repositoryRoot.resolve(relativePath)),
        StandardCharsets.UTF_8,
    )

    private fun findRepositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { candidate ->
                Files.isRegularFile(candidate.resolve("README.md")) &&
                    Files.isRegularFile(candidate.resolve(REGISTRY_PATH))
            }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length, 1).count { it == value }

    private companion object {
        const val REGISTRY_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/registry/" +
                "GboardPatchRegistry.kt"
        const val MARKER_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/" +
                "developeroptions/GboardDeveloperOptionsFeatureMarkerPatch.kt"
        const val SETTINGS_PATCH_PATH =
            "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/shared/" +
                "GboardPatchesSettingsPatch.kt"
        const val AVAILABILITY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesFeatureAvailability.java"
        const val FEATURE_REGISTRY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesSettingsFeatureRegistry.java"
        const val FEATURE_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "developeroptions/GboardDeveloperOptionsSettingsFeature.java"
        const val CONTRACT_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesSettingsContract.java"
        const val ACTIVITY_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesSettingsActivity.java"
        const val ORCHESTRATOR_PATH =
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/settings/" +
                "GboardPatchesSettingsOrchestrator.java"
        val SUPPORTED_PACKAGES = listOf(
            "com.google.android.inputmethod.latin",
            "com.google.android.inputmethod.latin.jason.dev",
            "com.touchtype.swiftkey",
        )
    }
}
