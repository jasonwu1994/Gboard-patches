package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public final class GboardPatchesFeatureAvailability {
    public static final String FEATURE_CLIPBOARD_ENHANCEMENTS =
            "dev.jason.gboardpatches.feature.clipboard_enhancements";
    public static final String FEATURE_CLIPBOARD_CONTENT_LIMIT =
            "dev.jason.gboardpatches.feature.clipboard_content_limit";
    public static final String FEATURE_WEB_CLIPBOARD =
            "dev.jason.gboardpatches.feature.web_clipboard";
    public static final String FEATURE_DEVICE_INTELLIGENCE =
            "dev.jason.gboardpatches.feature.device_intelligence";
    public static final String FEATURE_GRAMMAR_CHECKER =
            "dev.jason.gboardpatches.feature.grammar_checker";
    public static final String FEATURE_INLINE_SUGGESTIONS =
            "dev.jason.gboardpatches.feature.inline_suggestions";
    public static final String FEATURE_KEY_SHAPE_SELECTION =
            "dev.jason.gboardpatches.feature.key_shape_selection";
    public static final String FEATURE_CUSTOM_SYMBOLS =
            "dev.jason.gboardpatches.feature.custom_symbols";
    public static final String FEATURE_SETTINGS_HOMEPAGE =
            "dev.jason.gboardpatches.feature.settings_homepage";
    public static final String FEATURE_SYMBOL_FOOTER_ORDER =
            "dev.jason.gboardpatches.feature.symbol_footer_order";
    public static final String FEATURE_LATIN_GLOBE_KEY_IGNORE_INTERVAL =
            "dev.jason.gboardpatches.feature.latin_globe_key_ignore_interval";
    public static final String FEATURE_ENGLISH_UPPERCASE_TOGGLE =
            "dev.jason.gboardpatches.feature.english_uppercase_toggle";
    public static final String FEATURE_ZHUYIN_BOTTOM_ROW_WEIGHT =
            "dev.jason.gboardpatches.feature.zhuyin_bottom_row_weight";
    public static final String FEATURE_AI_WRITING_TOOLS =
            "dev.jason.gboardpatches.feature.ai_writing_tools";
    public static final String FEATURE_TOP_ROW_SWIPE =
            "dev.jason.gboardpatches.feature.top_row_swipe";
    public static final String FEATURE_DEVELOPER_OPTIONS =
            "dev.jason.gboardpatches.feature.developer_options";
    public static final String FEATURE_LONG_PRESS_QUICK_ACTIONS =
            "dev.jason.gboardpatches.feature.long_press_quick_actions";
    public static final String FEATURE_ADVANCED_VOICE_TYPING =
            "dev.jason.gboardpatches.feature.advanced_voice_typing";
    public static final String FEATURE_BLUETOOTH_MICROPHONE =
            "dev.jason.gboardpatches.feature.bluetooth_microphone";
    public static final String FEATURE_OCR_SCAN_TEXT =
            "dev.jason.gboardpatches.feature.ocr_scan_text";
    public static final String FEATURE_MANUAL_INCOGNITO =
            "dev.jason.gboardpatches.feature.manual_incognito";

    private static final String TAG = "GboardPatches";

    private GboardPatchesFeatureAvailability() {
    }

    public static boolean hasFeature(Context context, String featureKey) {
        if (context == null || featureKey == null || featureKey.isEmpty()) {
            return false;
        }
        return hasAnyFeature(context, featureKey);
    }

    public static boolean hasAnyFeature(Context context, String... featureKeys) {
        if (context == null || featureKeys == null || featureKeys.length == 0) {
            return false;
        }
        for (String featureKey : featureKeys) {
            if (featureKey == null || featureKey.isEmpty()) {
                return false;
            }
        }

        try {
            Context applicationContext = context.getApplicationContext();
            Context lookupContext = applicationContext != null ? applicationContext : context;
            PackageManager packageManager = lookupContext.getPackageManager();
            if (packageManager == null) {
                return false;
            }
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    lookupContext.getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle metaData = applicationInfo.metaData;
            if (metaData == null) {
                return false;
            }
            for (String featureKey : featureKeys) {
                if (metaData.getBoolean(featureKey, false)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to resolve feature marker: " + describeFeatures(featureKeys),
                    throwable);
            return false;
        }
    }

    private static String describeFeatures(String[] featureKeys) {
        if (featureKeys.length == 1) {
            return featureKeys[0];
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < featureKeys.length; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(featureKeys[index]);
        }
        return builder.toString();
    }
}
