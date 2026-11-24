package dev.jason.gboardpatches.extension.websearch;

import static org.junit.Assert.assertEquals;

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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
public class GboardFloatingWebSearchAccessPointLabelTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("web_search_label_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        GboardPatchesSettings.setStorage(ignored -> preferences);
    }

    @After public void tearDown() {
        GboardPatchesSettings.setStorage(null);
    }

    @Test public void labelFollowsThePatchesLanguage() {
        useLanguage("zh-Hant");
        assertEquals("懸浮搜尋", GboardFloatingWebSearchAccessPointContribution.label(context));
        useLanguage("en");
        assertEquals(GboardFloatingWebSearchAccessPointContribution.ENGLISH_LABEL,
                GboardFloatingWebSearchAccessPointContribution.label(context));
    }

    private void useLanguage(String tag) {
        preferences.edit().putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE, tag).commit();
    }
}
