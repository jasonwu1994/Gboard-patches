package dev.jason.gboardpatches.extension.languagemodels;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.HashMap;
import java.util.Map;
import java.security.MessageDigest;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelCatalog.Family;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelSettings;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelPolicy;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelRuntime;

public class GboardLanguageModelPolicyTest {
    @Test public void readinessMustBelongToNwpRatherThanAnotherEngine() {
        var other = new GboardLanguageModelRuntime.NwpReadiness();
        other.accept("[RuntimeEngineGraph]\nReady? 1\n[TfLiteNwpEngine]\nReady? 0, Version? 0\n[OtherEngine]\nReady? 1");
        assertFalse(other.isReady());
        var nwp = new GboardLanguageModelRuntime.NwpReadiness();
        nwp.accept("[TfLiteNwpEngine] \n  Ready? 1, Version? 0, FilterCandidates? 1\n[OtherEngine]");
        assertTrue(nwp.isReady());
    }
    @Test public void absentPreferencesDefaultToDisabledWithBakedSelections() {
        var state = GboardLanguageModelSettings.fromValues(Map.of());
        assertNotNull(state); assertFalse(state.enabled);
        for (Family family : Family.values()) assertEquals("apk", state.choice(family));
    }

    @Test public void bakedMeansActualApkDefaultNotEffectiveRemoteValue() {
        var state = GboardLanguageModelSettings.fromValues(Map.of(GboardLanguageModelSettings.ENABLED, true));
        assertEquals(2025110701L, GboardLanguageModelPolicy.apply("hmm_superpacks_manifest_version", 2026101403L, 2025110701L, state));
        assertEquals("actual baked", GboardLanguageModelPolicy.apply("hmm_superpacks_manifest_url", "remote", "actual baked", state));
        assertEquals("remote", GboardLanguageModelPolicy.apply("hmm_superpacks_manifest_url", "remote", null, state));
    }

    @Test public void disabledRestoresBakedDefaultsEvenWhenCustomChoicesAreRemembered() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put(GboardLanguageModelSettings.ENABLED, false);
        for (Family family : Family.values()) prefs.put(family.preferenceKey(), GboardLanguageModelCatalog.PIXEL);
        var state = GboardLanguageModelSettings.fromValues(prefs);
        assertEquals(2025110701L, GboardLanguageModelPolicy.apply(
                "hmm_superpacks_manifest_version", 2026081806L, 2025110701L, state));
        assertEquals("baked URL", GboardLanguageModelPolicy.apply(
                "delight_metadata_uri", "remote URL", "baked URL", state));
        assertEquals(true, GboardLanguageModelPolicy.apply("enable_nwp_tflite_engine", false, true, state));
        byte[] descriptor = {1, 2, 3};
        assertArrayEquals(descriptor, (byte[]) GboardLanguageModelPolicy.apply(
                "mozc_main_lm_mdd_data_file_group", new byte[]{4}, descriptor, state));
        assertEquals(GboardLanguageModelCatalog.PIXEL, state.choice(Family.HMM));
        assertEquals("remote URL", GboardLanguageModelPolicy.apply("delight_metadata_uri", "remote URL", null, state));
    }

    @Test public void reviewedTuplesAreCompleteAndKeepTheirOriginalTypes() {
        Map<String, Object> prefs = new HashMap<>(); prefs.put(GboardLanguageModelSettings.ENABLED, true);
        for (Family family : Family.values()) {
            prefs.put(family.preferenceKey(), GboardLanguageModelCatalog.PIXEL);
            var state = GboardLanguageModelSettings.fromValues(prefs);
            Map<String, Object> values = GboardLanguageModelCatalog.values(family, GboardLanguageModelCatalog.PIXEL);
            assertEquals(family.flagNames.size(), values.size());
            for (String name : family.flagNames) {
                Object expected = values.get(name);
                Object result = GboardLanguageModelPolicy.apply(name, expected, null, state);
                if (expected instanceof byte[] bytes) assertArrayEquals(bytes, (byte[]) result);
                else assertEquals(expected, result);
                assertEquals(17, GboardLanguageModelPolicy.apply(name, 17, null, state));
            }
        }
        assertEquals(2026101403L, GboardLanguageModelCatalog.values(Family.HMM, GboardLanguageModelCatalog.PIXEL).get("hmm_superpacks_manifest_version"));
        assertTrue(((String) GboardLanguageModelCatalog.values(Family.DELIGHT, GboardLanguageModelCatalog.PIXEL).get("delight_metadata_uri")).contains("2026092222"));
        assertEquals(2026100100L, GboardLanguageModelCatalog.values(Family.DELIGHT, GboardLanguageModelCatalog.PIXEL).get("delight_latest_metadata_version"));
    }

    @Test public void invalidSelectionFailsAsWholeSnapshotAndDoesNotMixTuple() {
        assertNull(GboardLanguageModelSettings.fromValues(Map.of(Family.HMM.preferenceKey(), "made-up-version")));
        assertNull(GboardLanguageModelSettings.fromValues(Map.of(Family.NWP.preferenceKey(), 2023)));
        assertNull(GboardLanguageModelSettings.fromValues(Map.of(GboardLanguageModelSettings.ENABLED, "invalid-boolean")));
        assertEquals(5L, GboardLanguageModelPolicy.apply("hmm_superpacks_manifest_version", 5L, 1L, null));
    }

    @Test public void serializedValuesPreserveValidatedBooleanAndModelChoice() {
        var state = GboardLanguageModelSettings.fromValues(Map.of(GboardLanguageModelSettings.ENABLED, "true",
                Family.HMM.preferenceKey(), GboardLanguageModelCatalog.PIXEL));
        assertTrue(state.enabled);
        assertEquals(2026101403L, GboardLanguageModelPolicy.apply("hmm_superpacks_manifest_version", 2026081806L, 2025110701L, state));
        assertFalse(GboardLanguageModelSettings.fromValues(Map.of(GboardLanguageModelSettings.ENABLED, "false")).enabled);
    }

    @Test public void pixelNwpIsExplicitlyDisabledAnd2023IsSeparateValidProfile() {
        var pixel = GboardLanguageModelCatalog.values(Family.NWP, GboardLanguageModelCatalog.PIXEL);
        assertEquals(false, pixel.get("enable_nwp_tflite_engine"));
        var updated = GboardLanguageModelCatalog.values(Family.NWP, GboardLanguageModelCatalog.NWP_2023);
        assertEquals(true, updated.get("enable_nwp_tflite_engine"));
        assertTrue((long) updated.get("nwp_manifest_version") >= (long) updated.get("nwp_min_supported_version"));
    }

    @Test public void mozcPresetMatchesReviewedNonSecretDescriptor() throws Exception {
        byte[] bytes = (byte[]) GboardLanguageModelCatalog.values(Family.MOZC, GboardLanguageModelCatalog.PIXEL).get(Family.MOZC.flagNames.get(0));
        assertEquals(154, bytes.length);
        StringBuilder sha = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sha.append(String.format("%02x", b));
        assertEquals("fb5585f4438e5d71fca69eb6832f6c6b20daadbfb82ca6590a5d649267d68803", sha.toString());
    }

    @Test public void addedResourceTuplesPreserveOfficialGatesAndReviewedDescriptor() throws Exception {
        var pixel = GboardLanguageModelCatalog.values(Family.KEY_CORRECTION, GboardLanguageModelCatalog.PIXEL);
        var s25 = GboardLanguageModelCatalog.values(Family.KEY_CORRECTION, GboardLanguageModelCatalog.S25);
        assertEquals(6, s25.size());
        assertEquals(false, pixel.get("enable_tflite_triggering_model"));
        assertEquals(true, s25.get("enable_tflite_triggering_model"));
        assertEquals(2024052201L, s25.get("key_correction_tflite_model_superpacks_manifest_version"));
        assertEquals(true, s25.get("enable_tflite_triggering_model_on_all_keys"));
        assertEquals(false, s25.get("enable_tflite_triggering_model_on_latin"));
        assertEquals(false, s25.get("enable_tflite_triggering_model_on_function"));
        assertEquals(2026070801L, GboardLanguageModelCatalog.values(Family.SAFT, GboardLanguageModelCatalog.PIXEL).get("saft_model_superpacks_manifest_version"));
        byte[] bytes = (byte[]) GboardLanguageModelCatalog.values(Family.MOZC_SPELL, GboardLanguageModelCatalog.PIXEL).get("mozc_spell_checker_lm_mdd_data_file_group");
        assertEquals(198, bytes.length);
        StringBuilder sha = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) sha.append(String.format("%02x", b));
        assertEquals("89153497bf2162799ed4dbbda05bdaca9aa35107b9a17172dfd83d4d3ac6b50f", sha.toString());
        assertNull(GboardLanguageModelCatalog.forFlag("ranking_model_superpacks_manifest_url"));
        assertNull(GboardLanguageModelSettings.fromValues(Map.of(Family.SAFT.preferenceKey(), GboardLanguageModelCatalog.S25)));
    }

    @Test public void noOtherModelOrBehaviourFlagsAreTouched() {
        var state = GboardLanguageModelSettings.fromValues(Map.of(GboardLanguageModelSettings.ENABLED, true));
        assertEquals(5L, GboardLanguageModelPolicy.apply("max_chinese_context_length", 5L, 20L, state));
        assertEquals(false, GboardLanguageModelPolicy.apply("enable_grammar_checker", false, true, state));
        assertEquals(false, GboardLanguageModelPolicy.apply("enable_inline_suggestions_on_client_side", false, true, state));
    }
}
