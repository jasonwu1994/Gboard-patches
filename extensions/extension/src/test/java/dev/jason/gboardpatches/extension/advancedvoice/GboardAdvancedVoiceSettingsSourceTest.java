package dev.jason.gboardpatches.extension.advancedvoice;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardAdvancedVoiceSettingsSourceTest {
    @Test
    public void settingsFeaturePreservesAcceptedRowsAndGuidance()
            throws Exception {
        Path root = repositoryRoot();
        String feature = read(root,
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/"
                        + "advancedvoice/GboardAdvancedVoiceSettingsFeature.java");
        String keyboardGroup = read(root,
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/"
                        + "keyboard/GboardAiVoiceSettingsGroupFeature.java");
        String availability = read(root,
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/"
                        + "settings/GboardPatchesFeatureAvailability.java");
        String settingsText = read(root,
                "extensions/extension/src/main/settings-text/gboard_settings_text.xml");

        Assert.assertTrue(keyboardGroup.contains(
                "new GboardAdvancedVoiceSettingsFeature(context)"));
        int informationRows = feature.indexOf("informationRows =");
        int modesDetailRow = feature.indexOf("modesTitle,", informationRows);
        int payloadGuidance = feature.indexOf("buildPayloadGuidance(", informationRows);
        Assert.assertTrue("modes detail row must lead the information section",
                informationRows > 0 && modesDetailRow > informationRows
                        && payloadGuidance > modesDetailRow);
        Assert.assertTrue(settingsText.contains(
                "Voice typing has three modes: Standard, Standard - Advanced features "
                        + "(colorful microphone), and Rambler."));
        Assert.assertTrue(settingsText.contains(
                "語音輸入分為三種：標準、標準-進階功能（彩色麥克風）以及 Rambler。"));
        Assert.assertEquals(2, count(feature,
                "new GboardPatchesSettingsContract.ToggleRow("));
        Assert.assertEquals(0, count(feature,
                "new GboardPatchesSettingsContract.SelectorRow("));
        Assert.assertFalse(feature.contains("GboardAdvancedVoiceSettings.readBackend("));
        Assert.assertFalse(feature.contains("showBackendDialog("));
        Assert.assertFalse(feature.contains("saveBackend("));
        Assert.assertTrue(feature.contains("refreshSafely("));
        Assert.assertFalse(feature.contains("BACKEND_RAMBLER"));
        Assert.assertTrue(feature.contains(
                "new GboardPatchesSettingsContract.DetailRow("));
        Assert.assertTrue(feature.contains(
                "new GboardPatchesSettingsContract.CommandRow("));
        Assert.assertTrue(feature.contains("GboardDictationPayloadDetector.detect("));
        Assert.assertTrue(feature.contains("targetPackageNames(context)"));
        Assert.assertTrue(feature.contains(
                "GboardPatchesSettingsContract.openExternalUrl(host, APKMIRROR_DOWNLOAD_URL)"));
        Assert.assertTrue(feature.contains("GboardSpeechServicesDetector.detect(context)"));
        Assert.assertTrue(feature.contains("host.getOfflineSpeechLanguages()"));
        Assert.assertTrue(feature.contains("public boolean requiresOfflineSpeechLanguages()"));
        Assert.assertTrue(feature.contains("GboardOfflineSpeechLanguageFormatter.format("));
        Assert.assertTrue(feature.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.N"));
        Assert.assertTrue(feature.contains("configuration.locale"));
        Assert.assertTrue(feature.contains(
                "GboardPatchesSettingsContract.openSpeechRecognitionAndSynthesisStoreListing("
                        + "host)"));
        Assert.assertTrue(feature.contains(
                "GboardPatchesSettingsContract.openLiveTranscribeLanguageManager(host)"));
        Assert.assertTrue(feature.contains("sectionOfflineSpeechModel"));
        Assert.assertTrue(feature.contains("sectionSupportedLanguages"));
        Assert.assertTrue(feature.contains("ADVANCED_VOICE_GITHUB_URL"));
        Assert.assertTrue(feature.contains(
                "() -> GboardPatchesSettingsContract.openExternalUrl("
                        + "host, ADVANCED_VOICE_GITHUB_URL)"));
        Assert.assertTrue(feature.matches(
                "(?s).*new GboardPatchesSettingsContract\\.Section\\("
                        + "\\s*null,\\s*githubStarRows\\).*"));
        Assert.assertTrue(feature.contains("18-5-4-993608717-release-arm64-v8a-2"));
        Assert.assertTrue(availability.contains("FEATURE_ADVANCED_VOICE_TYPING"));
        Assert.assertTrue(availability.contains(
                "dev.jason.gboardpatches.feature.advanced_voice_typing"));
        Assert.assertTrue(settingsText.contains("Rambler(限線上)"));
        Assert.assertTrue(settingsText.contains(
                "切換後須強制停止並重新啟動 Gboard 才會生效"));
        Assert.assertFalse(settingsText.contains("Experimental Rambler"));

    }

    private static int count(String source, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static String read(Path root, String relativePath) throws Exception {
        return new String(
                Files.readAllBytes(root.resolve(relativePath)),
                StandardCharsets.UTF_8);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
