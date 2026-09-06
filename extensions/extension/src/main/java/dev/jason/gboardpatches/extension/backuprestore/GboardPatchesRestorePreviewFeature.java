package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Immutable pre-restore difference review for the selected Patches modules. */
final class GboardPatchesRestorePreviewFeature
        implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final String json;
    private final Set<String> selectedModuleIds;
    private GboardPatchesBackupManager.RestorePreview preview;

    GboardPatchesRestorePreviewFeature(Context context, String json,
            Set<String> selectedModuleIds,
            GboardPatchesBackupManager.RestorePreview preview) {
        this.context = context;
        this.json = json;
        this.selectedModuleIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(selectedModuleIds));
        this.preview = preview;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_backup_restore_preview_title);
    }

    @Override
    public String getEntrySummary() {
        return null;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_backup_restore_preview_overall),
                        text(R.string.gboard_patches_backup_restore_preview_summary,
                                preview.getAddedCount(), preview.getChangedCount(),
                                preview.getRemovedCount(), preview.getUnchangedCount()),
                        GboardPatchesSettingsContract.StatusTone.INFO));
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        for (GboardPatchesBackupManager.ModuleChange module : preview.getModules()) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            int differenceCount = 0;
            for (GboardPatchesBackupManager.KeyChange key : module.getKeys()) {
                if (key.getStatus() == GboardPatchesBackupManager.ChangeStatus.UNCHANGED) {
                    continue;
                }
                differenceCount++;
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        key.getKey(), changeSummary(key), true));
            }
            if (rows.isEmpty()) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        text(R.string.gboard_patches_backup_restore_preview_no_changes),
                        null,
                        false));
            }
            sections.add(new GboardPatchesSettingsContract.Section(
                    module.getModuleName(),
                    text(R.string.gboard_patches_backup_restore_preview_module_summary,
                            differenceCount),
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    rows));
        }
        GboardPatchesSettingsContract.CommandRow action =
                new GboardPatchesSettingsContract.CommandRow(
                        text(R.string.gboard_patches_backup_restore_preview_action),
                        null,
                        preview.getDifferenceCount() > 0,
                        () -> restore(host));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard Patches", getEntryTitle(), null,
                status, sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                action);
    }

    private String changeSummary(GboardPatchesBackupManager.KeyChange key) {
        String current = key.getCurrentValue() == null
                ? text(R.string.gboard_patches_backup_restore_preview_absent)
                : key.getCurrentValue();
        String backup = key.getBackupValue() == null
                ? text(R.string.gboard_patches_backup_restore_preview_absent)
                : key.getBackupValue();
        return changeStatus(key.getStatus())
                + "\n" + text(R.string.gboard_patches_backup_restore_preview_current, current)
                + "\n" + text(R.string.gboard_patches_backup_restore_preview_backup, backup);
    }

    private String changeStatus(GboardPatchesBackupManager.ChangeStatus status) {
        return switch (status) {
            case ADDED -> text(R.string.gboard_patches_backup_restore_preview_added);
            case CHANGED -> text(R.string.gboard_patches_backup_restore_preview_changed);
            case REMOVED -> text(R.string.gboard_patches_backup_restore_preview_removed);
            case UNCHANGED -> text(R.string.gboard_patches_backup_restore_preview_unchanged);
        };
    }

    private void restore(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            GboardPatchesBackupManager.RestoreResult result =
                    GboardPatchesBackupManager.restoreBackup(
                            host.getContext(), json, selectedModuleIds);
            try {
                preview = GboardPatchesBackupManager.inspectRestore(
                        host.getContext(), json, selectedModuleIds);
            } catch (Throwable ignored) {
                // The restore result remains authoritative if refreshing the preview fails.
            }
            GboardPatchesSettingsContract.openFeature(host,
                    new GboardPatchesRestoreResultFeature(host.getContext(), result));
        } catch (Throwable failure) {
            GboardPatchesSettingsContract.showMessage(host,
                    text(R.string.gboard_patches_backup_restore_failed));
        }
    }

    private String text(int resourceId, Object... args) {
        String value = GboardSettingsText.get(context, resourceId);
        return args.length == 0 ? value : String.format(value, args);
    }
}
