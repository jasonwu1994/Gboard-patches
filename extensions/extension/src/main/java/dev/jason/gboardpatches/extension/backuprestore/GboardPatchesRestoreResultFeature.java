package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Immutable post-restore report rendered through the shared settings shell. */
final class GboardPatchesRestoreResultFeature
        implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final GboardPatchesBackupManager.RestoreResult result;

    GboardPatchesRestoreResultFeature(Context context,
            GboardPatchesBackupManager.RestoreResult result) {
        this.context = context;
        this.result = result;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_backup_restore_result_title);
    }

    @Override
    public String getEntrySummary() {
        return null;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.StatusTone tone = result.isSuccess()
                ? GboardPatchesSettingsContract.StatusTone.INFO
                : GboardPatchesSettingsContract.StatusTone.WARNING;
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_backup_restore_result_overall),
                        overallSummary(), tone));
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        for (GboardPatchesBackupManager.ModuleResult module : result.getModules()) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            for (GboardPatchesBackupManager.KeyResult key : module.getKeys()) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        key.getKey(), keyStatus(key.getStatus()), true));
            }
            if (rows.isEmpty()) {
                rows.add(new GboardPatchesSettingsContract.InfoRow(
                        text(R.string.gboard_patches_backup_restore_result_no_keys),
                        moduleStatus(module.getStatus()), true));
            }
            sections.add(new GboardPatchesSettingsContract.Section(
                    module.getModuleName(),
                    moduleStatus(module.getStatus())
                            + (module.getMessage() == null ? "" : " · " + module.getMessage()),
                    module.getStatus() == GboardPatchesBackupManager.ModuleStatus.SUCCESS
                            ? GboardPatchesSettingsContract.SectionStyle.DEFAULT
                            : GboardPatchesSettingsContract.SectionStyle.ADVANCED,
                    rows));
        }
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard Patches", getEntryTitle(), null,
                status, sections);
    }

    private String overallSummary() {
        if (result.isSuccess()) {
            int resource = result.areMirrorsSynchronized()
                    ? R.string.gboard_patches_backup_restore_result_success
                    : R.string.gboard_patches_backup_restore_result_mirror_pending;
            return text(resource, result.getRestoredStoreCount(), result.getRestoredEntryCount());
        }
        String summary = result.isPartialSuccess()
                ? text(R.string.gboard_patches_backup_restore_result_partial,
                        result.getRestoredStoreCount(), result.getRestoredEntryCount(),
                        result.getFailedModuleCount())
                : text(R.string.gboard_patches_backup_restore_result_failed);
        return result.getErrorMessage() == null
                ? summary : summary + " · " + result.getErrorMessage();
    }

    private String moduleStatus(GboardPatchesBackupManager.ModuleStatus status) {
        return switch (status) {
            case SUCCESS -> text(R.string.gboard_patches_backup_status_success);
            case PARTIAL -> text(R.string.gboard_patches_backup_status_partial);
            case FAILED -> text(R.string.gboard_patches_backup_status_failed);
            case PREVIOUS_VALUES_RESTORED ->
                    text(R.string.gboard_patches_backup_status_previous_values_restored);
        };
    }

    private String keyStatus(GboardPatchesBackupManager.KeyStatus status) {
        return switch (status) {
            case RESTORED -> text(R.string.gboard_patches_backup_key_restored);
            case REMOVED -> text(R.string.gboard_patches_backup_key_removed);
            case FAILED -> text(R.string.gboard_patches_backup_key_failed);
            case PREVIOUS_VALUE_RESTORED ->
                    text(R.string.gboard_patches_backup_key_previous_value_restored);
        };
    }

    private String text(int resourceId, Object... args) {
        String value = GboardSettingsText.get(context, resourceId);
        return args.length == 0 ? value : String.format(value, args);
    }
}
