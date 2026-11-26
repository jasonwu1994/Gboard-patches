package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;

public final class GboardClipboardSettings {
    public static final long MINUTE_MS = 60_000L;
    public static final long TTL_ONE_MINUTE_MS = MINUTE_MS;
    public static final long TTL_ONE_HOUR_MS = 60L * MINUTE_MS;
    public static final long INFINITE_TTL_MS = -1L;
    public static final int MAX_COUNT_TEN = 10;
    public static final int MAX_COUNT_ONE_HUNDRED = 100;
    public static final int INFINITE_MAX_COUNT = -1;
    public static final int CLIPBOARD_COLUMN_COUNT_ONE = 1;
    public static final int CLIPBOARD_COLUMN_COUNT_TWO = 2;
    public static final int CLIPBOARD_COLUMN_COUNT_THREE = 3;
    public static final int CLIPBOARD_CONTENT_MAX_LINES_STOCK = 5;
    public static final int CLIPBOARD_CONTENT_MAX_LINES_EXTENDED = 10;
    public static final String CLIPBOARD_ORDER_INDEX_MODE_NEWEST_FIRST = "newest_first";
    public static final String CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST = "oldest_first";
    public static final String PREF_VALUE_CUSTOM = "custom";

    public static final String PREF_FILE = GboardClipboardRuntime.SETTINGS_PREF_FILE;
    public static final String LEGACY_PREF_FILE = GboardClipboardRuntime.LEGACY_SETTINGS_PREF_FILE;

    static final String PREF_KEY_SELECT_ALL = "pref_clipboard_select_unpinned_enabled";
    static final String PREF_KEY_EXCLUDE_PINNED = "pref_clipboard_exclude_pinned";

    public static final String PREF_KEY_CLIPBOARD_ENABLED = "pref_clipboard_enabled";
    public static final String PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN =
            "pref_clipboard_show_countdown";
    public static final String PREF_KEY_CLIPBOARD_SHOW_CREATION_TIME =
            "pref_clipboard_show_creation_time";
    public static final String PREF_KEY_CLIPBOARD_SHOW_ORDER_INDEX =
            "pref_clipboard_show_order_index";
    public static final String PREF_KEY_CLIPBOARD_SHOW_SENSITIVE_CONTENT =
            "pref_clipboard_show_sensitive_content";
    public static final String PREF_KEY_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED =
            "pref_clipboard_card_preview_limit_enabled";
    public static final String PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE =
            "pref_clipboard_order_index_mode";
    public static final String PREF_KEY_CLIPBOARD_COLUMN_COUNT =
            "pref_clipboard_column_count";
    public static final String PREF_KEY_CLIPBOARD_TTL_MS = "pref_clipboard_ttl_ms";
    public static final String PREF_KEY_CLIPBOARD_MAX_COUNT = "pref_clipboard_max_count";
    public static final String PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES =
            "pref_clipboard_content_max_lines";
    public static final String PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES =
            "pref_clipboard_ttl_custom_minutes";
    public static final String PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM =
            "pref_clipboard_max_count_custom";
    public static final String PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM =
            "pref_clipboard_content_max_lines_custom";

    public static final boolean DEFAULT_CLIPBOARD_ENABLED = true;
    public static final boolean DEFAULT_CLIPBOARD_SHOW_COUNTDOWN = true;
    public static final boolean DEFAULT_CLIPBOARD_SHOW_CREATION_TIME = false;
    public static final boolean DEFAULT_CLIPBOARD_SHOW_ORDER_INDEX = true;
    public static final boolean DEFAULT_CLIPBOARD_SHOW_SENSITIVE_CONTENT = false;
    public static final boolean DEFAULT_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED = false;
    public static final String DEFAULT_CLIPBOARD_ORDER_INDEX_MODE =
            CLIPBOARD_ORDER_INDEX_MODE_NEWEST_FIRST;
    public static final int DEFAULT_CLIPBOARD_COLUMN_COUNT = CLIPBOARD_COLUMN_COUNT_TWO;
    public static final long DEFAULT_CLIPBOARD_TTL_MS = TTL_ONE_HOUR_MS;
    public static final int DEFAULT_CLIPBOARD_MAX_COUNT = MAX_COUNT_ONE_HUNDRED;
    public static final int DEFAULT_CLIPBOARD_CONTENT_MAX_LINES =
            CLIPBOARD_CONTENT_MAX_LINES_STOCK;
    public static final int DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES = 60;
    public static final int DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM = MAX_COUNT_ONE_HUNDRED;
    public static final int DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM =
            CLIPBOARD_CONTENT_MAX_LINES_EXTENDED;

    private GboardClipboardSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        migrateLegacyPreferences(context);
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        if (context == null) {
            return;
        }
        ensureDefaults(preferences(context));
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        SharedPreferences.Editor editor = null;
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_ENABLED,
                DEFAULT_CLIPBOARD_ENABLED);
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN,
                DEFAULT_CLIPBOARD_SHOW_COUNTDOWN);
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_SHOW_CREATION_TIME,
                DEFAULT_CLIPBOARD_SHOW_CREATION_TIME);
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_SHOW_ORDER_INDEX,
                DEFAULT_CLIPBOARD_SHOW_ORDER_INDEX);
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_SHOW_SENSITIVE_CONTENT,
                DEFAULT_CLIPBOARD_SHOW_SENSITIVE_CONTENT);
        editor = putBooleanPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED,
                DEFAULT_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED);
        editor = putStringPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE,
                DEFAULT_CLIPBOARD_ORDER_INDEX_MODE);
        editor = putStringPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_COLUMN_COUNT,
                Integer.toString(DEFAULT_CLIPBOARD_COLUMN_COUNT));
        editor = putStringPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_TTL_MS,
                Long.toString(DEFAULT_CLIPBOARD_TTL_MS));
        editor = putStringPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_MAX_COUNT,
                Integer.toString(DEFAULT_CLIPBOARD_MAX_COUNT));
        editor = putStringPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        editor = putIntPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES,
                DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES);
        editor = putIntPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM,
                DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM);
        editor = putIntPreferenceIfMissing(editor, preferences,
                PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM);
        editor = normalizeStoredValues(preferences, editor);
        if (editor != null) {
            editor.apply();
        }
    }

    public static boolean readClipboardEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardEnabled(preferences);
    }

    static boolean readSelectAll(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_SELECT_ALL, false);
    }

    static boolean readExcludePinned(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_EXCLUDE_PINNED, false);
    }

    public static boolean readClipboardEnabled(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_CLIPBOARD_ENABLED,
                DEFAULT_CLIPBOARD_ENABLED);
    }

    public static boolean readClipboardShowCountdown(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardShowCountdown(preferences);
    }

    public static boolean readClipboardShowCountdown(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN,
                DEFAULT_CLIPBOARD_SHOW_COUNTDOWN);
    }

    public static boolean readClipboardShowCreationTime(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardShowCreationTime(preferences);
    }

    public static boolean readClipboardShowCreationTime(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_CLIPBOARD_SHOW_CREATION_TIME,
                DEFAULT_CLIPBOARD_SHOW_CREATION_TIME);
    }

    public static boolean readClipboardShowOrderIndex(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardShowOrderIndex(preferences);
    }

    public static boolean readClipboardShowOrderIndex(SharedPreferences preferences) {
        return readBooleanPreference(preferences, PREF_KEY_CLIPBOARD_SHOW_ORDER_INDEX,
                DEFAULT_CLIPBOARD_SHOW_ORDER_INDEX);
    }

    public static boolean readClipboardShowSensitiveContent(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardShowSensitiveContent(preferences);
    }

    public static boolean readClipboardShowSensitiveContent(
            SharedPreferences preferences) {
        return readBooleanPreference(
                preferences,
                PREF_KEY_CLIPBOARD_SHOW_SENSITIVE_CONTENT,
                DEFAULT_CLIPBOARD_SHOW_SENSITIVE_CONTENT);
    }

    public static boolean readClipboardCardPreviewLimitEnabled(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardCardPreviewLimitEnabled(preferences);
    }

    public static boolean readClipboardCardPreviewLimitEnabled(
            SharedPreferences preferences) {
        return readBooleanPreference(
                preferences,
                PREF_KEY_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED,
                DEFAULT_CLIPBOARD_CARD_PREVIEW_LIMIT_ENABLED);
    }

    public static String readClipboardOrderIndexMode(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardOrderIndexMode(preferences);
    }

    public static String readClipboardOrderIndexMode(SharedPreferences preferences) {
        return resolveClipboardOrderIndexMode(preferences);
    }

    public static int readClipboardColumnCount(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardColumnCount(preferences);
    }

    public static int readClipboardColumnCount(SharedPreferences preferences) {
        return resolveClipboardColumnCount(preferences);
    }

    public static long readClipboardTtlMs(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardTtlMs(preferences);
    }

    public static int readClipboardMaxCount(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardMaxCount(preferences);
    }

    public static int readClipboardContentMaxLines(Context context) {
        SharedPreferences preferences = preferences(context);
        ensureDefaults(preferences);
        return readClipboardContentMaxLines(preferences);
    }

    public static long readClipboardTtlMs(SharedPreferences preferences) {
        return resolveClipboardTtlMs(preferences);
    }

    public static int readClipboardMaxCount(SharedPreferences preferences) {
        return resolveClipboardMaxCount(preferences);
    }

    public static int readClipboardContentMaxLines(SharedPreferences preferences) {
        return resolveClipboardContentMaxLines(preferences);
    }

    public static int readClipboardTtlCustomMinutes(SharedPreferences preferences) {
        return sanitizePositiveInt(
                readIntPreference(preferences, PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES,
                        DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES),
                DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES);
    }

    public static int readClipboardMaxCountCustomValue(SharedPreferences preferences) {
        return sanitizePositiveInt(
                readIntPreference(preferences, PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM,
                        DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM),
                DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM);
    }

    public static int readClipboardContentMaxLinesCustomValue(SharedPreferences preferences) {
        return sanitizePositiveInt(
                readIntPreference(preferences, PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                        DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM),
                DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM);
    }

    private static SharedPreferences.Editor putBooleanPreferenceIfMissing(
            SharedPreferences.Editor editor, SharedPreferences preferences, String key,
            boolean value) {
        if (preferences.contains(key)) {
            return editor;
        }
        SharedPreferences.Editor actualEditor = ensureEditor(editor, preferences);
        actualEditor.putBoolean(key, value);
        return actualEditor;
    }

    private static SharedPreferences.Editor putIntPreferenceIfMissing(
            SharedPreferences.Editor editor, SharedPreferences preferences, String key,
            int value) {
        if (preferences.contains(key)) {
            return editor;
        }
        SharedPreferences.Editor actualEditor = ensureEditor(editor, preferences);
        actualEditor.putInt(key, value);
        return actualEditor;
    }

    private static SharedPreferences.Editor putStringPreferenceIfMissing(
            SharedPreferences.Editor editor, SharedPreferences preferences, String key,
            String value) {
        if (preferences.contains(key)) {
            return editor;
        }
        SharedPreferences.Editor actualEditor = ensureEditor(editor, preferences);
        actualEditor.putString(key, value);
        return actualEditor;
    }

    private static SharedPreferences.Editor normalizeStoredValues(SharedPreferences preferences,
            SharedPreferences.Editor editor) {
        editor = normalizeClipboardOrderIndexModePreference(preferences, editor);
        editor = normalizeClipboardColumnCountPreference(preferences, editor);
        editor = normalizeClipboardTtlPreference(preferences, editor);
        editor = normalizeClipboardMaxCountPreference(preferences, editor);
        editor = normalizeClipboardContentMaxLinesPreference(preferences, editor);
        return editor;
    }

    private static SharedPreferences.Editor normalizeClipboardOrderIndexModePreference(
            SharedPreferences preferences, SharedPreferences.Editor editor) {
        Object rawValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE);
        String normalizedValue = sanitizeClipboardOrderIndexMode(
                readStringPreference(preferences, PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE,
                        DEFAULT_CLIPBOARD_ORDER_INDEX_MODE));
        if (!(rawValue instanceof String) || !normalizedValue.equals(rawValue)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE, normalizedValue);
        }
        return editor;
    }

    private static SharedPreferences.Editor normalizeClipboardColumnCountPreference(
            SharedPreferences preferences, SharedPreferences.Editor editor) {
        Object rawValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_COLUMN_COUNT);
        int normalizedValue = sanitizeClipboardColumnCount(parseIntPreferenceValue(rawValue,
                DEFAULT_CLIPBOARD_COLUMN_COUNT));
        String normalizedSelection = Integer.toString(normalizedValue);
        if (!(rawValue instanceof String) || !normalizedSelection.equals(rawValue)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_COLUMN_COUNT, normalizedSelection);
        }
        return editor;
    }

    private static SharedPreferences.Editor normalizeClipboardTtlPreference(
            SharedPreferences preferences, SharedPreferences.Editor editor) {
        Object rawValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_TTL_MS);
        String rawSelection = readSelectionValue(preferences,
                PREF_KEY_CLIPBOARD_TTL_MS,
                Long.toString(DEFAULT_CLIPBOARD_TTL_MS));
        Object rawCustomValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES);
        int customMinutes = parseIntPreferenceValue(rawCustomValue,
                DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES);
        int sanitizedCustomMinutes = customMinutes > 0
                ? customMinutes
                : DEFAULT_CLIPBOARD_TTL_CUSTOM_MINUTES;

        if (PREF_VALUE_CUSTOM.equals(rawSelection)) {
            if (!(rawValue instanceof String) || !PREF_VALUE_CUSTOM.equals(rawValue)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(PREF_KEY_CLIPBOARD_TTL_MS, PREF_VALUE_CUSTOM);
            }
            if (customMinutes != sanitizedCustomMinutes) {
                editor = ensureEditor(editor, preferences);
                editor.putInt(PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES, sanitizedCustomMinutes);
            }
            return editor;
        }

        long ttlMs = parseLongPreferenceValue(rawValue, DEFAULT_CLIPBOARD_TTL_MS);
        if (ttlMs > 0L && ttlMs % MINUTE_MS == 0L
                && ttlMs != TTL_ONE_MINUTE_MS
                && ttlMs != DEFAULT_CLIPBOARD_TTL_MS) {
            int minutes = (int) (ttlMs / MINUTE_MS);
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_TTL_MS, PREF_VALUE_CUSTOM);
            editor.putInt(PREF_KEY_CLIPBOARD_TTL_CUSTOM_MINUTES, minutes);
            return editor;
        }

        long normalizedTtlMs = ttlMs == INFINITE_TTL_MS
                || ttlMs == TTL_ONE_MINUTE_MS
                || ttlMs == DEFAULT_CLIPBOARD_TTL_MS
                ? ttlMs
                : DEFAULT_CLIPBOARD_TTL_MS;
        String normalizedSelection = Long.toString(normalizedTtlMs);
        if (!(rawValue instanceof String) || !normalizedSelection.equals(rawSelection)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_TTL_MS, normalizedSelection);
        }
        return editor;
    }

    private static SharedPreferences.Editor normalizeClipboardMaxCountPreference(
            SharedPreferences preferences, SharedPreferences.Editor editor) {
        Object rawValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_MAX_COUNT);
        String rawSelection = readSelectionValue(preferences,
                PREF_KEY_CLIPBOARD_MAX_COUNT,
                Integer.toString(DEFAULT_CLIPBOARD_MAX_COUNT));
        Object rawCustomValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM);
        int customCount = parseIntPreferenceValue(rawCustomValue,
                DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM);
        int sanitizedCustomCount = customCount > 0
                ? customCount
                : DEFAULT_CLIPBOARD_MAX_COUNT_CUSTOM;

        if (PREF_VALUE_CUSTOM.equals(rawSelection)) {
            if (!(rawValue instanceof String) || !PREF_VALUE_CUSTOM.equals(rawValue)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(PREF_KEY_CLIPBOARD_MAX_COUNT, PREF_VALUE_CUSTOM);
            }
            if (customCount != sanitizedCustomCount) {
                editor = ensureEditor(editor, preferences);
                editor.putInt(PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM, sanitizedCustomCount);
            }
            return editor;
        }

        int maxCount = parseIntPreferenceValue(rawValue, DEFAULT_CLIPBOARD_MAX_COUNT);
        if (maxCount > 0
                && maxCount != MAX_COUNT_TEN
                && maxCount != DEFAULT_CLIPBOARD_MAX_COUNT) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_MAX_COUNT, PREF_VALUE_CUSTOM);
            editor.putInt(PREF_KEY_CLIPBOARD_MAX_COUNT_CUSTOM, maxCount);
            return editor;
        }

        int normalizedMaxCount = maxCount == INFINITE_MAX_COUNT
                || maxCount == MAX_COUNT_TEN
                || maxCount == DEFAULT_CLIPBOARD_MAX_COUNT
                ? maxCount
                : DEFAULT_CLIPBOARD_MAX_COUNT;
        String normalizedSelection = Integer.toString(normalizedMaxCount);
        if (!(rawValue instanceof String) || !normalizedSelection.equals(rawSelection)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_MAX_COUNT, normalizedSelection);
        }
        return editor;
    }

    private static SharedPreferences.Editor normalizeClipboardContentMaxLinesPreference(
            SharedPreferences preferences, SharedPreferences.Editor editor) {
        Object rawValue = preferences.getAll().get(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES);
        String rawSelection = readSelectionValue(preferences,
                PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        Object rawCustomValue = preferences.getAll().get(
                PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM);
        int customValue = parseIntPreferenceValue(rawCustomValue,
                DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM);
        int sanitizedCustomValue = customValue > 0
                ? customValue
                : DEFAULT_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM;

        if (PREF_VALUE_CUSTOM.equals(rawSelection)) {
            if (!(rawValue instanceof String) || !PREF_VALUE_CUSTOM.equals(rawValue)) {
                editor = ensureEditor(editor, preferences);
                editor.putString(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES, PREF_VALUE_CUSTOM);
            }
            if (customValue != sanitizedCustomValue) {
                editor = ensureEditor(editor, preferences);
                editor.putInt(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                        sanitizedCustomValue);
            }
            return editor;
        }

        int value = parseIntPreferenceValue(rawValue, DEFAULT_CLIPBOARD_CONTENT_MAX_LINES);
        if (value > 0
                && value != DEFAULT_CLIPBOARD_CONTENT_MAX_LINES
                && value != CLIPBOARD_CONTENT_MAX_LINES_EXTENDED) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES, PREF_VALUE_CUSTOM);
            editor.putInt(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM, value);
            return editor;
        }

        int normalizedValue = value == DEFAULT_CLIPBOARD_CONTENT_MAX_LINES
                || value == CLIPBOARD_CONTENT_MAX_LINES_EXTENDED
                ? value
                : DEFAULT_CLIPBOARD_CONTENT_MAX_LINES;
        String normalizedSelection = Integer.toString(normalizedValue);
        if (!(rawValue instanceof String) || !normalizedSelection.equals(rawSelection)) {
            editor = ensureEditor(editor, preferences);
            editor.putString(PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES, normalizedSelection);
        }
        return editor;
    }

    private static SharedPreferences.Editor ensureEditor(SharedPreferences.Editor editor,
            SharedPreferences preferences) {
        return editor != null ? editor : preferences.edit();
    }

    private static boolean readBooleanPreference(SharedPreferences preferences, String key,
            boolean defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue.booleanValue();
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return defaultValue;
    }

    private static long resolveClipboardTtlMs(SharedPreferences preferences) {
        String value = readStringPreference(preferences, PREF_KEY_CLIPBOARD_TTL_MS,
                Long.toString(DEFAULT_CLIPBOARD_TTL_MS));
        if (PREF_VALUE_CUSTOM.equals(value)) {
            return ((long) readClipboardTtlCustomMinutes(preferences)) * MINUTE_MS;
        }
        return sanitizeClipboardTtlMs(readLongPreference(preferences, PREF_KEY_CLIPBOARD_TTL_MS,
                DEFAULT_CLIPBOARD_TTL_MS));
    }

    private static int resolveClipboardMaxCount(SharedPreferences preferences) {
        String value = readStringPreference(preferences, PREF_KEY_CLIPBOARD_MAX_COUNT,
                Integer.toString(DEFAULT_CLIPBOARD_MAX_COUNT));
        if (PREF_VALUE_CUSTOM.equals(value)) {
            return readClipboardMaxCountCustomValue(preferences);
        }
        return sanitizeClipboardMaxCount(readIntPreference(preferences,
                PREF_KEY_CLIPBOARD_MAX_COUNT, DEFAULT_CLIPBOARD_MAX_COUNT));
    }

    private static int resolveClipboardContentMaxLines(SharedPreferences preferences) {
        String value = readStringPreference(preferences, PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        if (PREF_VALUE_CUSTOM.equals(value)) {
            return readClipboardContentMaxLinesCustomValue(preferences);
        }
        return sanitizeClipboardContentMaxLines(readIntPreference(preferences,
                PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES, DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
    }

    private static String resolveClipboardOrderIndexMode(SharedPreferences preferences) {
        return sanitizeClipboardOrderIndexMode(readStringPreference(preferences,
                PREF_KEY_CLIPBOARD_ORDER_INDEX_MODE,
                DEFAULT_CLIPBOARD_ORDER_INDEX_MODE));
    }

    private static int resolveClipboardColumnCount(SharedPreferences preferences) {
        return sanitizeClipboardColumnCount(readIntPreference(preferences,
                PREF_KEY_CLIPBOARD_COLUMN_COUNT, DEFAULT_CLIPBOARD_COLUMN_COUNT));
    }

    private static long readLongPreference(SharedPreferences preferences, String key,
            long defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int readIntPreference(SharedPreferences preferences, String key,
            int defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String readStringPreference(SharedPreferences preferences, String key,
            String defaultValue) {
        Object value = preferences.getAll().get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Number number) {
            return Long.toString(number.longValue());
        }
        if (value instanceof Boolean booleanValue) {
            return Boolean.toString(booleanValue.booleanValue());
        }
        return defaultValue;
    }

    private static String readSelectionValue(SharedPreferences preferences, String key,
            String defaultValue) {
        return readStringPreference(preferences, key, defaultValue);
    }

    private static long parseLongPreferenceValue(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int parseIntPreferenceValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static long sanitizeClipboardTtlMs(long ttlMs) {
        if (ttlMs == INFINITE_TTL_MS) {
            return ttlMs;
        }
        if (ttlMs > 0L && ttlMs % MINUTE_MS == 0L) {
            return ttlMs;
        }
        return DEFAULT_CLIPBOARD_TTL_MS;
    }

    private static int sanitizeClipboardMaxCount(int maxCount) {
        if (maxCount == INFINITE_MAX_COUNT) {
            return maxCount;
        }
        return maxCount > 0 ? maxCount : DEFAULT_CLIPBOARD_MAX_COUNT;
    }

    private static int sanitizePositiveInt(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private static int sanitizeClipboardContentMaxLines(int value) {
        return sanitizePositiveInt(value, DEFAULT_CLIPBOARD_CONTENT_MAX_LINES);
    }

    private static String sanitizeClipboardOrderIndexMode(String value) {
        if (CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST.equals(value)) {
            return CLIPBOARD_ORDER_INDEX_MODE_OLDEST_FIRST;
        }
        return CLIPBOARD_ORDER_INDEX_MODE_NEWEST_FIRST;
    }

    private static int sanitizeClipboardColumnCount(int value) {
        if (value == CLIPBOARD_COLUMN_COUNT_ONE
                || value == CLIPBOARD_COLUMN_COUNT_TWO
                || value == CLIPBOARD_COLUMN_COUNT_THREE) {
            return value;
        }
        return DEFAULT_CLIPBOARD_COLUMN_COUNT;
    }

    private static void migrateLegacyPreferences(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences currentPreferences =
                context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences legacyPreferences =
                context.getSharedPreferences(LEGACY_PREF_FILE, Context.MODE_PRIVATE);
        if (legacyPreferences.getAll().isEmpty()) {
            return;
        }

        SharedPreferences.Editor editor = null;
        for (java.util.Map.Entry<String, ?> entry : legacyPreferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (currentPreferences.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (editor == null) {
                editor = currentPreferences.edit();
            }
            if (value instanceof String) {
                editor.putString(key, (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(key, ((Boolean) value).booleanValue());
            } else if (value instanceof Integer) {
                editor.putInt(key, ((Integer) value).intValue());
            } else if (value instanceof Long) {
                editor.putLong(key, ((Long) value).longValue());
            } else if (value instanceof Float) {
                editor.putFloat(key, ((Float) value).floatValue());
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }
}
