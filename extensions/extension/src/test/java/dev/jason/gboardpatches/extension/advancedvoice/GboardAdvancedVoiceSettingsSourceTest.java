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

        Assert.assertTrue(keyboardGroup.contains(
                "new GboardAdvancedVoiceSettingsFeature(context)"));
        Assert.assertEquals(2, count(feature,
                "new GboardPatchesSettingsContract.ToggleRow("));
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
        Assert.assertTrue(feature.contains("17-7-7-932364120-release-arm64-v8a"));
        Assert.assertTrue(availability.contains("FEATURE_ADVANCED_VOICE_TYPING"));
        Assert.assertTrue(availability.contains(
                "dev.jason.gboardpatches.feature.advanced_voice_typing"));

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
