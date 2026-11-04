package dev.jason.gboardpatches.extension.longpressquickactions;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardLongPressQuickActionsSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String PREVIEW_VIDEO_ENABLED_ASSET =
            "settings-previews/keyboard/"
                    + "gboard_long_press_quick_actions_enabled_preview.mp4";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String globeDragTitle;
    private final String globeDragSummary;
    private final String selectAllTitle;
    private final String selectAllSummary;
    private final String featureSectionTitle;
    private final String mappingsSectionTitle;
    private final String[] actionLabels;

    public GboardLongPressQuickActionsSettingsFeature(Context context) {
        this(
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_error_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_error_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_enabled_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_enabled_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_globe_drag_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_globe_drag_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_select_all_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_select_all_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_section_feature),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_section_mappings),
                new String[] {
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_select_all),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_undo),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_copy),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_cut),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_paste),
                        GboardSettingsText.get(context,
                                R.string.gboard_patches_long_press_quick_actions_action_redo)
                });
    }

    GboardLongPressQuickActionsSettingsFeature(String entryTitle, String entrySummary,
            String headerBadge, String errorTitle, String errorSummary, String enabledTitle,
            String enabledSummary, String globeDragTitle, String globeDragSummary,
            String selectAllTitle, String selectAllSummary,
            String featureSectionTitle, String mappingsSectionTitle, String[] actionLabels) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.errorTitle = errorTitle;
        this.errorSummary = errorSummary;
        this.enabledTitle = enabledTitle;
        this.enabledSummary = enabledSummary;
        this.globeDragTitle = globeDragTitle;
        this.globeDragSummary = globeDragSummary;
        this.selectAllTitle = selectAllTitle;
        this.selectAllSummary = selectAllSummary;
        this.featureSectionTitle = featureSectionTitle;
        this.mappingsSectionTitle = mappingsSectionTitle;
        this.actionLabels = actionLabels == null ? new String[0] : actionLabels.clone();
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
                GboardPatchesFeatureAvailability.FEATURE_LONG_PRESS_QUICK_ACTIONS);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            GboardLongPressQuickActionsSettings.ensureDefault(preferences);
            boolean enabled = GboardLongPressQuickActionsSettings.readEnabled(preferences);
            boolean globeDragEnabled =
                    GboardLongPressQuickActionsSettings.readGlobeDragEnabled(preferences);
            boolean selectAllWhenUnselected =
                    GboardLongPressQuickActionsSettings.readSelectAllWhenUnselected(preferences);
            return buildScreenForState(
                    enabled,
                    globeDragEnabled,
                    selectAllWhenUnselected,
                    value -> GboardLongPressQuickActionsSettings.writeEnabled(context, value),
                    value -> GboardLongPressQuickActionsSettings.writeGlobeDragEnabled(
                            context, value),
                    value -> GboardLongPressQuickActionsSettings.writeSelectAllWhenUnselected(
                            context, value));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render long-press quick action settings", throwable);
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(boolean enabled,
            boolean globeDragEnabled,
            boolean selectAllWhenUnselected,
            GboardPatchesSettingsContract.ToggleAction toggleAction,
            GboardPatchesSettingsContract.ToggleAction globeDragToggleAction,
            GboardPatchesSettingsContract.ToggleAction selectAllToggleAction) {
        String[] labels = normalizedActionLabels();
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.emptyList(),
                Arrays.asList(
                        new GboardPatchesSettingsContract.Section(
                                featureSectionTitle,
                                Arrays.asList(
                                        new GboardPatchesSettingsContract.ToggleRow(
                                                enabledTitle,
                                                enabledSummary,
                                                true,
                                                enabled,
                                                toggleAction,
                                                buildEnabledPreview()),
                                        new GboardPatchesSettingsContract.SwitchRow(
                                                globeDragTitle,
                                                globeDragSummary,
                                                enabled,
                                                globeDragEnabled,
                                                globeDragToggleAction),
                                        new GboardPatchesSettingsContract.SwitchRow(
                                                selectAllTitle,
                                                selectAllSummary,
                                                enabled,
                                                selectAllWhenUnselected,
                                                selectAllToggleAction))),
                        new GboardPatchesSettingsContract.Section(
                                mappingsSectionTitle,
                                Arrays.asList(
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "A", labels[0], true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "Z", labels[1], true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "C", labels[2], true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "X", labels[3], true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "V", labels[4], true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                "Y", labels[5], true)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private GboardPatchesSettingsContract.PreviewSpec buildEnabledPreview() {
        return new GboardPatchesSettingsContract.PreviewSpec(
                entryTitle,
                "",
                GboardPatchesSettingsContract.PreviewLayout.STACKED,
                GboardPatchesSettingsContract.PreviewVideo.fromAsset(
                        PREVIEW_VIDEO_ENABLED_ASSET,
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

    private String[] normalizedActionLabels() {
        String[] labels = new String[6];
        for (int index = 0; index < labels.length; index++) {
            labels[index] = index < actionLabels.length && actionLabels[index] != null
                    ? actionLabels[index]
                    : "";
        }
        return labels;
    }
}
