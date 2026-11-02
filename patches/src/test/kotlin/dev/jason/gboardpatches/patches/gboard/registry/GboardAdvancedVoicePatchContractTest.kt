package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonParser
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoice1803ZhTwPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.advancedvoice.gboardAdvancedVoiceFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.features.rambler.gboardRambler1803OfficialSelectorPatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.generated.GboardTargetAdmission
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardAdvancedVoicePatchContractTest {
    @Test
    fun releasePublishesExactlyOneTargetOnlyAdvancedVoiceTypingPatch() {
        val rows = generatedPublishedPatches()
            .filter { it.get("name").asString == "Advanced Voice Typing" }

        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.get("use").asBoolean)
        val compatiblePackages = row.getAsJsonObject("compatiblePackages")
        assertEquals(setOf(GBOARD_PACKAGE), compatiblePackages.keySet())
        assertEquals(
            listOf(GBOARD_VERSION),
            compatiblePackages.getAsJsonArray(GBOARD_PACKAGE).map { it.asString },
        )
    }

    @Test
    fun formal1853AdmissionUsesReviewedTypedVoiceAndOfficialSelectionBindings() {
        assertEquals(
            setOf(
                "advanced_voice_typing.flag",
                "advanced_voice_typing.bytecode",
            ),
            GboardTargetAdmission.admittedContributionIdsByFeature
                .getValue("advanced_voice_typing"),
        )
        assertEquals(
            listOf(
                gboardPatchesSettingsPatch,
                gboardAdvancedVoiceFeatureMarkerPatch,
                gboardAdvancedVoiceFlagValuePatch,
                gboardAdvancedVoice1803ZhTwPatch,
                gboardRambler1803OfficialSelectorPatch,
            ),
            gboardAdvancedVoiceTypingPatch.dependencies.toList(),
        )

        val bindings = JsonParser.parseString(
            Files.readString(
                repositoryRoot().resolve(
                    "patches/src/main/resources/gboard/gboard-version-bindings.json",
                ),
                StandardCharsets.UTF_8,
            ),
        ).asJsonObject.getAsJsonObject("bindings")
        val expected = linkedMapOf(
            "voice_native_readiness" to "Lssi;->a()Z",
            "voice_eligibility_constructor" to
                "Lssx;-><init>(Landroid/content/Context;Ltwj;Luym;Lsjt;Ltwj;Ltwj;Ltcy;Ltwj;Ltdf;Lsyl;Llez;Lsxs;Lsya;Lvec;Lszj;Ltdj;Ltfh;Lsiy;Lstm;Lssl;Ltwj;Laclu;Ljava/util/Set;)V",
            "voice_mdd_constructor" to "Lsjt;-><init>(Landroid/content/Context;Lsju;Lsjo;Laclu;)V",
            "voice_formatter_constructor" to
                "Lsmo;-><init>(Ljava/util/Locale;Letg;Lszc;IZLsmr;Ltey;Lvec;Ljava/util/concurrent/Executor;)V",
            "rambler_selector_layout" to
                "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->aC()I",
            "rambler_selector_setup" to
                "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->ac()V",
            "rambler_selector_create" to
                "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->f(Landroid/os/Bundle;)V",
            "rambler_selector_default" to "Lfhv;->hU()V",
            "rambler_selector_read" to "Lnca;->a(Landroid/content/Context;)Z",
            "rambler_selector_write" to
                "Lcom/google/android/apps/inputmethod/latin/preference/VoiceSettingsFragment;->aE(ZLqvt;Lcom/google/android/libraries/inputmethod/preferencewidgets/CustomSelectorWithWidgetPreference;Lcom/google/android/libraries/inputmethod/preferencewidgets/CustomSelectorWithWidgetPreference;)V",
        )
        expected.forEach { (key, descriptor) ->
            val binding = bindings.getAsJsonObject(key)
            assertEquals("method", binding.get("kind").asString)
            assertEquals(descriptor, binding.get("class_type").asString + "->" +
                binding.get("method_name").asString + "(" +
                binding.getAsJsonArray("parameter_types").joinToString("") { it.asString } + ")" +
                binding.get("return_type").asString)
        }
        assertTrue(!bindings.has("flag_factory"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("Could not locate repository root from $workingDirectory")
    }

    private companion object {
        const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
        const val GBOARD_VERSION = "18.5.4.993608717-release-arm64-v8a"
    }
}
