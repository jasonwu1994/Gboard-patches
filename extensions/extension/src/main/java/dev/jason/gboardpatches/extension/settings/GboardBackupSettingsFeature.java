package dev.jason.gboardpatches.extension.settings;

import android.content.Context;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;

public final class GboardBackupSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String EXPORT_FILE_NAME = "gboard_patches_backup.json";
    private static final String EXPORT_MIME_TYPE = "application/json";
    private static final String[] IMPORT_MIME_TYPES = {"application/json", "text/plain", "*/*"};

    private final String entryTitle;
    private final String entrySummary;
    private final String exportTitle;
    private final String exportSummary;
    private final String importTitle;
    private final String importSummary;
    private final String exportDoneMessage;
    private final String importDoneMessage;
    private final String importFailedMessage;

    public GboardBackupSettingsFeature(Context context) {
        entryTitle = GboardSettingsText.get(context, R.string.gboard_patches_backup_title);
        entrySummary = GboardSettingsText.get(context, R.string.gboard_patches_backup_summary);
        exportTitle = GboardSettingsText.get(context, R.string.gboard_patches_backup_export_title);
        exportSummary = GboardSettingsText.get(context, R.string.gboard_patches_backup_export_summary);
        importTitle = GboardSettingsText.get(context, R.string.gboard_patches_backup_import_title);
        importSummary = GboardSettingsText.get(context, R.string.gboard_patches_backup_import_summary);
        exportDoneMessage = GboardSettingsText.get(context, R.string.gboard_patches_backup_export_done);
        importDoneMessage = GboardSettingsText.get(context, R.string.gboard_patches_backup_import_done);
        importFailedMessage = GboardSettingsText.get(context, R.string.gboard_patches_backup_import_failed);
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
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                "Gboard",
                entryTitle,
                entrySummary,
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        null,
                        Arrays.asList(
                                new GboardPatchesSettingsContract.CommandRow(
                                        exportTitle,
                                        exportSummary,
                                        true,
                                        () -> exportSettings(host)),
                                new GboardPatchesSettingsContract.CommandRow(
                                        importTitle,
                                        importSummary,
                                        true,
                                        () -> importSettings(host))
                        ))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private void exportSettings(GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null || host.getContext() == null) {
            return;
        }
        Context context = host.getContext();
        String json = GboardBackupSettings.exportSettings(context);
        GboardPatchesSettingsContract.createTextDocument(host,
                EXPORT_FILE_NAME,
                EXPORT_MIME_TYPE,
                json,
                () -> Toast.makeText(context, exportDoneMessage, Toast.LENGTH_SHORT).show());
    }

    private void importSettings(GboardPatchesSettingsContract.FeatureHost host) {
        if (host == null || host.getContext() == null) {
            return;
        }
        Context context = host.getContext();
        GboardPatchesSettingsContract.openTextDocument(host,
                IMPORT_MIME_TYPES,
                json -> {
                    if (GboardBackupSettings.importSettings(context, json)) {
                        Toast.makeText(context, importDoneMessage, Toast.LENGTH_LONG).show();
                        GboardPatchesSettingsContract.refresh(host);
                    } else {
                        Toast.makeText(context, importFailedMessage, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
