package dev.jason.gboardpatches.extension.rambler;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Typed stock-route policy for Rambler in the formal Gboard 18.0.3 release. */
public final class GboardRamblerStockPolicy {
    public static final String SHOW_RAMBLER_DICT_SETTINGS_FLAG =
            "show_rambler_dict_settings";
    public static final String FILTER_RAMBLER_CONTRIBUTED_INPUT_VIEW_SESSION_FLAG =
            "filter_rambler_contributed_input_view_session";
    public static final String ENABLE_RAMBLER_TOOLBAR_AT_CURSOR_POSITION_FLAG =
            "enable_rambler_toolbar_at_cursor_position";
    public static final String ENABLE_RAMBLER_AL_TOOLBAR_FLAG =
            "enable_rambler_al_toolbar";
    public static final String ENABLE_AGENTIC_DICTATION_FLAG =
            "enable_agentic_dictation";
    public static final String AD_ACTIVATION_TYPE_FLAG = "ad_activation_type";

    private static final Set<String> BOOLEAN_FLAGS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    SHOW_RAMBLER_DICT_SETTINGS_FLAG,
                    FILTER_RAMBLER_CONTRIBUTED_INPUT_VIEW_SESSION_FLAG,
                    ENABLE_RAMBLER_TOOLBAR_AT_CURSOR_POSITION_FLAG,
                    ENABLE_RAMBLER_AL_TOOLBAR_FLAG,
                    ENABLE_AGENTIC_DICTATION_FLAG)));

    private GboardRamblerStockPolicy() {
    }

    public static Object maybeForceStockRouteValue(String flagName, Object originalResult) {
        if (ENABLE_AGENTIC_DICTATION_FLAG.equals(flagName)) {
            return originalResult instanceof Boolean
                            && GboardRamblerOfficialSelectionRuntime
                                    .shouldEnableAgenticDictation()
                    ? Boolean.TRUE
                    : originalResult;
        }
        if (BOOLEAN_FLAGS.contains(flagName)) {
            return originalResult instanceof Boolean ? Boolean.TRUE : originalResult;
        }
        if (AD_ACTIVATION_TYPE_FLAG.equals(flagName)) {
            return originalResult instanceof Long ? Long.valueOf(2L) : originalResult;
        }
        return originalResult;
    }

    public static boolean isTargetFlagName(String flagName) {
        return BOOLEAN_FLAGS.contains(flagName) || AD_ACTIVATION_TYPE_FLAG.equals(flagName);
    }
}
