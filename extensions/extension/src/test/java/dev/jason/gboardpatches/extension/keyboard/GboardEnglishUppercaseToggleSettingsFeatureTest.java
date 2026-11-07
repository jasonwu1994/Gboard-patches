package dev.jason.gboardpatches.extension.keyboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardEnglishUppercaseToggleSettingsFeatureTest {
    @Test
    public void buildScreenShowsSingleEnglishUppercaseToggle() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardEnglishUppercaseToggleSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardEnglishUppercaseToggleSettingsFeature(null).buildScreen(host);

        Assert.assertEquals("English Up-Flick Uppercase", screen.getToolbarTitle());
        Assert.assertEquals("English Up-Flick Uppercase", screen.getHeaderTitle());
        Assert.assertEquals("", screen.getHeaderSummary());

        GboardPatchesSettingsContract.ToggleRow enabledRow =
                findToggleRow(screen.getRows(), "Enable English Up-Flick Uppercase");
        Assert.assertNotNull(enabledRow);
        Assert.assertEquals(
                "Flicking up on lowercase letters outputs uppercase, and flicking up on uppercase letters outputs lowercase.",
                enabledRow.getSummary());
        Assert.assertFalse(enabledRow.isChecked());
        Assert.assertTrue(enabledRow.isEnabled());
    }

    @Test
    public void togglingFeaturePersistsEnabledState() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardEnglishUppercaseToggleSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardEnglishUppercaseToggleSettingsFeature(null).buildScreen(host);
        GboardPatchesSettingsContract.ToggleRow enabledRow =
                findToggleRow(screen.getRows(), "Enable English Up-Flick Uppercase");
        Assert.assertNotNull(enabledRow);

        enabledRow.getToggleAction().accept(true);
        Assert.assertTrue(GboardEnglishUppercaseToggleSettings.readEnabled(preferences));
    }

    @Test
    public void screenUsesStableTraditionalChineseCopyLikeOtherKeyboardFeatures() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("zh-Hant-TW"));
        try {
            InMemorySharedPreferences preferences = new InMemorySharedPreferences();
            GboardEnglishUppercaseToggleSettings.ensureDefaults(preferences);
            CapturingHost host = new CapturingHost(preferences);

            GboardPatchesSettingsContract.Screen screen =
                    new GboardEnglishUppercaseToggleSettingsFeature(host.getContext())
                            .buildScreen(host);

            Assert.assertEquals("英文鍵盤上滑大小寫", screen.getToolbarTitle());
            Assert.assertEquals("英文鍵盤上滑大小寫", screen.getHeaderTitle());

            GboardPatchesSettingsContract.ToggleRow enabledRow =
                    findToggleRow(screen.getRows(), "啟用英文鍵盤上滑大小寫");
            Assert.assertNotNull(enabledRow);
            Assert.assertEquals(
                    "小寫字母上滑會輸出大寫，大寫字母上滑會輸出小寫。",
                    enabledRow.getSummary());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static GboardPatchesSettingsContract.ToggleRow findToggleRow(
            List<GboardPatchesSettingsContract.Row> rows,
            String title) {
        for (GboardPatchesSettingsContract.Row row : rows) {
            if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow
                    && title.contentEquals(toggleRow.getTitle())) {
                return toggleRow;
            }
        }
        return null;
    }

    private static final class CapturingHost extends dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost {
        private final SharedPreferences preferences;
        private final Context context;

        private CapturingHost(SharedPreferences preferences) {
            this.preferences = preferences;
            this.context = new ContextWrapper(null) {
                @Override
                public Context getApplicationContext() {
                    return this;
                }

                @Override
                public SharedPreferences getSharedPreferences(String name, int mode) {
                    return CapturingHost.this.preferences;
                }

                @Override
                public String getPackageName() {
                    return "dev.jason.gboardpatches.test";
                }
            };
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void refresh() {
        }

        @Override
        public void openFeature(GboardPatchesSettingsContract.Feature feature) {
        }

        @Override
        public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        }

        @Override
        public void showPositiveIntegerDialog(String title, String hint, int initialValue,
                GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) {
        }

        @Override
        public void showTextInputDialog(String title, String hint, String initialValue,
                GboardPatchesSettingsContract.TextValueConsumer consumer) {
        }

        @Override
        public void showPreviewDialog(GboardPatchesSettingsContract.PreviewSpec previewSpec) {
        }

        @Override
        public void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction) {
        }

        @Override
        public void openTextDocument(String[] mimeTypes,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
        }
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(new HashMap<String, Object>(values));
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
                private final Map<String, Object> pending = new HashMap<>();

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
