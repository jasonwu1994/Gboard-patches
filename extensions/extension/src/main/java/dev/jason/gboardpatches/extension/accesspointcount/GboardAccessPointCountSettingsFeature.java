package dev.jason.gboardpatches.extension.accesspointcount;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardAccessPointCountSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String PREVIEW_IMAGE_ENABLED =
            "settings-previews/accesspointcount/enabled.png";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String countTitle;
    private final String countSummary;
    private final String countDialogHint;
    private final String countDialogError;

    public GboardAccessPointCountSettingsFeature(Context context) {
        this(
                text(context, R.string.gboard_patches_access_point_count_title),
                text(context, R.string.gboard_patches_access_point_count_summary),
                text(context, R.string.gboard_patches_header_badge),
                text(context, R.string.gboard_patches_access_point_count_error_title),
                text(context, R.string.gboard_patches_access_point_count_error_summary),
                text(context, R.string.gboard_patches_access_point_count_section),
                text(context, R.string.gboard_patches_access_point_count_enabled_title),
                text(context, R.string.gboard_patches_access_point_count_enabled_summary),
                text(context, R.string.gboard_patches_access_point_count_value_title),
                text(context, R.string.gboard_patches_access_point_count_value_summary),
                text(context, R.string.gboard_patches_access_point_count_value_hint),
                text(context, R.string.gboard_patches_access_point_count_value_error));
    }

    GboardAccessPointCountSettingsFeature(String entryTitle, String entrySummary,
            String headerBadge, String errorTitle, String errorSummary, String sectionTitle,
            String enabledTitle, String enabledSummary, String countTitle, String countSummary,
            String countDialogHint, String countDialogError) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.errorTitle = errorTitle;
        this.errorSummary = errorSummary;
        this.sectionTitle = sectionTitle;
        this.enabledTitle = enabledTitle;
        this.enabledSummary = enabledSummary;
        this.countTitle = countTitle;
        this.countSummary = countSummary;
        this.countDialogHint = countDialogHint;
        this.countDialogError = countDialogError;
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
                GboardPatchesFeatureAvailability.FEATURE_ACCESS_POINT_COUNT);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            SharedPreferences preferences =
                    GboardPatchesSettings.preferences(host.getContext());
            GboardAccessPointCountSettings.ensureDefaults(preferences);
            return buildScreenForState(
                    host,
                    GboardAccessPointCountSettings.read(preferences),
                    value -> GboardAccessPointCountSettings.writeEnabled(preferences, value),
                    value -> GboardAccessPointCountSettings.writeCount(preferences, value));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render access point count settings", throwable);
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            GboardPatchesSettingsContract.FeatureHost host,
            GboardAccessPointCountSettings.Snapshot snapshot,
            GboardPatchesSettingsContract.ToggleAction toggleAction,
            CountWriter countWriter) {
        GboardAccessPointCountSettings.Snapshot safeSnapshot = snapshot == null
                ? new GboardAccessPointCountSettings.Snapshot(
                        GboardAccessPointCountSettings.DEFAULT_ENABLED,
                        GboardAccessPointCountSettings.DEFAULT_COUNT)
                : snapshot;
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionTitle,
                        Arrays.asList(
                                new GboardPatchesSettingsContract.ToggleRow(
                                        enabledTitle,
                                        enabledSummary,
                                        true,
                                        safeSnapshot.enabled,
                                        toggleAction,
                                        buildEnabledPreview())
                                        .setRestartRequired(
                                                GboardAccessPointCountSettings.PREF_KEY_ENABLED),
                                new GboardPatchesSettingsContract.SelectorRow(
                                        countTitle,
                                        countSummary,
                                        Integer.toString(safeSnapshot.count),
                                        true,
                                        () -> showCountDialog(
                                                host, safeSnapshot.count, countWriter))
                                        .setRestartRequired(
                                                GboardAccessPointCountSettings.PREF_KEY_COUNT)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    int validateCount(int value) {
        if (!GboardAccessPointCountSettings.isValidCount(value)) {
            throw new IllegalArgumentException(countDialogError);
        }
        return value;
    }

    private void showCountDialog(GboardPatchesSettingsContract.FeatureHost host,
            int currentCount, CountWriter countWriter) {
        if (host == null || host.getContext() == null || countWriter == null) {
            return;
        }
        GboardPatchesSettingsContract.showPositiveIntegerDialog(
                host,
                countTitle,
                countDialogHint,
                currentCount,
                value -> {
                    int validated = validateCount(value);
                    if (!countWriter.write(validated)) {
                        throw new IllegalStateException("Failed to save access point count");
                    }
                });
    }

    private GboardPatchesSettingsContract.PreviewSpec buildEnabledPreview() {
        return new GboardPatchesSettingsContract.PreviewSpec(
                enabledTitle,
                "",
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                new GboardPatchesSettingsContract.PreviewImage(
                        PREVIEW_IMAGE_ENABLED,
                        ""));
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle,
                        errorSummary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }

    @FunctionalInterface
    interface CountWriter {
        boolean write(int value);
    }
}
