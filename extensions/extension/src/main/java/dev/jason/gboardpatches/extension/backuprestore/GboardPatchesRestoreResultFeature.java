package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Post-restore report: the overall outcome and key totals first, then only the keys that need
 * attention, then one entry per module that opens its full key list.
 */
final class GboardPatchesRestoreResultFeature
        implements GboardPatchesSettingsContract.Feature {
    /** Keeps the overview short when a whole module fails; the rest stay in that module's page. */
    private static final int MAX_ATTENTION_ROWS = 30;

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
        Counts total = new Counts();
        List<GboardPatchesSettingsContract.Row> attentionRows = new ArrayList<>();
        List<GboardPatchesSettingsContract.Row> moduleRows = new ArrayList<>();
        for (GboardPatchesBackupManager.ModuleResult module : result.getModules()) {
            Counts counts = Counts.of(module);
            total.add(counts);
            boolean moduleNeedsAttention =
                    module.getStatus() != GboardPatchesBackupManager.ModuleStatus.SUCCESS;
            for (GboardPatchesBackupManager.KeyResult key : module.getKeys()) {
                if (needsAttention(key.getStatus())) {
                    attentionRows.add(new GboardPatchesSettingsContract.InfoRow(
                            key.getKey(),
                            GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()) + " · " + keyStatus(key.getStatus()),
                            true));
                }
            }
            if (moduleNeedsAttention && counts.attention == 0) {
                attentionRows.add(new GboardPatchesSettingsContract.InfoRow(
                        GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()), moduleSummary(module), true));
            }
            if (module.getKeys().isEmpty()) {
                moduleRows.add(new GboardPatchesSettingsContract.InfoRow(
                        GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()),
                        moduleStatus(module.getStatus()) + " · "
                                + text(R.string.gboard_patches_backup_restore_result_no_keys),
                        true));
                continue;
            }
            moduleRows.add(new GboardPatchesSettingsContract.NavigationRow(
                    GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()),
                    moduleNeedsAttention
                            ? moduleStatus(module.getStatus()) + " · " + counts.summary(context)
                            : counts.summary(context),
                    counts.attention > 0
                            ? text(R.string.gboard_patches_backup_restore_result_attention_count,
                                    counts.attention)
                            : null,
                    true,
                    () -> GboardPatchesSettingsContract.openFeature(host,
                            new GboardPatchesRestoreResultModuleFeature(context, module))));
        }

        GboardPatchesSettingsContract.StatusTone tone = result.isSuccess() && attentionRows.isEmpty()
                ? GboardPatchesSettingsContract.StatusTone.INFO
                : GboardPatchesSettingsContract.StatusTone.WARNING;
        String totals = total.summary(context);
        List<GboardPatchesSettingsContract.StatusBlock> status = Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_backup_restore_result_overall),
                        totals.isEmpty() ? overallSummary() : overallSummary() + "\n" + totals,
                        tone));
        int attentionCount = attentionRows.size();
        if (attentionCount > MAX_ATTENTION_ROWS) {
            attentionRows = new ArrayList<>(attentionRows.subList(0, MAX_ATTENTION_ROWS));
            attentionRows.add(new GboardPatchesSettingsContract.InfoRow(
                    text(R.string.gboard_patches_backup_restore_result_attention_more,
                            attentionCount - MAX_ATTENTION_ROWS),
                    null,
                    false));
        }
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        if (attentionRows.isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_backup_restore_result_attention_title),
                    null,
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            text(R.string.gboard_patches_backup_restore_result_attention_none),
                            null,
                            true))));
        } else {
            sections.add(new GboardPatchesSettingsContract.Section(
                    GboardRestoreKeyFilter.groupTitle(context,
                            text(R.string.gboard_patches_backup_restore_result_attention_title),
                            attentionCount),
                    text(R.string.gboard_patches_backup_restore_result_attention_hint),
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    attentionRows));
        }
        if (!moduleRows.isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    GboardRestoreKeyFilter.groupTitle(context,
                            text(R.string.gboard_patches_backup_restore_result_modules_title),
                            moduleRows.size()),
                    null,
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    moduleRows));
        }
        GboardPatchesSettingsContract.CommandRow restart = result.getRestoredStoreCount() > 0
                ? new GboardPatchesSettingsContract.CommandRow(
                        text(R.string.gboard_patches_restart_action),
                        null,
                        true,
                        () -> GboardPatchesSettingsContract.requestTargetRestart(host))
                : null;
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard Patches", getEntryTitle(), null,
                status, sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                restart);
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

    private String moduleSummary(GboardPatchesBackupManager.ModuleResult module) {
        return moduleStatus(module.getStatus())
                + (module.getMessage() == null ? "" : " · " + module.getMessage());
    }

    static boolean needsAttention(GboardPatchesBackupManager.KeyStatus status) {
        return status == GboardPatchesBackupManager.KeyStatus.FAILED
                || status == GboardPatchesBackupManager.KeyStatus.PREVIOUS_VALUE_RESTORED;
    }

    private String moduleStatus(GboardPatchesBackupManager.ModuleStatus status) {
        return moduleStatus(context, status);
    }

    static String moduleStatus(Context context, GboardPatchesBackupManager.ModuleStatus status) {
        return GboardSettingsText.get(context, switch (status) {
            case SUCCESS -> R.string.gboard_patches_backup_status_success;
            case PARTIAL -> R.string.gboard_patches_backup_status_partial;
            case FAILED -> R.string.gboard_patches_backup_status_failed;
            case PREVIOUS_VALUES_RESTORED ->
                    R.string.gboard_patches_backup_status_previous_values_restored;
        });
    }

    private String keyStatus(GboardPatchesBackupManager.KeyStatus status) {
        return keyStatus(context, status);
    }

    static String keyStatus(Context context, GboardPatchesBackupManager.KeyStatus status) {
        return GboardSettingsText.get(context, switch (status) {
            case RESTORED -> R.string.gboard_patches_backup_key_restored;
            case REMOVED -> R.string.gboard_patches_backup_key_removed;
            case FAILED -> R.string.gboard_patches_backup_key_failed;
            case PREVIOUS_VALUE_RESTORED -> R.string.gboard_patches_backup_key_previous_value_restored;
        });
    }

    private String text(int resourceId, Object... args) {
        return args.length == 0
                ? GboardSettingsText.get(context, resourceId)
                : GboardSettingsText.format(context, resourceId, args);
    }

    /** Key totals by outcome: matched the backup, removed, or needing attention. */
    static final class Counts {
        int matched;
        int removed;
        int attention;

        static Counts of(GboardPatchesBackupManager.ModuleResult module) {
            Counts counts = new Counts();
            for (GboardPatchesBackupManager.KeyResult key : module.getKeys()) {
                switch (key.getStatus()) {
                    case RESTORED -> counts.matched++;
                    case REMOVED -> counts.removed++;
                    case FAILED, PREVIOUS_VALUE_RESTORED -> counts.attention++;
                }
            }
            return counts;
        }

        void add(Counts other) {
            matched += other.matched;
            removed += other.removed;
            attention += other.attention;
        }

        /** Only the non-zero outcomes, e.g. "Matched 312 · Removed 3". */
        String summary(Context context) {
            List<String> parts = new ArrayList<>();
            if (matched > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_result_count_matched, matched));
            }
            if (removed > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_result_count_removed, removed));
            }
            if (attention > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_result_count_attention, attention));
            }
            return String.join(" · ", parts);
        }
    }
}
