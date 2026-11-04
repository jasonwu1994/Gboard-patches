package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardSpacebarLogoSettings {
    private static final String TAG = "GboardPatches";
    public static final String PREF_KEY_ENABLED = "pref_spacebar_logo_enabled";
    public static final String PREF_KEY_COLOR = "pref_spacebar_logo_color";
    public static final String PREF_KEY_COLOR_SELECTION = "pref_spacebar_logo_color_selection";
    public static final String PREF_KEY_CUSTOM_COLOR = "pref_spacebar_logo_custom_color";
    public static final String PREF_KEY_ICON = "pref_spacebar_logo_icon";
    public static final String PREF_KEY_IMAGE = "pref_spacebar_logo_image";
    public static final String PREF_KEY_IMAGE_TINT = "pref_spacebar_logo_image_tint";
    public static final String PREF_KEY_SIZE_DP = "pref_spacebar_logo_size_dp";
    public static final boolean DEFAULT_ENABLED = false;
    public static final String DEFAULT_COLOR_HEX = "#4285F4";
    public static final String COLOR_VALUE_AUTO = "AUTO";
    public static final String COLOR_SELECTION_CUSTOM = "CUSTOM";
    public static final String GOOGLE_BLUE = "#4285F4";
    public static final String GOOGLE_RED = "#EA4335";
    public static final String GOOGLE_YELLOW = "#FBBC04";
    public static final String GOOGLE_GREEN = "#34A853";
    public static final String BLACK = "#000000";
    public static final String WHITE = "#FFFFFF";
    public static final String ICON_GOOGLE_G = GboardSpacebarLogoContract.ICON_GOOGLE_G;
    public static final String ICON_HEART = GboardSpacebarLogoContract.ICON_HEART;
    public static final String ICON_EMOJI = GboardSpacebarLogoContract.ICON_EMOJI;
    public static final String ICON_GOOGLE_G_COLOR = GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR;
    public static final String ICON_CUSTOM_IMAGE = GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE;
    public static final String DEFAULT_ICON = GboardSpacebarLogoContract.DEFAULT_ICON;

    private GboardSpacebarLogoSettings() {
    }

    public static boolean readEnabled(SharedPreferences preferences) {
        return preferences == null ? DEFAULT_ENABLED : enabledValue(preferences.getAll().get(PREF_KEY_ENABLED));
    }

    private static boolean enabledValue(Object raw) {
        if (raw instanceof Boolean) return ((Boolean) raw).booleanValue();
        if (raw instanceof String) {
            if ("true".equalsIgnoreCase((String) raw)) return true;
            if ("false".equalsIgnoreCase((String) raw)) return false;
        }
        return DEFAULT_ENABLED;
    }

    public static String readColorHex(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_COLOR_HEX;
        Object raw = preferences.getAll().get(PREF_KEY_COLOR);
        return normalizeColorValue(raw instanceof String ? (String) raw : null);
    }

    public static String readColorSelection(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_COLOR_HEX;
        Object raw = preferences.getAll().get(PREF_KEY_COLOR_SELECTION);
        String normalized = raw instanceof String ? normalizeColorSelection((String) raw) : null;
        return normalized != null ? normalized : selectionForColorValue(readColorHex(preferences));
    }

    public static String readCustomColorHex(SharedPreferences preferences) {
        if (preferences == null) return null;
        Object raw = preferences.getAll().get(PREF_KEY_CUSTOM_COLOR);
        return raw instanceof String ? tryNormalizeColorHex((String) raw) : null;
    }

    public static String readIcon(SharedPreferences preferences) {
        if (preferences == null) return DEFAULT_ICON;
        Object raw = preferences.getAll().get(PREF_KEY_ICON);
        return GboardSpacebarLogoContract.normalizeIcon(raw instanceof String ? (String) raw : null);
    }

    public static String readImage(SharedPreferences preferences) {
        Object raw = preferences == null ? null : preferences.getAll().get(PREF_KEY_IMAGE);
        return GboardSpacebarLogoContract.boundedImageValue(raw instanceof String ? (String) raw : null);
    }

    public static boolean readImageTint(SharedPreferences preferences) {
        Object raw = preferences == null ? null : preferences.getAll().get(PREF_KEY_IMAGE_TINT);
        return Boolean.TRUE.equals(raw) || "true".equalsIgnoreCase(String.valueOf(raw));
    }

    public static int readSizeDp(SharedPreferences preferences) {
        return sizeDpValue(preferences == null ? null : preferences.getAll().get(PREF_KEY_SIZE_DP));
    }

    private static int sizeDpValue(Object raw) {
        if (raw instanceof Integer value) return GboardSpacebarLogoContract.normalizeSizeDp(value);
        if (raw instanceof String value) {
            try { return GboardSpacebarLogoContract.normalizeSizeDp(Integer.parseInt(value.trim())); }
            catch (NumberFormatException ignored) { }
        }
        return GboardSpacebarLogoContract.DEFAULT_SIZE_DP;
    }

    public static boolean writeSizeDp(SharedPreferences preferences, int sizeDp) {
        return preferences != null && GboardSpacebarLogoContract.isValidSizeDp(sizeDp)
                && preferences.edit().putInt(PREF_KEY_SIZE_DP, sizeDp).commit();
    }


    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) return;
        synchronized (preferences) {
            SharedPreferences.Editor editor = preferences.edit();
            boolean changed = false;
            if (!preferences.contains(PREF_KEY_ENABLED)) {
                editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
                changed = true;
            }
            if (!preferences.contains(PREF_KEY_COLOR)) {
                editor.putString(PREF_KEY_COLOR, DEFAULT_COLOR_HEX);
                changed = true;
            }
            if (!preferences.contains(PREF_KEY_COLOR_SELECTION)) {
                String colorValue = readColorHex(preferences);
                String selection = selectionForColorValue(colorValue);
                editor.putString(PREF_KEY_COLOR_SELECTION, selection);
                if (COLOR_SELECTION_CUSTOM.equals(selection)
                        && !preferences.contains(PREF_KEY_CUSTOM_COLOR)) {
                    editor.putString(PREF_KEY_CUSTOM_COLOR, colorValue);
                }
                changed = true;
            }
            if (!preferences.contains(PREF_KEY_ICON)) {
                editor.putString(PREF_KEY_ICON, DEFAULT_ICON);
                changed = true;
            }
            if (changed) editor.commit();
        }
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        try {
            if (context == null) return false;
            return writeAndRefresh(context, writeEnabled(
                    GboardPatchesSettings.preferences(context), enabled));
        } catch (Throwable throwable) {
            logWarningSafely("Failed to write spacebar logo enabled state", throwable);
            return false;
        }
    }

    static boolean writeEnabled(SharedPreferences preferences, boolean enabled) {
        return preferences != null && preferences.edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .commit();
    }

    public static boolean writeColorHex(Context context, String colorHex) {
        try {
            if (context == null) return false;
            return writeAndRefresh(context, writeColorHex(
                    GboardPatchesSettings.preferences(context), colorHex));
        } catch (Throwable throwable) {
            logWarningSafely("Failed to write spacebar logo color", throwable);
            return false;
        }
    }

    static boolean writeColorHex(SharedPreferences preferences, String colorHex) {
        if (preferences == null) return false;
        String normalized = normalizeColorValue(colorHex);
        String selection = selectionForColorValue(normalized);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(PREF_KEY_COLOR, normalized)
                .putString(PREF_KEY_COLOR_SELECTION, selection);
        if (COLOR_SELECTION_CUSTOM.equals(selection)) {
            editor.putString(PREF_KEY_CUSTOM_COLOR, normalized);
        }
        return editor.commit();
    }

    public static boolean writeCustomColor(Context context, String colorHex) {
        try {
            if (context == null) return false;
            return writeAndRefresh(context, writeCustomColor(
                    GboardPatchesSettings.preferences(context), colorHex));
        } catch (Throwable throwable) {
            logWarningSafely("Failed to write custom spacebar logo color", throwable);
            return false;
        }
    }

    static boolean writeCustomColor(SharedPreferences preferences, String colorHex) {
        if (preferences == null) return false;
        String normalized = normalizeColorHex(colorHex);
        return preferences.edit()
                .putString(PREF_KEY_COLOR, normalized)
                .putString(PREF_KEY_COLOR_SELECTION, COLOR_SELECTION_CUSTOM)
                .putString(PREF_KEY_CUSTOM_COLOR, normalized)
                .commit();
    }

    public static boolean writeIcon(Context context, String icon) {
        try {
            if (context == null) return false;
            return writeAndRefresh(context, writeIcon(
                    GboardPatchesSettings.preferences(context), icon));
        } catch (Throwable throwable) {
            logWarningSafely("Failed to write spacebar logo icon", throwable);
            return false;
        }
    }

    static boolean writeIcon(SharedPreferences preferences, String icon) {
        if (preferences == null) return false;
        synchronized (preferences) {
            return preferences.edit().putString(
                    PREF_KEY_ICON, GboardSpacebarLogoContract.normalizeIcon(icon)).commit();
        }
    }

    public static boolean writeImage(Context context, String image) {
        try {
            boolean committed = writeImage(GboardPatchesSettings.preferencesOrNull(context), image);
            // A bind may have observed the tentative memory update before a failed disk write.
            refreshRuntime(context);
            return committed;
        } catch (Throwable failure) {
            logWarningSafely("Failed to save spacebar image", failure);
            return false;
        }
    }

    public static boolean removeImage(Context context) {
        try { return writeAndRefresh(context, removeImage(GboardPatchesSettings.preferencesOrNull(context))); }
        catch (Throwable failure) { logWarningSafely("Failed to remove spacebar image", failure); return false; }
    }

    public static boolean writeImageTint(Context context, boolean enabled) {
        try { return writeAndRefresh(context, writeImageTint(GboardPatchesSettings.preferencesOrNull(context), enabled)); }
        catch (Throwable failure) { logWarningSafely("Failed to change spacebar image color mode", failure); return false; }
    }

    public static boolean writeSizeDp(Context context, int sizeDp) {
        try { return writeAndRefresh(context, writeSizeDp(GboardPatchesSettings.preferencesOrNull(context), sizeDp)); }
        catch (Throwable failure) { logWarningSafely("Failed to change spacebar image size", failure); return false; }
    }

    public static boolean writeImage(SharedPreferences preferences, String image) {
        String bounded = GboardSpacebarLogoContract.boundedImageValue(image);
        if (preferences == null || bounded.isEmpty()) return false;
        synchronized (preferences) {
            Map<String, ?> previous;
            try { previous = preferences.getAll(); }
            catch (Throwable failure) {
                logWarningSafely("Failed to capture previous spacebar image", failure);
                return false;
            }
            try {
                if (preferences.edit().putString(PREF_KEY_IMAGE, bounded)
                        .putBoolean(PREF_KEY_IMAGE_TINT, false)
                        .putString(PREF_KEY_ICON, ICON_CUSTOM_IMAGE).commit()) return true;
            } catch (Throwable failure) {
                logWarningSafely("Failed to save spacebar image", failure);
            }
            // commit() can update memory before reporting disk failure. Undo only our keys.
            try {
                SharedPreferences.Editor editor = preferences.edit();
                restoreImageField(editor, previous, PREF_KEY_IMAGE);
                restoreImageField(editor, previous, PREF_KEY_IMAGE_TINT);
                restoreImageField(editor, previous, PREF_KEY_ICON);
                if (!editor.commit()) logWarningSafely("Failed to persist previous spacebar image", null);
            } catch (Throwable failure) {
                logWarningSafely("Failed to restore previous spacebar image", failure);
            }
            return false;
        }
    }

    private static void restoreImageField(SharedPreferences.Editor editor, Map<String, ?> previous, String key) {
        Object value = previous.get(key);
        if (value == null) editor.remove(key);
        else if (value instanceof String text) editor.putString(key, text);
        else if (value instanceof Boolean flag) editor.putBoolean(key, flag);
        else if (value instanceof Integer number) editor.putInt(key, number);
        else if (value instanceof Long number) editor.putLong(key, number);
        else if (value instanceof Float number) editor.putFloat(key, number);
        else if (value instanceof Set<?> values) {
            Set<String> strings = new HashSet<>();
            for (Object item : values) strings.add((String) item);
            editor.putStringSet(key, strings);
        } else throw new IllegalArgumentException("Unsupported stored spacebar image field");
    }

    public static boolean removeImage(SharedPreferences preferences) {
        if (preferences == null) return false;
        synchronized (preferences) {
            SharedPreferences.Editor editor = preferences.edit()
                    .remove(PREF_KEY_IMAGE).remove(PREF_KEY_IMAGE_TINT);
            if (ICON_CUSTOM_IMAGE.equals(readIcon(preferences))) editor.putString(PREF_KEY_ICON, DEFAULT_ICON);
            return editor.commit();
        }
    }

    public static boolean writeImageTint(SharedPreferences preferences, boolean enabled) {
        if (preferences == null) return false;
        synchronized (preferences) {
            SharedPreferences.Editor editor = preferences.edit().putBoolean(PREF_KEY_IMAGE_TINT, enabled);
            if (enabled && isAutomaticColor(readColorHex(preferences))) {
                editor.putString(PREF_KEY_COLOR, DEFAULT_COLOR_HEX)
                        .putString(PREF_KEY_COLOR_SELECTION, DEFAULT_COLOR_HEX);
            }
            return editor.commit();
        }
    }

    public static boolean isAutomaticColor(String value) {
        return COLOR_VALUE_AUTO.equals(normalizeColorValue(value));
    }

    public static String normalizeColorValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return COLOR_VALUE_AUTO.equalsIgnoreCase(normalized)
                ? COLOR_VALUE_AUTO : normalizeColorHex(normalized);
    }

    public static String normalizeColorHex(String value) {
        String normalized = tryNormalizeColorHex(value);
        return normalized == null ? DEFAULT_COLOR_HEX : normalized;
    }

    public static String tryNormalizeColorHex(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if ((normalized.length() != 6 && normalized.length() != 8)
                || !normalized.matches("[0-9a-fA-F]+")) return null;
        return "#" + normalized.toUpperCase(Locale.ROOT);
    }

    public static GboardSpacebarLogoRuntime.SettingsSnapshot snapshot(Context context) {
        try {
            return GboardPatchesSettings.readRuntimeSetting(context,
                    GboardSpacebarLogoSettings::snapshotFromPreferences, disabledSnapshot());
        } catch (Throwable throwable) {
            logWarningSafely("Failed to read spacebar logo settings", throwable);
            return disabledSnapshot();
        }
    }

    static GboardSpacebarLogoRuntime.SettingsSnapshot snapshotFromPreferences(SharedPreferences preferences) {
        if (preferences == null) return disabledSnapshot();
        try {
            // SharedPreferences publishes each edit atomically. Never wait behind a disk transaction
            // here: this read also runs during ordinary key binds, even when the feature is disabled.
            Map<String, ?> values = preferences.getAll();
            Object rawColor = values.get(PREF_KEY_COLOR);
            String color = normalizeColorValue(rawColor instanceof String text ? text : null);
            int parsedColor = Color.parseColor(isAutomaticColor(color) ? DEFAULT_COLOR_HEX : color);
            Object rawIcon = values.get(PREF_KEY_ICON);
            Object rawImage = values.get(PREF_KEY_IMAGE);
            Object rawTint = values.get(PREF_KEY_IMAGE_TINT);
            return new GboardSpacebarLogoRuntime.SettingsSnapshot(
                    enabledValue(values.get(PREF_KEY_ENABLED)), !isAutomaticColor(color), parsedColor,
                    GboardSpacebarLogoContract.normalizeIcon(rawIcon instanceof String text ? text : null),
                    GboardSpacebarLogoContract.boundedImageValue(rawImage instanceof String text ? text : null),
                    Boolean.TRUE.equals(rawTint) || "true".equalsIgnoreCase(String.valueOf(rawTint)),
                    sizeDpValue(values.get(PREF_KEY_SIZE_DP)));
        } catch (Throwable throwable) {
            logWarningSafely("Failed to read spacebar logo settings", throwable);
            return disabledSnapshot();
        }
    }

    private static boolean writeAndRefresh(Context context, boolean committed) {
        if (committed) refreshRuntime(context);
        return committed;
    }

    public static boolean refreshRuntime(Context context) {
        if (context == null) return false;
        try {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Context application = context.getApplicationContext();
                Context refreshContext = application != null ? application : context;
                // Capture on the UI thread so delayed imports cannot publish an older snapshot.
                return new Handler(Looper.getMainLooper()).post(() -> refreshRuntime(refreshContext));
            }
            GboardSpacebarLogoRuntime.refreshBoundSpacebars(snapshot(context));
            return true;
        } catch (Throwable failure) {
            logWarningSafely("Failed to refresh spacebar logo settings", failure);
            return false;
        }
    }

    private static GboardSpacebarLogoRuntime.SettingsSnapshot disabledSnapshot() {
        return new GboardSpacebarLogoRuntime.SettingsSnapshot(
                false, false, 0, DEFAULT_ICON);
    }

    private static void logWarningSafely(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (Throwable ignored) {
            // Settings failure handling must not affect the host app.
        }
    }

    private static String normalizeColorSelection(String value) {
        if (value == null) return null;
        if (COLOR_SELECTION_CUSTOM.equalsIgnoreCase(value.trim())) return COLOR_SELECTION_CUSTOM;
        if (COLOR_VALUE_AUTO.equalsIgnoreCase(value.trim())) return COLOR_VALUE_AUTO;
        String color = tryNormalizeColorHex(value);
        return isPresetColor(color) ? color : null;
    }

    private static String selectionForColorValue(String value) {
        if (isAutomaticColor(value)) return COLOR_VALUE_AUTO;
        String normalized = normalizeColorHex(value);
        return isPresetColor(normalized) ? normalized : COLOR_SELECTION_CUSTOM;
    }

    private static boolean isPresetColor(String value) {
        return GOOGLE_BLUE.equals(value) || GOOGLE_RED.equals(value)
                || GOOGLE_YELLOW.equals(value) || GOOGLE_GREEN.equals(value)
                || BLACK.equals(value) || WHITE.equals(value);
    }
}
