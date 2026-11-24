package dev.jason.gboardpatches.extension.editingaccesspoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardSettingsLocaleManager;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class GboardEditingAccessPointLabelsTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("editing_ap_labels_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        GboardPatchesSettings.setStorage(ignored -> preferences);
    }

    @After public void tearDown() {
        GboardPatchesSettings.setStorage(null);
    }

    @Test public void labelsFollowThePatchesLanguage() {
        useLanguage("zh-Hant");
        String[] expected = {"全選", "複製", "剪下", "貼上", "向前刪除", "純文字貼上", "行首", "行尾",
                "Tab", "向左", "向右", "向上", "向下"};
        EditorAction[] actions = EditorAction.values();
        assertEquals(expected.length, actions.length);
        for (int i = 0; i < actions.length; i++) {
            assertEquals(expected[i], GboardEditingAccessPointsContribution.label(context, actions[i]));
        }
    }

    @Test public void englishEntriesMatchTheFallbackLabels() {
        useLanguage("en");
        for (EditorAction action : EditorAction.values()) {
            assertEquals(GboardEditingAccessPointsContribution.label(action),
                    GboardEditingAccessPointsContribution.label(context, action));
        }
    }

    @Test public void everyBuiltInLanguageTranslatesEveryLabel() {
        for (String tag : GboardSettingsLocaleManager.supportedLanguageTags()) {
            if ("en".equals(tag)) continue;
            for (EditorAction action : EditorAction.values()) {
                if (action == EditorAction.TAB) continue;
                assertNotEquals(tag + " lacks " + action.token,
                        GboardEditingAccessPointsContribution.label(action),
                        GboardSettingsText.getInLanguage(
                                GboardEditingAccessPointsContribution.labelRes(action), tag));
            }
        }
    }

    @Test public void tabFollowsGboardsKeyName() {
        String[][] expected = {
                {"en", "Tab"}, {"zh-Hant", "Tab"}, {"ja", "Tab"}, {"ru", "Tab"},
                {"es", "Tabulador"}, {"fr", "Tabulation"}, {"de", "Tabulatortaste"}, {"tr", "Sekme"}};
        int labelRes = GboardEditingAccessPointsContribution.labelRes(EditorAction.TAB);
        for (String[] entry : expected) {
            assertEquals(entry[0], entry[1], GboardSettingsText.getInLanguage(labelRes, entry[0]));
        }
    }

    @Test public void unavailableSettingsFallBackToTheDeviceLanguage() {
        GboardPatchesSettings.setStorage(ignored -> null);
        assertEquals("Copy", GboardEditingAccessPointsContribution.label(context, EditorAction.COPY));
    }

    private void useLanguage(String tag) {
        preferences.edit().putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE, tag).commit();
    }
}
