package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GboardSelectAllSettingsTest {
    @Test
    public void selectAllDefaultsOffAndAcceptsBooleanOrPortableString() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();
        Assert.assertFalse(GboardLongPressQuickActionsSettings.readSelectAllWhenUnselected(null));
        Assert.assertFalse(GboardLongPressQuickActionsSettings
                .readSelectAllWhenUnselected(preferences));
        for (Object value : new Object[] {Boolean.TRUE, "true", Boolean.FALSE, "false", 1}) {
            preferences.values.put(
                    GboardLongPressQuickActionsSettings.PREF_KEY_SELECT_ALL_WHEN_UNSELECTED, value);
            Assert.assertEquals(Boolean.TRUE.equals(value) || "true".equals(value),
                    GboardLongPressQuickActionsSettings.readSelectAllWhenUnselected(preferences));
        }
    }

    @Test
    public void selectAllDefaultIsPersistedWithoutOverwritingUserChoice() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();
        GboardLongPressQuickActionsSettings.ensureDefault(preferences);
        Assert.assertEquals(Boolean.FALSE, preferences.values.get(
                GboardLongPressQuickActionsSettings.PREF_KEY_SELECT_ALL_WHEN_UNSELECTED));
        Assert.assertTrue(GboardLongPressQuickActionsSettings
                .writeSelectAllWhenUnselected(preferences, true));
        GboardLongPressQuickActionsSettings.ensureDefault(preferences);
        Assert.assertTrue(GboardLongPressQuickActionsSettings
                .readSelectAllWhenUnselected(preferences));
    }

    @Test
    public void selectAllWriteDoesNotChangeOtherFeaturePreferences() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();
        Assert.assertTrue(GboardLongPressQuickActionsSettings
                .writeSelectAllWhenUnselected(preferences, true));
        Assert.assertEquals(Collections.singleton(
                GboardLongPressQuickActionsSettings.PREF_KEY_SELECT_ALL_WHEN_UNSELECTED),
                preferences.values.keySet());
    }

    @Test
    public void parentDefaultsEnabledWhileGlobeDragDefaultsDisabled() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();

        Assert.assertTrue(GboardLongPressQuickActionsSettings.readEnabled(preferences));
        Assert.assertFalse(GboardLongPressQuickActionsSettings.readGlobeDragEnabled(preferences));

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED,
                Integer.valueOf(0));
        Assert.assertTrue(GboardLongPressQuickActionsSettings.readEnabled(preferences));

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_GLOBE_DRAG_ENABLED,
                Integer.valueOf(0));
        Assert.assertFalse(
                GboardLongPressQuickActionsSettings.readGlobeDragEnabled(preferences));
    }

    @Test
    public void acceptsLocalBooleanAndRemoteStringRepresentations() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED,
                Boolean.FALSE);
        Assert.assertFalse(GboardLongPressQuickActionsSettings.readEnabled(preferences));

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_GLOBE_DRAG_ENABLED,
                "false");
        Assert.assertFalse(
                GboardLongPressQuickActionsSettings.readGlobeDragEnabled(preferences));

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED,
                "true");
        Assert.assertTrue(GboardLongPressQuickActionsSettings.readEnabled(preferences));

        preferences.values.put(
                GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED,
                "false");
        Assert.assertFalse(GboardLongPressQuickActionsSettings.readEnabled(preferences));
    }

    @Test
    public void writeUsesOnlyOwnedPreferenceKey() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();

        Assert.assertTrue(GboardLongPressQuickActionsSettings.writeEnabled(
                preferences,
                false));

        Assert.assertEquals(
                Collections.singleton(GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED),
                preferences.values.keySet());
        Assert.assertEquals(
                Boolean.FALSE,
                preferences.values.get(
                        GboardLongPressQuickActionsSettings.PREF_KEY_ENABLED));
    }

    @Test
    public void globeDragWriteUsesIndependentOwnedPreferenceKey() {
        SelectAllTestPreferences preferences = new SelectAllTestPreferences();

        Assert.assertTrue(GboardLongPressQuickActionsSettings.writeGlobeDragEnabled(
                preferences, false));

        Assert.assertEquals(
                Collections.singleton(
                        GboardLongPressQuickActionsSettings.PREF_KEY_GLOBE_DRAG_ENABLED),
                preferences.values.keySet());
        Assert.assertEquals(Boolean.FALSE, preferences.values.get(
                GboardLongPressQuickActionsSettings.PREF_KEY_GLOBE_DRAG_ENABLED));
    }
}

final class SelectAllTestPreferences implements SharedPreferences {
    final Map<String, Object> values = new HashMap<>();

    @Override
    public Map<String, ?> getAll() {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }

    @Override
    public String getString(String key, String defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object value = values.get(key);
        if (value == null) {
            return defValues;
        }
        if (!(value instanceof Set<?>)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return (Set<String>) value;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Number)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return ((Number) value).intValue();
    }

    @Override
    public long getLong(String key, long defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Number)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return ((Number) value).longValue();
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Number)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return ((Number) value).floatValue();
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object value = values.get(key);
        if (value == null) {
            return defValue;
        }
        if (!(value instanceof Boolean)) {
            throw new ClassCastException(value.getClass().getName());
        }
        return ((Boolean) value).booleanValue();
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new TestEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    private final class TestEditor implements Editor {
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removals = new HashSet<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value == null ? null : new HashSet<>(value));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, Integer.valueOf(value));
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, Long.valueOf(value));
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, Float.valueOf(value));
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, Boolean.valueOf(value));
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            apply();
            return true;
        }

        @Override
        public void apply() {
            if (clear) {
                values.clear();
            }
            for (String key : removals) {
                values.remove(key);
            }
            values.putAll(pending);
        }
    }
}
