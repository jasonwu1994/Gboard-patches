package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardCalculatorSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String title;
    private final String summary;
    private final String headerBadge;
    private final String enableTitle;
    private final String enableSummary;
    private final String sectionFeature;
    private final String sectionUsage;
    private final String usageTitle;
    private final String usageSummary;
    private final String limitsTitle;
    private final String limitsSummary;
    private final String errorTitle;
    private final String errorSummary;

    public GboardCalculatorSettingsFeature(Context context) {
        title = text(context, R.string.gboard_patches_calculator_title);
        summary = text(context, R.string.gboard_patches_calculator_summary);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        enableTitle = text(context, R.string.gboard_patches_calculator_enable_title);
        enableSummary = text(context, R.string.gboard_patches_calculator_enable_summary);
        sectionFeature = text(context, R.string.gboard_patches_calculator_section_feature);
        sectionUsage = text(context, R.string.gboard_patches_calculator_section_usage);
        usageTitle = text(context, R.string.gboard_patches_calculator_usage_title);
        usageSummary = text(context, R.string.gboard_patches_calculator_usage_summary);
        limitsTitle = text(context, R.string.gboard_patches_calculator_limits_title);
        limitsSummary = text(context, R.string.gboard_patches_calculator_limits_summary);
        errorTitle = text(context, R.string.gboard_patches_calculator_error_title);
        errorSummary = text(context, R.string.gboard_patches_calculator_error_summary);
    }

    @Override
    public String getEntryTitle() {
        return title;
    }

    @Override
    public String getEntrySummary() {
        return summary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_SIMPLE_CALCULATOR);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            boolean enabled = GboardCalculatorSettings.readEnabled(context);
            return buildScreenForState(
                    enabled,
                    value -> GboardCalculatorSettings.writeEnabled(context, value));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render calculator settings", throwable);
            return errorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction) {
        return new GboardPatchesSettingsContract.Screen(
                title,
                headerBadge,
                title,
                summary,
                Collections.emptyList(),
                Arrays.asList(
                        new GboardPatchesSettingsContract.Section(
                                sectionFeature,
                                Collections.singletonList(
                                        new GboardPatchesSettingsContract.ToggleRow(
                                                enableTitle,
                                                enableSummary,
                                                true,
                                                enabled,
                                                toggleAction))),
                        new GboardPatchesSettingsContract.Section(
                                sectionUsage,
                                Arrays.asList(
                                        new GboardPatchesSettingsContract.InfoRow(
                                                usageTitle,
                                                usageSummary,
                                                true),
                                        new GboardPatchesSettingsContract.InfoRow(
                                                limitsTitle,
                                                limitsSummary,
                                                true)))));
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                title,
                headerBadge,
                title,
                summary,
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        errorTitle,
                        errorSummary,
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private static String text(Context context, int resourceId) {
        return GboardSettingsText.get(context, resourceId);
    }
}
