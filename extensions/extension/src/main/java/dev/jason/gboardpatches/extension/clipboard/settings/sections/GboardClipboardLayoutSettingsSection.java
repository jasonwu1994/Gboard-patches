package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

final class GboardClipboardLayoutSettingsSection {
    void appendRows(List<GboardPatchesSettingsContract.Row> rows,
            GboardPatchesSettingsContract.FeatureHost host, SharedPreferences preferences,
            boolean clipboardEnabled) {
        Context context = host.getContext();
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_clipboard_columns),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_clipboard_columns_summary),
                currentColumnCountLabel(context, preferences),
                clipboardEnabled,
                () -> showColumnCountDialog(host, preferences),
                buildColumnCountPreview(context)));
        rows.add(new GboardPatchesSettingsContract.SelectorRow(
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_content_max_lines),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_content_max_lines_summary),
                currentPreviewLinesLabel(context, preferences),
                clipboardEnabled,
                () -> showPreviewLinesDialog(host, preferences),
                buildPreviewLinesPreview(context)));
    }

    private void showColumnCountDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences) {
        Context context = host.getContext();
        String[] labels = new String[] {
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_clipboard_columns_one),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_clipboard_columns_two_default),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_clipboard_columns_three)
        };
        String[] values = new String[] {
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_ONE),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_TWO),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_THREE)
        };
        GboardPatchesSettingsContract.showChoiceDialog(host,
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_clipboard_columns),
                labels,
                values,
                Integer.toString(GboardClipboardSettings.readClipboardColumnCount(preferences)),
                "",
                () -> {
                },
                value -> preferences.edit()
                        .putString(
                                GboardClipboardSettings.PREF_KEY_CLIPBOARD_COLUMN_COUNT,
                                value)
                        .apply());
    }

    private void showPreviewLinesDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences) {
        Context context = host.getContext();
        String[] labels = new String[] {
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_content_max_lines_option_five_default),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_content_max_lines_option_ten),
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_option_custom)
        };
        String[] values = new String[] {
                Integer.toString(GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES),
                Integer.toString(GboardClipboardSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED),
                GboardClipboardSettings.PREF_VALUE_CUSTOM
        };
        GboardPatchesSettingsContract.showChoiceDialog(host,
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_content_max_lines),
                labels,
                values,
                GboardClipboardSettingsSupport.readSelectionValue(
                        preferences,
                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                        Integer.toString(
                                GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES)),
                GboardClipboardSettings.PREF_VALUE_CUSTOM,
                () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(host,
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_custom_content_max_lines_dialog_title),
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_custom_content_max_lines_dialog_hint),
                        GboardClipboardSettings.readClipboardContentMaxLinesCustomValue(
                                preferences),
                        value -> preferences.edit()
                                .putString(
                                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                        GboardClipboardSettings.PREF_VALUE_CUSTOM)
                                .putInt(
                                        GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES_CUSTOM,
                                        value)
                                .apply()),
                value -> preferences.edit()
                        .putString(
                                GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                                value)
                        .apply());
    }

    private String currentColumnCountLabel(Context context, SharedPreferences preferences) {
        int selection = GboardClipboardSettings.readClipboardColumnCount(preferences);
        if (selection == GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_ONE) {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_clipboard_columns_one);
        }
        if (selection == GboardClipboardSettings.CLIPBOARD_COLUMN_COUNT_THREE) {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_clipboard_columns_three);
        }
        return GboardSettingsText.get(
                context,
                R.string.gboard_patches_clipboard_columns_two_default);
    }

    private String currentPreviewLinesLabel(Context context, SharedPreferences preferences) {
        String selection = GboardClipboardSettingsSupport.readSelectionValue(
                preferences,
                GboardClipboardSettings.PREF_KEY_CLIPBOARD_CONTENT_MAX_LINES,
                Integer.toString(GboardClipboardSettings.DEFAULT_CLIPBOARD_CONTENT_MAX_LINES));
        if (GboardClipboardSettings.PREF_VALUE_CUSTOM.equals(selection)) {
            return GboardClipboardSettingsSupport.formatCustomCountLabel(
                    context,
                    GboardClipboardSettings.readClipboardContentMaxLinesCustomValue(preferences));
        }
        if (Integer.toString(GboardClipboardSettings.CLIPBOARD_CONTENT_MAX_LINES_EXTENDED)
                .equals(selection)) {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_content_max_lines_option_ten);
        }
        return GboardSettingsText.get(
                context,
                R.string.gboard_patches_content_max_lines_option_five_default);
    }

    private GboardPatchesSettingsContract.PreviewSpec buildColumnCountPreview(Context context) {
        return new GboardPatchesSettingsContract.PreviewSpec(
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_clipboard_columns),
                "",
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/column_count_one.png",
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_preview_column_count_one_caption)),
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/column_count_three.png",
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_preview_column_count_three_caption)));
    }

    private GboardPatchesSettingsContract.PreviewSpec buildPreviewLinesPreview(Context context) {
        return new GboardPatchesSettingsContract.PreviewSpec(
                GboardSettingsText.get(
                        context,
                        R.string.gboard_patches_pref_content_max_lines),
                "",
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/preview_lines_default_5.png",
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_preview_lines_default_caption)),
                new GboardPatchesSettingsContract.PreviewImage(
                        "settings-previews/clipboard/preview_lines_extended_8.png",
                        GboardSettingsText.get(
                                context,
                                R.string.gboard_patches_preview_lines_extended_caption)));
    }
}
