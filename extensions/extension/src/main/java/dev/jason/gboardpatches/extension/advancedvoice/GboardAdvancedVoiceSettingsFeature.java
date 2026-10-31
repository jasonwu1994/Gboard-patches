package dev.jason.gboardpatches.extension.advancedvoice;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardAdvancedVoiceSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String APKMIRROR_DOWNLOAD_URL =
            "https://www.apkmirror.com/apk/google-inc/gboard/gboard-the-google-keyboard-18-5-4-993608717-release/gboard-the-google-keyboard-18-5-4-993608717-release-arm64-v8a-2-android-apk-download/";
    private static final String ADVANCED_VOICE_GITHUB_URL =
            "https://github.com/jasonwu1994/Gboard-patches";

    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;
    private final String headerSummary;
    private final String errorTitle;
    private final String errorSummary;
    private final String enabledTitle;
    private final String enabledSummary;
    private final String zhTwPunctuationTitle;
    private final String zhTwPunctuationSummary;
    private final String sectionBehavior;
    private final String sectionInformation;
    private final String sectionOfflineSpeechModel;
    private final String sectionSupportedLanguages;
    private final String supportedLanguagesTitle;
    private final String supportedLanguagesSummary;
    private final String githubStarTitle;
    private final String githubStarSummary;
    private final String speechServicesPresentTitle;
    private final String speechServicesAbsentTitle;
    private final String speechServicesUnknownTitle;
    private final String speechServicesStatusSummary;
    private final String downloadedLanguagesTitle;
    private final String downloadedLanguagesLoadingSummary;
    private final String downloadedLanguagesEmptySummary;
    private final String downloadedLanguagesUnsupportedSummary;
    private final String downloadedLanguagesUnavailableSummary;
    private final String downloadedLanguagesErrorSummary;
    private final String downloadedLanguagesSeparator;
    private final String liveTranscribeManageTitle;
    private final String liveTranscribeManageSummary;
    private final String modesTitle;
    private final String modesSummary;
    private final String payloadPresentTitle;
    private final String payloadPresentSummary;
    private final String payloadPresentRequirementTitle;
    private final String payloadPresentRequirementSummary;
    private final String payloadAbsentTitle;
    private final String payloadAbsentSummary;
    private final String payloadAbsentRequirementTitle;
    private final String payloadAbsentRequirementSummary;
    private final String payloadUnknownTitle;
    private final String payloadUnknownSummary;
    private final String payloadUnknownRequirementTitle;
    private final String payloadUnknownRequirementSummary;

    public GboardAdvancedVoiceSettingsFeature(Context context) {
        entryTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_title);
        entrySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_summary);
        headerBadge = GboardSettingsText.get(context,
                R.string.gboard_patches_header_badge);
        headerSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_header_summary);
        errorTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_error_title);
        errorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_error_summary);
        enabledTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_enabled_title);
        enabledSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_enabled_summary);
        zhTwPunctuationTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_zh_tw_punctuation_title);
        zhTwPunctuationSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_zh_tw_punctuation_summary);
        sectionBehavior = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_section_behavior);
        sectionInformation = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_section_information);
        sectionOfflineSpeechModel = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_section_offline_speech_model);
        sectionSupportedLanguages = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_section_supported_languages);
        supportedLanguagesTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_supported_languages_title);
        supportedLanguagesSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_supported_languages_summary);
        githubStarTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_github_star_title);
        githubStarSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_github_star_summary);
        speechServicesPresentTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_speech_services_present_title);
        speechServicesAbsentTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_speech_services_absent_title);
        speechServicesUnknownTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_speech_services_unknown_title);
        speechServicesStatusSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_speech_services_status_summary);
        downloadedLanguagesTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_title);
        downloadedLanguagesLoadingSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_loading_summary);
        downloadedLanguagesEmptySummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_empty_summary);
        downloadedLanguagesUnsupportedSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_unsupported_summary);
        downloadedLanguagesUnavailableSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_unavailable_summary);
        downloadedLanguagesErrorSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_error_summary);
        downloadedLanguagesSeparator = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_downloaded_languages_separator);
        liveTranscribeManageTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_live_transcribe_manage_title);
        liveTranscribeManageSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_live_transcribe_manage_summary);
        modesTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_modes_title);
        modesSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_modes_summary);
        payloadPresentTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_present_title);
        payloadPresentSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_present_summary);
        payloadPresentRequirementTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_present_requirement_title);
        payloadPresentRequirementSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_present_requirement_summary);
        payloadAbsentTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_absent_title);
        payloadAbsentSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_absent_summary);
        payloadAbsentRequirementTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_absent_requirement_title);
        payloadAbsentRequirementSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_absent_requirement_summary);
        payloadUnknownTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_unknown_title);
        payloadUnknownSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_unknown_summary);
        payloadUnknownRequirementTitle = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_unknown_requirement_title);
        payloadUnknownRequirementSummary = GboardSettingsText.get(context,
                R.string.gboard_patches_advanced_voice_payload_unknown_requirement_summary);
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
                GboardPatchesFeatureAvailability.FEATURE_ADVANCED_VOICE_TYPING);
    }

    @Override
    public boolean requiresOfflineSpeechLanguages() {
        return true;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return buildErrorScreen();
            }

            Context context = host.getContext();
            SharedPreferences preferences = GboardAdvancedVoiceSettings.preferences(context);
            GboardAdvancedVoiceSettings.ensureDefaults(preferences);
            boolean enabled = GboardAdvancedVoiceSettings.readEnabled(preferences);
            boolean zhTwPunctuationEnabled =
                    GboardAdvancedVoiceSettings.readZhTwPunctuationEnabled(preferences);
            GboardDictationPayloadDetector.Detection payloadDetection =
                    GboardDictationPayloadDetector.detect(
                            context,
                            targetPackageNames(context));
            GboardSpeechServicesDetector.Status speechServicesStatus =
                    GboardSpeechServicesDetector.detect(context);
            GboardPatchesSettingsContract.OfflineSpeechLanguages offlineSpeechLanguages =
                    host.getOfflineSpeechLanguages();
            Log.i(TAG, "Loaded Advanced Voice Typing enabled=" + enabled
                    + ", zhTwPunctuationEnabled=" + zhTwPunctuationEnabled
                    + ", payloadStatus=" + payloadDetection.getStatus()
                    + ", payloadPackage=" + payloadDetection.getPackageName()
                    + ", speechServicesStatus=" + speechServicesStatus
                    + ", offlineSpeechLanguagesStatus="
                    + (offlineSpeechLanguages == null
                    ? "null"
                    : offlineSpeechLanguages.getStatus()));

            List<GboardPatchesSettingsContract.Row> behaviorRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            behaviorRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    enabledTitle,
                    enabledSummary,
                    true,
                    enabled,
                    value -> saveEnabled(host, context, value))
                    .setRestartRequired(GboardAdvancedVoiceSettings.PREF_KEY_ENABLED));
            behaviorRows.add(new GboardPatchesSettingsContract.ToggleRow(
                    zhTwPunctuationTitle,
                    zhTwPunctuationSummary,
                    enabled,
                    zhTwPunctuationEnabled,
                    value -> saveZhTwPunctuationEnabled(context, value))
                    .setRestartRequired(
                            GboardAdvancedVoiceSettings.PREF_KEY_ZH_TW_PUNCTUATION_ENABLED));

            List<GboardPatchesSettingsContract.Row> informationRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            informationRows.add(new GboardPatchesSettingsContract.DetailRow(
                    modesTitle,
                    modesSummary,
                    true));
            PayloadGuidance guidance = buildPayloadGuidance(payloadDetection);
            informationRows.add(new GboardPatchesSettingsContract.DetailRow(
                    guidance.statusTitle,
                    guidance.statusSummary,
                    true));
            informationRows.add(new GboardPatchesSettingsContract.CommandRow(
                    guidance.commandTitle,
                    guidance.commandSummary,
                    true,
                    () -> GboardPatchesSettingsContract.openExternalUrl(host, APKMIRROR_DOWNLOAD_URL)));

            List<GboardPatchesSettingsContract.Row> offlineSpeechModelRows =
                    new ArrayList<GboardPatchesSettingsContract.Row>();
            offlineSpeechModelRows.add(new GboardPatchesSettingsContract.CommandRow(
                    speechServicesStatusTitle(speechServicesStatus),
                    speechServicesStatusSummary,
                    true,
                    () -> GboardPatchesSettingsContract.openSpeechRecognitionAndSynthesisStoreListing(host)));
            offlineSpeechModelRows.add(new GboardPatchesSettingsContract.DetailRow(
                    downloadedLanguagesTitle,
                    downloadedLanguagesSummary(context, offlineSpeechLanguages),
                    true));
            offlineSpeechModelRows.add(new GboardPatchesSettingsContract.CommandRow(
                    liveTranscribeManageTitle,
                    liveTranscribeManageSummary,
                    true,
                    () -> GboardPatchesSettingsContract.openLiveTranscribeLanguageManager(host)));

            List<GboardPatchesSettingsContract.Row> supportedLocalesRows =
                    Collections.singletonList(new GboardPatchesSettingsContract.DetailRow(
                            supportedLanguagesTitle,
                            supportedLanguagesSummary,
                            true));
            List<GboardPatchesSettingsContract.Row> githubStarRows =
                    Collections.singletonList(new GboardPatchesSettingsContract.CommandRow(
                            githubStarTitle,
                            githubStarSummary,
                            true,
                            () -> GboardPatchesSettingsContract.openExternalUrl(host, ADVANCED_VOICE_GITHUB_URL)));

            return new GboardPatchesSettingsContract.Screen(
                    entryTitle,
                    headerBadge,
                    entryTitle,
                    headerSummary,
                    Collections.emptyList(),
                    Arrays.asList(
                            new GboardPatchesSettingsContract.Section(
                                    sectionBehavior,
                                    behaviorRows),
                            new GboardPatchesSettingsContract.Section(
                                    sectionInformation,
                                    informationRows),
                            new GboardPatchesSettingsContract.Section(
                                    sectionOfflineSpeechModel,
                                    offlineSpeechModelRows),
                            new GboardPatchesSettingsContract.Section(
                                    sectionSupportedLanguages,
                                    supportedLocalesRows),
                            new GboardPatchesSettingsContract.Section(
                                    null,
                                    githubStarRows)),
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render Advanced Voice Typing settings screen", throwable);
            return buildErrorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildErrorScreen() {
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                errorTitle,
                errorSummary,
                GboardPatchesSettingsContract.StatusTone.WARNING));
        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                headerSummary,
                statusBlocks,
                Collections.emptyList());
    }

    private void saveEnabled(GboardPatchesSettingsContract.FeatureHost host,
            Context context, boolean enabled) {
        try {
            GboardAdvancedVoiceSettings.writeEnabled(context, enabled);
            Log.i(TAG, "Saved Advanced Voice Typing enabled=" + enabled);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to save enhanced voice typing state", throwable);
        }
        refreshSafely(host);
    }

    private void saveZhTwPunctuationEnabled(Context context, boolean enabled) {
        try {
            GboardAdvancedVoiceSettings.writeZhTwPunctuationEnabled(context, enabled);
            Log.i(TAG, "Saved zh-TW punctuation enabled=" + enabled);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to save zh-TW punctuation state", throwable);
        }
    }

    private void refreshSafely(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            GboardPatchesSettingsContract.refresh(host);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to refresh Advanced Voice Typing settings", throwable);
        }
    }

    private PayloadGuidance buildPayloadGuidance(
            GboardDictationPayloadDetector.Detection detection) {
        if (detection != null
                && detection.getStatus() == GboardDictationPayloadDetector.Status.PRESENT
                && detection.getPackageName() != null) {
            return new PayloadGuidance(
                    payloadPresentTitle,
                    String.format(payloadPresentSummary, detection.getPackageName()),
                    payloadPresentRequirementTitle,
                    payloadPresentRequirementSummary);
        }
        if (detection != null
                && detection.getStatus() == GboardDictationPayloadDetector.Status.ABSENT
                && detection.getPackageName() != null) {
            return new PayloadGuidance(
                    payloadAbsentTitle,
                    String.format(payloadAbsentSummary, detection.getPackageName()),
                    payloadAbsentRequirementTitle,
                    payloadAbsentRequirementSummary);
        }
        return new PayloadGuidance(
                payloadUnknownTitle,
                payloadUnknownSummary,
                payloadUnknownRequirementTitle,
                payloadUnknownRequirementSummary);
    }

    private String speechServicesStatusTitle(GboardSpeechServicesDetector.Status status) {
        if (status == GboardSpeechServicesDetector.Status.PRESENT) {
            return speechServicesPresentTitle;
        }
        if (status == GboardSpeechServicesDetector.Status.ABSENT) {
            return speechServicesAbsentTitle;
        }
        return speechServicesUnknownTitle;
    }

    private String downloadedLanguagesSummary(
            Context context,
            GboardPatchesSettingsContract.OfflineSpeechLanguages offlineSpeechLanguages) {
        if (offlineSpeechLanguages == null) {
            return downloadedLanguagesErrorSummary;
        }
        switch (offlineSpeechLanguages.getStatus()) {
            case LOADING:
                return downloadedLanguagesLoadingSummary;
            case UNSUPPORTED:
                return downloadedLanguagesUnsupportedSummary;
            case UNAVAILABLE:
                return downloadedLanguagesUnavailableSummary;
            case ERROR:
                return downloadedLanguagesErrorSummary;
            case AVAILABLE:
            default:
                List<String> displayNames = GboardOfflineSpeechLanguageFormatter.format(
                        offlineSpeechLanguages.getLanguageTags(),
                        displayLocale(context));
                if (displayNames.isEmpty()) {
                    return downloadedLanguagesEmptySummary;
                }
                return String.join(downloadedLanguagesSeparator, displayNames);
        }
    }

    private static Locale displayLocale(Context context) {
        if (context == null || context.getResources() == null
                || context.getResources().getConfiguration() == null) {
            return Locale.getDefault();
        }
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!configuration.getLocales().isEmpty()) {
                return configuration.getLocales().get(0);
            }
            return Locale.getDefault();
        }
        return configuration.locale == null ? Locale.getDefault() : configuration.locale;
    }


    private static List<String> targetPackageNames(Context context) {
        List<String> packageNames = new ArrayList<String>();
        addPackageName(packageNames, context == null ? null : context.getPackageName());
        addPackageName(packageNames, "com.google.android.inputmethod.latin");
        addPackageName(packageNames, "com.google.android.inputmethod.latin.jason.dev");
        addPackageName(packageNames, "com.touchtype.swiftkey");
        return packageNames;
    }

    private static void addPackageName(List<String> packageNames, String packageName) {
        if (packageName != null
                && !packageName.isEmpty()
                && !packageNames.contains(packageName)) {
            packageNames.add(packageName);
        }
    }

    private static final class PayloadGuidance {
        private final String statusTitle;
        private final String statusSummary;
        private final String commandTitle;
        private final String commandSummary;

        private PayloadGuidance(
                String statusTitle,
                String statusSummary,
                String commandTitle,
                String commandSummary) {
            this.statusTitle = statusTitle;
            this.statusSummary = statusSummary;
            this.commandTitle = commandTitle;
            this.commandSummary = commandSummary;
        }
    }
}
