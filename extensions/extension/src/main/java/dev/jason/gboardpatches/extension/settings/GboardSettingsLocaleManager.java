package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public final class GboardSettingsLocaleManager {
    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_TRADITIONAL_CHINESE = "zh-Hant";

    private GboardSettingsLocaleManager() {
    }

    public static String readLanguagePreference(SharedPreferences preferences) {
        if (preferences == null) {
            return LANGUAGE_SYSTEM;
        }
        return normalizePreferenceValue(
                preferences.getString(
                        GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE,
                        GboardSettingsLocaleManager.LANGUAGE_SYSTEM));
    }

    public static void writeLanguagePreference(Context context, String value) {
        if (context == null) {
            return;
        }
        GboardPatchesSettings.preferences(context)
                .edit()
                .putString(
                        GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE,
                        normalizePreferenceValue(value))
                .apply();
    }

    public static String[] supportedLanguageTags() {
        return GboardSettingsTextCatalog.supportedLanguageTags();
    }

    public static String displayNameForLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.isEmpty()) {
            return "English";
        }
        switch (languageTag) {
            case "en":
                return "English";
            case "zh-Hant":
                return "\u7e41\u9ad4\u4e2d\u6587";
            case "zh-Hans":
                return "\u7b80\u4f53\u4e2d\u6587";
            case "ja":
                return "\u65e5\u672c\u8a9e";
            case "ko":
                return "\ud55c\uad6d\uc5b4";
            case "es":
                return "Espa\u00f1ol";
            case "pt-BR":
                return "Portugu\u00eas (Brasil)";
            case "ru":
                return "\u0420\u0443\u0441\u0441\u043a\u0438\u0439";
            case "de":
                return "Deutsch";
            case "fr":
                return "Fran\u00e7ais";
            case "vi":
                return "Ti\u1ebfng Vi\u1ec7t";
            case "id":
                return "Bahasa Indonesia";
            case "tr":
                return "T\u00fcrk\u00e7e";
            default:
                Locale locale = Locale.forLanguageTag(languageTag);
                String name = locale.getDisplayName(locale);
                if (name == null || name.isEmpty()) {
                    return languageTag;
                }
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    public static String resolveEffectiveLanguageTag(String preferenceValue, Locale deviceLocale) {
        String normalizedPreference = normalizePreferenceValue(preferenceValue);
        if (!LANGUAGE_SYSTEM.equals(normalizedPreference)) {
            return normalizedPreference;
        }
        Locale locale = deviceLocale != null ? deviceLocale : Locale.getDefault();
        return matchSupportedLocale(locale);
    }

    static String matchSupportedLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.trim().isEmpty()) {
            return LANGUAGE_ENGLISH;
        }
        if (GboardSettingsTextCatalog.isSupportedLanguageTag(languageTag)) {
            return languageTag;
        }
        return matchSupportedLocale(Locale.forLanguageTag(languageTag));
    }

    static String matchSupportedLocale(Locale locale) {
        if (locale == null) {
            return LANGUAGE_ENGLISH;
        }
        String language = canonicalizeLanguage(locale.getLanguage());
        if (language.isEmpty()) {
            return LANGUAGE_ENGLISH;
        }
        if ("zh".equals(language)) {
            if (isTraditionalChinese(locale)) {
                return GboardSettingsTextCatalog.isSupportedLanguageTag(LANGUAGE_TRADITIONAL_CHINESE)
                        ? LANGUAGE_TRADITIONAL_CHINESE
                        : LANGUAGE_ENGLISH;
            }
            if (GboardSettingsTextCatalog.isSupportedLanguageTag("zh-Hans")) {
                return "zh-Hans";
            }
            return LANGUAGE_ENGLISH;
        }
        String country = locale.getCountry();
        if (country != null && !country.isEmpty()) {
            String withRegion = language + "-" + country.toUpperCase(Locale.ROOT);
            if (GboardSettingsTextCatalog.isSupportedLanguageTag(withRegion)) {
                return withRegion;
            }
        }
        if (GboardSettingsTextCatalog.isSupportedLanguageTag(language)) {
            return language;
        }
        String prefix = language + "-";
        for (String tag : GboardSettingsTextCatalog.supportedLanguageTags()) {
            if (tag.startsWith(prefix)) {
                return tag;
            }
        }
        return LANGUAGE_ENGLISH;
    }

    public static Context wrapContext(Context base) {
        if (base == null) {
            return null;
        }
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(base);
            Locale locale = toLocale(resolveEffectiveLanguageTag(
                    readLanguagePreference(preferences),
                    extractDeviceLocale(base)));
            Configuration configuration = new Configuration(base.getResources().getConfiguration());
            configuration.setLocale(locale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                configuration.setLocales(new android.os.LocaleList(locale));
            }
            return base.createConfigurationContext(configuration);
        } catch (Throwable ignored) {
            return base;
        }
    }

    private static Locale extractDeviceLocale(Context context) {
        if (context == null || context.getResources() == null) {
            return Locale.getDefault();
        }
        Configuration configuration = context.getResources().getConfiguration();
        if (configuration == null) {
            return Locale.getDefault();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && configuration.getLocales() != null
                && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        Locale locale = configuration.locale;
        return locale != null ? locale : Locale.getDefault();
    }

    private static Locale toLocale(String languageTag) {
        if (languageTag == null || LANGUAGE_ENGLISH.equals(languageTag)) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(languageTag);
    }

    private static String canonicalizeLanguage(String language) {
        if (language == null) {
            return "";
        }
        String lower = language.toLowerCase(Locale.ROOT);
        if ("in".equals(lower)) {
            return "id";
        }
        if ("iw".equals(lower)) {
            return "he";
        }
        if ("ji".equals(lower)) {
            return "yi";
        }
        return lower;
    }

    private static boolean isTraditionalChinese(Locale locale) {
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) {
            return false;
        }
        String script = locale.getScript();
        if ("Hant".equalsIgnoreCase(script)) {
            return true;
        }
        if ("Hans".equalsIgnoreCase(script)) {
            return false;
        }
        String country = locale.getCountry();
        return "TW".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country);
    }

    private static String normalizePreferenceValue(String value) {
        if (value == null || value.isEmpty() || LANGUAGE_SYSTEM.equals(value)) {
            return LANGUAGE_SYSTEM;
        }
        if (GboardSettingsTextCatalog.isSupportedLanguageTag(value)) {
            return value;
        }
        return LANGUAGE_SYSTEM;
    }
}
