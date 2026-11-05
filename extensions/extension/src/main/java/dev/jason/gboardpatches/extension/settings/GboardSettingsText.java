package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

public final class GboardSettingsText {
    private static final String LANGUAGE_ENGLISH = "en";
    private static final String LANGUAGE_TRADITIONAL_CHINESE = "zh-Hant";
    private static final String LANGUAGE_FRENCH = "fr";

    private GboardSettingsText() {
    }

    public static String get(Context context, int resId) {
        String stableText = resolveStableText(context, resId);
        if (stableText != null) {
            return stableText;
        }
        return requireContext(context).getString(resId);
    }

    public static String format(Context context, int resId, Object... args) {
        String stableText = resolveStableText(context, resId, args);
        if (stableText != null) {
            return stableText;
        }
        return requireContext(context).getString(resId, args);
    }

    public static String quantity(Context context, int resId, int quantity) {
        String stableQuantity = resolveStableQuantity(context, resId, quantity);
        if (stableQuantity != null) {
            return stableQuantity;
        }
        return requireContext(context).getResources().getQuantityString(resId, quantity, quantity);
    }

    /** The language the settings page shows its text in for {@code context}. */
    public static String languageTag(Context context) {
        return resolveLanguageTag(context);
    }

    /** The packaged, unformatted text of {@code resId} in {@code languageTag}; null if none. */
    public static String getInLanguage(int resId, String languageTag) {
        return GboardSettingsTextCatalog.template(resId, normalizeLanguageTag(languageTag));
    }

    static String resolveStableTextForTesting(int resId, String languageTag, Object... args) {
        return resolveStableText(resId, normalizeLanguageTag(languageTag), args);
    }

    static String resolveStableQuantityForTesting(int resId, String languageTag, int quantity) {
        return resolveStableQuantity(resId, normalizeLanguageTag(languageTag), quantity);
    }

    private static String resolveStableText(Context context, int resId, Object... args) {
        return resolveStableText(resId, resolveLanguageTag(context), args);
    }

    private static String resolveStableText(int resId, String languageTag, Object... args) {
        return formatTemplate(
                languageTag,
                GboardSettingsTextCatalog.template(resId, languageTag),
                args);
    }

    private static String resolveStableQuantity(Context context, int resId, int quantity) {
        return resolveStableQuantity(resId, resolveLanguageTag(context), quantity);
    }

    private static String resolveStableQuantity(int resId, String languageTag, int quantity) {
        return formatTemplate(
                languageTag,
                GboardSettingsTextCatalog.quantityTemplate(resId, languageTag, quantity),
                quantity);
    }

    private static String formatTemplate(String languageTag, String template, Object... args) {
        if (template == null) {
            return null;
        }
        if (args == null || args.length == 0) {
            return template;
        }
        Locale formatLocale;
        if (LANGUAGE_TRADITIONAL_CHINESE.equals(languageTag)) {
            formatLocale = Locale.TRADITIONAL_CHINESE;
        } else if (LANGUAGE_FRENCH.equals(languageTag)) {
            formatLocale = Locale.FRANCE;
        } else {
            formatLocale = Locale.US;
        }
        return String.format(formatLocale, template, args);
    }

    private static String resolveLanguageTag(Context context) {
        Locale locale = extractLocale(context);
        if (context != null) {
            try {
                String preference = GboardSettingsLocaleManager.readLanguagePreference(
                        GboardPatchesSettings.preferences(context));
                return GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                        preference, locale);
            } catch (Throwable ignored) {
                // Static feature initialization can request stable text without a usable context.
            }
        }
        if (isTraditionalChinese(locale)) {
            return LANGUAGE_TRADITIONAL_CHINESE;
        }
        if (isFrench(locale)) {
            return LANGUAGE_FRENCH;
        }
        return LANGUAGE_ENGLISH;
    }

    private static Locale extractLocale(Context context) {
        if (context == null) {
            return Locale.getDefault();
        }
        Configuration configuration;
        try {
            if (context.getResources() == null) {
                return Locale.getDefault();
            }
            configuration = context.getResources().getConfiguration();
        } catch (Throwable ignored) {
            return Locale.getDefault();
        }
        if (configuration == null) {
            return Locale.getDefault();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && configuration.getLocales() != null
                && !configuration.getLocales().isEmpty()) {
            Locale locale = configuration.getLocales().get(0);
            return locale != null ? locale : Locale.getDefault();
        }
        Locale locale = configuration.locale;
        return locale != null ? locale : Locale.getDefault();
    }

    private static String normalizeLanguageTag(String languageTag) {
        if (LANGUAGE_TRADITIONAL_CHINESE.equals(languageTag)) {
            return LANGUAGE_TRADITIONAL_CHINESE;
        }
        if (LANGUAGE_FRENCH.equals(languageTag)) {
            return LANGUAGE_FRENCH;
        }
        if (languageTag != null && !languageTag.isBlank()) {
            Locale locale = Locale.forLanguageTag(languageTag);
            if (isTraditionalChinese(locale)) {
                return LANGUAGE_TRADITIONAL_CHINESE;
            }
            if (isFrench(locale)) {
                return LANGUAGE_FRENCH;
            }
        }
        return LANGUAGE_ENGLISH;
    }

    private static boolean isFrench(Locale locale) {
        return locale != null && "fr".equalsIgnoreCase(locale.getLanguage());
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

    private static Context requireContext(Context context) {
        if (context == null) {
            throw new IllegalStateException("Context required for Gboard settings text lookup.");
        }
        return context;
    }
}
