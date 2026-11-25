package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** One module's pending restore differences, grouped by kind and filterable by key name. */
final class GboardPatchesRestorePreviewModuleFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final GboardPatchesBackupManager.ChangeStatus[] GROUP_ORDER = {
            GboardPatchesBackupManager.ChangeStatus.CHANGED,
            GboardPatchesBackupManager.ChangeStatus.ADDED,
            GboardPatchesBackupManager.ChangeStatus.REMOVED};

    private final Context context;
    private final GboardPatchesBackupManager.ModuleChange module;
    private final GboardRestoreKeyFilter filter = new GboardRestoreKeyFilter();

    GboardPatchesRestorePreviewModuleFeature(Context context,
            GboardPatchesBackupManager.ModuleChange module) {
        this.context = context;
        this.module = module;
    }

    @Override
    public String getEntryTitle() {
        return GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName());
    }

    @Override
    public String getEntrySummary() {
        return null;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesRestorePreviewFeature.Counts counts =
                GboardPatchesRestorePreviewFeature.Counts.of(module);
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        int matched = 0;
        for (GboardPatchesBackupManager.ChangeStatus status : GROUP_ORDER) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            for (GboardPatchesBackupManager.KeyChange key : module.getKeys()) {
                if (key.getStatus() == status && filter.matches(key.getKey())) {
                    rows.add(new GboardPatchesSettingsContract.InfoRow(
                            key.getKey(), valueSummary(key), true));
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            matched += rows.size();
            sections.add(new GboardPatchesSettingsContract.Section(
                    GboardRestoreKeyFilter.groupTitle(context, statusLabel(status), rows.size()),
                    null,
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    rows));
        }
        List<GboardPatchesSettingsContract.Row> filterRows =
                filter.rows(context, host, counts.differences(), matched);
        if (!filterRows.isEmpty()) {
            sections.add(0, new GboardPatchesSettingsContract.Section(null, filterRows));
        }
        if (matched == 0) {
            sections.add(new GboardPatchesSettingsContract.Section(null,
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            text(filter.isActive()
                                    ? R.string.gboard_patches_backup_filter_no_match
                                    : R.string.gboard_patches_backup_restore_preview_no_changes),
                            null,
                            false))));
        }
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_backup_restore_preview_module_overall),
                        GboardPatchesRestorePreviewFeature.Counts.summary(context, counts),
                        GboardPatchesSettingsContract.StatusTone.INFO));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), text(R.string.gboard_patches_backup_restore_preview_title),
                getEntryTitle(), null, status, sections);
    }

    private String valueSummary(GboardPatchesBackupManager.KeyChange key) {
        return text(R.string.gboard_patches_backup_restore_preview_current, display(key.getCurrentValue()))
                + "\n" + text(R.string.gboard_patches_backup_restore_preview_backup,
                        display(key.getBackupValue()));
    }

    private String display(String value) {
        return value == null ? text(R.string.gboard_patches_backup_restore_preview_absent) : value;
    }

    private String statusLabel(GboardPatchesBackupManager.ChangeStatus status) {
        return switch (status) {
            case ADDED -> text(R.string.gboard_patches_backup_restore_preview_added);
            case CHANGED -> text(R.string.gboard_patches_backup_restore_preview_changed);
            case REMOVED -> text(R.string.gboard_patches_backup_restore_preview_removed);
            case UNCHANGED -> text(R.string.gboard_patches_backup_restore_preview_unchanged);
        };
    }

    private String text(int resourceId, Object... args) {
        return args.length == 0
                ? GboardSettingsText.get(context, resourceId)
                : GboardSettingsText.format(context, resourceId, args);
    }
}
