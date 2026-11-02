package dev.jason.gboardpatches.extension.writingtools;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardAiWritingToolsSettingsFeatureTest {
    @Test
    public void buildScreenShowsExactlyMasterAllKeyboardsAndBackendRows() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardAiWritingToolsSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardAiWritingToolsSettingsFeature(null).buildScreen(host);

        Assert.assertEquals("AI Writing Tools", screen.getToolbarTitle());
        Assert.assertEquals("AI Writing Tools", screen.getHeaderTitle());
        Assert.assertEquals("", screen.getHeaderSummary());

        GboardPatchesSettingsContract.ToggleRow enabledRow =
                findToggleRow(screen.getRows(), "Enable AI Writing Tools");
        GboardPatchesSettingsContract.ToggleRow allKeyboardsRow =
                findToggleRow(screen.getRows(), "Support All Keyboards");
        GboardPatchesSettingsContract.SelectorRow backendRow =
                findSelectorRow(screen.getRows(), "Backend type");

        Assert.assertNotNull(enabledRow);
        Assert.assertNotNull(allKeyboardsRow);
        Assert.assertNotNull(backendRow);
        Assert.assertEquals(3, screen.getRows().size());
        Assert.assertEquals(
                "Force-show Corrections & suggestions > Writing tools > Show writing tools icon "
                        + "in suggestion strip while typing. Force-stop and restart Gboard for "
                        + "changes to take effect.",
                enabledRow.getSummary());
        Assert.assertEquals(
                "When enabled, force writing tools to work on every keyboard. Force-stop "
                        + "and restart Gboard for the change to take effect.",
                allKeyboardsRow.getSummary());
        Assert.assertEquals(
                "If you select AI CORE or ASTREA, your account or device must be eligible to "
                        + "enable it.",
                backendRow.getSummary());
        Assert.assertTrue(enabledRow.isChecked());
        Assert.assertTrue(allKeyboardsRow.isChecked());
        Assert.assertTrue(allKeyboardsRow.isEnabled());
        Assert.assertTrue(backendRow.isEnabled());
        Assert.assertEquals("GBOARD_SERVER", backendRow.getCurrentValue());
    }

    @Test
    public void enablingFeatureUnlocksAllKeyboardsToggle() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardAiWritingToolsSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen initialScreen =
                new GboardAiWritingToolsSettingsFeature(null).buildScreen(host);
        GboardPatchesSettingsContract.ToggleRow enabledRow =
                findToggleRow(initialScreen.getRows(), "Enable AI Writing Tools");
        enabledRow.getToggleAction().accept(true);

        GboardPatchesSettingsContract.Screen updatedScreen =
                new GboardAiWritingToolsSettingsFeature(null).buildScreen(host);
        GboardPatchesSettingsContract.ToggleRow allKeyboardsRow =
                findToggleRow(updatedScreen.getRows(), "Support All Keyboards");

        Assert.assertTrue(allKeyboardsRow.isEnabled());
        allKeyboardsRow.getToggleAction().accept(true);
        Assert.assertTrue(GboardAiWritingToolsSettings.readAllKeyboardsEnabled(preferences));
    }

    @Test
    public void masterOffDisablesAllKeyboardsAndBackendSelector() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardAiWritingToolsSettings.ensureDefaults(preferences);
        GboardAiWritingToolsSettings.writeEnabled(preferences, false);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardAiWritingToolsSettingsFeature(null).buildScreen(host);

        Assert.assertFalse(findToggleRow(
                screen.getRows(), "Support All Keyboards").isEnabled());
        Assert.assertFalse(findSelectorRow(
                screen.getRows(), "Backend type").isEnabled());
    }

    @Test
    public void backendSelectorOffersExactValuesPersistsChoiceAndRefreshes() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardAiWritingToolsSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);
        GboardPatchesSettingsContract.SelectorRow backendRow = findSelectorRow(
                new GboardAiWritingToolsSettingsFeature(null).buildScreen(host).getRows(),
                "Backend type");

        backendRow.getAction().run();

        Assert.assertArrayEquals(
                new String[]{
                        "GBOARD_SERVER",
                        "PRIVATE_INFERENCE_AICORE",
                        "PRIVATE_INFERENCE_ASTREA"
                },
                host.choiceValues);
        Assert.assertEquals("GBOARD_SERVER", host.choiceCurrentValue);
        host.choiceConsumer.accept("PRIVATE_INFERENCE_ASTREA");
        Assert.assertEquals(
                "PRIVATE_INFERENCE_ASTREA",
                GboardAiWritingToolsSettings.readBackendType(preferences));
        Assert.assertEquals(1, host.refreshCount);
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

    private static GboardPatchesSettingsContract.SelectorRow findSelectorRow(
            List<GboardPatchesSettingsContract.Row> rows,
            String title) {
        for (GboardPatchesSettingsContract.Row row : rows) {
            if (row instanceof GboardPatchesSettingsContract.SelectorRow selectorRow
                    && title.contentEquals(selectorRow.getTitle())) {
                return selectorRow;
            }
        }
        return null;
    }

    private static final class CapturingHost extends dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost {
        private final SharedPreferences preferences;
        private final Context context;
        private String[] choiceValues;
        private String choiceCurrentValue;
        private GboardPatchesSettingsContract.StringValueConsumer choiceConsumer;
        private int refreshCount;

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
            refreshCount++;
        }

        @Override
        public void openFeature(GboardPatchesSettingsContract.Feature feature) {
        }

        @Override
        public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
            choiceValues = values;
            choiceCurrentValue = currentValue;
            choiceConsumer = valueConsumer;
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
        private final Map<String, Object> values = new HashMap<String, Object>();

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
