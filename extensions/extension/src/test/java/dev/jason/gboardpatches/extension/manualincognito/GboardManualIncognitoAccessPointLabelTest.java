package dev.jason.gboardpatches.extension.manualincognito;

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
public class GboardManualIncognitoAccessPointLabelTest {
    private Context context;
    private SharedPreferences preferences;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences("incognito_label_test", Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        GboardPatchesSettings.setStorage(ignored -> preferences);
    }

    @After public void tearDown() {
        GboardPatchesSettings.setStorage(null);
    }

    @Test public void labelFollowsThePatchesLanguage() {
        useLanguage("zh-Hant");
        assertEquals("無痕模式", GboardManualIncognitoAccessPointSupport.label(context));
        useLanguage("en");
        assertEquals(GboardManualIncognitoAccessPointSupport.ENGLISH_LABEL,
                GboardManualIncognitoAccessPointSupport.label(context));
    }

    private void useLanguage(String tag) {
        preferences.edit().putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE, tag).commit();
    }
}
