package dev.jason.gboardpatches.extension.languagemodels;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reviewed delivery tuples for the admitted 18.5.4 APK; never infer versions from URL dates. */
public final class GboardLanguageModelCatalog {
    public static final String APK = "apk";
    public static final String PIXEL = "pixel_20261027";
    public static final String PREVIOUS = "pixel_20261014";
    public static final String NWP_2023 = "nwp_2023";
    public static final String S25 = "s25_20261027";

    public enum Family {
        HMM("hmm", "2025110701", List.of(
                "hmm_superpacks_manifest_url", "hmm_superpacks_manifest_version")),
        DELIGHT("delight", "2026092900", List.of(
                "delight_metadata_uri", "delight_latest_metadata_version")),
        NWP("nwp", "20210507", List.of("enable_nwp_tflite_engine",
                "nwp_superpacks_manifest_uri", "nwp_manifest_version", "nwp_min_supported_version")),
        KEY_CORRECTION("key_correction", "2020062001", List.of(
                "key_correction_tflite_model_superpacks_manifest_url", "key_correction_tflite_model_superpacks_manifest_version",
                "enable_tflite_triggering_model", "enable_tflite_triggering_model_on_all_keys",
                "enable_tflite_triggering_model_on_latin", "enable_tflite_triggering_model_on_function")),
        SAFT("saft", "2026061502", List.of("saft_model_superpacks_manifest_url", "saft_model_superpacks_manifest_version")),
        MOZC("mozc", "2023100109", List.of("mozc_main_lm_mdd_data_file_group")),
        MOZC_SPELL("mozc_spell", "0", List.of("mozc_spell_checker_lm_mdd_data_file_group")),
        GRAMMAR("grammar", "2021082601", List.of("grammar_checker_use_grammar_checker_manager",
                "grammar_checker_manifest_uri", "grammar_checker_min_sentence_length")),
        LANG_ID("lang_id", "20190527", List.of(
                "lang_id_superpacks_manifest_uri", "lang_id_manifest_version"));

        public final String id;
        public final String bakedVersion;
        public final List<String> flagNames;

        Family(String id, String bakedVersion, List<String> flags) {
            this.id = id;
            this.bakedVersion = bakedVersion;
            this.flagNames = flags;
        }

        public String preferenceKey() { return "pref_language_models_" + id; }
    }

    private static final Map<String, Family> FLAG_FAMILIES;
    static {
        Map<String, Family> flags = new LinkedHashMap<>();
        for (Family family : Family.values()) {
            for (String flag : family.flagNames) flags.put(flag, family);
        }
        FLAG_FAMILIES = Collections.unmodifiableMap(flags);
    }

    private GboardLanguageModelCatalog() {}

    public static Family forFlag(String name) { return FLAG_FAMILIES.get(name); }

    public static Family forId(String id) {
        for (Family family : Family.values()) if (family.id.equals(id)) return family;
        return null;
    }

    public static List<String> choices(Family family) {
        if (family == Family.HMM) return List.of(APK, PREVIOUS, PIXEL);
        if (family == Family.NWP) return List.of(APK, NWP_2023, PIXEL);
        if (family == Family.KEY_CORRECTION) return List.of(APK, PIXEL, S25);
        return List.of(APK, PIXEL);
    }

    public static String version(Family family, String choice) {
        if (APK.equals(choice)) return family.bakedVersion;
        return switch (family) {
            case HMM -> PREVIOUS.equals(choice) ? "2026081806" : "2026101403";
            case DELIGHT -> "2026100100";
            case NWP -> NWP_2023.equals(choice) ? "20240611" : "20180920";
            case KEY_CORRECTION -> S25.equals(choice) ? "2024052201" : "2020082801";
            case SAFT -> "2026070801";
            case MOZC -> "2026081709";
            case MOZC_SPELL -> "2026082700";
            case GRAMMAR -> "2023080700";
            case LANG_ID -> "20230119";
        };
    }

    public static Map<String, Object> values(Family family, String choice) {
        if (!choices(family).contains(choice)) throw new IllegalArgumentException("Unknown model preset");
        if (APK.equals(choice)) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        switch (family) {
            case HMM -> {
                long version = Long.parseLong(version(family, choice));
                result.put(family.flagNames.get(0), "https://www.gstatic.com/android/keyboard/hmmpack/"
                        + version + "/metadata_" + version + ".json");
                result.put(family.flagNames.get(1), version);
            }
            case DELIGHT -> {
                result.put(family.flagNames.get(0), "https://www.gstatic.com/android/keyboard/dictionarypack/2026092222/metadata.json");
                result.put(family.flagNames.get(1), 2026100100L);
            }
            case NWP -> {
                boolean updated = NWP_2023.equals(choice);
                result.put(family.flagNames.get(0), updated);
                result.put(family.flagNames.get(1), "https://www.gstatic.com/android/keyboard/next_word_predictor/prod/superpacks-manifest-"
                        + (updated ? "20230719_210421" : "20180920") + ".json");
                result.put(family.flagNames.get(2), updated ? 20240611L : 20180920L);
                result.put(family.flagNames.get(3), 20190717L);
            }
            case KEY_CORRECTION -> {
                boolean updated = S25.equals(choice);
                result.put(family.flagNames.get(0), "https://www.gstatic.com/android/keyboard/kc_triggering_model/superpacks-manifest-"
                        + (updated ? "20240423_102657" : "20200828_154332") + ".json");
                result.put(family.flagNames.get(1), updated ? 2024052201L : 2020082801L);
                result.put(family.flagNames.get(2), updated);
                result.put(family.flagNames.get(3), true);
                result.put(family.flagNames.get(4), false);
                result.put(family.flagNames.get(5), false);
            }
            case SAFT -> {
                result.put(family.flagNames.get(0), "https://www.gstatic.com/android/keyboard/saft_model/superpacks-manifest-20260708_143929.json");
                result.put(family.flagNames.get(1), 2026070801L);
            }
            case GRAMMAR -> {
                result.put(family.flagNames.get(0), true);
                result.put(family.flagNames.get(1), "https://www.gstatic.com/android/keyboard/spell_checker/prod/2023080700/metadata_cpu_2023080700.json");
                result.put(family.flagNames.get(2), 3L);
            }
            case LANG_ID -> {
                result.put(family.flagNames.get(0), "https://www.gstatic.com/android/keyboard/langid/20230119/superpacks_manifest.json");
                result.put(family.flagNames.get(1), 20230119L);
            }
            case MOZC -> result.put(family.flagNames.get(0), mozcPixelDescriptor());
            case MOZC_SPELL -> result.put(family.flagNames.get(0), descriptorFromHex(
                    "0a215f5f4d4f5a435041434b5f6d6f7a637370656c6c636865636b65725f6a615f4a50129a01126c68747470733a2f2f646c2e676f6f676c652e636f6d2f646c2f65646765646c2f6a6170616e6573652d696d652f64696374696f6e6172792f737475646965732f7370656c6c696e672f6d6f7a637370656c6c636865636b6572646174612d323032363038323730302e7a6970209f98b2203a146d6f7a637370656c6c636865636b6572646174615a070a0522030a012a78018001a9a59616508ca38ec607"));
            default -> throw new IllegalArgumentException("No scalar tuple");
        }
        return Collections.unmodifiableMap(result);
    }

    private static byte[] mozcPixelDescriptor() {
        // Reviewed public MDD descriptor, SHA-256 fb5585f4...; no device token or credentials.
        String hex = "0a195f5f4d4f5a435041434b5f6d6f7a636d61696e5f6a615f4a501277125568747470733a2f2f646c2e676f6f676c652e636f6d2f646c2f65646765646c2f6a6170616e6573652d696d652f64696374696f6e6172792f32342f6d6f7a63646174612d32342e32303236303831372e392e7a697020f09ad2123a086d6f7a63646174615a070a0522030a012a78018001e991da0d50ad9b8ec607";
        return descriptorFromHex(hex);
    }

    private static byte[] descriptorFromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
