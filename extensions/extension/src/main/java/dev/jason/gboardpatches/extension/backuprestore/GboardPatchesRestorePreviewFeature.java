package dev.jason.gboardpatches.extension.backuprestore;

import android.content.Context;
import android.icu.text.ListFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Pre-restore review for the selected Patches modules: an overview with one entry per module
 * that changes, each opening that module's key differences, and a pinned restore action.
 */
final class GboardPatchesRestorePreviewFeature
        implements GboardPatchesSettingsContract.Feature {
    private final Context context;
    private final String json;
    private final Set<String> selectedModuleIds;
    private GboardPatchesBackupManager.RestorePreview preview;

    GboardPatchesRestorePreviewFeature(Context context, String json,
            Set<String> selectedModuleIds,
            GboardPatchesBackupManager.RestorePreview preview) {
        this.context = context;
        this.json = json;
        this.selectedModuleIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(selectedModuleIds));
        this.preview = preview;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_backup_restore_preview_title);
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
                        text(R.string.gboard_patches_backup_restore_preview_overall),
                        text(R.string.gboard_patches_backup_restore_preview_summary,
                                preview.getAddedCount(), preview.getChangedCount(),
                                preview.getRemovedCount(), preview.getUnchangedCount()),
                        GboardPatchesSettingsContract.StatusTone.INFO));
        List<GboardPatchesSettingsContract.Row> changedRows = new ArrayList<>();
        List<String> unchangedNames = new ArrayList<>();
        for (GboardPatchesBackupManager.ModuleChange module : preview.getModules()) {
            Counts counts = Counts.of(module);
            if (counts.differences() == 0) {
                unchangedNames.add(GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()));
                continue;
            }
            changedRows.add(new GboardPatchesSettingsContract.NavigationRow(
                    GboardPatchesBackupModuleLabels.label(context, module.getModuleId(), module.getModuleName()),
                    counts.summary(this),
                    Integer.toString(counts.differences()),
                    true,
                    () -> GboardPatchesSettingsContract.openFeature(host,
                            new GboardPatchesRestorePreviewModuleFeature(context, module))));
        }
        List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
        if (!changedRows.isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_backup_restore_preview_changed_modules,
                            changedRows.size()),
                    text(R.string.gboard_patches_backup_restore_preview_changed_modules_hint),
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT,
                    changedRows));
        }
        if (!unchangedNames.isEmpty()) {
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_backup_restore_preview_unchanged_modules,
                            unchangedNames.size()),
                    null,
                    GboardPatchesSettingsContract.SectionStyle.ADVANCED,
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            joinNames(unchangedNames),
                            text(R.string.gboard_patches_backup_restore_preview_no_changes),
                            false))));
        }
        GboardPatchesSettingsContract.CommandRow action =
                new GboardPatchesSettingsContract.CommandRow(
                        preview.getDifferenceCount() > 0
                                ? text(R.string.gboard_patches_backup_restore_preview_action_count,
                                        preview.getDifferenceCount())
                                : text(R.string.gboard_patches_backup_restore_preview_no_changes),
                        null,
                        preview.getDifferenceCount() > 0,
                        () -> restore(host));
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(), "Gboard Patches", getEntryTitle(), null,
                status, sections,
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT,
                action);
    }

    private String joinNames(List<String> names) {
        try {
            return ListFormatter.getInstance(
                    Locale.forLanguageTag(GboardSettingsText.languageTag(context))).format(names);
        } catch (Throwable unavailable) {
            return String.join(", ", names);
        }
    }

    private void restore(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            GboardPatchesBackupManager.RestoreResult result =
                    GboardPatchesBackupManager.restoreBackup(
                            host.getContext(), json, selectedModuleIds);
            try {
                preview = GboardPatchesBackupManager.inspectRestore(
                        host.getContext(), json, selectedModuleIds);
            } catch (Throwable ignored) {
                // The restore result remains authoritative if refreshing the preview fails.
            }
            GboardPatchesSettingsContract.openFeature(host, new GboardPatchesRestoreResultFeature(host.getContext(), result));
        } catch (Throwable failure) {
            GboardPatchesSettingsContract.showMessage(host, text(R.string.gboard_patches_backup_restore_failed));
        }
    }

    private String text(int resourceId, Object... args) {
        return args.length == 0
                ? GboardSettingsText.get(context, resourceId)
                : GboardSettingsText.format(context, resourceId, args);
    }

    /** Per-module difference counts. */
    // A plain class, not a record: the patched APK's dex has no record support.
    static final class Counts {
        private final int added;
        private final int changed;
        private final int removed;

        Counts(int added, int changed, int removed) {
            this.added = added;
            this.changed = changed;
            this.removed = removed;
        }

        int added() {
            return added;
        }

        int changed() {
            return changed;
        }

        int removed() {
            return removed;
        }

        static Counts of(GboardPatchesBackupManager.ModuleChange module) {
            int added = 0;
            int changed = 0;
            int removed = 0;
            for (GboardPatchesBackupManager.KeyChange key : module.getKeys()) {
                switch (key.getStatus()) {
                    case ADDED -> added++;
                    case CHANGED -> changed++;
                    case REMOVED -> removed++;
                    case UNCHANGED -> { }
                }
            }
            return new Counts(added, changed, removed);
        }

        int differences() {
            return added + changed + removed;
        }

        /** Only the non-zero kinds, e.g. "Changed 5 · Added 2". */
        String summary(GboardPatchesRestorePreviewFeature feature) {
            return summary(feature.context, this);
        }

        static String summary(Context context, Counts counts) {
            List<String> parts = new ArrayList<>();
            if (counts.changed > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_preview_count_changed,
                        counts.changed));
            }
            if (counts.added > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_preview_count_added,
                        counts.added));
            }
            if (counts.removed > 0) {
                parts.add(GboardSettingsText.format(context,
                        R.string.gboard_patches_backup_restore_preview_count_removed,
                        counts.removed));
            }
            return String.join(" · ", parts);
        }
    }
}
