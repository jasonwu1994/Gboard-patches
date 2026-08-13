package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.jason.gboardpatches.extension.core.PatchedServiceBridge;

public final class GboardTopRowSwipeSettings {
    private static final String REMOTE_SYNC_THREAD_NAME = "gboardpatches-top-row-remote-sync";
    private static final AtomicBoolean REMOTE_SYNC_LISTENER_REGISTERED =
            new AtomicBoolean(false);
    private static final AtomicBoolean REMOTE_SYNC_RUNNING =
            new AtomicBoolean(false);
    private static final AtomicInteger REMOTE_SYNC_REQUEST_VERSION =
            new AtomicInteger(0);
    private static final AtomicInteger REMOTE_SYNC_COMPLETED_VERSION =
            new AtomicInteger(0);
    private static final AtomicReference<Context> REMOTE_SYNC_CONTEXT =
            new AtomicReference<Context>();
    private static final ExecutorService REMOTE_SYNC_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, REMOTE_SYNC_THREAD_NAME);
                thread.setDaemon(true);
                return thread;
            });
    public static final int SLOT_COUNT = 10;
    public static final boolean DEFAULT_ENABLED = false;
    public static final boolean DEFAULT_ZHUYIN_ENABLED = true;
    public static final boolean DEFAULT_ENGLISH_QWERTY_ENABLED = true;
    public static final int DEFAULT_TIMEOUT_MS = 1000;
    public static final int MIN_TIMEOUT_MS = 1;
    public static final int MAX_TIMEOUT_MS = 30000;
    public static final int DEFAULT_RESPONSE_BODY_LIMIT_BYTES = 1024 * 1024;
    public static final int MIN_RESPONSE_BODY_LIMIT_BYTES = 4 * 1024;
    public static final int MAX_RESPONSE_BODY_LIMIT_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_TIMEOUT_MAX_MS = 30000;
    public static final int MIN_TIMEOUT_MAX_MS = 1;
    public static final int DEFAULT_MEMORY_LIMIT_BYTES = 8 * 1024 * 1024;
    public static final int MIN_MEMORY_LIMIT_BYTES = 1024 * 1024;
    public static final int MAX_MEMORY_LIMIT_BYTES = 64 * 1024 * 1024;
    public static final int DEFAULT_MAX_STACK_BYTES = 512 * 1024;
    public static final int MIN_MAX_STACK_BYTES = 64 * 1024;
    public static final int MAX_MAX_STACK_BYTES = 4 * 1024 * 1024;

    public static final String PREF_FILE = "gboard_top_row_swipe_settings";
    public static final String PREF_KEY_TOP_ROW_SWIPE_ENABLED =
            "pref_top_row_swipe_enabled";
    public static final String PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED =
            "pref_top_row_swipe_zhuyin_enabled";
    public static final String PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED =
            "pref_top_row_swipe_english_qwerty_enabled";
    public static final String PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT =
            "pref_top_row_swipe_global_java_script";
    public static final String PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES =
            "pref_top_row_swipe_js_response_body_limit_bytes";
    public static final String PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS =
            "pref_top_row_swipe_js_timeout_max_ms";
    public static final String PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES =
            "pref_top_row_swipe_js_memory_limit_bytes";
    public static final String PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES =
            "pref_top_row_swipe_js_max_stack_bytes";

    public static final String METHOD_GET_TOP_ROW_SWIPE_SETTINGS =
            "get_top_row_swipe_settings";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_ENABLED =
            "top_row_swipe_enabled";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED =
            "top_row_swipe_zhuyin_enabled";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED =
            "top_row_swipe_english_qwerty_enabled";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_DISPLAY_TEXTS =
            "top_row_swipe_display_texts";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_COMMIT_TEXTS =
            "top_row_swipe_commit_texts";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_IS_JAVA_SCRIPT =
            "top_row_swipe_is_java_script";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_SCRIPT_TEXTS =
            "top_row_swipe_script_texts";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_TIMEOUTS_MS =
            "top_row_swipe_timeouts_ms";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT =
            "top_row_swipe_global_java_script";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES =
            "top_row_swipe_js_response_body_limit_bytes";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS =
            "top_row_swipe_js_timeout_max_ms";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES =
            "top_row_swipe_js_memory_limit_bytes";
    public static final String BUNDLE_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES =
            "top_row_swipe_js_max_stack_bytes";

    private static final String EXPORT_FORMAT_KEY = "format";
    private static final String EXPORT_FORMAT_VALUE =
            "gboardpatches.top-row-swipe.slots";
    private static final String EXPORT_VERSION_KEY = "version";
    private static final int EXPORT_VERSION_VALUE = 3;
    private static final int LEGACY_EXPORT_VERSION_VALUE = 1;
    private static final String EXPORT_GLOBAL_JAVA_SCRIPT_KEY = "globalJavaScript";
    private static final String EXPORT_JAVA_SCRIPT_RUNTIME_LIMITS_KEY =
            "javaScriptRuntimeLimits";
    private static final String EXPORT_RESPONSE_BODY_LIMIT_BYTES_KEY =
            "responseBodyLimitBytes";
    private static final String EXPORT_TIMEOUT_MAX_MS_KEY = "timeoutMaxMs";
    private static final String EXPORT_MEMORY_LIMIT_BYTES_KEY = "memoryLimitBytes";
    private static final String EXPORT_MAX_STACK_BYTES_KEY = "maxStackBytes";
    private static final String EXPORT_SLOTS_KEY = "slots";
    private static final String EXPORT_DISPLAY_KEY = "displayText";
    private static final String EXPORT_COMMIT_KEY = "commitText";
    private static final String LEGACY_EXPORT_DISPLAY_KEY = "display";
    private static final String LEGACY_EXPORT_COMMIT_KEY = "commit";
    private static final String EXPORT_IS_JAVA_SCRIPT_KEY = "isJavaScript";
    private static final String EXPORT_SCRIPT_KEY = "scriptText";
    private static final String EXPORT_TIMEOUT_KEY = "timeoutMs";
    private static final List<String> DEFAULT_TEXTS =
            Collections.unmodifiableList(Arrays.asList(
                    "😀",
                    "😁",
                    "😂",
                    "🤣",
                    "😃",
                    "😄",
                    "😅",
                    "😆",
                    "😉",
                    "😊"));

    private GboardTopRowSwipeSettings() {
    }

    public static final class SlotText {
        public final String displayText;
        public final String commitText;
        public final boolean isJavaScript;
        public final String scriptText;
        public final int timeoutMs;

        public SlotText(String displayText, String commitText) {
            this(displayText, commitText, false, "", DEFAULT_TIMEOUT_MS);
        }

        public SlotText(String displayText, String commitText, boolean isJavaScript,
                String scriptText, int timeoutMs) {
            this.displayText = displayText;
            this.commitText = commitText;
            this.isJavaScript = isJavaScript;
            this.scriptText = scriptText;
            this.timeoutMs = timeoutMs;
        }
    }

    public static final class RuntimeSettings {
        public final boolean available;
        public final boolean enabled;
        public final boolean zhuyinEnabled;
        public final boolean englishQwertyEnabled;
        public final String globalJavaScript;
        public final JavaScriptRuntimeLimits javaScriptRuntimeLimits;
        public final List<SlotText> slots;

        private RuntimeSettings(boolean available, boolean enabled, boolean zhuyinEnabled,
                boolean englishQwertyEnabled, String globalJavaScript,
                JavaScriptRuntimeLimits javaScriptRuntimeLimits,
                List<SlotText> slots) {
            this.available = available;
            this.enabled = enabled;
            this.zhuyinEnabled = zhuyinEnabled;
            this.englishQwertyEnabled = englishQwertyEnabled;
            this.globalJavaScript = globalJavaScript;
            this.javaScriptRuntimeLimits = javaScriptRuntimeLimits;
            this.slots = slots;
        }
    }

    public static final class JavaScriptRuntimeLimits {
        public final int responseBodyLimitBytes;
        public final int timeoutMaxMs;
        public final int memoryLimitBytes;
        public final int maxStackBytes;

        public JavaScriptRuntimeLimits(int responseBodyLimitBytes, int timeoutMaxMs,
                int memoryLimitBytes, int maxStackBytes) {
            this.responseBodyLimitBytes = responseBodyLimitBytes;
            this.timeoutMaxMs = timeoutMaxMs;
            this.memoryLimitBytes = memoryLimitBytes;
            this.maxStackBytes = maxStackBytes;
        }
    }

    public static final class ExportedSettings {
        public final String globalJavaScript;
        public final JavaScriptRuntimeLimits javaScriptRuntimeLimits;
        public final List<SlotText> slots;

        private ExportedSettings(String globalJavaScript,
                JavaScriptRuntimeLimits javaScriptRuntimeLimits,
                List<SlotText> slots) {
            this.globalJavaScript = globalJavaScript != null ? globalJavaScript : "";
            this.javaScriptRuntimeLimits = javaScriptRuntimeLimits != null
                    ? javaScriptRuntimeLimits : defaultJavaScriptRuntimeLimits();
            this.slots = slots != null ? slots : Collections.emptyList();
        }
    }

    public static SharedPreferences preferences(Context context) {
        Context lookupContext = resolveLookupContext(context);
        if (lookupContext == null) {
            return null;
        }
        try {
            PatchedServiceBridge.initialize(lookupContext);
        } catch (Throwable ignored) {
            // Unit tests do not provide a real Android logging/runtime environment.
        }
        return lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        scheduleLocalMirrorSync(context);
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = null;
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_ENABLED)) {
            editor = preferences.edit();
            editor.putBoolean(PREF_KEY_TOP_ROW_SWIPE_ENABLED, DEFAULT_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                    DEFAULT_ZHUYIN_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED)) {
            editor = ensureEditor(editor, preferences);
            editor.putBoolean(PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                    DEFAULT_ENGLISH_QWERTY_ENABLED);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT, "");
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES)) {
            editor = ensureEditor(editor, preferences);
            editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                    DEFAULT_RESPONSE_BODY_LIMIT_BYTES);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS)) {
            editor = ensureEditor(editor, preferences);
            editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                    DEFAULT_TIMEOUT_MAX_MS);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES)) {
            editor = ensureEditor(editor, preferences);
            editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                    DEFAULT_MEMORY_LIMIT_BYTES);
        }
        if (!preferences.contains(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES)) {
            editor = ensureEditor(editor, preferences);
            editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                    DEFAULT_MAX_STACK_BYTES);
        }
        List<SlotText> defaults = defaultSlots();
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = defaults.get(index);
            String displayKey = displayPreferenceKey(index);
            String commitKey = commitPreferenceKey(index);
            String isJavaScriptKey = isJavaScriptPreferenceKey(index);
            String scriptKey = scriptPreferenceKey(index);
            String timeoutKey = timeoutPreferenceKey(index);
            if (!preferences.contains(displayKey)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(displayKey, slot.displayText);
            }
            if (!preferences.contains(commitKey)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(commitKey, slot.commitText);
            }
            if (!preferences.contains(isJavaScriptKey)) {
                editor = ensureEditor(editor, preferences);
                editor.putBoolean(isJavaScriptKey, slot.isJavaScript);
            }
            if (!preferences.contains(scriptKey)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(scriptKey, slot.scriptText);
            }
            if (!preferences.contains(timeoutKey)) {
                editor = ensureEditor(editor, preferences);
                editor.putInt(timeoutKey, slot.timeoutMs);
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    public static boolean readEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readEnabled(preferences);
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        if (preferences == null) {
            return DEFAULT_ENABLED;
        }
        Object value = preferences.getAll().get(PREF_KEY_TOP_ROW_SWIPE_ENABLED);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return DEFAULT_ENABLED;
    }

    public static boolean readZhuyinEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readZhuyinEnabled(preferences);
    }

    public static boolean readZhuyinEnabled(SharedPreferences preferences) {
        return readBoolean(preferences, PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                DEFAULT_ZHUYIN_ENABLED);
    }

    public static boolean readEnglishQwertyEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readEnglishQwertyEnabled(preferences);
    }

    public static boolean readEnglishQwertyEnabled(SharedPreferences preferences) {
        return readBoolean(preferences, PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                DEFAULT_ENGLISH_QWERTY_ENABLED);
    }

    public static String readGlobalJavaScript(Context context) {
        if (context == null) {
            return "";
        }
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readGlobalJavaScript(preferences);
    }

    public static String readGlobalJavaScript(SharedPreferences preferences) {
        if (preferences == null) {
            return "";
        }
        String value = readString(preferences, PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT, "");
        return value != null ? value : "";
    }

    public static JavaScriptRuntimeLimits readJavaScriptRuntimeLimits(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readJavaScriptRuntimeLimits(preferences);
    }

    public static JavaScriptRuntimeLimits readJavaScriptRuntimeLimits(
            SharedPreferences preferences) {
        if (preferences == null) {
            return defaultJavaScriptRuntimeLimits();
        }
        Map<String, ?> values = preferences.getAll();
        return javaScriptRuntimeLimitsFromValues(values);
    }

    public static void writeEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        writeEnabled(preferences(context), enabled);
        scheduleLocalMirrorSync(context);
    }

    public static void writeEnabled(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putBoolean(PREF_KEY_TOP_ROW_SWIPE_ENABLED, enabled)
                .apply();
    }

    public static void writeZhuyinEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        writeZhuyinEnabled(preferences(context), enabled);
        scheduleLocalMirrorSync(context);
    }

    public static void writeZhuyinEnabled(SharedPreferences preferences, boolean enabled) {
        writeBoolean(preferences, PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED, enabled);
    }

    public static void writeEnglishQwertyEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        writeEnglishQwertyEnabled(preferences(context), enabled);
        scheduleLocalMirrorSync(context);
    }

    public static void writeEnglishQwertyEnabled(SharedPreferences preferences,
            boolean enabled) {
        writeBoolean(preferences, PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED, enabled);
    }

    public static void writeGlobalJavaScript(Context context, String globalJavaScript) {
        if (context == null) {
            return;
        }
        writeGlobalJavaScript(preferences(context), globalJavaScript);
        scheduleLocalMirrorSync(context);
    }

    public static void writeGlobalJavaScript(SharedPreferences preferences,
            String globalJavaScript) {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putString(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT,
                        globalJavaScript != null ? globalJavaScript : "")
                .apply();
    }

    public static void writeJavaScriptRuntimeLimits(Context context,
            JavaScriptRuntimeLimits javaScriptRuntimeLimits) {
        if (context == null) {
            return;
        }
        writeJavaScriptRuntimeLimits(preferences(context), javaScriptRuntimeLimits);
        scheduleLocalMirrorSync(context);
    }

    public static void writeJavaScriptRuntimeLimits(SharedPreferences preferences,
            JavaScriptRuntimeLimits javaScriptRuntimeLimits) {
        if (preferences == null || javaScriptRuntimeLimits == null) {
            return;
        }
        JavaScriptRuntimeLimits sanitized = sanitizeJavaScriptRuntimeLimits(
                javaScriptRuntimeLimits);
        preferences.edit()
                .putInt(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                        sanitized.responseBodyLimitBytes)
                .putInt(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                        sanitized.timeoutMaxMs)
                .putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                        sanitized.memoryLimitBytes)
                .putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                        sanitized.maxStackBytes)
                .apply();
    }

    public static List<SlotText> readSlots(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readSlots(preferences);
    }

    public static List<SlotText> readSlots(SharedPreferences preferences) {
        if (preferences == null) {
            return defaultSlots();
        }
        JavaScriptRuntimeLimits javaScriptRuntimeLimits =
                readJavaScriptRuntimeLimits(preferences);
        Map<String, ?> values = preferences.getAll();
        List<String> displays = new ArrayList<String>(SLOT_COUNT);
        List<String> commits = new ArrayList<String>(SLOT_COUNT);
        List<Boolean> isJavaScripts = new ArrayList<Boolean>(SLOT_COUNT);
        List<String> scripts = new ArrayList<String>(SLOT_COUNT);
        List<Integer> timeouts = new ArrayList<Integer>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            displays.add(readString(preferences, displayPreferenceKey(index), null));
            commits.add(readString(preferences, commitPreferenceKey(index), null));
            isJavaScripts.add(booleanValue(values.get(isJavaScriptPreferenceKey(index))));
            scripts.add(readString(preferences, scriptPreferenceKey(index), null));
            timeouts.add(integerValue(values.get(timeoutPreferenceKey(index))));
        }
        return sanitizeSlots(displays, commits, isJavaScripts, scripts, timeouts,
                javaScriptRuntimeLimits.timeoutMaxMs);
    }

    public static RuntimeSettings readStrictRuntimeSettings(Context context) {
        if (context == null) {
            return unavailableRuntimeSettings();
        }
        return readStrictRuntimeSettings(preferences(context));
    }

    public static RuntimeSettings readStrictRuntimeSettings(SharedPreferences preferences) {
        if (preferences == null) {
            return unavailableRuntimeSettings();
        }
        return strictRuntimeSettingsFromValues(preferences.getAll());
    }

    static RuntimeSettings strictRuntimeSettingsFromValues(Map<String, ?> values) {
        if (values == null) {
            return unavailableRuntimeSettings();
        }
        Boolean enabled = strictBooleanValue(values.get(PREF_KEY_TOP_ROW_SWIPE_ENABLED));
        if (enabled == null) {
            return unavailableRuntimeSettings();
        }
        Boolean zhuyinEnabled = strictBooleanValue(
                values.get(PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED));
        Boolean englishQwertyEnabled = strictBooleanValue(
                values.get(PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED));
        String globalJavaScript = strictStringValue(
                values.get(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT));
        JavaScriptRuntimeLimits javaScriptRuntimeLimits =
                strictJavaScriptRuntimeLimitsFromValues(values);
        if (zhuyinEnabled == null || englishQwertyEnabled == null
                || globalJavaScript == null || javaScriptRuntimeLimits == null) {
            return unavailableRuntimeSettings();
        }

        List<String> displays = new ArrayList<String>(SLOT_COUNT);
        List<String> commits = new ArrayList<String>(SLOT_COUNT);
        List<Boolean> isJavaScripts = new ArrayList<Boolean>(SLOT_COUNT);
        List<String> scripts = new ArrayList<String>(SLOT_COUNT);
        List<Integer> timeouts = new ArrayList<Integer>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            String display = strictTextValue(values.get(displayPreferenceKey(index)));
            String commit = strictStringValue(values.get(commitPreferenceKey(index)));
            Boolean isJavaScript = strictBooleanValue(
                    values.get(isJavaScriptPreferenceKey(index)));
            String script = strictStringValue(values.get(scriptPreferenceKey(index)));
            Integer timeout = strictIntegerValue(values.get(timeoutPreferenceKey(index)));
            if (display == null || commit == null || isJavaScript == null
                    || script == null || timeout == null) {
                return unavailableRuntimeSettings();
            }
            displays.add(display);
            commits.add(commit);
            isJavaScripts.add(isJavaScript);
            scripts.add(script);
            timeouts.add(timeout);
        }
        List<SlotText> slots = sanitizeSlots(displays, commits, isJavaScripts, scripts,
                timeouts, javaScriptRuntimeLimits.timeoutMaxMs);
        return new RuntimeSettings(true, enabled.booleanValue(),
                zhuyinEnabled.booleanValue(), englishQwertyEnabled.booleanValue(),
                globalJavaScript,
                javaScriptRuntimeLimits,
                Collections.unmodifiableList(slots));
    }

    public static void writeSlot(Context context, int index, SlotText slot) {
        if (context == null) {
            return;
        }
        writeSlot(preferences(context), index, slot);
        scheduleLocalMirrorSync(context);
    }

    public static void writeSlot(SharedPreferences preferences, int index, SlotText slot) {
        if (preferences == null || index < 0 || index >= SLOT_COUNT || slot == null) {
            return;
        }
        List<String> displays = new ArrayList<String>(Collections.nCopies(SLOT_COUNT, null));
        List<String> commits = new ArrayList<String>(Collections.nCopies(SLOT_COUNT, null));
        List<Boolean> isJavaScripts =
                new ArrayList<Boolean>(Collections.nCopies(SLOT_COUNT, null));
        List<String> scripts = new ArrayList<String>(Collections.nCopies(SLOT_COUNT, null));
        List<Integer> timeouts =
                new ArrayList<Integer>(Collections.nCopies(SLOT_COUNT, null));
        displays.set(index, slot.displayText);
        commits.set(index, slot.commitText);
        isJavaScripts.set(index, Boolean.valueOf(slot.isJavaScript));
        scripts.set(index, slot.scriptText);
        timeouts.set(index, Integer.valueOf(slot.timeoutMs));
        int timeoutMaxMs = readJavaScriptRuntimeLimits(preferences).timeoutMaxMs;
        SlotText sanitized = sanitizeSlots(displays, commits, isJavaScripts, scripts,
                timeouts, timeoutMaxMs).get(index);
        preferences.edit()
                .putString(displayPreferenceKey(index), sanitized.displayText)
                .putString(commitPreferenceKey(index), sanitized.commitText)
                .putBoolean(isJavaScriptPreferenceKey(index), sanitized.isJavaScript)
                .putString(scriptPreferenceKey(index), sanitized.scriptText)
                .putInt(timeoutPreferenceKey(index), sanitized.timeoutMs)
                .apply();
    }

    public static void writeSlots(Context context, List<SlotText> slots) {
        if (context == null) {
            return;
        }
        writeSlots(preferences(context), slots);
        scheduleLocalMirrorSync(context);
    }

    public static void writeSlots(SharedPreferences preferences, List<SlotText> slots) {
        if (preferences == null) {
            return;
        }
        List<SlotText> sanitized = sanitizeSlotTexts(
                slots,
                readJavaScriptRuntimeLimits(preferences).timeoutMaxMs);
        SharedPreferences.Editor editor = preferences.edit();
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = sanitized.get(index);
            editor.putString(displayPreferenceKey(index), slot.displayText);
            editor.putString(commitPreferenceKey(index), slot.commitText);
            editor.putBoolean(isJavaScriptPreferenceKey(index), slot.isJavaScript);
            editor.putString(scriptPreferenceKey(index), slot.scriptText);
            editor.putInt(timeoutPreferenceKey(index), slot.timeoutMs);
        }
        editor.apply();
    }

    static void writeImportedSettings(SharedPreferences preferences,
            ExportedSettings importedSettings) {
        if (preferences == null || importedSettings == null) {
            return;
        }
        JavaScriptRuntimeLimits javaScriptRuntimeLimits = sanitizeJavaScriptRuntimeLimits(
                importedSettings.javaScriptRuntimeLimits);
        List<SlotText> sanitized = sanitizeSlotTexts(
                importedSettings.slots,
                javaScriptRuntimeLimits.timeoutMaxMs);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT,
                importedSettings.globalJavaScript);
        editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                javaScriptRuntimeLimits.responseBodyLimitBytes);
        editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                javaScriptRuntimeLimits.timeoutMaxMs);
        editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                javaScriptRuntimeLimits.memoryLimitBytes);
        editor.putInt(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                javaScriptRuntimeLimits.maxStackBytes);
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = sanitized.get(index);
            editor.putString(displayPreferenceKey(index), slot.displayText);
            editor.putString(commitPreferenceKey(index), slot.commitText);
            editor.putBoolean(isJavaScriptPreferenceKey(index), slot.isJavaScript);
            editor.putString(scriptPreferenceKey(index), slot.scriptText);
            editor.putInt(timeoutPreferenceKey(index), slot.timeoutMs);
        }
        editor.apply();
    }

    static void writeImportedSettings(Context context, ExportedSettings importedSettings) {
        if (context == null) {
            return;
        }
        writeImportedSettings(preferences(context), importedSettings);
        scheduleLocalMirrorSync(context);
    }

    public static void resetSlot(Context context, int index) {
        if (context == null) {
            return;
        }
        resetSlot(preferences(context), index);
        scheduleLocalMirrorSync(context);
    }

    public static void resetSlot(SharedPreferences preferences, int index) {
        if (preferences == null || index < 0 || index >= SLOT_COUNT) {
            return;
        }
        writeSlot(preferences, index, defaultSlot(index));
    }

    public static void resetSlots(Context context) {
        if (context == null) {
            return;
        }
        resetSlots(preferences(context));
        scheduleLocalMirrorSync(context);
    }

    public static void resetSlots(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        List<SlotText> defaults = defaultSlots();
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = defaults.get(index);
            editor.putString(displayPreferenceKey(index), slot.displayText);
            editor.putString(commitPreferenceKey(index), slot.commitText);
            editor.putBoolean(isJavaScriptPreferenceKey(index), slot.isJavaScript);
            editor.putString(scriptPreferenceKey(index), slot.scriptText);
            editor.putInt(timeoutPreferenceKey(index), slot.timeoutMs);
        }
        editor.apply();
    }

    public static SlotText defaultSlot(int index) {
        List<SlotText> defaults = defaultSlots();
        if (index < 0 || index >= defaults.size()) {
            return defaults.get(0);
        }
        return defaults.get(index);
    }

    public static List<SlotText> defaultSlots() {
        List<SlotText> slots = new ArrayList<SlotText>(SLOT_COUNT);
        for (String text : DEFAULT_TEXTS) {
            slots.add(new SlotText(text, text));
        }
        return Collections.unmodifiableList(slots);
    }

    public static void initializeRemoteSync(Context context) {
        Context lookupContext = resolveLookupContext(context);
        if (lookupContext == null) {
            return;
        }
        try {
            PatchedServiceBridge.initialize(lookupContext);
        } catch (Throwable ignored) {
            return;
        }
        if (!REMOTE_SYNC_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        try {
            PatchedServiceBridge.registerOnServiceBoundListener((boundContext, service) ->
                    syncLocalMirrorToRemote(boundContext));
        } catch (Throwable ignored) {
            // Best effort; runtime writes still trigger explicit sync.
        }
    }

    private static void scheduleLocalMirrorSync(Context context) {
        Context lookupContext = resolveLookupContext(context);
        if (lookupContext == null) {
            return;
        }
        initializeRemoteSync(lookupContext);
        REMOTE_SYNC_CONTEXT.set(lookupContext);
        REMOTE_SYNC_REQUEST_VERSION.incrementAndGet();
        startPendingRemoteSyncs();
    }

    private static void startPendingRemoteSyncs() {
        if (!REMOTE_SYNC_RUNNING.compareAndSet(false, true)) {
            return;
        }
        try {
            REMOTE_SYNC_EXECUTOR.execute(GboardTopRowSwipeSettings::runPendingRemoteSyncs);
        } catch (Throwable throwable) {
            REMOTE_SYNC_RUNNING.set(false);
            startPendingRemoteSyncsFallback();
        }
    }

    private static void startPendingRemoteSyncsFallback() {
        try {
            Thread thread = new Thread(GboardTopRowSwipeSettings::runPendingRemoteSyncs,
                    REMOTE_SYNC_THREAD_NAME + "-fallback");
            thread.setDaemon(true);
            if (!REMOTE_SYNC_RUNNING.compareAndSet(false, true)) {
                return;
            }
            thread.start();
        } catch (Throwable ignored) {
            REMOTE_SYNC_RUNNING.set(false);
        }
    }

    private static void runPendingRemoteSyncs() {
        try {
            while (true) {
                int requestedVersion = REMOTE_SYNC_REQUEST_VERSION.get();
                Context context = REMOTE_SYNC_CONTEXT.get();
                if (context != null) {
                    syncLocalMirrorToRemote(context);
                }
                REMOTE_SYNC_COMPLETED_VERSION.set(requestedVersion);
                if (REMOTE_SYNC_REQUEST_VERSION.get() == requestedVersion) {
                    return;
                }
            }
        } finally {
            REMOTE_SYNC_RUNNING.set(false);
            if (REMOTE_SYNC_COMPLETED_VERSION.get() != REMOTE_SYNC_REQUEST_VERSION.get()) {
                startPendingRemoteSyncs();
            }
        }
    }

    public static boolean syncLocalMirrorToRemote(Context context) {
        Context lookupContext = resolveLookupContext(context);
        if (lookupContext == null) {
            return false;
        }
        initializeRemoteSync(lookupContext);
        SharedPreferences localPreferences = preferences(lookupContext);
        ensureDefaults(localPreferences);
        SharedPreferences remotePreferences = remotePreferences();
        if (remotePreferences == null) {
            return false;
        }
        return copyToRemotePreferences(localPreferences, remotePreferences);
    }

    private static Context resolveLookupContext(Context context) {
        if (context == null) {
            return null;
        }
        try {
            Context applicationContext = context.getApplicationContext();
            return applicationContext != null ? applicationContext : context;
        } catch (Throwable ignored) {
            return context;
        }
    }

    static boolean copyToRemotePreferences(SharedPreferences localPreferences,
            SharedPreferences remotePreferences) {
        if (localPreferences == null || remotePreferences == null) {
            return false;
        }
        RuntimeSettings runtimeSettings = readStrictRuntimeSettings(localPreferences);
        if (!runtimeSettings.available) {
            return false;
        }
        SharedPreferences.Editor editor = remotePreferences.edit();
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_ENABLED,
                Boolean.toString(runtimeSettings.enabled));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                Boolean.toString(runtimeSettings.zhuyinEnabled));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                Boolean.toString(runtimeSettings.englishQwertyEnabled));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT,
                runtimeSettings.globalJavaScript);
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                Integer.toString(runtimeSettings.javaScriptRuntimeLimits
                        .responseBodyLimitBytes));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                Integer.toString(runtimeSettings.javaScriptRuntimeLimits.timeoutMaxMs));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                Integer.toString(runtimeSettings.javaScriptRuntimeLimits.memoryLimitBytes));
        editor.putString(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                Integer.toString(runtimeSettings.javaScriptRuntimeLimits.maxStackBytes));
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = runtimeSettings.slots.get(index);
            editor.putString(displayPreferenceKey(index), slot.displayText);
            editor.putString(commitPreferenceKey(index), slot.commitText);
            editor.putString(isJavaScriptPreferenceKey(index),
                    Boolean.toString(slot.isJavaScript));
            editor.putString(scriptPreferenceKey(index), slot.scriptText);
            editor.putString(timeoutPreferenceKey(index), Integer.toString(slot.timeoutMs));
        }
        return editor.commit();
    }

    public static String exportSlots(List<SlotText> slots) {
        return exportSettings("", defaultJavaScriptRuntimeLimits(), slots);
    }

    public static String exportSettings(String globalJavaScript, List<SlotText> slots) {
        return exportSettings(globalJavaScript, defaultJavaScriptRuntimeLimits(), slots);
    }

    public static String exportSettings(String globalJavaScript,
            JavaScriptRuntimeLimits javaScriptRuntimeLimits, List<SlotText> slots) {
        JavaScriptRuntimeLimits sanitizedRuntimeLimits = sanitizeJavaScriptRuntimeLimits(
                javaScriptRuntimeLimits);
        List<SlotText> sanitized = sanitizeSlotTexts(
                slots,
                sanitizedRuntimeLimits.timeoutMaxMs);
        String safeGlobalJavaScript = globalJavaScript != null ? globalJavaScript : "";
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"")
                .append(EXPORT_FORMAT_KEY)
                .append("\": \"")
                .append(escapeJsonString(EXPORT_FORMAT_VALUE))
                .append("\",\n");
        builder.append("  \"")
                .append(EXPORT_VERSION_KEY)
                .append("\": ")
                .append(EXPORT_VERSION_VALUE)
                .append(",\n");
        builder.append("  \"")
                .append(EXPORT_GLOBAL_JAVA_SCRIPT_KEY)
                .append("\": \"")
                .append(escapeJsonString(safeGlobalJavaScript))
                .append("\",\n");
        builder.append("  \"")
                .append(EXPORT_JAVA_SCRIPT_RUNTIME_LIMITS_KEY)
                .append("\": {\n");
        builder.append("    \"")
                .append(EXPORT_RESPONSE_BODY_LIMIT_BYTES_KEY)
                .append("\": ")
                .append(sanitizedRuntimeLimits.responseBodyLimitBytes)
                .append(",\n");
        builder.append("    \"")
                .append(EXPORT_TIMEOUT_MAX_MS_KEY)
                .append("\": ")
                .append(sanitizedRuntimeLimits.timeoutMaxMs)
                .append(",\n");
        builder.append("    \"")
                .append(EXPORT_MEMORY_LIMIT_BYTES_KEY)
                .append("\": ")
                .append(sanitizedRuntimeLimits.memoryLimitBytes)
                .append(",\n");
        builder.append("    \"")
                .append(EXPORT_MAX_STACK_BYTES_KEY)
                .append("\": ")
                .append(sanitizedRuntimeLimits.maxStackBytes)
                .append("\n");
        builder.append("  },\n");
        builder.append("  \"")
                .append(EXPORT_SLOTS_KEY)
                .append("\": [\n");
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = sanitized.get(index);
            builder.append("    {\"")
                    .append(EXPORT_DISPLAY_KEY)
                    .append("\": \"")
                    .append(escapeJsonString(slot.displayText))
                    .append("\", \"")
                    .append(EXPORT_COMMIT_KEY)
                    .append("\": \"")
                    .append(escapeJsonString(slot.commitText))
                    .append("\", \"")
                    .append(EXPORT_IS_JAVA_SCRIPT_KEY)
                    .append("\": ")
                    .append(slot.isJavaScript)
                    .append(", \"")
                    .append(EXPORT_SCRIPT_KEY)
                    .append("\": \"")
                    .append(escapeJsonString(slot.scriptText))
                    .append("\", \"")
                    .append(EXPORT_TIMEOUT_KEY)
                    .append("\": ")
                    .append(slot.timeoutMs)
                    .append("}");
            if (index < SLOT_COUNT - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    public static List<SlotText> parseExportedSlots(String text) {
        return parseExportedSettings(text).slots;
    }

    public static ExportedSettings parseExportedSettings(String text) {
        if (isBlank(text)) {
            throw new IllegalArgumentException("Import text cannot be blank.");
        }

        Object parsed = new JsonParser(text).parse();
        if (!(parsed instanceof Map<?, ?> rawObject)) {
            throw new IllegalArgumentException("Import JSON root must be an object.");
        }

        String format = requiredJsonStringField(rawObject, EXPORT_FORMAT_KEY,
                "Import format");
        if (!EXPORT_FORMAT_VALUE.equals(format)) {
            throw new IllegalArgumentException("Unsupported Top Row Swipe export format.");
        }
        Integer version = requiredJsonIntegerField(rawObject, EXPORT_VERSION_KEY,
                "Import version");
        boolean legacyVersion = version.intValue() == LEGACY_EXPORT_VERSION_VALUE;
        if (!legacyVersion && version.intValue() != EXPORT_VERSION_VALUE) {
            throw new IllegalArgumentException("Unsupported Top Row Swipe export version.");
        }
        String globalJavaScript = optionalJsonStringField(rawObject,
                EXPORT_GLOBAL_JAVA_SCRIPT_KEY,
                "Field '" + EXPORT_GLOBAL_JAVA_SCRIPT_KEY + "'");
        if (globalJavaScript == null) {
            globalJavaScript = "";
        }
        JavaScriptRuntimeLimits javaScriptRuntimeLimits =
                parseExportedJavaScriptRuntimeLimits(rawObject);
        Object rawSlots = rawObject.get(EXPORT_SLOTS_KEY);
        if (!(rawSlots instanceof List<?> slotObjects)
                || slotObjects.size() != SLOT_COUNT) {
            throw new IllegalArgumentException("Import JSON must contain exactly 10 slots.");
        }

        List<String> displays = new ArrayList<String>(SLOT_COUNT);
        List<String> commits = new ArrayList<String>(SLOT_COUNT);
        List<Boolean> isJavaScripts = new ArrayList<Boolean>(SLOT_COUNT);
        List<String> scripts = new ArrayList<String>(SLOT_COUNT);
        List<Integer> timeouts = new ArrayList<Integer>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            Object rawSlot = slotObjects.get(index);
            if (!(rawSlot instanceof Map<?, ?> slotObject)) {
                throw new IllegalArgumentException(
                        "Slot " + (index + 1) + " must be a JSON object.");
            }
            String displayKey = legacyVersion ? LEGACY_EXPORT_DISPLAY_KEY : EXPORT_DISPLAY_KEY;
            String commitKey = legacyVersion ? LEGACY_EXPORT_COMMIT_KEY : EXPORT_COMMIT_KEY;
            String display = optionalJsonStringField(slotObject, displayKey,
                    slotFieldLabel(index, displayKey));
            if (display != null && display.trim().isEmpty()) {
                throw new IllegalArgumentException(slotFieldLabel(index, displayKey)
                        + " must not be blank.");
            }
            displays.add(display);
            commits.add(optionalJsonStringField(slotObject, commitKey,
                    slotFieldLabel(index, commitKey)));
            isJavaScripts.add(optionalJsonBooleanField(slotObject,
                    EXPORT_IS_JAVA_SCRIPT_KEY,
                    slotFieldLabel(index, EXPORT_IS_JAVA_SCRIPT_KEY)));
            scripts.add(optionalJsonStringField(slotObject, EXPORT_SCRIPT_KEY,
                    slotFieldLabel(index, EXPORT_SCRIPT_KEY)));
            timeouts.add(optionalJsonIntegerField(slotObject, EXPORT_TIMEOUT_KEY,
                    slotFieldLabel(index, EXPORT_TIMEOUT_KEY)));
        }
        return new ExportedSettings(globalJavaScript,
                javaScriptRuntimeLimits,
                sanitizeSlots(displays, commits, isJavaScripts, scripts, timeouts,
                        javaScriptRuntimeLimits.timeoutMaxMs));
    }

    public static List<SlotText> sanitizeSlots(List<String> displays, List<String> commits) {
        return sanitizeSlots(displays, commits, null, null, null, DEFAULT_TIMEOUT_MAX_MS);
    }

    public static List<SlotText> sanitizeSlots(List<String> displays, List<String> commits,
            List<Boolean> isJavaScripts, List<String> scripts, List<Integer> timeouts) {
        return sanitizeSlots(displays, commits, isJavaScripts, scripts, timeouts,
                DEFAULT_TIMEOUT_MAX_MS);
    }

    public static List<SlotText> sanitizeSlots(List<String> displays, List<String> commits,
            List<Boolean> isJavaScripts, List<String> scripts, List<Integer> timeouts,
            int timeoutMaxMs) {
        List<SlotText> defaults = defaultSlots();
        List<SlotText> sanitized = new ArrayList<SlotText>(SLOT_COUNT);
        int sanitizedTimeoutMaxMs = sanitizeTimeoutMaxMs(Integer.valueOf(timeoutMaxMs));
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText defaultSlot = defaults.get(index);
            String display = valueAt(displays, index);
            if (isBlank(display)) {
                display = defaultSlot.displayText;
            } else {
                display = display.trim();
            }

            String commit = valueAt(commits, index);
            if (isBlank(commit)) {
                commit = display;
            } else {
                commit = commit.trim();
            }

            boolean isJavaScript = booleanAt(isJavaScripts, index, false);
            if (!isJavaScript) {
                sanitized.add(new SlotText(display, commit, false, "",
                        DEFAULT_TIMEOUT_MS));
                continue;
            }

            String script = valueAt(scripts, index);
            script = script != null ? script.trim() : "";
            int timeout = sanitizeTimeout(valueAtInteger(timeouts, index), sanitizedTimeoutMaxMs);
            sanitized.add(new SlotText(display, commit, true, script, timeout));
        }
        return Collections.unmodifiableList(sanitized);
    }

    public static String displayPreferenceKey(int index) {
        return "pref_top_row_swipe_slot_" + paddedOneBasedIndex(index) + "_display";
    }

    public static String commitPreferenceKey(int index) {
        return "pref_top_row_swipe_slot_" + paddedOneBasedIndex(index) + "_commit";
    }

    public static String isJavaScriptPreferenceKey(int index) {
        return "pref_top_row_swipe_slot_" + paddedOneBasedIndex(index) + "_is_js";
    }

    public static String scriptPreferenceKey(int index) {
        return "pref_top_row_swipe_slot_" + paddedOneBasedIndex(index) + "_script";
    }

    public static String timeoutPreferenceKey(int index) {
        return "pref_top_row_swipe_slot_" + paddedOneBasedIndex(index) + "_timeout";
    }

    private static List<SlotText> sanitizeSlotTexts(List<SlotText> slots, int timeoutMaxMs) {
        List<String> displays = new ArrayList<String>(SLOT_COUNT);
        List<String> commits = new ArrayList<String>(SLOT_COUNT);
        List<Boolean> isJavaScripts = new ArrayList<Boolean>(SLOT_COUNT);
        List<String> scripts = new ArrayList<String>(SLOT_COUNT);
        List<Integer> timeouts = new ArrayList<Integer>(SLOT_COUNT);
        for (int index = 0; index < SLOT_COUNT; index++) {
            SlotText slot = valueAtSlotTexts(slots, index);
            displays.add(slot != null ? slot.displayText : null);
            commits.add(slot != null ? slot.commitText : null);
            isJavaScripts.add(slot != null ? Boolean.valueOf(slot.isJavaScript) : null);
            scripts.add(slot != null ? slot.scriptText : null);
            timeouts.add(slot != null ? Integer.valueOf(slot.timeoutMs) : null);
        }
        return sanitizeSlots(displays, commits, isJavaScripts, scripts, timeouts,
                timeoutMaxMs);
    }

    public static SlotText resolveEditedSlot(String oldDisplay, String oldCommit,
            String newDisplay, String requestedCommit) {
        boolean commitManuallyEdited =
                !safeTrim(oldCommit).equals(safeTrim(oldDisplay));
        return resolveEditedSlot(oldDisplay, oldCommit, newDisplay, requestedCommit,
                commitManuallyEdited);
    }

    public static SlotText resolveEditedSlot(String oldDisplay, String oldCommit,
            String newDisplay, String requestedCommit, boolean commitManuallyEdited) {
        String display = isBlank(newDisplay) ? safeTrim(oldDisplay) : newDisplay.trim();
        if (isBlank(display)) {
            display = defaultSlots().get(0).displayText;
        }

        String commit;
        if (isBlank(requestedCommit)) {
            commit = display;
        } else if (!commitManuallyEdited
                && safeTrim(oldCommit).equals(safeTrim(oldDisplay))
                && safeTrim(requestedCommit).equals(safeTrim(oldCommit))) {
            commit = display;
        } else {
            commit = requestedCommit.trim();
        }
        return new SlotText(display, commit);
    }

    private static RuntimeSettings unavailableRuntimeSettings() {
        return new RuntimeSettings(false, false, false, false, "",
                defaultJavaScriptRuntimeLimits(),
                Collections.emptyList());
    }

    private static boolean readBoolean(SharedPreferences preferences, String key,
            boolean defaultValue) {
        if (preferences == null) {
            return defaultValue;
        }
        Object value = preferences.getAll().get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }

    private static void writeBoolean(SharedPreferences preferences, String key,
            boolean enabled) {
        if (preferences == null) {
            return;
        }
        preferences.edit()
                .putBoolean(key, enabled)
                .apply();
    }

    private static Boolean strictBooleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim();
            if ("true".equalsIgnoreCase(normalized)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(normalized)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static Boolean booleanValue(Object value) {
        return strictBooleanValue(value);
    }

    private static String strictTextValue(Object value) {
        if (!(value instanceof String stringValue)) {
            return null;
        }
        String trimmed = stringValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String strictStringValue(Object value) {
        return value instanceof String stringValue ? stringValue : null;
    }

    private static Integer strictIntegerValue(Object value) {
        return integerValue(value);
    }

    private static JavaScriptRuntimeLimits javaScriptRuntimeLimitsFromValues(
            Map<String, ?> values) {
        if (values == null) {
            return defaultJavaScriptRuntimeLimits();
        }
        return sanitizeJavaScriptRuntimeLimits(new JavaScriptRuntimeLimits(
                integerValue(values.get(PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES))
                        != null
                                ? integerValue(values.get(
                                        PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES))
                                : DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
                integerValue(values.get(PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS)) != null
                                ? integerValue(values.get(
                                        PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS))
                                : DEFAULT_TIMEOUT_MAX_MS,
                integerValue(values.get(PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES)) != null
                                ? integerValue(values.get(
                                        PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES))
                                : DEFAULT_MEMORY_LIMIT_BYTES,
                integerValue(values.get(PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES)) != null
                                ? integerValue(values.get(
                                        PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES))
                                : DEFAULT_MAX_STACK_BYTES));
    }

    private static JavaScriptRuntimeLimits strictJavaScriptRuntimeLimitsFromValues(
            Map<String, ?> values) {
        Integer responseBodyLimitBytes = strictOptionalIntegerValue(values,
                PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                DEFAULT_RESPONSE_BODY_LIMIT_BYTES);
        Integer timeoutMaxMs = strictOptionalIntegerValue(values,
                PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                DEFAULT_TIMEOUT_MAX_MS);
        Integer memoryLimitBytes = strictOptionalIntegerValue(values,
                PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                DEFAULT_MEMORY_LIMIT_BYTES);
        Integer maxStackBytes = strictOptionalIntegerValue(values,
                PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                DEFAULT_MAX_STACK_BYTES);
        if (responseBodyLimitBytes == null
                || timeoutMaxMs == null
                || memoryLimitBytes == null
                || maxStackBytes == null) {
            return null;
        }
        return sanitizeJavaScriptRuntimeLimits(new JavaScriptRuntimeLimits(
                responseBodyLimitBytes.intValue(),
                timeoutMaxMs.intValue(),
                memoryLimitBytes.intValue(),
                maxStackBytes.intValue()));
    }

    public static JavaScriptRuntimeLimits defaultJavaScriptRuntimeLimits() {
        return new JavaScriptRuntimeLimits(
                DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
                DEFAULT_TIMEOUT_MAX_MS,
                DEFAULT_MEMORY_LIMIT_BYTES,
                DEFAULT_MAX_STACK_BYTES);
    }

    public static JavaScriptRuntimeLimits sanitizeJavaScriptRuntimeLimits(
            JavaScriptRuntimeLimits javaScriptRuntimeLimits) {
        if (javaScriptRuntimeLimits == null) {
            return defaultJavaScriptRuntimeLimits();
        }
        return new JavaScriptRuntimeLimits(
                sanitizeResponseBodyLimitBytes(
                        Integer.valueOf(javaScriptRuntimeLimits.responseBodyLimitBytes)),
                sanitizeTimeoutMaxMs(Integer.valueOf(javaScriptRuntimeLimits.timeoutMaxMs)),
                sanitizeMemoryLimitBytes(
                        Integer.valueOf(javaScriptRuntimeLimits.memoryLimitBytes)),
                sanitizeMaxStackBytes(Integer.valueOf(javaScriptRuntimeLimits.maxStackBytes)));
    }

    private static SharedPreferences.Editor ensureEditor(SharedPreferences.Editor editor,
            SharedPreferences preferences) {
        return editor != null ? editor : preferences.edit();
    }

    private static SharedPreferences remotePreferences() {
        return PatchedServiceBridge.remotePreferences(PREF_FILE);
    }

    private static Integer strictOptionalIntegerValue(Map<String, ?> values, String key,
            int defaultValue) {
        if (values == null || key == null) {
            return Integer.valueOf(defaultValue);
        }
        if (!values.containsKey(key)) {
            return Integer.valueOf(defaultValue);
        }
        return strictIntegerValue(values.get(key));
    }

    private static String requiredJsonStringField(Map<?, ?> object, String key,
            String label) {
        String value = optionalJsonStringField(object, key, label);
        if (value == null) {
            throw new IllegalArgumentException(label + " must be a JSON string.");
        }
        return value;
    }

    private static Integer requiredJsonIntegerField(Map<?, ?> object, String key,
            String label) {
        Integer value = optionalJsonIntegerField(object, key, label);
        if (value == null) {
            throw new IllegalArgumentException(label + " must be a JSON integer.");
        }
        return value;
    }

    private static JavaScriptRuntimeLimits parseExportedJavaScriptRuntimeLimits(
            Map<?, ?> rawObject) {
        Object rawRuntimeLimits = rawObject.get(EXPORT_JAVA_SCRIPT_RUNTIME_LIMITS_KEY);
        if (!(rawRuntimeLimits instanceof Map<?, ?> runtimeLimitsObject)) {
            return defaultJavaScriptRuntimeLimits();
        }
        Integer responseBodyLimitBytes = optionalJsonIntegerField(runtimeLimitsObject,
                EXPORT_RESPONSE_BODY_LIMIT_BYTES_KEY,
                "Field '" + EXPORT_RESPONSE_BODY_LIMIT_BYTES_KEY + "'");
        Integer timeoutMaxMs = optionalJsonIntegerField(runtimeLimitsObject,
                EXPORT_TIMEOUT_MAX_MS_KEY,
                "Field '" + EXPORT_TIMEOUT_MAX_MS_KEY + "'");
        Integer memoryLimitBytes = optionalJsonIntegerField(runtimeLimitsObject,
                EXPORT_MEMORY_LIMIT_BYTES_KEY,
                "Field '" + EXPORT_MEMORY_LIMIT_BYTES_KEY + "'");
        Integer maxStackBytes = optionalJsonIntegerField(runtimeLimitsObject,
                EXPORT_MAX_STACK_BYTES_KEY,
                "Field '" + EXPORT_MAX_STACK_BYTES_KEY + "'");
        return sanitizeJavaScriptRuntimeLimits(new JavaScriptRuntimeLimits(
                responseBodyLimitBytes != null
                        ? responseBodyLimitBytes.intValue() : DEFAULT_RESPONSE_BODY_LIMIT_BYTES,
                timeoutMaxMs != null
                        ? timeoutMaxMs.intValue() : DEFAULT_TIMEOUT_MAX_MS,
                memoryLimitBytes != null
                        ? memoryLimitBytes.intValue() : DEFAULT_MEMORY_LIMIT_BYTES,
                maxStackBytes != null
                        ? maxStackBytes.intValue() : DEFAULT_MAX_STACK_BYTES));
    }

    private static String optionalJsonStringField(Map<?, ?> object, String key,
            String label) {
        if (object == null || !object.containsKey(key)) {
            return null;
        }
        Object value = object.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new IllegalArgumentException(label + " must be a JSON string when present.");
    }

    private static Boolean optionalJsonBooleanField(Map<?, ?> object, String key,
            String label) {
        if (object == null || !object.containsKey(key)) {
            return null;
        }
        Object value = object.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException(label + " must be a JSON boolean when present.");
    }

    private static Integer optionalJsonIntegerField(Map<?, ?> object, String key,
            String label) {
        if (object == null || !object.containsKey(key)) {
            return null;
        }
        Object value = object.get(key);
        if (value instanceof Long longValue) {
            long rawValue = longValue.longValue();
            if (rawValue >= Integer.MIN_VALUE && rawValue <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) rawValue);
            }
        } else if (value instanceof Integer intValue) {
            return intValue;
        }
        throw new IllegalArgumentException(label + " must be a JSON integer when present.");
    }

    private static String slotFieldLabel(int slotIndex, String key) {
        return "Slot " + (slotIndex + 1) + " field '" + key + "'";
    }

    private static String escapeJsonString(String value) {
        String rawValue = value != null ? value : "";
        StringBuilder builder = new StringBuilder(rawValue.length());
        for (int index = 0; index < rawValue.length(); index++) {
            char character = rawValue.charAt(index);
            switch (character) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (character < 0x20 || character == '\u2028'
                            || character == '\u2029') {
                        builder.append("\\u");
                        String hex = Integer.toHexString(character);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(character);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static String stringValue(Object value) {
        return value instanceof String stringValue ? stringValue : null;
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Long longValue) {
            long rawValue = longValue.longValue();
            if (rawValue < Integer.MIN_VALUE || rawValue > Integer.MAX_VALUE) {
                return null;
            }
            return Integer.valueOf((int) rawValue);
        }
        if (value instanceof Number numberValue) {
            double rawValue = numberValue.doubleValue();
            if (!Double.isFinite(rawValue) || rawValue != Math.rint(rawValue)
                    || rawValue < Integer.MIN_VALUE || rawValue > Integer.MAX_VALUE) {
                return null;
            }
            return Integer.valueOf((int) rawValue);
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.valueOf(Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String readString(SharedPreferences preferences, String key,
            String defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        if (value instanceof Boolean booleanValue) {
            return Boolean.toString(booleanValue);
        }
        return defaultValue;
    }

    private static String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static Integer valueAtInteger(List<Integer> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static boolean booleanAt(List<Boolean> values, int index, boolean defaultValue) {
        if (values == null || index < 0 || index >= values.size()) {
            return defaultValue;
        }
        Boolean value = values.get(index);
        return value != null ? value.booleanValue() : defaultValue;
    }

    private static int sanitizeTimeout(Integer timeoutMs, int timeoutMaxMs) {
        if (timeoutMs == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        int timeout = timeoutMs.intValue();
        if (timeout < MIN_TIMEOUT_MS) {
            return MIN_TIMEOUT_MS;
        }
        if (timeout > timeoutMaxMs) {
            return timeoutMaxMs;
        }
        return timeout;
    }

    private static int sanitizeResponseBodyLimitBytes(Integer limitBytes) {
        if (limitBytes == null) {
            return DEFAULT_RESPONSE_BODY_LIMIT_BYTES;
        }
        int limit = limitBytes.intValue();
        if (limit < MIN_RESPONSE_BODY_LIMIT_BYTES) {
            return MIN_RESPONSE_BODY_LIMIT_BYTES;
        }
        return limit;
    }

    private static int sanitizeTimeoutMaxMs(Integer timeoutMaxMs) {
        if (timeoutMaxMs == null) {
            return DEFAULT_TIMEOUT_MAX_MS;
        }
        int timeout = timeoutMaxMs.intValue();
        if (timeout < MIN_TIMEOUT_MAX_MS) {
            return MIN_TIMEOUT_MAX_MS;
        }
        return timeout;
    }

    private static int sanitizeMemoryLimitBytes(Integer memoryLimitBytes) {
        if (memoryLimitBytes == null) {
            return DEFAULT_MEMORY_LIMIT_BYTES;
        }
        int limit = memoryLimitBytes.intValue();
        if (limit < MIN_MEMORY_LIMIT_BYTES) {
            return MIN_MEMORY_LIMIT_BYTES;
        }
        return limit;
    }

    private static int sanitizeMaxStackBytes(Integer maxStackBytes) {
        if (maxStackBytes == null) {
            return DEFAULT_MAX_STACK_BYTES;
        }
        int limit = maxStackBytes.intValue();
        if (limit < MIN_MAX_STACK_BYTES) {
            return MIN_MAX_STACK_BYTES;
        }
        return limit;
    }

    private static SlotText valueAtSlotTexts(List<SlotText> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return null;
        }
        return values.get(index);
    }

    private static String paddedOneBasedIndex(int index) {
        int oneBased = index + 1;
        return oneBased < 10 ? "0" + oneBased : Integer.toString(oneBased);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class JsonParser {
        private final String text;
        private int position;

        JsonParser(String text) {
            this.text = text != null ? text : "";
        }

        Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (position != text.length()) {
                throw error("Unexpected trailing JSON content.");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (position >= text.length()) {
                throw error("Unexpected end of JSON.");
            }
            char character = text.charAt(position);
            if (character == '{') {
                return parseObject();
            }
            if (character == '[') {
                return parseArray();
            }
            if (character == '"') {
                return parseString();
            }
            if (character == '-' || isDigit(character)) {
                return parseNumber();
            }
            if (consumeLiteral("true")) {
                return Boolean.TRUE;
            }
            if (consumeLiteral("false")) {
                return Boolean.FALSE;
            }
            if (consumeLiteral("null")) {
                return null;
            }
            throw error("Unexpected JSON value.");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new HashMap<String, Object>();
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                skipWhitespace();
                if (position >= text.length() || text.charAt(position) != '"') {
                    throw error("JSON object keys must be strings.");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<Object>();
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (position < text.length()) {
                char character = text.charAt(position++);
                if (character == '"') {
                    return builder.toString();
                }
                if (character != '\\') {
                    if (character < 0x20) {
                        throw error("Unescaped control character in JSON string.");
                    }
                    builder.append(character);
                    continue;
                }
                if (position >= text.length()) {
                    throw error("Unterminated JSON string escape.");
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escaped);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicodeEscape());
                        break;
                    default:
                        throw error("Unsupported JSON string escape.");
                }
            }
            throw error("Unterminated JSON string.");
        }

        private char parseUnicodeEscape() {
            if (position + 4 > text.length()) {
                throw error("Incomplete JSON unicode escape.");
            }
            int value = 0;
            for (int offset = 0; offset < 4; offset++) {
                char character = text.charAt(position++);
                int digit = Character.digit(character, 16);
                if (digit < 0) {
                    throw error("Invalid JSON unicode escape.");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private Number parseNumber() {
            int start = position;
            consume('-');
            if (consume('0')) {
                // Leading zero is valid only for the single zero digit.
            } else {
                if (position >= text.length() || !isDigit(text.charAt(position))) {
                    throw error("Invalid JSON number.");
                }
                while (position < text.length() && isDigit(text.charAt(position))) {
                    position++;
                }
            }
            boolean floatingPoint = false;
            if (consume('.')) {
                floatingPoint = true;
                if (position >= text.length() || !isDigit(text.charAt(position))) {
                    throw error("Invalid JSON number fraction.");
                }
                while (position < text.length() && isDigit(text.charAt(position))) {
                    position++;
                }
            }
            if (position < text.length()
                    && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
                floatingPoint = true;
                position++;
                if (position < text.length()
                        && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
                    position++;
                }
                if (position >= text.length() || !isDigit(text.charAt(position))) {
                    throw error("Invalid JSON number exponent.");
                }
                while (position < text.length() && isDigit(text.charAt(position))) {
                    position++;
                }
            }
            String numberText = text.substring(start, position);
            try {
                if (floatingPoint) {
                    return Double.valueOf(Double.parseDouble(numberText));
                }
                return Long.valueOf(Long.parseLong(numberText));
            } catch (NumberFormatException exception) {
                throw error("Invalid JSON number.");
            }
        }

        private void skipWhitespace() {
            while (position < text.length()) {
                char character = text.charAt(position);
                if (character != ' ' && character != '\n'
                        && character != '\r' && character != '\t') {
                    return;
                }
                position++;
            }
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private boolean consumeLiteral(String literal) {
            if (text.startsWith(literal, position)) {
                position += literal.length();
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'.");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Position: " + position);
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }
    }
}
