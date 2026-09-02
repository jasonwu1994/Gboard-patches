package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;

@RunWith(RobolectricTestRunner.class)
public final class GboardFlagStoreLocalizationTest {
    @Test
    public void replacementDiffUsesEnglishActivityConfiguration() {
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
            GboardFlagStoreDiff.Result diff = new GboardFlagStoreDiff.Result(
                    0, 0, Collections.emptyList());

            GboardFlagStoreDiffSettingsFeature feature =
                    new GboardFlagStoreDiffSettingsFeature(
                            englishContext, "flag_override.xml", new byte[0], diff);

            assertEquals("Restore differences", feature.getEntryTitle());
            GboardPatchesSettingsContract.Screen screen =
                    feature.buildScreen(new TestHost(englishContext));
            assertEquals("Restore differences", screen.getToolbarTitle());
            assertNotNull(screen.getPrimaryAction());
            assertEquals("Apply and restart Gboard", screen.getPrimaryAction().getTitle());
            assertFalse(screen.getSections().stream()
                    .anyMatch(section -> "Confirm".contentEquals(section.getTitle())));
            assertNoHan(screen.getToolbarTitle());
            assertNoHan(screen.getStatusBlocks().get(0).getSummary());
            for (GboardPatchesSettingsContract.Section section : screen.getSections()) {
                assertNoHan(section.getTitle());
                for (GboardPatchesSettingsContract.Row row : section.getItems()) {
                    assertNoHan(row.getTitle());
                    assertNoHan(row.getSummary());
                }
            }
        } finally {
            application.getResources().updateConfiguration(
                    original, application.getResources().getDisplayMetrics());
        }
    }

    @Test
    public void replacementActionReplacesPendingFileAndRestartsWithoutIntermediateScreen() {
        Application application = RuntimeEnvironment.getApplication();
        byte[] replacement = "<?xml version=\"1.0\" encoding=\"utf-8\"?><map/>"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        GboardFlagStoreDiff.Result diff = new GboardFlagStoreDiff.Result(
                0, 0, Collections.emptyList());
        GboardFlagStoreDiffSettingsFeature feature =
                new GboardFlagStoreDiffSettingsFeature(
                        application, GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE,
                        replacement, diff);
        RestartCapturingHost host = new RestartCapturingHost(application);
        try {
            GboardFlagStoreManager.stage(
                    application, GboardFlagStoreFiles.LEGACY_VALUE_FILE, replacement);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }

        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(host);
        assertNotNull(screen.getPrimaryAction());
        screen.getPrimaryAction().getAction().run();

        assertEquals(Collections.singletonList(GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE),
                GboardFlagStoreManager.inspect(application).getStagedFiles());
        assertEquals(1, host.restartCount);
        assertNull(host.getOpenedFeature());
        GboardFlagStoreManager.applyPendingAtColdStart(application);
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

    private static final class RestartCapturingHost extends GboardPatchesSettingsTestHost {
        private int restartCount;

        RestartCapturingHost(Context context) {
            super(context);
        }

        @Override
        public void requestTargetRestart() {
            restartCount++;
        }
    }
}
