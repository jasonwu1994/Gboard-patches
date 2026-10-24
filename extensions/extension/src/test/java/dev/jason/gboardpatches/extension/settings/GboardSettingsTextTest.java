package dev.jason.gboardpatches.extension.settings;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import dev.jason.gboardpatches.extension.R;

@RunWith(RobolectricTestRunner.class)
public final class GboardSettingsTextTest {
    @Test
    public void nullContextRemainsSafeForStaticFeatureInitialization() {
        Assert.assertEquals("Patch settings", GboardSettingsText.get(
                null, R.string.gboard_patches_header_title));
    }

    @Test
    public void runtimeTextFollowsPatchLanguagePreference() {
        Context context = RuntimeEnvironment.getApplication();
        GboardPatchesSettings.preferences(context).edit()
                .putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE,
                        GboardSettingsLocaleManager.LANGUAGE_ENGLISH)
                .commit();

        Assert.assertEquals("Choose theme source", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_source_chooser_title));
        Assert.assertEquals("Create from photo", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_source_photo));
        Assert.assertEquals("Import theme (.zip)", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_source_zip));
        Assert.assertEquals("Choose a theme to import", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_choose_import));

        GboardPatchesSettings.preferences(context).edit()
                .putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE,
                        GboardSettingsLocaleManager.LANGUAGE_TRADITIONAL_CHINESE)
                .commit();
        Assert.assertEquals("選擇主題來源", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_source_chooser_title));
        Assert.assertEquals("選擇要匯入的主題", GboardSettingsText.get(context,
                R.string.gboard_patches_custom_theme_choose_import));

        GboardPatchesSettings.preferences(context).edit()
                .putString(GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE,
                        GboardSettingsLocaleManager.LANGUAGE_JAPANESE)
                .commit();
        Assert.assertEquals("パッチの設定", GboardSettingsText.get(context,
                R.string.gboard_patches_header_title));
        Assert.assertEquals("日本語", GboardSettingsText.get(context,
                R.string.gboard_patches_language_japanese_label));
    }

    @Test
    public void authoritativeCopyResolvesBothLocalesWithoutCallerFallback() {
        Assert.assertEquals(
                "Patch settings",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "en"));
        Assert.assertEquals(
                "Patch 設定",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "zh-Hant"));
        Assert.assertEquals(
                "パッチの設定",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "ja"));
    }

    @Test
    public void localeNormalizationAndFormattingStayBehindTheSeam() {
        Assert.assertEquals(
                "跟隨系統（繁體中文）",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_language_system_value,
                        "zh-TW",
                        "繁體中文"));
        Assert.assertEquals(
                "System default (English)",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_language_system_value,
                        "fr-FR",
                        "English"));
        Assert.assertEquals(
                "システムのデフォルト（日本語）",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_language_system_value,
                        "ja-JP",
                        "日本語"));
        Assert.assertEquals(
                "Patch settings",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_header_title,
                        "zh-CN"));
    }

    @Test
    public void authorityEliminatesLegacyCallerFallbackDrift() {
        Assert.assertEquals(
                "",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_group_keyboard_header_summary,
                        "en"));
        Assert.assertEquals(
                "Controls Gboard AI Writing Tools availability, keyboard support, "
                        + "and backend routing.",
                GboardSettingsText.resolveStableTextForTesting(
                        R.string.gboard_patches_ai_writing_tools_summary,
                        "en"));
    }

    @Test
    public void quantityProjectionUsesTheSameLocaleAuthority() {
        Assert.assertEquals(
                "1 minute",
                GboardSettingsText.resolveStableQuantityForTesting(
                        R.plurals.gboard_patches_minutes,
                        "en",
                        1));
        Assert.assertEquals(
                "2 minutes",
                GboardSettingsText.resolveStableQuantityForTesting(
                        R.plurals.gboard_patches_minutes,
                        "en",
                        2));
        Assert.assertEquals(
                "2 分鐘",
                GboardSettingsText.resolveStableQuantityForTesting(
                        R.plurals.gboard_patches_minutes,
                        "zh-Hant",
                        2));
        Assert.assertEquals(
                "1 分",
                GboardSettingsText.resolveStableQuantityForTesting(
                        R.plurals.gboard_patches_minutes,
                        "ja",
                        1));
        Assert.assertEquals(
                "2 分",
                GboardSettingsText.resolveStableQuantityForTesting(
                        R.plurals.gboard_patches_minutes,
                        "ja",
                        2));
    }

    @Test
    public void everyGeneratedSettingsStringHasEveryLocaleCoverage()
            throws Exception {
        for (Field field : R.string.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            int resId = field.getInt(null);
            Assert.assertNotNull(
                    field.getName() + " missing English settings text",
                    GboardSettingsText.resolveStableTextForTesting(resId, "en"));
            Assert.assertNotNull(
                    field.getName() + " missing zh-Hant settings text",
                    GboardSettingsText.resolveStableTextForTesting(resId, "zh-Hant"));
            Assert.assertNotNull(
                    field.getName() + " missing ja settings text",
                    GboardSettingsText.resolveStableTextForTesting(resId, "ja"));
        }
    }
}
