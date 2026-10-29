package dev.jason.gboardpatches.extension.languagemodels;

import static org.junit.Assert.*;
import android.content.Context;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class GboardLanguageModelSettingsFeatureTest {
    @Test public void nineSelectorsUseThreePurposeGroupsAndDisabledMeansApk() {
        Context context = RuntimeEnvironment.getApplication();
        var preferences = GboardPatchesSettings.preferences(context);
        preferences.edit().clear().putString(GboardLanguageModelCatalog.Family.HMM.preferenceKey(),
                GboardLanguageModelCatalog.PIXEL).commit();
        var feature = new GboardLanguageModelSettingsFeature(context);
        var screen = feature.buildScreen(null);
        assertEquals("Dictionaries & Predictions", feature.getEntryTitle());
        assertEquals(6, screen.getSections().size());
        int[] expected = {4, 3, 2};
        for (int i = 0; i < expected.length; i++) {
            var selectors = screen.getSections().get(i + 2).getItems().stream()
                    .filter(r -> r instanceof GboardPatchesSettingsContract.SelectorRow).toList();
            assertEquals(expected[i], selectors.size());
            for (var row : selectors) {
                assertFalse(row.isEnabled()); assertTrue(row.isRestartRequired());
                assertTrue(row.getSummary().contains("\nAPK"));
            }
        }
        assertTrue(GboardLanguageModelSettings.write(context, null, null, true));
        assertTrue(GboardLanguageModelSettings.read(preferences).enabled);
        assertEquals(GboardLanguageModelCatalog.PIXEL,
                GboardLanguageModelSettings.read(preferences).effectiveChoice(GboardLanguageModelCatalog.Family.HMM));
        assertTrue(GboardLanguageModelSettings.write(context, null, null, false));
        assertEquals(GboardLanguageModelCatalog.APK,
                GboardLanguageModelSettings.read(preferences).effectiveChoice(GboardLanguageModelCatalog.Family.HMM));
        assertEquals(GboardLanguageModelCatalog.PIXEL,
                GboardLanguageModelSettings.read(preferences).choice(GboardLanguageModelCatalog.Family.HMM));
        assertFalse(GboardLanguageModelSettings.write(context, GboardLanguageModelCatalog.Family.HMM, "invalid", true));
        assertFalse(GboardLanguageModelSettings.read(preferences).enabled);
    }

    @Test public void traditionalChineseUsesRequestedModelTitlesAndGenericDescriptions() {
        Context app = RuntimeEnvironment.getApplication();
        var config = new android.content.res.Configuration(app.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag("zh-Hant-TW"));
        Context context = app.createConfigurationContext(config);
        var feature = new GboardLanguageModelSettingsFeature(context);
        assertEquals("字典與預測", feature.getEntryTitle());
        var screen = feature.buildScreen(null);
        var hmm = screen.getSections().get(2).getItems().get(0);
        assertEquals("HMM", hmm.getTitle());
        assertTrue(hmm.getSummary().startsWith("繁體中文、其他中韓語言"));
        String explanation = screen.getSections().get(5).getItems().stream()
                .map(r -> r.getSummary()).reduce("", (a, b) -> a + b);
        assertFalse(explanation.contains("最新 Pixel"));
        assertFalse(explanation.contains("本次 Pixel"));
    }
}
