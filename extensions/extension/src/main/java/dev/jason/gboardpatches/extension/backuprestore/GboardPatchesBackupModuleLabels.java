package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Localized names for Patches backup modules. The backup file keeps the catalog's English
 * {@code moduleName}; only the screens show these, reusing each feature's own page title.
 */
final class GboardPatchesBackupModuleLabels {
    private GboardPatchesBackupModuleLabels() {
    }

    static String label(Context context, String moduleId, String fallback) {
        int resource = moduleId == null ? 0 : switch (moduleId) {
            case "gboard-patches.core" -> R.string.gboard_patches_backup_module_core;
            case "gboard-patches.custom-top-row" -> R.string.gboard_patches_top_row_title;
            case "gboard-patches.manual-incognito" -> R.string.gboard_patches_manual_incognito_title;
            case "gboard-patches.settings-homepage" -> R.string.gboard_patches_settings_homepage_title;
            case "gboard-patches.zhuyin-bottom-row" -> R.string.gboard_patches_zhuyin_bottom_row_title;
            case "gboard-patches.latin-globe" -> R.string.gboard_patches_latin_globe_title;
            case "gboard-patches.lan-ftp" -> R.string.gboard_patches_lan_ftp_title;
            case "gboard-patches.clipboard" -> R.string.gboard_patches_clipboard_title;
            case "gboard-patches.web-clipboard" -> R.string.gboard_patches_web_clipboard_title;
            case "gboard-patches.ocr" -> R.string.gboard_patches_ocr_title;
            case "gboard-patches.symbol-footer" -> R.string.gboard_patches_symbol_footer_title;
            case "gboard-patches.english-uppercase" ->
                    R.string.gboard_patches_english_uppercase_toggle_title;
            case "gboard-patches.ai-writing-tools" -> R.string.gboard_patches_ai_writing_tools_title;
            case "gboard-patches.clipboard-content-limit" ->
                    R.string.gboard_patches_clipboard_content_limit_title;
            case "gboard-patches.custom-symbol-recents" ->
                    R.string.gboard_patches_backup_module_symbol_recents;
            default -> 0;
        };
        if (resource == 0) {
            return fallback;
        }
        String value = GboardSettingsText.get(context, resource);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
