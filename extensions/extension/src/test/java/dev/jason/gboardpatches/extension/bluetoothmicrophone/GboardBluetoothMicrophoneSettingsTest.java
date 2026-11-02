package dev.jason.gboardpatches.extension.bluetoothmicrophone;

import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GboardBluetoothMicrophoneSettingsTest {
    @Test
    public void ownsExpectedPreferenceKeyAndDefaultsOffAndFailsClosedForMalformedValues() {
        TestSharedPreferences preferences = new TestSharedPreferences();

        Assert.assertEquals(
                "pref_use_bluetooth_microphone_override_enabled",
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED);
        Assert.assertFalse(GboardBluetoothMicrophoneSettings.DEFAULT_ENABLED);
        Assert.assertFalse(GboardBluetoothMicrophoneSettings.readEnabled(preferences));
        Assert.assertFalse(GboardBluetoothMicrophoneSettings.readEnabled(
                (SharedPreferences) null));

        preferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                Integer.valueOf(1));
        Assert.assertFalse(GboardBluetoothMicrophoneSettings.readEnabled(preferences));
    }

    @Test
    public void ensureDefaultPersistsDisabledForFreshInstalls() {
        TestSharedPreferences preferences = new TestSharedPreferences();

        GboardBluetoothMicrophoneSettings.ensureDefault(preferences);

        Assert.assertEquals(
                Boolean.FALSE,
                preferences.values.get(GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED));
    }

    @Test
    public void firstPopulationNeverOverwritesExistingFalseTrueOrMalformedPreference() {
        for (Object value : new Object[]{Boolean.FALSE, Boolean.TRUE, "true", "false", Integer.valueOf(1)}) {
            TestSharedPreferences preferences = new TestSharedPreferences();
            preferences.values.put(GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED, value);
            GboardBluetoothMicrophoneSettings.ensureDefault(preferences);
            Assert.assertSame(value, preferences.values.get(GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED));
        }
    }

    @Test
    public void acceptsBooleanAndLegacyStringTrue() {
        TestSharedPreferences preferences = new TestSharedPreferences();

        preferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                Boolean.TRUE);
        Assert.assertTrue(GboardBluetoothMicrophoneSettings.readEnabled(preferences));

        preferences.values.put(
                GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED,
                "true");
        Assert.assertTrue(GboardBluetoothMicrophoneSettings.readEnabled(preferences));
    }

    @Test
    public void writeUsesOnlyOwnedPreferenceKey() {
        TestSharedPreferences preferences = new TestSharedPreferences();

        Assert.assertTrue(GboardBluetoothMicrophoneSettings.writeEnabled(
                preferences,
                true));

        Assert.assertEquals(
                Collections.singleton(GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED),
                preferences.values.keySet());
        Assert.assertEquals(
                Boolean.TRUE,
                preferences.values.get(GboardBluetoothMicrophoneSettings.PREF_KEY_ENABLED));
    }
}

final class TestSharedPreferences implements SharedPreferences {
    final Map<String, Object> values = new HashMap<>();
    int getAllCalls;

    @Override
    public Map<String, ?> getAll() {
        getAllCalls++;
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
