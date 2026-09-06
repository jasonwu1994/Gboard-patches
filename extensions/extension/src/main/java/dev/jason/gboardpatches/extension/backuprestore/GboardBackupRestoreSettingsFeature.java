package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.backuprestore.flagstore.GboardFlagStoreSettingsFeature;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardBackupRestoreSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String EXPORT_FILE_NAME = "gboard-patches-backup.json";
    private static final String JSON_MIME_TYPE = "application/json";

    private final String entryTitle;
    private final String entrySummary;
    private final Context context;

    public GboardBackupRestoreSettingsFeature(Context context) {
        this.context = context;
        entryTitle = text(context, R.string.gboard_patches_backup_restore_title);
        entrySummary = text(context, R.string.gboard_patches_backup_restore_summary);
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context,
                GboardPatchesFeatureAvailability.FEATURE_BACKUP_RESTORE);
    }

    @Override
    public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
        return Collections.singletonList(
                new GboardFlagStoreSettingsFeature(context));
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        Context context = host.getContext();
        List<GboardPatchesSettingsContract.Row> patchesRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        patchesRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(context, R.string.gboard_patches_backup_export_title),
                null,
                true,
                () -> exportPatches(host)));
        patchesRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(context, R.string.gboard_patches_backup_restore_action_title),
                null,
                true,
                () -> importPatches(host)));

        List<GboardPatchesSettingsContract.Row> flagRows = Collections.singletonList(
                new GboardPatchesSettingsContract.CommandRow(
                        text(context, R.string.gboard_flag_store_replacement_title),
                        null,
                        true,
                        () -> GboardPatchesSettingsContract.openFeature(host,
                                new GboardFlagStoreSettingsFeature(context))));

        List<GboardPatchesSettingsContract.Section> sections =
                new ArrayList<GboardPatchesSettingsContract.Section>();
        sections.add(new GboardPatchesSettingsContract.Section(
                text(context, R.string.gboard_patches_backup_section_patches),
                null,
                GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                patchesRows));
        sections.add(new GboardPatchesSettingsContract.Section(
                text(context, R.string.gboard_patches_backup_section_flags),
                null,
                GboardPatchesSettingsContract.SectionStyle.ADVANCED,
                flagRows));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                "Gboard",
                entryTitle,
                null,
                Collections.emptyList(),
                sections);
    }

    private static void exportPatches(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            String json = GboardPatchesBackupManager.exportBackup(host.getContext());
            GboardPatchesSettingsContract.createTextDocument(host,
                    EXPORT_FILE_NAME, JSON_MIME_TYPE, json,
                    () -> GboardPatchesSettingsContract.showMessage(host, text(
                            host.getContext(), R.string.gboard_patches_backup_export_done)));
        } catch (Throwable failure) {
            GboardPatchesSettingsContract.showMessage(host,
                    text(host.getContext(), R.string.gboard_patches_backup_export_failed));
        }
    }

    private static void importPatches(GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.openTextDocument(host,
                new String[] {JSON_MIME_TYPE, "text/plain"},
                json -> {
                    try {
                        showModuleSelection(host, json,
                                GboardPatchesBackupManager.inspectBackup(json));
                    } catch (Throwable failure) {
                        GboardPatchesSettingsContract.showMessage(host, text(
                                host.getContext(),
                                R.string.gboard_patches_backup_restore_failed));
                    }
                });
    }

    private static void showModuleSelection(GboardPatchesSettingsContract.FeatureHost host,
            String json, GboardPatchesBackupManager.BackupPlan plan) {
        List<GboardPatchesBackupManager.ModulePlan> modules = plan.getModules();
        String[] labels = new String[modules.size()];
        String[] values = new String[modules.size()];
        boolean[] selected = new boolean[modules.size()];
        for (int index = 0; index < modules.size(); index++) {
            GboardPatchesBackupManager.ModulePlan module = modules.get(index);
            labels[index] = module.getModuleName()
                    + " (" + module.getKeys().size() + ")";
            values[index] = module.getModuleId();
            selected[index] = true;
        }
        GboardPatchesSettingsContract.showMultiChoiceDialog(host,
                text(host.getContext(), R.string.gboard_patches_backup_restore_modules_title),
                labels,
                values,
                selected,
                text(host.getContext(), R.string.gboard_patches_backup_restore_modules_action),
                chosen -> {
                    try {
                        Set<String> selectedModuleIds = new LinkedHashSet<>(chosen);
                        GboardPatchesBackupManager.RestorePreview preview =
                                GboardPatchesBackupManager.inspectRestore(
                                        host.getContext(), json, selectedModuleIds);
                        GboardPatchesSettingsContract.openFeature(host,
                                new GboardPatchesRestorePreviewFeature(
                                        host.getContext(), json, selectedModuleIds, preview));
                    } catch (Throwable failure) {
                        GboardPatchesSettingsContract.showMessage(host, text(
                                host.getContext(),
                                R.string.gboard_patches_backup_restore_failed));
                    }
                });
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
