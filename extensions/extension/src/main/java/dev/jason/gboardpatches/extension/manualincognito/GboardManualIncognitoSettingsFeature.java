package dev.jason.gboardpatches.extension.manualincognito;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardManualIncognitoSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String PREVIEW_IMAGE_ENABLED =
            "settings-previews/manualincognito/access_point.png";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String sectionBehavior;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String clipboardTitle;
    private final String clipboardSummary;
    private final String voiceTitle;
    private final String voiceSummary;
    private final String errorTitle;
    private final String errorSummary;

    public GboardManualIncognitoSettingsFeature(Context context) {
        this(
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_summary),
                GboardSettingsText.get(context, R.string.gboard_patches_header_badge),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_section_behavior),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_enabled_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_enabled_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_clipboard_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_clipboard_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_voice_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_voice_summary),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_error_title),
                GboardSettingsText.get(context,
                        R.string.gboard_patches_manual_incognito_error_summary));
    }

    GboardManualIncognitoSettingsFeature(String entryTitle, String entrySummary,
            String headerBadge, String sectionBehavior, String enabledTitle,
            String enabledSummary, String clipboardTitle, String clipboardSummary,
            String voiceTitle, String voiceSummary, String errorTitle, String errorSummary) {
        this.entryTitle = entryTitle;
        this.entrySummary = entrySummary;
        this.headerBadge = headerBadge;
        this.sectionBehavior = sectionBehavior;
        this.enabledTitle = enabledTitle;
        this.enabledSummary = enabledSummary;
        this.clipboardTitle = clipboardTitle;
        this.clipboardSummary = clipboardSummary;
        this.voiceTitle = voiceTitle;
        this.voiceSummary = voiceSummary;
        this.errorTitle = errorTitle;
        this.errorSummary = errorSummary;
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
                GboardPatchesFeatureAvailability.FEATURE_MANUAL_INCOGNITO);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }
            Context context = host.getContext();
            GboardManualIncognitoSettings.RequestedPolicy requested =
                    GboardManualIncognitoSettings.read(context);
            return buildScreenForState(
                    requested,
                    value -> {
                        if (GboardManualIncognitoSettings.writeFeatureEnabled(context, value)) {
                            GboardPatchesSettingsContract.refresh(host);
                        }
                    },
                    value -> GboardManualIncognitoSettings.writeAllowClipboard(context, value),
                    value -> GboardManualIncognitoSettings.writeAllowVoiceTyping(context, value));
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to render manual incognito settings", failure);
            return buildErrorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            GboardManualIncognitoSettings.RequestedPolicy requested,
            GboardPatchesSettingsContract.ToggleAction featureToggle,
            GboardPatchesSettingsContract.ToggleAction clipboardToggle,
            GboardPatchesSettingsContract.ToggleAction voiceToggle) {
        GboardManualIncognitoSettings.RequestedPolicy safeRequested = requested == null
                ? GboardManualIncognitoSettings.RequestedPolicy.defaults()
                : requested;
        List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
        rows.add(new GboardPatchesSettingsContract.ToggleRow(
                enabledTitle,
                enabledSummary,
                true,
                safeRequested.featureEnabled,
                featureToggle,
                buildEnabledPreview()));
        rows.add(new GboardPatchesSettingsContract.ToggleRow(
                clipboardTitle,
                clipboardSummary,
                safeRequested.featureEnabled,
                safeRequested.allowClipboardRequested,
                clipboardToggle));
        rows.add(new GboardPatchesSettingsContract.ToggleRow(
                voiceTitle,
                voiceSummary,
                safeRequested.featureEnabled,
                safeRequested.allowVoiceRequested,
                voiceToggle));

        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                "",
                Collections.emptyList(),
                Collections.singletonList(new GboardPatchesSettingsContract.Section(
                        sectionBehavior,
                        rows)),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private GboardPatchesSettingsContract.PreviewSpec buildEnabledPreview() {
        return new GboardPatchesSettingsContract.PreviewSpec(
                enabledTitle,
                "",
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
}
