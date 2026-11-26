package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Key-name filter shared by the per-module restore preview and restore result pages. */
public final class GboardRestoreKeyFilter {
    /** Below this many keys a module page is short enough that a filter only adds noise. */
    public static final int MIN_KEYS_FOR_FILTER = 8;

    private String query = "";

    public boolean isActive() {
        return !query.isEmpty();
    }

    public boolean matches(String key) {
        if (query.isEmpty()) {
            return true;
        }
        return key != null && key.toLowerCase(Locale.ROOT).contains(query);
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** The filter control rows, or none when the module has too few keys to need one. */
    public List<GboardPatchesSettingsContract.Row> rows(Context context,
            GboardPatchesSettingsContract.FeatureHost host, int totalKeys, int matchCount) {
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        if (totalKeys < MIN_KEYS_FOR_FILTER && !isActive()) {
            return rows;
        }
        String summary = isActive()
                ? GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_filter_active, query, matchCount)
                : GboardSettingsText.get(context, R.string.gboard_patches_backup_filter_summary);
        rows.add(new GboardPatchesSettingsContract.SearchRow(
                GboardSettingsText.get(context, R.string.gboard_patches_backup_filter_title),
                summary,
                () -> GboardPatchesSettingsContract.showTextInputDialog(host,
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_backup_filter_title),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_backup_filter_hint),
                        query,
                        value -> {
                            setQuery(value);
                            GboardPatchesSettingsContract.refresh(host);
                        })));
        if (isActive()) {
            rows.add(new GboardPatchesSettingsContract.CommandRow(
                    GboardSettingsText.get(context, R.string.gboard_patches_backup_filter_clear),
                    null,
                    true,
                    () -> {
                        setQuery("");
                        GboardPatchesSettingsContract.refresh(host);
                    }));
        }
        return rows;
    }

    /** "Title (count)" heading for a status group inside a module page. */
    public static String groupTitle(Context context, String label, int count) {
        return GboardSettingsText.format(context,
                R.string.gboard_patches_backup_group_title, label, count);
    }
}
