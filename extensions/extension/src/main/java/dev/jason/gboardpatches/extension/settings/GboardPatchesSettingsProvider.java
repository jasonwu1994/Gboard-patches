package dev.jason.gboardpatches.extension.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.jason.gboardpatches.extension.symbolfooter.GboardSymbolFooterOrderSettings;
import dev.jason.gboardpatches.extension.toprowswipe.GboardTopRowSwipeSettings;

public final class GboardPatchesSettingsProvider extends ContentProvider {
    private static final String TAG = "GboardPatches";
    private static final String LOG_LABEL = "expression footer tab order";
    private static final String GBOARD_PACKAGE_STABLE =
            "com.google.android.inputmethod.latin";
    private static final String GBOARD_PACKAGE_JASON_DEV =
            "com.google.android.inputmethod.latin.jason.dev";
    private static final String GBOARD_PACKAGE_REVERSED_DEV =
            "com.touchtype.swiftkey";
    public static final String METHOD_GET_SYMBOL_FOOTER_ORDER_SETTINGS =
            "get_symbol_footer_order_settings";
    public static final String BUNDLE_KEY_SYMBOL_FOOTER_ORDER =
            "symbol_footer_order";
    public static final String AUTHORITY_SUFFIX = ".gboard_patches";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_GET_SYMBOL_FOOTER_ORDER_SETTINGS.equals(method)) {
            try {
                if (getContext() == null) {
                    return new Bundle();
                }
                GboardSymbolFooterOrderSettings.ensureDefaults(getContext());
                ArrayList<String> configuredOrder =
                        new ArrayList<String>(
                                GboardSymbolFooterOrderSettings.readSymbolFooterOrder(
                                        getContext()));
                Log.i(TAG, "Provider returning " + LOG_LABEL + ": " + configuredOrder);
                Bundle result = new Bundle();
                result.putStringArrayList(
                        BUNDLE_KEY_SYMBOL_FOOTER_ORDER,
                        configuredOrder);
                return result;
            } catch (Throwable throwable) {
                Log.w(TAG, "Provider failed to read " + LOG_LABEL, throwable);
                return new Bundle();
            }
        }
        if (GboardTopRowSwipeSettings.METHOD_GET_TOP_ROW_SWIPE_SETTINGS.equals(method)) {
            try {
                if (getContext() == null) {
                    return unavailableTopRowSwipeSettings();
                }
                if (!shouldExposeTopRowSwipeSettings(
                        GboardPatchesFeatureAvailability.hasFeature(
                                getContext(),
                                GboardPatchesFeatureAvailability.FEATURE_TOP_ROW_SWIPE),
                        getContext().getPackageName(),
                        getCallingPackage())) {
                    return unavailableTopRowSwipeSettings();
                }
                GboardTopRowSwipeSettings.ensureDefaults(getContext());
                GboardTopRowSwipeSettings.RuntimeSettings settings =
                        GboardTopRowSwipeSettings.readStrictRuntimeSettings(getContext());
                if (!settings.available) {
                    return unavailableTopRowSwipeSettings();
                }
                return buildAvailableTopRowSwipeSettingsBundle(
                        settings.enabled,
                        settings.zhuyinEnabled,
                        settings.englishQwertyEnabled,
                        settings.globalJavaScript,
                        settings.javaScriptRuntimeLimits,
                        settings.slots);
            } catch (Throwable throwable) {
                Log.w(TAG, "Provider failed to read top row swipe settings", throwable);
                return unavailableTopRowSwipeSettings();
            }
        }

        try {
            return super.call(method, arg, extras);
        } catch (Throwable throwable) {
            Log.w(TAG, "Provider call failed for method=" + method, throwable);
            return null;
        }
    }

    static Bundle buildAvailableTopRowSwipeSettingsBundle(boolean enabled,
            boolean zhuyinEnabled,
            boolean englishQwertyEnabled,
            String globalJavaScript,
            GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits,
            List<GboardTopRowSwipeSettings.SlotText> slots) {
        return topRowSwipeSettingsBundleFromValues(
                buildAvailableTopRowSwipeSettingsBundleValues(
                        enabled,
                        zhuyinEnabled,
                        englishQwertyEnabled,
                        globalJavaScript,
                        javaScriptRuntimeLimits,
                        slots));
    }

    static Map<String, Object> buildAvailableTopRowSwipeSettingsBundleValues(
            boolean enabled,
            boolean zhuyinEnabled,
            boolean englishQwertyEnabled,
            String globalJavaScript,
            GboardTopRowSwipeSettings.JavaScriptRuntimeLimits javaScriptRuntimeLimits,
            List<GboardTopRowSwipeSettings.SlotText> slots) {
        ArrayList<String> displays = new ArrayList<String>(slots.size());
        ArrayList<String> commits = new ArrayList<String>(slots.size());
        ArrayList<String> scripts = new ArrayList<String>(slots.size());
        boolean[] isJavaScripts = new boolean[slots.size()];
        int[] timeouts = new int[slots.size()];
        GboardTopRowSwipeSettings.JavaScriptRuntimeLimits sanitizedRuntimeLimits =
                GboardTopRowSwipeSettings.sanitizeJavaScriptRuntimeLimits(
                        javaScriptRuntimeLimits);
        for (int index = 0; index < slots.size(); index++) {
            GboardTopRowSwipeSettings.SlotText slot = slots.get(index);
            displays.add(slot.displayText);
            commits.add(slot.commitText);
            isJavaScripts[index] = slot.isJavaScript;
            scripts.add(slot.scriptText);
            timeouts[index] = slot.timeoutMs;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ENABLED,
                Boolean.valueOf(enabled));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                Boolean.valueOf(zhuyinEnabled));
        result.put(GboardTopRowSwipeSettings
                        .BUNDLE_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                Boolean.valueOf(englishQwertyEnabled));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_GLOBAL_JAVA_SCRIPT,
                globalJavaScript != null ? globalJavaScript : "");
        result.put(
                GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_RESPONSE_BODY_LIMIT_BYTES,
                Integer.valueOf(sanitizedRuntimeLimits.responseBodyLimitBytes));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_TIMEOUT_MAX_MS,
                Integer.valueOf(sanitizedRuntimeLimits.timeoutMaxMs));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_MEMORY_LIMIT_BYTES,
                Integer.valueOf(sanitizedRuntimeLimits.memoryLimitBytes));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_JS_MAX_STACK_BYTES,
                Integer.valueOf(sanitizedRuntimeLimits.maxStackBytes));
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_DISPLAY_TEXTS, displays);
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_COMMIT_TEXTS, commits);
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_IS_JAVA_SCRIPT,
                isJavaScripts);
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_SCRIPT_TEXTS, scripts);
        result.put(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_TIMEOUTS_MS, timeouts);
        return result;
    }

    private static Bundle topRowSwipeSettingsBundleFromValues(Map<String, Object> values) {
        Bundle result = new Bundle();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            putTopRowSwipeSettingsBundleValue(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void putTopRowSwipeSettingsBundleValue(Bundle result, String key,
            Object value) {
        if (value instanceof Boolean booleanValue) {
            result.putBoolean(key, booleanValue.booleanValue());
        } else if (value instanceof Integer intValue) {
            result.putInt(key, intValue.intValue());
        } else if (value instanceof String stringValue) {
            result.putString(key, stringValue);
        } else if (value instanceof ArrayList<?>) {
            result.putStringArrayList(key, (ArrayList<String>) value);
        } else if (value instanceof boolean[] booleanArray) {
            result.putBooleanArray(key, booleanArray);
        } else if (value instanceof int[] intArray) {
            result.putIntArray(key, intArray);
        }
    }

    static boolean isTopRowSwipeSettingsCallerAllowed(String modulePackage,
            String callingPackage) {
        if (modulePackage == null || modulePackage.isEmpty()) {
            return false;
        }
        if (callingPackage == null || callingPackage.isEmpty()) {
            return true;
        }
        return modulePackage.equals(callingPackage)
                || GBOARD_PACKAGE_STABLE.equals(callingPackage)
                || GBOARD_PACKAGE_JASON_DEV.equals(callingPackage)
                || GBOARD_PACKAGE_REVERSED_DEV.equals(callingPackage);
    }

    static boolean shouldExposeTopRowSwipeSettings(boolean hasFeatureMarker,
            String modulePackage,
            String callingPackage) {
        return hasFeatureMarker
                && isTopRowSwipeSettingsCallerAllowed(modulePackage, callingPackage);
    }

    private static Bundle unavailableTopRowSwipeSettings() {
        Bundle result = new Bundle();
        result.putBoolean(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ENABLED, false);
        result.putBoolean(GboardTopRowSwipeSettings.BUNDLE_KEY_TOP_ROW_SWIPE_ZHUYIN_ENABLED,
                false);
        result.putBoolean(
                GboardTopRowSwipeSettings
                        .BUNDLE_KEY_TOP_ROW_SWIPE_ENGLISH_QWERTY_ENABLED,
                false);
        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
