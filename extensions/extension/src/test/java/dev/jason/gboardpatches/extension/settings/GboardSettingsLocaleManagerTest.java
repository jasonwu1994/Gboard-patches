package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
public final class GboardSettingsLocaleManagerTest {
    @Test
    public void explicitPreferenceWinsOverDeviceLocale() {
        Assert.assertEquals("en", GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                "en", Locale.JAPAN));
        Assert.assertEquals("zh-Hant", GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                "zh-Hant", Locale.US));
        Assert.assertEquals("ja", GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                "ja", Locale.TAIWAN));
    }

    @Test
    public void systemPreferenceFollowsSupportedDeviceLocales() {
        Assert.assertEquals("ja", resolveSystem("ja-JP"));
        Assert.assertEquals("ja", resolveSystem("ja"));
        Assert.assertEquals("zh-Hant", resolveSystem("zh-TW"));
        Assert.assertEquals("zh-Hant", resolveSystem("zh-HK"));
        Assert.assertEquals("zh-Hant", resolveSystem("zh-MO"));
        Assert.assertEquals("zh-Hant", resolveSystem("zh-Hant-CN"));
        Assert.assertEquals("en", resolveSystem("zh-CN"));
        Assert.assertEquals("en", resolveSystem("en-US"));
        Assert.assertEquals("en", resolveSystem("fr-FR"));
    }

    @Test
    public void unknownPreferenceValuesNormalizeToSystem() {
        Assert.assertEquals("ja", GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                "ja-JP", Locale.JAPAN));
        Assert.assertEquals("en", GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                "fr", Locale.FRANCE));
    }

    @Test
    public void japanesePreferenceRoundTrips() {
        Context context = RuntimeEnvironment.getApplication();
        GboardSettingsLocaleManager.writeLanguagePreference(
                context, GboardSettingsLocaleManager.LANGUAGE_JAPANESE);

        Assert.assertEquals(
                GboardSettingsLocaleManager.LANGUAGE_JAPANESE,
                GboardSettingsLocaleManager.readLanguagePreference(
                        GboardPatchesSettings.preferences(context)));
        Assert.assertEquals(
                Locale.JAPAN,
                GboardSettingsLocaleManager.wrapContext(context)
                        .getResources().getConfiguration().locale);
    }

    private static String resolveSystem(String deviceLanguageTag) {
        return GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                GboardSettingsLocaleManager.LANGUAGE_SYSTEM,
                Locale.forLanguageTag(deviceLanguageTag));
    }
}
