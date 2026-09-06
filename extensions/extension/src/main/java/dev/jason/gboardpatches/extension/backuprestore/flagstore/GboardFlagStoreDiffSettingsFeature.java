package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Read-only complete semantic diff shown before a raw replacement is applied. */
final class GboardFlagStoreDiffSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final String fileName;
    private final byte[] replacement;
    private final GboardFlagStoreDiff.Result diff;

    GboardFlagStoreDiffSettingsFeature(Context context, String fileName, byte[] replacement,
            GboardFlagStoreDiff.Result diff) {
        this.context = context;
        this.fileName = fileName;
        this.replacement = replacement.clone();
        this.diff = diff;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_backup_restore_preview_title);
    }

    @Override
    public String getEntrySummary() {
        return "";
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        String summary = text(R.string.gboard_flag_store_diff_summary,
                diff.getCurrentValueCount(), diff.getReplacementValueCount(),
                diff.count(GboardFlagStoreDiff.Change.CHANGED),
                diff.count(GboardFlagStoreDiff.Change.ADDED),
                diff.count(GboardFlagStoreDiff.Change.REMOVED));
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        addDifferenceSection(sections, GboardFlagStoreDiff.Change.CHANGED,
                R.string.gboard_flag_store_diff_changed);
        addDifferenceSection(sections, GboardFlagStoreDiff.Change.ADDED,
                R.string.gboard_flag_store_diff_added);
        addDifferenceSection(sections, GboardFlagStoreDiff.Change.REMOVED,
                R.string.gboard_flag_store_diff_removed);
        if (diff.getDifferences().isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_flag_store_diff_no_changes),
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            fileName, text(R.string.gboard_flag_store_diff_identical), true))));
        }
        GboardPatchesSettingsContract.CommandRow action =
                new GboardPatchesSettingsContract.DangerRow(
                        text(R.string.gboard_flag_store_replacement_apply_title),
                        null,
                        true,
                        () -> applyAndRestart(host),
                        text(R.string.gboard_flag_store_replacement_apply_confirm_title),
                        text(R.string.gboard_flag_store_replacement_apply_confirm_message));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard Patches", getEntryTitle(), null,
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        fileName, summary, GboardPatchesSettingsContract.StatusTone.INFO)),
                sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                action);
    }

    private void addDifferenceSection(List<GboardPatchesSettingsContract.Section> sections,
            GboardFlagStoreDiff.Change change, int titleResource) {
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        for (GboardFlagStoreDiff.Difference difference : diff.getDifferences()) {
            if (difference.getChange() != change) {
                continue;
            }
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    difference.getKey(), describe(difference), true));
        }
        if (!rows.isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(titleResource) + " (" + rows.size() + ")", null,
                    change == GboardFlagStoreDiff.Change.CHANGED
                            ? GboardPatchesSettingsContract.SectionStyle.ADVANCED
                            : GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    rows));
        }
    }

    private String describe(GboardFlagStoreDiff.Difference difference) {
        return switch (difference.getChange()) {
            case CHANGED -> text(R.string.gboard_flag_store_diff_value_changed,
                    difference.getOldValue(), difference.getNewValue());
            case ADDED -> text(R.string.gboard_flag_store_diff_value_added,
                    difference.getNewValue());
            case REMOVED -> text(R.string.gboard_flag_store_diff_value_removed,
                    difference.getOldValue());
        };
    }

    private void applyAndRestart(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            GboardFlagStoreManager.stage(host.getContext(), fileName, replacement);
            GboardPatchesSettingsContract.requestTargetRestart(host);
        } catch (Throwable failure) {
            String message = failure.getMessage();
            GboardPatchesSettingsContract.showMessage(host,
                    text(R.string.gboard_flag_store_replacement_stage_failed)
                            + (message == null || message.isBlank() ? "" : " · " + message));
        }
    }

    private String text(int resourceId, Object... args) {
        String value = GboardSettingsText.get(context, resourceId);
        return args.length == 0 ? value : String.format(value, args);
    }
}
