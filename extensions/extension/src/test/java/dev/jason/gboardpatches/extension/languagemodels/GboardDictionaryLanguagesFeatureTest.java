package dev.jason.gboardpatches.extension.languagemodels;

import android.content.Context;
import dev.jason.gboardpatches.extension.languagemodels.GboardLanguageModelContentVersions;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class GboardDictionaryLanguagesFeatureTest {
    private Context context;
    private JSONArray entries;

    @Before public void prepare() throws Exception {
        entries = new JSONArray(new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("../../patches/src/main/resources/language-models/delight-versions.json")), java.nio.charset.StandardCharsets.UTF_8));
        ReflectionHelpers.setStaticField(GboardLanguageModelContentVersions.class, "dictionaries", entries);
        Context app = RuntimeEnvironment.getApplication();
        var real = app.getResources();
        var labels = new android.content.res.Resources(real.getAssets(), real.getDisplayMetrics(), real.getConfiguration()) {
            @Override public CharSequence getText(int id) { return "label " + id + " %1$d"; }
        };
        context = new android.content.ContextWrapper(app) {
            @Override public android.content.res.Resources getResources() { return labels; }
        };
    }

    @Test public void emptyQueryShowsEveryLocaleAndSeparatesSearchFromResults() throws Exception {
        var screen = new GboardDictionaryLanguagesFeature(context, "").buildScreen(null);
        assertEquals(890, screen.getSections().get(1).getItems().size());
        assertTrue(screen.getSections().get(0).getItems().get(0) instanceof GboardPatchesSettingsContract.SearchRow);
        String last = entries.getJSONObject(entries.length() - 1).getString("locale");
        assertTrue(screen.getSections().get(1).getItems().stream().anyMatch(r -> r.getTitle().toString().endsWith(last)));
    }

    @Test public void broadSearchDoesNotTruncateAtThirtyAndExactLocaleFilters() {
        var broad = new GboardDictionaryLanguagesFeature(context, "en").buildScreen(null);
        assertTrue(broad.getSections().get(1).getItems().size() > 30);
        var exact = new GboardDictionaryLanguagesFeature(context, "de-DE").buildScreen(null);
        assertEquals(1, exact.getSections().get(1).getItems().size());
        assertTrue(exact.getSections().get(1).getItems().get(0).getTitle().toString().endsWith("de-DE"));
    }
}
