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
    private final String featureSectionTitle;
    private final String mappingsSectionTitle;
    private final String positionTitle;
    private final String positionSummary;
    private final String positionFirstLabel;
    private final String positionLastLabel;
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
                        R.string.gboard_patches_long_press_quick_actions_section_feature),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_section_mappings),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_setting_position),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_setting_position_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_position_first),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_long_press_quick_actions_position_last),
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
            String featureSectionTitle, String mappingsSectionTitle, String positionTitle,
            String positionSummary, String positionFirstLabel, String positionLastLabel,
            String[] actionLabels) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.errorTitle = errorTitle;
        this.errorSummary = errorSummary;
        this.enabledTitle = enabledTitle;
        this.enabledSummary = enabledSummary;
        this.globeDragTitle = globeDragTitle;
        this.globeDragSummary = globeDragSummary;
        this.featureSectionTitle = featureSectionTitle;
        this.mappingsSectionTitle = mappingsSectionTitle;
        this.positionTitle = positionTitle;
        this.positionSummary = positionSummary;
        this.positionFirstLabel = positionFirstLabel;
        this.positionLastLabel = positionLastLabel;
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
            int position = GboardLongPressQuickActionsSettings.readPosition(preferences);
            return buildScreen(
                    enabled,
                    globeDragEnabled,
                    position,
                    value -> GboardLongPressQuickActionsSettings.writeEnabled(context, value),
                    value -> GboardLongPressQuickActionsSettings.writeGlobeDragEnabled(
                            context, value),
                    host,
                    preferences);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render long-press quick action settings", throwable);
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(boolean enabled,
            boolean globeDragEnabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction,
            GboardPatchesSettingsContract.ToggleAction globeDragToggleAction) {
        return buildScreen(enabled, globeDragEnabled,
                GboardLongPressQuickActionsSettings.DEFAULT_POSITION,
                toggleAction, globeDragToggleAction, null, null);
    }

    private GboardPatchesSettingsContract.Screen buildScreen(boolean enabled,
            boolean globeDragEnabled,
            int position,
            GboardPatchesSettingsContract.ToggleAction toggleAction,
            GboardPatchesSettingsContract.ToggleAction globeDragToggleAction,
            GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences) {
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
                                        new GboardPatchesSettingsContract.SelectorRow(
                                                positionTitle,
                                                positionSummary,
                                                positionLabel(position),
                                                enabled && host != null && preferences != null,
                                                () -> showPositionDialog(
                                                        host, preferences, position)))),
                        new GboardPatchesSettingsContract.Section(
                                mappingsSectionTitle,
                                Arrays.asList(
                                        mappingRow("A", labels[0], enabled),
                                        mappingRow("Z", labels[1], enabled),
                                        mappingRow("C", labels[2], enabled),
                                        mappingRow("X", labels[3], enabled),
                                        mappingRow("V", labels[4], enabled),
                                        mappingRow("Y", labels[5], enabled)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private static GboardPatchesSettingsContract.Row mappingRow(
            String key, String actionLabel, boolean enabled) {
        return new GboardPatchesSettingsContract.InfoRow(key, actionLabel, enabled);
    }

    private void showPositionDialog(GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences, int currentPosition) {
        GboardPatchesSettingsContract.showChoiceDialog(
                host, positionTitle, positionLabels(), positionValues(),
                Integer.toString(currentPosition), "", () -> { }, value -> {
                    try {
                        GboardLongPressQuickActionsSettings.writePosition(
                                preferences, Integer.parseInt(value));
                        GboardPatchesSettingsContract.refresh(host);
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed dialog values.
                    }
                });
    }

    private String positionLabel(int position) {
        if (position == GboardLongPressQuickActionsSettings.POSITION_LAST) {
            return positionLastLabel;
        }
        return positionFirstLabel;
    }

    private String[] positionLabels() {
        return new String[] {positionFirstLabel, positionLastLabel};
    }

    private static String[] positionValues() {
        return new String[] {
                Integer.toString(GboardLongPressQuickActionsSettings.POSITION_FIRST),
                Integer.toString(GboardLongPressQuickActionsSettings.POSITION_LAST)
        };
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
