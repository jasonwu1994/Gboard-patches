package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Locale;

import dev.jason.gboardpatches.extension.R;

@RunWith(RobolectricTestRunner.class)
public final class GboardSettingsLocaleFailureBoundaryTest {
    @Test
    public void wrapContextFallsBackToBaseWhenConfigurationCannotBeRead() {
        Context failing = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public Resources getResources() {
                throw new IllegalStateException("resources unavailable");
            }
        };

        Assert.assertSame(failing, GboardSettingsLocaleManager.wrapContext(failing));
    }

    @Test
    public void unknownStoredLanguageFollowsTheSystemLocale() {
        Context context = RuntimeEnvironment.getApplication();
        GboardPatchesSettings.preferences(context).edit()
                .putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE, "xx-INVALID")
                .commit();

        Assert.assertEquals(GboardSettingsLocaleManager.LANGUAGE_SYSTEM,
                GboardSettingsLocaleManager.readLanguagePreference(
                        GboardPatchesSettings.preferences(context)));
        Assert.assertNotNull(GboardSettingsLocaleManager.wrapContext(context));
    }

    @Test
    public void unsupportedDeviceLocaleUsesEnglish() {
        Assert.assertEquals(GboardSettingsLocaleManager.LANGUAGE_ENGLISH,
                GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                        GboardSettingsLocaleManager.LANGUAGE_SYSTEM,
                        Locale.forLanguageTag("th-TH")));
    }

    @Test
    public void everySupportedLanguageHasALabelAndResolvesText() {
        String[] tags = GboardSettingsLocaleManager.supportedLanguageTags();
        Assert.assertEquals(13, tags.length);
        for (String tag : tags) {
            String label = GboardSettingsLocaleManager.displayNameForLanguageTag(tag);
            Assert.assertFalse(tag, label == null || label.isEmpty());
            String text = GboardSettingsText.resolveStableTextForTesting(
                    R.string.gboard_patches_search_title, tag);
            Assert.assertFalse(tag, text == null || text.isEmpty());
        }
        Assert.assertNotNull(GboardSettingsLocaleManager.displayNameForLanguageTag("xx-INVALID"));
    }
}
