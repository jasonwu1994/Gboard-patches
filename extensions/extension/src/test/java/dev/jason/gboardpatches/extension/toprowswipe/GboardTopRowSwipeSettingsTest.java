package dev.jason.gboardpatches.extension.toprowswipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class GboardTopRowSwipeSettingsTest {
    @Test
    public void defaultSlotsUseTenMatchingDisplayAndCommitTexts() {
        List<GboardTopRowSwipeSettings.SlotText> slots =
                GboardTopRowSwipeSettings.defaultSlots();

        assertEquals(10, slots.size());
        assertEquals("😀", slots.get(0).displayText);
        assertEquals("😀", slots.get(0).commitText);
        assertEquals("😊", slots.get(9).displayText);
        assertEquals("😊", slots.get(9).commitText);
    }

    @Test
    public void defaultsDisableFeatureButEnableBothSupportedLayouts() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        GboardTopRowSwipeSettings.ensureDefaults(preferences);

        assertFalse(GboardTopRowSwipeSettings.readEnabled(preferences));
        assertTrue(GboardTopRowSwipeSettings.readZhuyinEnabled(preferences));
        assertTrue(GboardTopRowSwipeSettings.readEnglishQwertyEnabled(preferences));
    }

    @Test
    public void ensureDefaultsPreservesExistingFeatureChoice() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardTopRowSwipeSettings.writeEnabled(preferences, true);

        GboardTopRowSwipeSettings.ensureDefaults(preferences);

        assertTrue(GboardTopRowSwipeSettings.readEnabled(preferences));
    }

    @Test
    public void writesIndividualLayoutToggles() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardTopRowSwipeSettings.ensureDefaults(preferences);

        GboardTopRowSwipeSettings.writeZhuyinEnabled(preferences, false);
        GboardTopRowSwipeSettings.writeEnglishQwertyEnabled(preferences, false);

        assertFalse(GboardTopRowSwipeSettings.readZhuyinEnabled(preferences));
        assertFalse(GboardTopRowSwipeSettings.readEnglishQwertyEnabled(preferences));
    }

    @Test
    public void sanitizesBlankDisplayToDefaultAndBlankCommitToDisplay() {
        List<GboardTopRowSwipeSettings.SlotText> slots =
                GboardTopRowSwipeSettings.sanitizeSlots(
                        Arrays.asList("", "A"),
                        Arrays.asList("ignored", ""));

        assertEquals(10, slots.size());
        assertEquals("😀", slots.get(0).displayText);
        assertEquals("ignored", slots.get(0).commitText);
        assertEquals("A", slots.get(1).displayText);
        assertEquals("A", slots.get(1).commitText);
    }

    @Test
    public void changingDisplayAutoFillsCommitWhenCommitWasStillMirrored() {
        GboardTopRowSwipeSettings.SlotText edited =
                GboardTopRowSwipeSettings.resolveEditedSlot(
                        "😀",
                        "😀",
                        "哈",
                        "😀",
                        false);

        assertEquals("哈", edited.displayText);
        assertEquals("哈", edited.commitText);
    }

    @Test
    public void manuallyEditedCommitCanMatchOldMirroredDisplay() {
        GboardTopRowSwipeSettings.SlotText edited =
                GboardTopRowSwipeSettings.resolveEditedSlot(
                        "😀",
                        "😀",
                        "哈",
                        "😀",
                        true);

        assertEquals("哈", edited.displayText);
        assertEquals("😀", edited.commitText);
    }

    @Test
    public void dialogTreatsBlankCommitEditAsAutoFillAgain() {
        assertTrue(GboardTopRowSwipeSlotEditorDialog.commitManuallyEditedAfterUserEdit(
                "custom sentence"));
        assertFalse(GboardTopRowSwipeSlotEditorDialog.commitManuallyEditedAfterUserEdit(""));
        assertFalse(GboardTopRowSwipeSlotEditorDialog.commitManuallyEditedAfterUserEdit("   "));
    }

    @Test
    public void slotPreferenceKeysAreOneBasedAndZeroPadded() {
        assertEquals("pref_top_row_swipe_slot_01_display",
                GboardTopRowSwipeSettings.displayPreferenceKey(0));
        assertEquals("pref_top_row_swipe_slot_10_commit",
                GboardTopRowSwipeSettings.commitPreferenceKey(9));
    }

    @Test
    public void exportedSlotsRoundTripDisplayAndCommitTexts() {
        List<GboardTopRowSwipeSettings.SlotText> slots =
                Arrays.asList(
                        new GboardTopRowSwipeSettings.SlotText("哈", "輸入一句話"),
                        new GboardTopRowSwipeSettings.SlotText("A", "Alpha"),
                        new GboardTopRowSwipeSettings.SlotText("B", "Beta"),
                        new GboardTopRowSwipeSettings.SlotText("C", "Gamma"),
                        new GboardTopRowSwipeSettings.SlotText("D", "Delta"),
                        new GboardTopRowSwipeSettings.SlotText("E", "Line 1\nLine 2"),
                        new GboardTopRowSwipeSettings.SlotText("F", "value=with=equals"),
                        new GboardTopRowSwipeSettings.SlotText("G", "value:with:colon"),
                        new GboardTopRowSwipeSettings.SlotText("H", "value\\with\\slash"),
                        new GboardTopRowSwipeSettings.SlotText("I", "Final"));

        String exported = GboardTopRowSwipeSettings.exportSlots(slots);
        List<GboardTopRowSwipeSettings.SlotText> parsed =
                GboardTopRowSwipeSettings.parseExportedSlots(exported);

        assertTrue(exported.trim().startsWith("{"));
        assertTrue(exported.contains("\"format\": \"gboardpatches.top-row-swipe.slots\""));
        assertTrue(exported.contains("\"slots\""));
        assertEquals(10, parsed.size());
        assertEquals("哈", parsed.get(0).displayText);
        assertEquals("輸入一句話", parsed.get(0).commitText);
        assertEquals("Line 1\nLine 2", parsed.get(5).commitText);
        assertEquals("value=with=equals", parsed.get(6).commitText);
        assertEquals("value:with:colon", parsed.get(7).commitText);
        assertEquals("value\\with\\slash", parsed.get(8).commitText);
    }

    @Test
    public void importAcceptsLegacyMorpheFormat() {
        String exported = "{\n"
                + "  \"format\": \"gboardpatches.top-row-swipe.slots\",\n"
                + "  \"version\": 1,\n"
                + "  \"slots\": [\n"
                + "    {\"display\": \"一\", \"commit\": \"一\"},\n"
                + "    {\"display\": \"二\"},\n"
                + "    {\"display\": \"三\"},\n"
                + "    {\"display\": \"四\"},\n"
                + "    {\"display\": \"五\"},\n"
                + "    {\"display\": \"六\"},\n"
                + "    {\"display\": \"七\"},\n"
                + "    {\"display\": \"八\"},\n"
                + "    {\"display\": \"九\"},\n"
                + "    {\"display\": \"十\"}\n"
                + "  ]\n"
                + "}";

        List<GboardTopRowSwipeSettings.SlotText> parsed =
                GboardTopRowSwipeSettings.parseExportedSlots(exported);

        assertEquals("一", parsed.get(0).displayText);
        assertEquals("十", parsed.get(9).displayText);
    }

    @Test(expected = IllegalArgumentException.class)
    public void importRejectsBlankDisplayText() {
        String exported = "{\n"
                + "  \"format\": \"gboardpatches.top-row-swipe.slots\",\n"
                + "  \"version\": 1,\n"
                + "  \"slots\": [\n"
                + "    {\"display\": \"\"},\n"
                + "    {\"display\": \"二\"},\n"
                + "    {\"display\": \"三\"},\n"
                + "    {\"display\": \"四\"},\n"
                + "    {\"display\": \"五\"},\n"
                + "    {\"display\": \"六\"},\n"
                + "    {\"display\": \"七\"},\n"
                + "    {\"display\": \"八\"},\n"
                + "    {\"display\": \"九\"},\n"
                + "    {\"display\": \"十\"}\n"
                + "  ]\n"
                + "}";

        GboardTopRowSwipeSettings.parseExportedSlots(exported);
    }

    @Test
    public void strictRuntimeSettingsRejectMalformedPersistedValues() {
        Map<String, Object> values = validPersistedRuntimeValues();

        assertTrue(GboardTopRowSwipeSettings.strictRuntimeSettingsFromValues(values).available);

        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_ENABLED, "maybe");
        assertFalse(GboardTopRowSwipeSettings.strictRuntimeSettingsFromValues(values).available);
    }

    @Test
    public void writeJavaScriptRuntimeLimitsPreservesLargeCustomValues() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        GboardTopRowSwipeSettings.writeJavaScriptRuntimeLimits(
                preferences,
                new GboardTopRowSwipeSettings.JavaScriptRuntimeLimits(
                        64 * 1024 * 1024,
                        120_000,
                        128 * 1024 * 1024,
                        8 * 1024 * 1024));

        GboardTopRowSwipeSettings.JavaScriptRuntimeLimits limits =
                GboardTopRowSwipeSettings.readJavaScriptRuntimeLimits(preferences);

        assertEquals(64 * 1024 * 1024, limits.responseBodyLimitBytes);
        assertEquals(120_000, limits.timeoutMaxMs);
        assertEquals(128 * 1024 * 1024, limits.memoryLimitBytes);
        assertEquals(8 * 1024 * 1024, limits.maxStackBytes);
    }

    private static Map<String, Object> validPersistedRuntimeValues() {
        Map<String, Object> values = new HashMap<String, Object>();
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_ENABLED, Boolean.TRUE);
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                Boolean.TRUE);
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                Boolean.TRUE);
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT, "");
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                Integer.valueOf(GboardTopRowSwipeSettings.DEFAULT_RESPONSE_BODY_LIMIT_BYTES));
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                Integer.valueOf(GboardTopRowSwipeSettings.DEFAULT_TIMEOUT_MAX_MS));
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                Integer.valueOf(GboardTopRowSwipeSettings.DEFAULT_MEMORY_LIMIT_BYTES));
        values.put(GboardTopRowSwipeSettings.PREF_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                Integer.valueOf(GboardTopRowSwipeSettings.DEFAULT_MAX_STACK_BYTES));
        List<GboardTopRowSwipeSettings.SlotText> defaults =
                GboardTopRowSwipeSettings.defaultSlots();
        for (int index = 0; index < GboardTopRowSwipeSettings.SLOT_COUNT; index++) {
            GboardTopRowSwipeSettings.SlotText slot = defaults.get(index);
            values.put(GboardTopRowSwipeSettings.displayPreferenceKey(index), slot.displayText);
            values.put(GboardTopRowSwipeSettings.commitPreferenceKey(index), slot.commitText);
            values.put(GboardTopRowSwipeSettings.isJavaScriptPreferenceKey(index),
                    Boolean.valueOf(slot.isJavaScript));
            values.put(GboardTopRowSwipeSettings.scriptPreferenceKey(index), slot.scriptText);
            values.put(GboardTopRowSwipeSettings.timeoutPreferenceKey(index),
                    Integer.valueOf(slot.timeoutMs));
        }
        return values;
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<String, Object>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String stringValue ? stringValue : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            if (value instanceof Set<?> setValue) {
                Set<String> strings = new HashSet<String>();
                for (Object item : setValue) {
                    if (item instanceof String stringItem) {
                        strings.add(stringItem);
                    }
                }
                return strings;
            }
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer intValue ? intValue.intValue() : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long longValue ? longValue.longValue() : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float floatValue ? floatValue.floatValue() : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean booleanValue ? booleanValue.booleanValue() : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new InMemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        private final class InMemoryEditor implements SharedPreferences.Editor {
            private final Map<String, Object> pendingValues = new HashMap<String, Object>();
            private final Set<String> removals = new HashSet<String>();
            private boolean clearRequested;

            @Override
            public SharedPreferences.Editor putString(String key, String value) {
                pendingValues.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor putStringSet(String key, Set<String> value) {
                pendingValues.put(key, value != null ? new HashSet<String>(value) : null);
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor putInt(String key, int value) {
                pendingValues.put(key, Integer.valueOf(value));
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor putLong(String key, long value) {
                pendingValues.put(key, Long.valueOf(value));
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor putFloat(String key, float value) {
                pendingValues.put(key, Float.valueOf(value));
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor putBoolean(String key, boolean value) {
                pendingValues.put(key, Boolean.valueOf(value));
                removals.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor remove(String key) {
                removals.add(key);
                pendingValues.remove(key);
                return this;
            }

            @Override
            public SharedPreferences.Editor clear() {
                clearRequested = true;
                pendingValues.clear();
                removals.clear();
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clearRequested) {
                    values.clear();
                }
                for (String key : removals) {
                    values.remove(key);
                }
                values.putAll(pendingValues);
            }
        }
    }
}
