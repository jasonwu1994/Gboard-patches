package dev.jason.gboardpatches.extension.advancedvoice;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Typed stock capability policy for the formal Gboard 18.0.3 release. */
public final class GboardAdvancedVoiceStockPolicy {
    public static final String ENABLE_NGA_FLAG = "enable_nga";
    public static final String ENABLE_ADVANCED_FEATURES_FLAG =
            "enable_advanced_features_in_consolidated_sd_stack";
    public static final String ENABLE_DICTATION_SPLIT_INSTALL_FLAG =
            "enable_dictation_feature_split_install";
    public static final String HANDLE_FALLBACK_INSIDE_SD_STACK_FLAG =
            "handle_fallback_inside_sd_stack";
    public static final String ENABLE_STICKY_MIC_BACKGROUND_FLAG =
            "enable_sticky_mic_background";
    public static final String ENABLE_SODA_LONGFORM_EXPERIMENT_FLAG =
            "enable_soda_longform_experiment";

    private static final Set<String> STOCK_FLAGS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    ENABLE_NGA_FLAG,
                    ENABLE_ADVANCED_FEATURES_FLAG,
                    ENABLE_DICTATION_SPLIT_INSTALL_FLAG,
                    HANDLE_FALLBACK_INSIDE_SD_STACK_FLAG,
                    ENABLE_STICKY_MIC_BACKGROUND_FLAG,
                    ENABLE_SODA_LONGFORM_EXPERIMENT_FLAG)));

    private GboardAdvancedVoiceStockPolicy() {
    }

    public static Object maybeForceStockFlag(String flagName, Object originalResult) {
        return STOCK_FLAGS.contains(flagName) && originalResult instanceof Boolean
                ? Boolean.TRUE
                : originalResult;
    }

    public static boolean isTargetFlagName(String flagName) {
        return STOCK_FLAGS.contains(flagName);
    }
}
