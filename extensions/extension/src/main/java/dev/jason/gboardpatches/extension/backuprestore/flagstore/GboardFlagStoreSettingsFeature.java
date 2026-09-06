package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.backuprestore.GboardExportFileNames;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Settings surface for byte-exact Gboard flag-store replacement. */
public final class GboardFlagStoreSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private final String entryTitle;

    public GboardFlagStoreSettingsFeature(Context context) {
        entryTitle = text(context, R.string.gboard_flag_store_replacement_title);
    }

    @Override
    public String getEntryTitle() { return entryTitle; }

    @Override
    public String getEntrySummary() { return null; }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context,
                GboardPatchesFeatureAvailability.FEATURE_BACKUP_RESTORE);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost featureHost) {
        GboardFlagStoreManager.Inspection inspection =
                GboardFlagStoreManager.inspect(featureHost.getContext());
        GboardFlagStorageStatus status = inspection.getStatus();
        Context context = featureHost.getContext();
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        text(context, R.string.gboard_patches_backup_flag_backend_title),
                        backendSummary(context, status), backendTone(status)));

        List<GboardPatchesSettingsContract.Row> exportRows = new ArrayList<>();
        exportRows.add(exportRow(featureHost, GboardFlagStoreFiles.PROTO_FILE,
                status.isProtoStorePresent()));
        exportRows.add(exportRow(featureHost, GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE,
                status.isLegacyOverridePresent()));
        exportRows.add(exportRow(featureHost, GboardFlagStoreFiles.LEGACY_VALUE_FILE,
                status.isLegacyValuePresent()));

        List<GboardPatchesSettingsContract.Row> importRows = new ArrayList<>();
        importRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(context, R.string.gboard_flag_store_replacement_choose_title),
                null,
                canStageReplacement(status),
                () -> chooseReplacement(featureHost)));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle, "Gboard Patches", entryTitle, null, statusBlocks,
                List.of(
                        new GboardPatchesSettingsContract.Section(
                                text(context,
                                        R.string.gboard_flag_store_replacement_export_section),
                                null,
                                GboardPatchesSettingsContract.SectionStyle.DEFAULT, exportRows),
                        new GboardPatchesSettingsContract.Section(
                                text(context,
                                        R.string.gboard_flag_store_replacement_import_section),
                                null,
                                GboardPatchesSettingsContract.SectionStyle.ADVANCED, importRows)));
    }

    private GboardPatchesSettingsContract.Row exportRow(
            GboardPatchesSettingsContract.FeatureHost host, String fileName, boolean enabled) {
        return new GboardPatchesSettingsContract.CommandRow(
                text(host.getContext(), R.string.gboard_flag_store_replacement_export_file,
                        fileName),
                null,
                enabled,
                () -> exportFile(host, fileName));
    }

    private void exportFile(GboardPatchesSettingsContract.FeatureHost host, String fileName) {
        try {
            byte[] data = GboardFlagStoreManager.export(host.getContext(), fileName);
            GboardPatchesSettingsContract.createBinaryDocument(host,
                    fileName,
                    mimeType(fileName),
                    data,
                    () -> GboardPatchesSettingsContract.showMessage(host,
                            text(host.getContext(),
                                    R.string.gboard_flag_store_replacement_export_done)));
        } catch (Throwable failure) {
            GboardPatchesSettingsContract.showMessage(host,
                    text(host.getContext(), R.string.gboard_flag_store_replacement_export_failed));
        }
    }

    private void chooseReplacement(GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.openBinaryDocument(host, new String[] {
                "application/octet-stream", "application/xml", "text/xml"}, document -> {
            String name = canonicalFileName(document.getDisplayName());
            if (name == null) {
                GboardPatchesSettingsContract.showMessage(host,
                        text(host.getContext(),
                                R.string.gboard_flag_store_replacement_invalid_name));
                return;
            }
            try {
                byte[] current = GboardFlagStoreManager.read(host.getContext(), name);
                GboardFlagStoreDiff.Result diff = GboardFlagStoreDiff.compare(
                        name, current, document.getData());
                GboardPatchesSettingsContract.openFeature(host,
                        new GboardFlagStoreDiffSettingsFeature(
                                host.getContext(), name, document.getData(), diff));
            } catch (Throwable failure) {
                GboardPatchesSettingsContract.showMessage(host,
                        text(host.getContext(), R.string.gboard_flag_store_diff_decode_failed));
            }
        });
    }

    static String canonicalFileName(String displayName) {
        return GboardExportFileNames.canonicalImportedName(displayName,
                GboardFlagStoreFiles.PROTO_FILE,
                GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE,
                GboardFlagStoreFiles.LEGACY_VALUE_FILE);
    }

    private static String backendSummary(Context context, GboardFlagStorageStatus status) {
        String summary = switch (status.getBackend()) {
            case LOADING -> text(context, R.string.gboard_patches_backup_flag_backend_loading);
            case JETPACK_PROTO -> text(context,
                    R.string.gboard_patches_backup_flag_backend_proto);
            case JETPACK_PROTO_WITH_LEGACY_FILES -> text(context,
                    R.string.gboard_patches_backup_flag_backend_proto_with_legacy);
            case LEGACY_SHARED_PREFERENCES -> text(context,
                    R.string.gboard_patches_backup_flag_backend_legacy);
            case NOT_CREATED -> text(context,
                    R.string.gboard_patches_backup_flag_backend_not_created);
            case UNAVAILABLE -> text(context,
                    R.string.gboard_patches_backup_flag_backend_unavailable);
        };
        List<String> lastModified = new ArrayList<>();
        if (status.isProtoStorePresent()) {
            lastModified.add(text(context,
                    R.string.gboard_patches_backup_flag_backend_proto_last_modified,
                    formatLastModified(context, status.getProtoStoreLastModified())));
        }
        if (status.isLegacyOverridePresent()) {
            lastModified.add(text(context,
                    R.string.gboard_patches_backup_flag_backend_override_last_modified,
                    formatLastModified(context, status.getLegacyOverrideLastModified())));
        }
        if (status.isLegacyValuePresent()) {
            lastModified.add(text(context,
                    R.string.gboard_patches_backup_flag_backend_value_last_modified,
                    formatLastModified(context, status.getLegacyValueLastModified())));
        }
        return lastModified.isEmpty() ? summary
                : summary + "\n\n" + String.join("\n", lastModified);
    }

    private static String formatLastModified(Context context, long lastModified) {
        if (lastModified <= 0L) {
            return text(context, R.string.gboard_patches_backup_flag_backend_time_unknown);
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(lastModified));
    }

    private static GboardPatchesSettingsContract.StatusTone backendTone(
            GboardFlagStorageStatus status) {
        return switch (status.getBackend()) {
            case JETPACK_PROTO, LEGACY_SHARED_PREFERENCES ->
                    GboardPatchesSettingsContract.StatusTone.INFO;
            case LOADING -> GboardPatchesSettingsContract.StatusTone.NEUTRAL;
            default -> GboardPatchesSettingsContract.StatusTone.WARNING;
        };
    }

    private static boolean canStageReplacement(GboardFlagStorageStatus status) {
        return switch (status.getBackend()) {
            case JETPACK_PROTO, JETPACK_PROTO_WITH_LEGACY_FILES,
                    LEGACY_SHARED_PREFERENCES, NOT_CREATED -> true;
            case LOADING, UNAVAILABLE -> false;
        };
    }

    private static String mimeType(String fileName) {
        return fileName.endsWith(".xml") ? "application/xml" : "application/octet-stream";
    }

    private static String text(Context context, int resourceId, Object... args) {
        String value = GboardSettingsText.get(context, resourceId);
        return args.length == 0 ? value : String.format(value, args);
    }
}
