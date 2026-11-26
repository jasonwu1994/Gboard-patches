package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** One module's restored keys: those needing attention first, then removed, then matched. */
final class GboardPatchesRestoreResultModuleFeature
        implements GboardPatchesSettingsContract.Feature {
    private enum Group { ATTENTION, REMOVED, MATCHED }

    private final Context context;
    private final GboardPatchesBackupManager.ModuleResult module;
    private final GboardRestoreKeyFilter filter = new GboardRestoreKeyFilter();

    GboardPatchesRestoreResultModuleFeature(Context context,
            GboardPatchesBackupManager.ModuleResult module) {
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
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        int matched = 0;
        for (Group group : Group.values()) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            for (GboardPatchesBackupManager.KeyResult key : module.getKeys()) {
                if (groupOf(key.getStatus()) == group && filter.matches(key.getKey())) {
                    rows.add(new GboardPatchesSettingsContract.InfoRow(key.getKey(),
                            group == Group.ATTENTION
                                    ? GboardPatchesRestoreResultFeature.keyStatus(
                                            context, key.getStatus())
                                    : null,
                            true));
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            matched += rows.size();
            sections.add(new GboardPatchesSettingsContract.Section(
                    GboardRestoreKeyFilter.groupTitle(context, groupLabel(group), rows.size()),
                    groupHint(group),
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    rows));
        }
        List<GboardPatchesSettingsContract.Row> filterRows =
                filter.rows(context, host, module.getKeys().size(), matched);
        if (!filterRows.isEmpty()) {
            sections.add(0, new GboardPatchesSettingsContract.Section(null, filterRows));
        }
        if (matched == 0) {
            sections.add(new GboardPatchesSettingsContract.Section(null,
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            text(filter.isActive()
                                    ? R.string.gboard_patches_backup_filter_no_match
                                    : R.string.gboard_patches_backup_restore_result_no_keys),
                            null,
                            false))));
        }
        GboardPatchesRestoreResultFeature.Counts counts =
                GboardPatchesRestoreResultFeature.Counts.of(module);
        boolean success = module.getStatus() == GboardPatchesBackupManager.ModuleStatus.SUCCESS
                && counts.attention == 0;
        String statusText = GboardPatchesRestoreResultFeature.moduleStatus(
                context, module.getStatus());
        if (module.getMessage() != null) {
            statusText += " · " + module.getMessage();
        }
        String totals = counts.summary(context);
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_backup_restore_result_module_overall),
                        totals.isEmpty() ? statusText : statusText + "\n" + totals,
                        success
                                ? GboardPatchesSettingsContract.StatusTone.INFO
                                : GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), text(R.string.gboard_patches_backup_restore_result_title),
                getEntryTitle(), null, status, sections);
    }

    private static Group groupOf(GboardPatchesBackupManager.KeyStatus status) {
        return switch (status) {
            case RESTORED -> Group.MATCHED;
            case REMOVED -> Group.REMOVED;
            case FAILED, PREVIOUS_VALUE_RESTORED -> Group.ATTENTION;
        };
    }

    private String groupLabel(Group group) {
        return text(switch (group) {
            case ATTENTION -> R.string.gboard_patches_backup_restore_result_attention_title;
            case REMOVED -> R.string.gboard_patches_backup_restore_result_group_removed;
            case MATCHED -> R.string.gboard_patches_backup_restore_result_group_matched;
        });
    }

    private String groupHint(Group group) {
        return switch (group) {
            case ATTENTION -> null;
            case REMOVED -> text(R.string.gboard_patches_backup_key_removed);
            case MATCHED -> text(R.string.gboard_patches_backup_key_restored);
        };
    }

    private String text(int resourceId, Object... args) {
        return args.length == 0
                ? GboardSettingsText.get(context, resourceId)
                : GboardSettingsText.format(context, resourceId, args);
    }
}
