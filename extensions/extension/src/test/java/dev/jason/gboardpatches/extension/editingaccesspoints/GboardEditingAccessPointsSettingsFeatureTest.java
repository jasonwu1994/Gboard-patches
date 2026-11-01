package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class GboardEditingAccessPointsSettingsFeatureTest {
    private Context context;
    private SharedPreferences preferences;
    private GboardEditingAccessPointsSettingsFeature feature;
    private GboardPatchesSettingsContract.FeatureHost host;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().putString("pref_settings_ui_language", "en").commit();
        context.getApplicationInfo().metaData = new Bundle();
        feature = new GboardEditingAccessPointsSettingsFeature(context);
        host = new GboardPatchesSettingsContract.FeatureHost() {
            public Context getContext() { return context; }
            public void submit(GboardPatchesSettingsContract.Intent intent) {}
        };
    }

    @Test public void visibilityRequiresItsOwnMarker() {
        assertFalse(feature.isAvailable(context));
        context.getApplicationInfo().metaData.putBoolean(
                GboardPatchesFeatureAvailability.FEATURE_FLOATING_WEB_SEARCH, true);
        assertFalse(feature.isAvailable(context));
        context.getApplicationInfo().metaData.putBoolean(
                GboardPatchesFeatureAvailability.FEATURE_EDITING_ACCESS_POINTS, true);
        assertTrue(feature.isAvailable(context));
    }

    @Test public void featureSwitchesAndIndependentSelectAllPersist() {
        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);
        assertEquals("Feature", screen.getSections().get(0).getTitle());
        assertEquals("Add Select All, Copy, Cut and Paste to the menu.", screen.getHeaderSummary());
        assertEquals(6, screen.getRows().size());
        GboardPatchesSettingsContract.ToggleRow overall =
                (GboardPatchesSettingsContract.ToggleRow) screen.getRows().get(0);
        assertTrue(overall.isChecked());
        assertEquals("Changes take effect after you force stop and restart Gboard.",
                overall.getSummary());
        GboardPatchesSettingsContract.PreviewImage image =
                (GboardPatchesSettingsContract.PreviewImage) overall.getPreviewSpec()
                        .getMediaItems().get(0);
        assertEquals("settings-previews/editingaccesspoints/01_toolbar_editing_buttons.png",
                image.getAssetPath());
        overall.getToggleAction().accept(false);
        assertFalse(GboardEditingAccessPointsRuntime.enabled(context, EditorAction.PASTE));
        assertFalse(feature.buildScreen(host).getRows().get(1).isEnabled());
        overall.getToggleAction().accept(true);
        GboardPatchesSettingsContract.ToggleRow copy =
                (GboardPatchesSettingsContract.ToggleRow) feature.buildScreen(host).getRows().get(2);
        copy.getToggleAction().accept(false);
        assertFalse(GboardEditingAccessPointsRuntime.enabled(context, EditorAction.COPY));
        assertTrue(GboardEditingAccessPointsRuntime.enabled(context, EditorAction.CUT));
    }

    @Test public void selectAllDefaultsOffAndKeepsItsOwnPreference() {
        GboardPatchesSettingsContract.SwitchRow row =
                (GboardPatchesSettingsContract.SwitchRow) feature.buildScreen(host).getRows().get(5);
        assertFalse(row.isChecked());
        assertTrue(row.isEnabled());
        assertEquals("Select all when nothing is selected", row.getTitle());
        row.getToggleAction().accept(true);
        assertTrue(preferences.getBoolean(
                GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED, false));
        assertFalse(preferences.contains("pref_long_press_quick_actions_select_all_when_unselected"));
        preferences.edit().putBoolean(GboardEditingAccessPointsSettings.ENABLED, false).commit();
        assertFalse(feature.buildScreen(host).getRows().get(5).isEnabled());
    }

    @Test public void traditionalChineseCopyAndRenderFailureAreSafe() {
        preferences.edit().putString("pref_settings_ui_language", "zh-Hant").commit();
        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);
        assertEquals("功能", screen.getSections().get(0).getTitle());
        assertEquals("將全選、複製、剪下與貼上加入選單。", screen.getHeaderSummary());
        assertEquals("強制停止並重新啟動 Gboard 後生效", screen.getRows().get(0).getSummary());
        assertEquals("未選取時自動全選", screen.getRows().get(5).getTitle());
        assertEquals(1, feature.buildScreen(null).getStatusBlocks().size());
    }
}
