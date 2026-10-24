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
    public static final String LANGUAGE_JAPANESE = "ja";

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

    public static String resolveEffectiveLanguageTag(String preferenceValue, Locale deviceLocale) {
        String normalizedPreference = normalizePreferenceValue(preferenceValue);
        if (LANGUAGE_ENGLISH.equals(normalizedPreference)
                || LANGUAGE_TRADITIONAL_CHINESE.equals(normalizedPreference)
                || LANGUAGE_JAPANESE.equals(normalizedPreference)) {
            return normalizedPreference;
        }
        Locale locale = deviceLocale != null ? deviceLocale : Locale.getDefault();
        if (locale != null && isTraditionalChinese(locale)) {
            return LANGUAGE_TRADITIONAL_CHINESE;
        }
        if (locale != null && "ja".equalsIgnoreCase(locale.getLanguage())) {
            return LANGUAGE_JAPANESE;
        }
        if (locale != null && "en".equalsIgnoreCase(locale.getLanguage())) {
            return LANGUAGE_ENGLISH;
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
        if (LANGUAGE_TRADITIONAL_CHINESE.equals(languageTag)) {
            return Locale.forLanguageTag(LANGUAGE_TRADITIONAL_CHINESE);
        }
        if (LANGUAGE_JAPANESE.equals(languageTag)) {
            return Locale.JAPAN;
        }
        return Locale.ENGLISH;
    }

    private static boolean isTraditionalChinese(Locale locale) {
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) {
            return false;
        }
        String script = locale.getScript();
        if ("Hant".equalsIgnoreCase(script)) {
            return true;
        }
        String country = locale.getCountry();
        return "TW".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country);
    }

    private static String normalizePreferenceValue(String value) {
        if (LANGUAGE_ENGLISH.equals(value)) {
            return LANGUAGE_ENGLISH;
        }
        if (LANGUAGE_TRADITIONAL_CHINESE.equals(value)) {
            return LANGUAGE_TRADITIONAL_CHINESE;
        }
        if (LANGUAGE_JAPANESE.equals(value)) {
            return LANGUAGE_JAPANESE;
        }
        return LANGUAGE_SYSTEM;
    }
}
