package dev.jason.gboardpatches.extension.writingtools;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardAiWritingToolsRuntime {
    static final String FLAG_WRITING_HELPER = "writing_helper";
    static final String FLAG_CONFIG_PROOFREAD = "config_proofread";
    static final String FLAG_WRITING_HELPER_ON_SELECTED_TEXT =
            "writing_helper_on_selected_text";
    static final String FLAG_WRITING_HELPER_ENABLE_TEXT_STYLIZATION_INTERNAL =
            "writing_helper_enable_text_stylization_internal";
    static final String FLAG_WRITING_TOOLS = "writing_tools";
    static final String FLAG_ENABLE_WRITING_TOOLS_COOPERATIVE_MODE =
            "enable_writing_tools_cooperative_mode";
    static final String FLAG_WRITING_HELPER_SUPPORTED_LANGUAGE_TAGS =
            "writing_helper_supported_language_tags";
    static final String FLAG_LLM_PC_SUPPORTED_LANGUAGE_TAGS =
            "llm_pc_supported_language_tags";
    static final String FLAG_WRITING_HELPER_MODEL_VERSION =
            "writing_helper_model_version";
    static final String FLAG_WRITING_HELPER_VOICE_MODEL_VERSION =
            "writing_helper_voice_input_model_version";
    static final String FLAG_WRITING_HELPER_TEXT_STYLIZATION_MODEL_VERSION =
            "writing_helper_text_stylization_model_version";
    static final String FLAG_ENABLE_WRITING_TOOLS_FOR_MINORS =
            "enable_writing_tools_for_minors";
    static final String FLAG_BACKEND_TYPE = "writing_tools_v2_backend_type";
    static final String FLAG_HYBRID = "writing_tools_enable_hybrid";
    static final String FLAG_ON_DEVICE_PROOFREAD = "enable_on_device_proofread";
    static final String FLAG_ENABLE_WRITING_TOOLS_VOICE_COMMANDS =
            "enable_writing_tools_voice_commands";
    static final String FLAG_ENABLE_MODELESS_SMART_EDIT =
            "enable_nga_lab_modeless_smartedit";
    static final String FLAG_MODELESS_SMART_EDIT_REGEX_VERSION =
            "nga_lab_modeless_smartedit_regex_version";
    static final String ALL_LANGUAGES_ALLOWLIST_VALUE = "*";
    static final String MODELESS_SMART_EDIT_REGEX_VERSION = "v3";
    static final String SERVER_WRITING_CONFIGURATION =
            "202504090000_writing_tools_config";

    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[gboard-writing-tools-18.0.3] ";
    private static final String[] FORCED_SIGNAL_TARGET_SPECS = new String[]{
            "gvu#a",
            "lvi#b",
            "gqd#a"
    };
    private static final ConcurrentHashMap<Class<?>, Field> FLAG_NAME_FIELDS =
            new ConcurrentHashMap<Class<?>, Field>();
    private static final ConcurrentHashMap<ClassLoader, Set<Object>> FORCED_SIGNALS =
            new ConcurrentHashMap<ClassLoader, Set<Object>>();
    private static final AtomicInteger FLAG_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger FLAG_FAILURE_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SIGNAL_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SIGNAL_FAILURE_LOG_COUNT = new AtomicInteger(0);
    private static final AtomicInteger SETTINGS_REMOVAL_LOG_COUNT = new AtomicInteger(0);
    private static final ThreadLocal<Integer> SETTINGS_CONTROLLER_SCOPE_DEPTH =
            new ThreadLocal<Integer>();

    private static volatile Context applicationContext;

    private GboardAiWritingToolsRuntime() {
    }

    public static Object applyOverriddenFlagValue(Object receiver, Object originalResult) {
        try {
            if (receiver == null) return originalResult;
            String flagName = readFlagName(receiver);
            if (!isOverrideCandidate(flagName, originalResult)) return originalResult;
            Context context = resolveContext();
            if (context == null) {
                return originalResult;
            }
            GboardAiWritingToolsSettings.Snapshot settings =
                    GboardAiWritingToolsSettings.snapshot(context);
            Object enforced = computeOverrideValue(
                    flagName,
                    originalResult,
                    settings,
                    null);
            if (enforced != originalResult) {
                logLimited(FLAG_LOG_COUNT,
                        "flag=" + flagName
                                + ", original=" + describe(originalResult)
                                + ", enforced=" + describe(enforced)
                                + ", featureEnabled=" + settings.featureEnabled
                                + ", allKeyboards=" + settings.allKeyboardsEnabled
                                + ", backendType=" + settings.backendType);
            }
            return enforced;
        } catch (Throwable throwable) {
            logLimited(FLAG_FAILURE_LOG_COUNT,
                    "failed to apply flag override", throwable);
            return originalResult;
        }
    }

    static Object computeOverrideValue(String flagName, Object originalResult,
            GboardAiWritingToolsSettings.Snapshot settings,
            GboardAiWritingToolsOfficialPreferences.Snapshot officialPreferences) {
        if (flagName == null || settings == null || !settings.featureEnabled) {
            return originalResult;
        }

        BackendDecision backend = backendDecision(settings.backendType);
        if (originalResult instanceof String
                && Long.valueOf(1L).equals(backend.backendType)
                && isServerModelConfiguration(flagName)) {
            return SERVER_WRITING_CONFIGURATION;
        }
        if (FLAG_BACKEND_TYPE.equals(flagName)) {
            return originalResult instanceof Long && backend.backendType != null ? backend.backendType : originalResult;
        }
        if (FLAG_HYBRID.equals(flagName)) {
            return originalResult instanceof Boolean && backend.hybridEnabled != null ? backend.hybridEnabled : originalResult;
        }
        if (FLAG_ON_DEVICE_PROOFREAD.equals(flagName)) {
            return originalResult instanceof Boolean && backend.onDeviceProofreadEnabled != null
                    ? backend.onDeviceProofreadEnabled
                    : originalResult;
        }
        if (FLAG_ENABLE_WRITING_TOOLS_VOICE_COMMANDS.equals(flagName)) {
            return originalResult instanceof Boolean ? Boolean.TRUE : originalResult;
        }
        if (FLAG_MODELESS_SMART_EDIT_REGEX_VERSION.equals(flagName)) {
            return originalResult instanceof String && ((String) originalResult).isEmpty()
                    ? MODELESS_SMART_EDIT_REGEX_VERSION
                    : originalResult;
        }

        if (FLAG_WRITING_HELPER_SUPPORTED_LANGUAGE_TAGS.equals(flagName)
                || FLAG_LLM_PC_SUPPORTED_LANGUAGE_TAGS.equals(flagName)) {
            return settings.allKeyboardsEnabled && originalResult instanceof String
                    ? ALL_LANGUAGES_ALLOWLIST_VALUE
                    : originalResult;
        }
        if (!(originalResult instanceof Boolean)) {
            return originalResult;
        }

        if (isEnabledCapabilityFlag(flagName)) {
            return Boolean.TRUE;
        }
        return originalResult;
    }

    private static boolean isOverrideCandidate(String flagName, Object result) {
        if (result instanceof String) {
            return isServerModelConfiguration(flagName)
                    || FLAG_MODELESS_SMART_EDIT_REGEX_VERSION.equals(flagName)
                    || FLAG_WRITING_HELPER_SUPPORTED_LANGUAGE_TAGS.equals(flagName)
                    || FLAG_LLM_PC_SUPPORTED_LANGUAGE_TAGS.equals(flagName);
        }
        if (result instanceof Long) return FLAG_BACKEND_TYPE.equals(flagName);
        return result instanceof Boolean && (isEnabledCapabilityFlag(flagName)
                || FLAG_HYBRID.equals(flagName)
                || FLAG_ON_DEVICE_PROOFREAD.equals(flagName)
                || FLAG_ENABLE_WRITING_TOOLS_VOICE_COMMANDS.equals(flagName));
    }

    private static boolean isEnabledCapabilityFlag(String flagName) {
        return FLAG_CONFIG_PROOFREAD.equals(flagName)
                || FLAG_WRITING_TOOLS.equals(flagName)
                || FLAG_ENABLE_WRITING_TOOLS_COOPERATIVE_MODE.equals(flagName)
                || FLAG_WRITING_HELPER_ON_SELECTED_TEXT.equals(flagName)
                || FLAG_WRITING_HELPER_ENABLE_TEXT_STYLIZATION_INTERNAL.equals(flagName)
                || FLAG_WRITING_HELPER.equals(flagName)
                || FLAG_ENABLE_WRITING_TOOLS_FOR_MINORS.equals(flagName)
                || FLAG_ENABLE_MODELESS_SMART_EDIT.equals(flagName);
    }

    private static boolean isServerModelConfiguration(String flagName) {
        return FLAG_WRITING_HELPER_MODEL_VERSION.equals(flagName)
                || FLAG_WRITING_HELPER_VOICE_MODEL_VERSION.equals(flagName)
                || FLAG_WRITING_HELPER_TEXT_STYLIZATION_MODEL_VERSION.equals(flagName);
    }

    static BackendDecision backendDecision(String backendType) {
        if (GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE.equals(backendType)) {
            return new BackendDecision(Long.valueOf(2L), Boolean.TRUE, Boolean.TRUE);
        }
        if (GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA.equals(backendType)) {
            return new BackendDecision(Long.valueOf(3L), Boolean.TRUE, Boolean.FALSE);
        }
        if (GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER.equals(backendType)) {
            return new BackendDecision(Long.valueOf(1L), Boolean.FALSE, Boolean.FALSE);
        }
        return new BackendDecision(null, null, null);
    }

    public static boolean applySignalResult(Object signal, boolean originalResult) {
        if (originalResult) {
            return true;
        }
        try {
            Context context = resolveContext();
            if (context == null
                    || signal == null
                    || !GboardAiWritingToolsSettings.snapshot(context).featureEnabled) {
                return false;
            }
            boolean forced = forcedSignals(signal.getClass().getClassLoader()).contains(signal);
            if (forced) {
                logLimited(SIGNAL_LOG_COUNT,
                        "signal=" + describe(signal) + ", original=false, enforced=true");
            }
            return forced;
        } catch (Throwable throwable) {
            logLimited(SIGNAL_FAILURE_LOG_COUNT,
                    "failed to evaluate signal", throwable);
            return originalResult;
        }
    }

    public static void enterSettingsControllerScope() {
        SETTINGS_CONTROLLER_SCOPE_DEPTH.set(Integer.valueOf(
                currentSettingsControllerScopeDepth() + 1));
    }

    public static void exitSettingsControllerScope() {
        int nextDepth = currentSettingsControllerScopeDepth() - 1;
        if (nextDepth <= 0) {
            SETTINGS_CONTROLLER_SCOPE_DEPTH.remove();
        } else {
            SETTINGS_CONTROLLER_SCOPE_DEPTH.set(Integer.valueOf(nextDepth));
        }
    }

    static int currentSettingsControllerScopeDepth() {
        Integer depth = SETTINGS_CONTROLLER_SCOPE_DEPTH.get();
        return depth == null ? 0 : depth.intValue();
    }

    public static boolean shouldBypassSettingsRemoval(int keyResId) {
        try {
            Context context = resolveContext();
            if (context == null
                    || currentSettingsControllerScopeDepth() <= 0
                    || !GboardAiWritingToolsSettings.snapshot(context).featureEnabled) {
                return false;
            }
            return keyResId == GboardAiWritingToolsOfficialPreferences.PROOFREAD_PREF_KEY
                    || keyResId == GboardAiWritingToolsOfficialPreferences.WRITING_TOOLS_PREF_KEY
                    || keyResId == GboardAiWritingToolsOfficialPreferences
                    .WRITING_TOOLS_CATEGORY_KEY;
        } catch (Throwable throwable) {
            logLimited(SETTINGS_REMOVAL_LOG_COUNT,
                    "failed to evaluate settings removal", throwable);
            return false;
        }
    }

    private static String readFlagName(Object receiver) throws Throwable {
        Field field = FLAG_NAME_FIELDS.get(receiver.getClass());
        if (field == null) {
            Field resolved = receiver.getClass().getDeclaredField("a");
            if (resolved.getType() != String.class) {
                return null;
            }
            resolved.setAccessible(true);
            Field existing = FLAG_NAME_FIELDS.putIfAbsent(receiver.getClass(), resolved);
            field = existing == null ? resolved : existing;
        }
        return (String) field.get(receiver);
    }

    private static Set<Object> forcedSignals(ClassLoader classLoader) throws Throwable {
        Set<Object> cached = FORCED_SIGNALS.get(classLoader);
        if (cached != null) {
            return cached;
        }
        Set<Object> resolved = Collections.newSetFromMap(
                new IdentityHashMap<Object, Boolean>());
        for (String spec : FORCED_SIGNAL_TARGET_SPECS) {
            int separator = spec.indexOf('#');
            Class<?> owner = Class.forName(spec.substring(0, separator), false, classLoader);
            Field field = owner.getDeclaredField(spec.substring(separator + 1));
            field.setAccessible(true);
            Object signal = field.get(null);
            if (signal != null) {
                resolved.add(signal);
            }
        }
        Set<Object> immutable = Collections.unmodifiableSet(resolved);
        Set<Object> existing = FORCED_SIGNALS.putIfAbsent(classLoader, immutable);
        return existing == null ? immutable : existing;
    }

    static Context resolveContext() {
        Context cached = applicationContext;
        if (cached != null) {
            return cached;
        }
        Context reflected = reflectedApplicationContext(
                "android.app.ActivityThread", "currentApplication");
        if (reflected == null) {
            reflected = reflectedApplicationContext(
                    "android.app.AppGlobals", "getInitialApplication");
        }
        if (reflected != null) {
            applicationContext = reflected;
        }
        return reflected;
    }

    static Context reflectedApplicationContext(String className, String methodName) {
        try {
            Object application = Class.forName(className).getMethod(methodName).invoke(null);
            if (!(application instanceof Context)) {
                return null;
            }
            Context context = (Context) application;
            Context applicationContext = context.getApplicationContext();
            return applicationContext == null ? context : applicationContext;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String[] forcedSignalTargetSpecsForTesting() {
        return FORCED_SIGNAL_TARGET_SPECS.clone();
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number || value instanceof CharSequence) {
            return value.getClass().getSimpleName() + "(" + value + ")";
        }
        return value.getClass().getName();
    }

    private static void logLimited(AtomicInteger counter, String message) {
        logLimited(counter, message, null);
    }

    private static void logLimited(AtomicInteger counter, String message, Throwable throwable) {
        if (counter.incrementAndGet() <= 80) {
            safeLog(message, throwable);
        }
    }

    private static void safeLog(String message, Throwable throwable) {
        try {
            if (throwable == null) {
                Log.w(TAG, LOG_PREFIX + message);
            } else {
                Log.w(TAG, LOG_PREFIX + message, throwable);
            }
        } catch (Throwable ignored) {
            // Host-side unit tests do not provide android.util.Log.
        }
    }

    static final class BackendDecision {
        final Long backendType;
        final Boolean hybridEnabled;
        final Boolean onDeviceProofreadEnabled;

        private BackendDecision(Long backendType, Boolean hybridEnabled,
                Boolean onDeviceProofreadEnabled) {
            this.backendType = backendType;
            this.hybridEnabled = hybridEnabled;
            this.onDeviceProofreadEnabled = onDeviceProofreadEnabled;
        }
    }
}
