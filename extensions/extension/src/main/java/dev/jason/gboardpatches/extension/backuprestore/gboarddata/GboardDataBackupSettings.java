package dev.jason.gboardpatches.extension.backuprestore.gboarddata;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Export and import rows for Gboard's personal data on the Backup &amp; Restore page. */
public final class GboardDataBackupSettings {
    private static final int MAX_IMPORT_BYTES = 192 * 1024 * 1024;
    private static final String CLIPBOARD_PROVIDER = "clipboard";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Progress toasts only appear when a step is slow enough to look stuck. */
    private static final long PROGRESS_TOAST_DELAY_MS = 700L;
    /**
     * Fixed display order: typing data first, then expressions, look, and the rest. Providers a
     * future Gboard adds without a known id follow in the order Gboard registers them.
     */
    private static final List<String> DISPLAY_ORDER = Arrays.asList(
            "delight5_user_history", "hmm_user_history", "mozc", "delight5_blocklist",
            "personal_dictionary", "expression_history", "image-file-backup",
            "customized_theme", CLIPBOARD_PROVIDER, GboardDataBackupArchive.SETTINGS_ID,
            "usage_store");

    private GboardDataBackupSettings() {
    }

    public static GboardPatchesSettingsContract.Section section(
            GboardPatchesSettingsContract.FeatureHost host, GboardDataBackupGateway gateway) {
        Context context = host.getContext();
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        rows.add(new GboardPatchesSettingsContract.CommandRow(
                text(context, R.string.gboard_data_backup_export_title),
                text(context, R.string.gboard_data_backup_export_summary),
                gateway != null,
                () -> startExport(host, gateway)));
        rows.add(new GboardPatchesSettingsContract.CommandRow(
                text(context, R.string.gboard_data_backup_import_title),
                text(context, R.string.gboard_data_backup_import_summary),
                gateway != null,
                () -> startImport(host, gateway)));
        rows.add(new GboardPatchesSettingsContract.InfoRow(
                text(context, R.string.gboard_data_backup_notes_title),
                text(context, R.string.gboard_data_backup_notes_summary),
                true));
        return new GboardPatchesSettingsContract.Section(
                text(context, R.string.gboard_data_backup_section_title),
                text(context, R.string.gboard_data_backup_section_description),
                GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                rows);
    }

    static void startExport(GboardPatchesSettingsContract.FeatureHost host,
            GboardDataBackupGateway gateway) {
        Context context = host.getContext();
        Runnable progress = delayedMessage(host,
                text(context, R.string.gboard_data_backup_reading));
        gateway.inspect(context, (inspection, error) -> {
            MAIN.removeCallbacks(progress);
            if (inspection == null) {
                GboardPatchesSettingsContract.showMessage(host,
                        withDetail(text(context, R.string.gboard_data_backup_unavailable), error));
                return;
            }
            List<String> values = new ArrayList<>();
            for (GboardDataBackupResults.Provider provider : inspection.getProviders()) {
                values.add(provider.getId());
            }
            values.add(GboardDataBackupArchive.SETTINGS_ID);
            sortForDisplay(values);
            String[] labels = new String[values.size()];
            boolean[] selected = new boolean[values.size()];
            for (int index = 0; index < labels.length; index++) {
                String id = values.get(index);
                labels[index] = providerLabel(context, id, categoryOf(inspection, id));
                selected[index] = !CLIPBOARD_PROVIDER.equals(id);
            }
            GboardPatchesSettingsContract.showMultiChoiceDialog(host,
                    text(context, R.string.gboard_data_backup_export_select_title),
                    labels, values.toArray(new String[0]), selected,
                    text(context, R.string.gboard_data_backup_export_action),
                    chosen -> export(host, gateway, chosen));
        });
    }

    private static String categoryOf(GboardDataBackupResults.Inspection inspection, String id) {
        if (GboardDataBackupArchive.SETTINGS_ID.equals(id)) {
            return GboardDataBackupArchive.SETTINGS_CATEGORY;
        }
        for (GboardDataBackupResults.Provider provider : inspection.getProviders()) {
            if (provider.getId().equals(id)) {
                return provider.getCategory();
            }
        }
        return null;
    }

    private static void export(GboardPatchesSettingsContract.FeatureHost host,
            GboardDataBackupGateway gateway, List<String> chosen) {
        Context context = host.getContext();
        if (chosen == null || chosen.isEmpty()) {
            return;
        }
        Runnable progress = delayedMessage(host,
                text(context, R.string.gboard_data_backup_working));
        gateway.export(context, new LinkedHashSet<>(chosen), (archive, error) -> {
            MAIN.removeCallbacks(progress);
            if (archive == null || archive.getData() == null) {
                GboardPatchesSettingsContract.showMessage(host, withDetail(
                        text(context, R.string.gboard_data_backup_export_failed), error));
                return;
            }
            String done = archive.getFailures().isEmpty()
                    ? format(context, R.string.gboard_data_backup_export_done,
                            archive.getEntryCount())
                    : format(context, R.string.gboard_data_backup_export_partial,
                            archive.getEntryCount(), archive.getFailures().size());
            GboardPatchesSettingsContract.createBinaryDocument(host,
                    GboardDataBackupArchive.CANONICAL_FILE_NAME,
                    GboardDataBackupArchive.MIME_TYPE, archive.getData(),
                    () -> GboardPatchesSettingsContract.showMessage(host, done));
        });
    }

    private static void startImport(GboardPatchesSettingsContract.FeatureHost host,
            GboardDataBackupGateway gateway) {
        GboardPatchesSettingsContract.openBinaryDocument(host, new String[] {
                GboardDataBackupArchive.MIME_TYPE, "application/x-zip-compressed",
                "application/octet-stream"}, MAX_IMPORT_BYTES, document -> {
            byte[] data = document.getData();
            GboardDataBackupArchive.Manifest manifest;
            try {
                manifest = GboardDataBackupArchive.readManifest(data);
            } catch (Throwable invalid) {
                MAIN.post(() -> GboardPatchesSettingsContract.showMessage(host,
                        text(host.getContext(), R.string.gboard_data_backup_import_invalid)));
                return;
            }
            MAIN.post(() -> chooseImport(host, gateway, data, manifest));
        });
    }

    private static void chooseImport(GboardPatchesSettingsContract.FeatureHost host,
            GboardDataBackupGateway gateway, byte[] data,
            GboardDataBackupArchive.Manifest manifest) {
        Context context = host.getContext();
        List<String> values = importableIds(manifest);
        if (values.isEmpty()) {
            GboardPatchesSettingsContract.showMessage(host,
                    text(context, R.string.gboard_data_backup_import_empty));
            return;
        }
        String[] labels = new String[values.size()];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = labelFor(context, manifest, values.get(index));
        }
        boolean[] selected = new boolean[values.size()];
        Arrays.fill(selected, true);
        GboardPatchesSettingsContract.showMultiChoiceDialog(host,
                format(context, R.string.gboard_data_backup_import_select_title,
                        manifest.getGboardVersionName()),
                labels, values.toArray(new String[0]), selected,
                text(context, R.string.gboard_data_backup_import_next),
                chosen -> {
                    if (chosen == null || chosen.isEmpty()) {
                        return;
                    }
                    GboardPatchesSettingsContract.openFeature(host,
                            new GboardDataRestoreConfirmFeature(context, gateway, data,
                                    manifest, new LinkedHashSet<>(chosen)));
                });
    }

    /** Ids the archive holds data for, in display order. */
    static List<String> importableIds(GboardDataBackupArchive.Manifest manifest) {
        List<String> values = new ArrayList<>();
        for (GboardDataBackupArchive.ProviderEntry provider : manifest.getProviders()) {
            if (!provider.getKeys().isEmpty()) {
                values.add(provider.getId());
            }
        }
        if (manifest.hasSettings()) {
            values.add(GboardDataBackupArchive.SETTINGS_ID);
        }
        sortForDisplay(values);
        return values;
    }

    static String labelFor(Context context, GboardDataBackupArchive.Manifest manifest,
            String id) {
        if (GboardDataBackupArchive.SETTINGS_ID.equals(id)) {
            return providerLabel(context, id, GboardDataBackupArchive.SETTINGS_CATEGORY);
        }
        GboardDataBackupArchive.ProviderEntry provider = manifest.provider(id);
        return providerLabel(context, id, provider == null ? null : provider.getCategory());
    }

    /** Runs the restore and opens its report; shared by the confirmation page. */
    static void restore(GboardPatchesSettingsContract.FeatureHost host,
            GboardDataBackupGateway gateway, byte[] data,
            GboardDataBackupArchive.Manifest manifest, Set<String> selected,
            Runnable onFinished) {
        Context context = host.getContext();
        Runnable progress = delayedMessage(host,
                text(context, R.string.gboard_data_backup_working));
        gateway.restore(context, data, selected, (result, error) -> {
            MAIN.removeCallbacks(progress);
            if (onFinished != null) {
                onFinished.run();
            }
            if (result == null) {
                String message = error != null
                        && error.contains(GboardDataBackupResults.ERROR_NEWER_GBOARD)
                        ? format(context, R.string.gboard_data_backup_import_newer,
                                manifest.getGboardVersionName())
                        : withDetail(text(context, R.string.gboard_data_backup_import_failed),
                                error);
                GboardPatchesSettingsContract.showMessage(host, message);
                return;
            }
            GboardPatchesSettingsContract.openFeature(host,
                    new GboardDataRestoreResultFeature(context, result));
        });
    }

    static void sortForDisplay(List<String> ids) {
        ids.sort(Comparator.comparingInt(id -> {
            int index = DISPLAY_ORDER.indexOf(id);
            return index < 0 ? DISPLAY_ORDER.size() : index;
        }));
    }

    private static Runnable delayedMessage(GboardPatchesSettingsContract.FeatureHost host,
            String message) {
        Runnable show = () -> GboardPatchesSettingsContract.showMessage(host, message);
        MAIN.postDelayed(show, PROGRESS_TOAST_DELAY_MS);
        return show;
    }

    static String providerLabel(Context context, String id, String category) {
        int resource = switch (id) {
            case "delight5_user_history" -> R.string.gboard_data_backup_provider_latin_history;
            case "hmm_user_history" -> R.string.gboard_data_backup_provider_chinese_history;
            case "mozc" -> R.string.gboard_data_backup_provider_japanese_history;
            case "delight5_blocklist" -> R.string.gboard_data_backup_provider_blocklist;
            case "personal_dictionary" -> R.string.gboard_data_backup_provider_personal_dictionary;
            case "expression_history" -> R.string.gboard_data_backup_provider_expression_history;
            case "image-file-backup" -> R.string.gboard_data_backup_provider_expression_images;
            case "customized_theme" -> R.string.gboard_data_backup_provider_themes;
            case "clipboard" -> R.string.gboard_data_backup_provider_clipboard;
            case "usage_store" -> R.string.gboard_data_backup_provider_usage;
            case GboardDataBackupArchive.SETTINGS_ID ->
                    R.string.gboard_data_backup_provider_settings;
            default -> 0;
        };
        if (resource != 0) {
            return text(context, resource);
        }
        return category == null || category.isEmpty() ? id : id + " (" + category + ")";
    }

    private static String withDetail(String message, String detail) {
        return detail == null || detail.isBlank() ? message : message + "\n" + detail;
    }

    static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }

    static String format(Context context, int resourceId, Object... args) {
        return GboardSettingsText.format(context, resourceId, args);
    }
}
