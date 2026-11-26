package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardPreferences;

public final class GboardClipboardSettingsFeatureTest {
    @Test
    public void clipboardRootScreenUsesMorpheSectionLayout() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        GboardClipboardSettings.ensureDefaults(preferences);
        WebClipboardPreferences.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardClipboardSettingsFeature().buildScreen(host);

        List<GboardPatchesSettingsContract.Section> sections = screen.getSections();
        Assert.assertEquals(
                Arrays.asList("General", "Metadata", "Layout", "Retention", "Extensions"),
                sectionTitles(sections));
        Assert.assertEquals("ToggleRow", sections.get(0).getItems().get(0).getClass().getSimpleName());
        Assert.assertEquals(4, sections.get(0).getItems().size());
        GboardPatchesSettingsContract.ToggleRow selectAll =
                (GboardPatchesSettingsContract.ToggleRow) sections.get(0).getItems().get(3);
        Assert.assertEquals("Add a Select All button in edit mode", selectAll.getTitle());
        Assert.assertFalse(selectAll.isChecked());
        Assert.assertEquals("settings-previews/clipboard/select_all_in_edit_mode.png",
                ((GboardPatchesSettingsContract.PreviewImage)
                        selectAll.getPreviewSpec().getMediaItems().get(0)).getAssetPath());
        selectAll.getToggleAction().accept(true);
        GboardPatchesSettingsContract.Screen enabled =
                new GboardClipboardSettingsFeature().buildScreen(host);
        GboardPatchesSettingsContract.ToggleRow excludePinned =
                (GboardPatchesSettingsContract.ToggleRow) enabled.getSections().get(0).getItems().get(4);
        Assert.assertEquals("Exclude pinned clips", excludePinned.getTitle());
        Assert.assertFalse(excludePinned.isChecked());
        excludePinned.getToggleAction().accept(true);
        Assert.assertTrue(GboardClipboardSettings.readExcludePinned(preferences));
        selectAll.getToggleAction().accept(false);
        Assert.assertEquals(4,
                new GboardClipboardSettingsFeature().buildScreen(host).getSections().get(0)
                        .getItems().size());
        GboardPatchesSettingsContract.ToggleRow sensitiveRow =
                (GboardPatchesSettingsContract.ToggleRow) findRow(
                        screen,
                        "Show sensitive clipboard contents");
        Assert.assertFalse(sensitiveRow.isChecked());
        Assert.assertNotNull(sensitiveRow.getPreviewSpec());
        Assert.assertEquals(
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                sensitiveRow.getPreviewSpec().getLayout());
        Assert.assertEquals(2, sensitiveRow.getPreviewSpec().getMediaItems().size());
        Assert.assertEquals(
                "settings-previews/clipboard/show_sensitive_content_enabled.png",
                ((GboardPatchesSettingsContract.PreviewImage)
                        sensitiveRow.getPreviewSpec().getMediaItems().get(0)).getAssetPath());
        Assert.assertEquals(
                "settings-previews/clipboard/show_sensitive_content_disabled.png",
                ((GboardPatchesSettingsContract.PreviewImage)
                        sensitiveRow.getPreviewSpec().getMediaItems().get(1)).getAssetPath());
        Assert.assertEquals(
                "Enabled: original text",
                sensitiveRow.getPreviewSpec().getMediaItems().get(0).getCaption());
        Assert.assertEquals(
                "Disabled: dots",
                sensitiveRow.getPreviewSpec().getMediaItems().get(1).getCaption());
        Assert.assertTrue(preferences.contains(
                GboardClipboardSettings.PREF_KEY_CLIPBOARD_SHOW_SENSITIVE_CONTENT));
        sensitiveRow.getToggleAction().accept(true);
        Assert.assertTrue(GboardClipboardSettings.readClipboardShowSensitiveContent(preferences));
        Assert.assertEquals(
                "ToggleRow",
                findRow(screen, "Show only the first 1,000 characters")
                        .getClass().getSimpleName());
        Assert.assertEquals(
                "SelectorRow",
                findRow(screen, "Clipboard columns").getClass().getSimpleName());
        Assert.assertEquals(
                "SelectorRow",
                findRow(screen, "Clipboard Custom Character Limit")
                        .getClass().getSimpleName());
        Assert.assertEquals(
                "NavigationRow",
                sections.get(4).getItems().get(0).getClass().getSimpleName());
    }

    @Test
    public void webClipboardSubpageUsesMorpheSectionsAndConnectedClientsNavigation() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        WebClipboardPreferences.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen screen =
                new GboardClipboardSettingsFeature()
                        .getWebClipboardFeature()
                        .buildScreen(host);

        List<GboardPatchesSettingsContract.Section> sections = screen.getSections();
        Assert.assertEquals(
                Arrays.asList("General", "Security", "Network", "Connected clients"),
                sectionTitles(sections));
        Assert.assertEquals("ToggleRow", sections.get(0).getItems().get(0).getClass().getSimpleName());
        Assert.assertEquals("SelectorRow", findRow(screen, "Pairing code").getClass().getSimpleName());
        Assert.assertEquals(
                "NavigationRow",
                sections.get(3).getItems().get(0).getClass().getSimpleName());
    }

    @Test
    public void connectedClientsEmptyStateUsesStatusBlockInsteadOfInlineInfoRow() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        WebClipboardPreferences.ensureDefaults(preferences);
        CapturingHost host = new CapturingHost(preferences);

        GboardPatchesSettingsContract.Screen webClipboardScreen =
                new GboardClipboardSettingsFeature()
                        .getWebClipboardFeature()
                        .buildScreen(host);

        invokeAction(findRow(webClipboardScreen, "Connected clients"));

        Assert.assertNotNull(host.openedFeature);
        GboardPatchesSettingsContract.Screen clientsScreen =
                host.openedFeature.buildScreen(host);
        Assert.assertEquals(1, clientsScreen.getStatusBlocks().size());
        Assert.assertEquals("Connected clients: 0", clientsScreen.getStatusBlocks().get(0).getTitle());
        Assert.assertEquals(0, clientsScreen.getSections().size());
    }

    private static List<String> sectionTitles(List<GboardPatchesSettingsContract.Section> sections) {
        List<String> titles = new ArrayList<>();
        for (GboardPatchesSettingsContract.Section section : sections) {
            titles.add(section.getTitle());
        }
        return titles;
    }

    private static GboardPatchesSettingsContract.Row findRow(
            GboardPatchesSettingsContract.Screen screen,
            String titlePrefix) {
        for (GboardPatchesSettingsContract.Row row : screen.getRows()) {
            if (row.getTitle().toString().startsWith(titlePrefix)) {
                return row;
            }
        }
        throw new AssertionError("Missing row with title prefix: " + titlePrefix);
    }

    private static void invokeAction(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.NavigationRow navigationRow) {
            navigationRow.getAction().run();
            return;
        }
        if (row instanceof GboardPatchesSettingsContract.ActionRow actionRow) {
            actionRow.getAction().run();
            return;
        }
        throw new AssertionError("Row is not actionable: " + row.getClass().getSimpleName());
    }

    private static final class CapturingHost extends dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost {
        private final SharedPreferences preferences;
        private final Context context;
        private GboardPatchesSettingsContract.Feature openedFeature;

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
                public Resources getResources() {
                    return Resources.getSystem();
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
            openedFeature = feature;
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
            return Collections.unmodifiableMap(new HashMap<>(values));
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
