package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

/**
 * Last stop before a Gboard data import: names the backup and every item it will overwrite,
 * offers to export the current data first, and pins the overwrite action.
 */
final class GboardDataRestoreConfirmFeature implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final GboardDataBackupGateway gateway;
    private final byte[] data;
    private final GboardDataBackupArchive.Manifest manifest;
    private final Set<String> selected;
    private volatile boolean running;

    GboardDataRestoreConfirmFeature(Context context, GboardDataBackupGateway gateway, byte[] data,
            GboardDataBackupArchive.Manifest manifest, Set<String> selected) {
        this.context = context;
        this.gateway = gateway;
        this.data = data;
        this.manifest = manifest;
        this.selected = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    @Override
    public String getEntryTitle() {
        return GboardDataBackupSettings.text(context, R.string.gboard_data_backup_confirm_title);
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
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_data_backup_confirm_warning_title),
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_data_backup_confirm_warning_summary),
                        GboardPatchesSettingsContract.StatusTone.WARNING));

        List<GboardPatchesSettingsContract.Row> backupRows = new ArrayList<>();
        backupRows.add(new GboardPatchesSettingsContract.InfoRow(
                GboardDataBackupSettings.text(context,
                        R.string.gboard_data_backup_confirm_gboard_version),
                manifest.getGboardVersionName().isEmpty()
                        ? "-" : manifest.getGboardVersionName(),
                true));
        if (manifest.getCreatedAtMillis() > 0L) {
            backupRows.add(new GboardPatchesSettingsContract.InfoRow(
                    GboardDataBackupSettings.text(context,
                            R.string.gboard_data_backup_confirm_created),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(manifest.getCreatedAtMillis())),
                    true));
        }

        List<GboardPatchesSettingsContract.Row> itemRows = new ArrayList<>();
        List<String> ordered = new ArrayList<>(selected);
        GboardDataBackupSettings.sortForDisplay(ordered);
        for (String id : ordered) {
            String summary = GboardDataBackupArchive.SETTINGS_ID.equals(id)
                    ? GboardDataBackupSettings.format(context,
                            R.string.gboard_data_backup_confirm_settings_count,
                            manifest.getSettingsEntryCount())
                    : null;
            itemRows.add(new GboardPatchesSettingsContract.InfoRow(
                    GboardDataBackupSettings.labelFor(context, manifest, id), summary, true));
        }

        List<GboardPatchesSettingsContract.Row> safetyRows = Collections.singletonList(
                new GboardPatchesSettingsContract.CommandRow(
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_data_backup_confirm_export_first),
                        GboardDataBackupSettings.text(context,
                                R.string.gboard_data_backup_confirm_export_first_summary),
                        !running,
                        () -> GboardDataBackupSettings.startExport(host, gateway)));

        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        sections.add(new GboardPatchesSettingsContract.Section(
                GboardDataBackupSettings.format(context,
                        R.string.gboard_data_backup_confirm_items, ordered.size()),
                null, GboardPatchesSettingsContract.SectionStyle.DEFAULT, itemRows));
        sections.add(new GboardPatchesSettingsContract.Section(
                GboardDataBackupSettings.text(context, R.string.gboard_data_backup_confirm_backup),
                null, GboardPatchesSettingsContract.SectionStyle.DEFAULT, backupRows));
        sections.add(new GboardPatchesSettingsContract.Section(
                null, null, GboardPatchesSettingsContract.SectionStyle.DEFAULT, safetyRows));

        GboardPatchesSettingsContract.CommandRow action =
                new GboardPatchesSettingsContract.CommandRow(
                        GboardDataBackupSettings.format(context,
                                R.string.gboard_data_backup_confirm_action, ordered.size()),
                        null,
                        !running,
                        () -> start(host));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard", getEntryTitle(), null, status, sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                action);
    }

    private void start(GboardPatchesSettingsContract.FeatureHost host) {
        if (running) {
            return;
        }
        running = true;
        GboardPatchesSettingsContract.refresh(host);
        GboardDataBackupSettings.restore(host, gateway, data, manifest, selected, () -> {
            running = false;
            GboardPatchesSettingsContract.refresh(host);
        });
    }
}
