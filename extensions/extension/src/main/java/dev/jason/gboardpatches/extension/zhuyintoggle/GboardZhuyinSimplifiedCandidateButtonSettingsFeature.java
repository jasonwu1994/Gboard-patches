package dev.jason.gboardpatches.extension.zhuyintoggle;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Collections;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardZhuyinSimplifiedCandidateButtonSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String errorTitle;
    private final String errorSummary;
    private final String sectionTitle;
    private final String enabledTitle;
    private final String enabledSummary;

    public GboardZhuyinSimplifiedCandidateButtonSettingsFeature(Context context) {
        entryTitle = text(context, R.string.gboard_patches_zhuyin_simplified_button_title);
        entrySummary = text(context, R.string.gboard_patches_zhuyin_simplified_button_summary);
        headerBadge = text(context, R.string.gboard_patches_header_badge);
        errorTitle = text(context, R.string.gboard_patches_zhuyin_simplified_button_error_title);
        errorSummary = text(context,
                R.string.gboard_patches_zhuyin_simplified_button_error_summary);
        sectionTitle = text(context,
                R.string.gboard_patches_zhuyin_simplified_button_section);
        enabledTitle = text(context,
                R.string.gboard_patches_zhuyin_simplified_button_enabled_title);
        enabledSummary = text(context,
                R.string.gboard_patches_zhuyin_simplified_button_enabled_summary);
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
                GboardPatchesFeatureAvailability.FEATURE_ZHUYIN_SIMPLIFIED_CANDIDATE_BUTTON);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            SharedPreferences preferences = GboardPatchesSettings.preferences(host.getContext());
            GboardZhuyinSimplifiedCandidateButtonSettings.ensureDefault(preferences);
            return buildScreenForState(
                    GboardZhuyinSimplifiedCandidateButtonSettings.readEnabled(host.getContext()),
                    enabled -> {
                        if (!GboardZhuyinSimplifiedCandidateButtonSettings.writeEnabled(
                                host.getContext(), enabled)) {
                            throw new IllegalStateException(
                                    "Failed to save simplified candidate button setting");
                        }
                    });
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to render simplified candidate button settings", throwable);
            } catch (Throwable ignored) {
                // The settings screen must still fail closed if logging is unavailable.
            }
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            boolean enabled,
            GboardPatchesSettingsContract.ToggleAction toggleAction) {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                entrySummary,
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionTitle,
                        Collections.singletonList(new GboardPatchesSettingsContract.ToggleRow(
                                enabledTitle,
                                enabledSummary,
                                true,
                                enabled,
                                toggleAction)))),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                entrySummary,
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
