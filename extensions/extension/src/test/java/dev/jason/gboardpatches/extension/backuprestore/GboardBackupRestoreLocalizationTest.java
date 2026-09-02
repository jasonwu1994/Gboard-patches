package dev.jason.gboardpatches.extension.backuprestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

@RunWith(RobolectricTestRunner.class)
public final class GboardBackupRestoreLocalizationTest {
    @Test
    public void restorePreviewUsesEnglishActivityConfiguration() {
        Application application = RuntimeEnvironment.getApplication();
        Configuration original = new Configuration(
                application.getResources().getConfiguration());
        try {
            Configuration traditionalChinese = new Configuration(original);
            traditionalChinese.setLocale(Locale.forLanguageTag("zh-Hant"));
            application.getResources().updateConfiguration(
                    traditionalChinese, application.getResources().getDisplayMetrics());

            Configuration english = new Configuration(traditionalChinese);
            english.setLocale(Locale.ENGLISH);
            Context englishContext = application.createConfigurationContext(english);
            assertEquals("Restore differences", GboardSettingsText.get(
                    englishContext, R.string.gboard_patches_backup_restore_preview_title));
            GboardPatchesBackupManager.RestorePreview preview =
                    new GboardPatchesBackupManager.RestorePreview(
                            Collections.emptyList(), 0, 0, 0, 0);

            GboardPatchesRestorePreviewFeature feature =
                    new GboardPatchesRestorePreviewFeature(
                            englishContext, "{}", Collections.emptySet(), preview);

            assertEquals("Restore differences", feature.getEntryTitle());
            assertEnglish(feature.buildScreen(new TestHost(englishContext)));

            GboardPatchesBackupManager.RestoreResult result =
                    new GboardPatchesBackupManager.RestoreResult(
                            true, false, 0, 0, 0, true, null, Collections.emptyList());
            GboardPatchesRestoreResultFeature resultFeature =
                    new GboardPatchesRestoreResultFeature(englishContext, result);
            assertEquals("Restore result", resultFeature.getEntryTitle());
            assertEnglish(resultFeature.buildScreen(new TestHost(englishContext)));

            GboardBackupRestoreSettingsFeature rootFeature =
                    new GboardBackupRestoreSettingsFeature(englishContext);
            assertEquals("Backup & Restore", rootFeature.getEntryTitle());
            assertEnglish(rootFeature.buildScreen(new TestHost(englishContext)));
            assertEquals("PB/XML Backup & Restore",
                    rootFeature.getNavigationChildren().get(0).getEntryTitle());
        } finally {
            application.getResources().updateConfiguration(
                    original, application.getResources().getDisplayMetrics());
        }
    }

    @Test
    public void everyBackupRestoreResourceHasNonChineseEnglishText() throws Exception {
        Path sourceRoot = Path.of("src/main/java/dev/jason/gboardpatches/extension/backuprestore");
        Pattern referencePattern = Pattern.compile("R\\.string\\.([A-Za-z0-9_]+)");
        Set<String> resourceNames = new LinkedHashSet<>();
        try (var paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                assertFalse("Hard-coded Chinese UI text in " + path,
                        Pattern.compile("[\\p{IsHan}]").matcher(source).find());
                Matcher references = referencePattern.matcher(source);
                while (references.find()) {
                    resourceNames.add(references.group(1));
                }
            }
        }
        String authority = new String(Files.readAllBytes(
                Path.of("src/main/settings-text/gboard_settings_text.xml")),
                StandardCharsets.UTF_8);
        for (String resourceName : resourceNames) {
            Pattern blockPattern = Pattern.compile(
                    "<string name=\"" + Pattern.quote(resourceName)
                            + "\">(.*?)</string>", Pattern.DOTALL);
            Matcher block = blockPattern.matcher(authority);
            assertTrue("Missing settings-text entry: " + resourceName, block.find());
            Matcher english = Pattern.compile(
                    "<translation locale=\"en\">(.*?)</translation>", Pattern.DOTALL)
                    .matcher(block.group(1));
            assertTrue("Missing English translation: " + resourceName, english.find());
            String englishText = english.group(1).trim();
            assertFalse("Blank English translation: " + resourceName, englishText.isEmpty());
            assertFalse("Chinese text in English translation: " + resourceName,
                    Pattern.compile("[\\p{IsHan}]").matcher(englishText).find());
        }
        assertTrue("No backup/restore string resources were audited", resourceNames.size() > 40);
    }

    private static void assertEnglish(GboardPatchesSettingsContract.Screen screen) {
        assertNoHan(screen.getToolbarTitle());
        assertNoHan(screen.getHeaderBadge());
        assertNoHan(screen.getHeaderTitle());
        assertNoHan(screen.getHeaderSummary());
        for (GboardPatchesSettingsContract.StatusBlock status : screen.getStatusBlocks()) {
            assertNoHan(status.getTitle());
            assertNoHan(status.getSummary());
        }
        for (GboardPatchesSettingsContract.Section section : screen.getSections()) {
            assertNoHan(section.getTitle());
            assertNoHan(section.getDescription());
            for (GboardPatchesSettingsContract.Row row : section.getItems()) {
                assertNoHan(row.getTitle());
                assertNoHan(row.getSummary());
            }
        }
        if (screen.getPrimaryAction() != null) {
            assertNoHan(screen.getPrimaryAction().getTitle());
            assertNoHan(screen.getPrimaryAction().getSummary());
        }
    }

    private static void assertNoHan(CharSequence value) {
        if (value != null) {
            assertFalse("Unexpected Chinese UI text: " + value,
                    Pattern.compile("[\\p{IsHan}]").matcher(value).find());
        }
    }

    private static final class TestHost implements GboardPatchesSettingsContract.FeatureHost {
        private final Context context;

        TestHost(Context context) {
            this.context = context;
        }

        @Override
        public Context getContext() {
            return context;
        }

        @Override
        public void submit(GboardPatchesSettingsContract.Intent intent) {
        }
    }
}
