package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardTopRowSwipeSettingsFeatureTest {
    @Test
    public void screenUsesStableTraditionalChineseLsposedCopy() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("zh-Hant-TW"));
        try {
            InMemorySharedPreferences preferences = new InMemorySharedPreferences();
            GboardTopRowSwipeSettings.ensureDefaults(preferences);
            CapturingHost host = new CapturingHost(preferences);

            GboardTopRowSwipeSettingsFeature feature =
                    new GboardTopRowSwipeSettingsFeature(null);

            Assert.assertEquals("自訂第一排按鍵", feature.getEntryTitle());
            Assert.assertEquals(
                    "左右滑動第一排，打開可自訂文字與 JavaScript slot 的列。",
                    feature.getEntrySummary());

            GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);

            GboardPatchesSettingsContract.ToggleRow enabledRow =
                    (GboardPatchesSettingsContract.ToggleRow)
                            screen.getSections().get(0).getItems().get(0);
            GboardPatchesSettingsContract.ToggleRow zhuyinRow =
                    (GboardPatchesSettingsContract.ToggleRow)
                            screen.getSections().get(1).getItems().get(0);
            Assert.assertFalse(enabledRow.isChecked());
            Assert.assertFalse(zhuyinRow.isEnabled());
            Assert.assertTrue(zhuyinRow.isChecked());
            Assert.assertEquals(
                    Arrays.asList("行為", "支援的版面", "JavaScript", "Slot", "匯入匯出", "進階",
                            "JavaScript 說明"),
                    sectionTitles(screen.getSections()));
            Assert.assertEquals(
                    "啟用",
                    screen.getSections().get(0).getItems().get(0).getTitle().toString());
            Assert.assertEquals(
                    "注音",
                    screen.getSections().get(1).getItems().get(0).getTitle().toString());
            Assert.assertEquals(
                    "全域 JavaScript",
                    screen.getSections().get(2).getItems().get(0).getTitle().toString());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void dialogStringsUseStableTraditionalChineseCopy() {
        Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("zh-Hant-TW"));
        try {
            GboardTopRowSwipeStrings strings = GboardTopRowSwipeStrings.from(null);

            Assert.assertEquals("行為", strings.sectionBehavior);
            Assert.assertEquals("使用 JavaScript", strings.editorUseJavaScriptLabel);
            Assert.assertEquals("Timeout（ms）", strings.editorTimeoutLabel);
            Assert.assertEquals("已解鎖", strings.editorUnlockedState);
            Assert.assertEquals("重設", strings.resetButton);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    public void enabledRowUsesSinglePreviewVideoAsset() throws Exception {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardTopRowSwipeSettings.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardTopRowSwipeSettingsFeature(null).buildScreen(host);

        GboardPatchesSettingsContract.ToggleRow enabledRow = null;
        for (GboardPatchesSettingsContract.Row row : screen.getRows()) {
            if (row instanceof GboardPatchesSettingsContract.ToggleRow toggleRow
                    && "Enabled".contentEquals(toggleRow.getTitle())) {
                enabledRow = toggleRow;
                break;
            }
        }
        Assert.assertNotNull("Missing enabled row", enabledRow);
        Assert.assertNotNull("Missing enabled preview", enabledRow.getPreviewSpec());
        Assert.assertEquals("Custom Top Row", enabledRow.getPreviewSpec().getTitle());
        Assert.assertEquals(1, enabledRow.getPreviewSpec().getMediaItems().size());
        Assert.assertEquals(
                "settings-previews/keyboard/gboard_top_row_swipe_enabled_preview.mp4",
                assetPath(enabledRow.getPreviewSpec().getMediaItems().get(0)));
        Assert.assertEquals("",
                enabledRow.getPreviewSpec().getMediaItems().get(0).getCaption());
    }

    @Test
    public void enabledPreviewVideoAssetExists() {
        Assert.assertTrue(Files.exists(java.nio.file.Path.of(
                "..", "..", "patches", "src", "main", "resources",
                "settings-previews", "keyboard",
                "gboard_top_row_swipe_enabled_preview.mp4")));
    }

    private static String assetPath(GboardPatchesSettingsContract.PreviewMedia previewMedia)
            throws Exception {
        Method method = previewMedia.getClass().getMethod("getAssetPath");
        Object value = method.invoke(previewMedia);
        return value == null ? null : value.toString();
    }

    private static List<String> sectionTitles(List<GboardPatchesSettingsContract.Section> sections) {
        List<String> titles = new ArrayList<String>();
        for (GboardPatchesSettingsContract.Section section : sections) {
            titles.add(section.getTitle());
        }
        return titles;
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

                @Override
                public ClassLoader getClassLoader() {
                    return CapturingHost.class.getClassLoader();
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
                private boolean clearRequested;

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
                    clearRequested = true;
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
                    if (clearRequested) {
                        values.clear();
                    }
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
