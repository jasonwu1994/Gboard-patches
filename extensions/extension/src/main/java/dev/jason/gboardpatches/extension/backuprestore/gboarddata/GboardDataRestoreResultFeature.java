package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

/** Per-provider report for a Gboard data import. */
public final class GboardDataRestoreResultFeature implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final GboardDataBackupResults.Restore result;

    public GboardDataRestoreResultFeature(Context context,
            GboardDataBackupResults.Restore result) {
        this.context = context;
        this.result = result;
    }

    @Override
    public String getEntryTitle() {
        return GboardDataBackupSettings.text(context,
                R.string.gboard_patches_backup_restore_result_title);
    }

    @Override
    public String getEntrySummary() {
        return null;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        boolean success = result.isCompleteSuccess();
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_patches_backup_restore_result_overall),
                        GboardDataBackupSettings.text(context, success
                                ? R.string.gboard_data_backup_result_success
                                : R.string.gboard_data_backup_result_partial),
                        success ? GboardPatchesSettingsContract.StatusTone.INFO
                                : GboardPatchesSettingsContract.StatusTone.WARNING));

        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        for (GboardDataBackupResults.Outcome outcome : result.getOutcomes()) {
            rows.add(new GboardPatchesSettingsContract.InfoRow(
                    GboardDataBackupSettings.providerLabel(context, outcome.getId(),
                            outcome.getCategory()),
                    summary(outcome), true));
        }
        List<GboardPatchesSettingsContract.Section> sections = Collections.singletonList(
                new GboardPatchesSettingsContract.Section(
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_data_backup_section_title),
                        null, GboardPatchesSettingsContract.SectionStyle.DEFAULT, rows));
        GboardPatchesSettingsContract.CommandRow restart =
                new GboardPatchesSettingsContract.CommandRow(
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_patches_restart_action),
                        null,
                        true,
                        () -> GboardPatchesSettingsContract.requestTargetRestart(host));
        return new GboardPatchesSettingsContract.Screen(getEntryTitle(), "Gboard",
                getEntryTitle(), null, status, sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                restart);
    }

    private String summary(GboardDataBackupResults.Outcome outcome) {
        String status = GboardDataBackupSettings.text(context, switch (outcome.getStatus()) {
            case RESTORED -> R.string.gboard_data_backup_status_restored;
            case PARTIAL -> R.string.gboard_data_backup_status_partial;
            case FAILED -> R.string.gboard_data_backup_status_failed;
            case UNSUPPORTED -> R.string.gboard_data_backup_status_unsupported;
        });
        StringBuilder summary = new StringBuilder(status);
        if (!outcome.getFailedKeys().isEmpty()
                && outcome.getStatus() != GboardDataBackupResults.Status.UNSUPPORTED) {
            summary.append(" · ").append(String.join(", ", outcome.getFailedKeys()));
        }
        if (outcome.getError() != null) {
            summary.append('\n').append(outcome.getError());
        }
        if (GboardDataBackupArchive.SETTINGS_ID.equals(outcome.getId())
                && outcome.getStatus() != GboardDataBackupResults.Status.FAILED
                && outcome.getStatus() != GboardDataBackupResults.Status.UNSUPPORTED) {
            summary.append('\n').append(GboardDataBackupSettings.text(context,
                    R.string.gboard_data_backup_settings_contacts_note));
        }
        return summary.toString();
    }
}
