package dev.jason.gboardpatches.extension.writingtools;

import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class GboardAiWritingToolsSettingsTest {
    @Test
    public void ensureDefaultsSeedsDedicatedPreferenceFile() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        GboardAiWritingToolsSettings.ensureDefaults(preferences);

        Assert.assertEquals(
                Boolean.TRUE,
                preferences.values.get(GboardAiWritingToolsSettings.PREF_KEY_ENABLED));
        Assert.assertEquals(
                Boolean.TRUE,
                preferences.values.get(GboardAiWritingToolsSettings.PREF_KEY_ALL_KEYBOARDS));
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER,
                preferences.values.get(GboardAiWritingToolsSettings.PREF_KEY_BACKEND_TYPE));
    }

    @Test
    public void readsAndWritesIndependentWritingToolsSettings() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        Assert.assertTrue(GboardAiWritingToolsSettings.writeEnabled(preferences, true));
        Assert.assertTrue(GboardAiWritingToolsSettings.writeAllKeyboardsEnabled(
                preferences,
                true));
        Assert.assertTrue(GboardAiWritingToolsSettings.writeBackendType(
                preferences,
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA));

        Assert.assertTrue(GboardAiWritingToolsSettings.readEnabled(preferences));
        Assert.assertTrue(GboardAiWritingToolsSettings.readAllKeyboardsEnabled(preferences));
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_ASTREA,
                GboardAiWritingToolsSettings.readBackendType(preferences));
    }

    @Test
    public void backendTypeSanitizesMissingInvalidAndLegacyBooleanValuesToServer() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();

        preferences.edit()
                .putBoolean("pref_ai_writing_tools_use_google_servers", false)
                .apply();
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER,
                GboardAiWritingToolsSettings.readBackendType(preferences));

        preferences.edit()
                .putString(GboardAiWritingToolsSettings.PREF_KEY_BACKEND_TYPE, "INVALID")
                .apply();
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_GBOARD_SERVER,
                GboardAiWritingToolsSettings.readBackendType(preferences));

        preferences.edit()
                .putString(
                        GboardAiWritingToolsSettings.PREF_KEY_BACKEND_TYPE,
                        GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE)
                .apply();
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE,
                GboardAiWritingToolsSettings.readBackendType(preferences));
    }

    @Test
    public void snapshotCarriesSanitizedBackendAndMasksAllKeyboardsWhileDisabled() {
        GboardAiWritingToolsSettings.Snapshot snapshot =
                new GboardAiWritingToolsSettings.Snapshot(
                        false,
                        true,
                        GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE);

        Assert.assertFalse(snapshot.featureEnabled);
        Assert.assertFalse(snapshot.allKeyboardsEnabled);
        Assert.assertEquals(
                GboardAiWritingToolsSettings.BACKEND_PRIVATE_INFERENCE_AICORE,
                snapshot.backendType);
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defValues) {
            Object value = values.get(key);
            return value instanceof Set ? (Set<String>) value : defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).longValue() : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Number ? ((Number) value).floatValue() : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                private final Map<String, Object> pending = new HashMap<String, Object>();

                @Override
                public Editor putString(String key, String value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    pending.put(key, values);
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    pending.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    pending.put(key, null);
                    return this;
                }

                @Override
                public Editor clear() {
                    values.clear();
                    pending.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    apply();
                    return true;
                }

                @Override
                public void apply() {
                    for (Map.Entry<String, Object> entry : pending.entrySet()) {
                        if (entry.getValue() == null) {
                            values.remove(entry.getKey());
                        } else {
                            values.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }
}
