package dev.jason.gboardpatches.extension.websearch;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

@RunWith(RobolectricTestRunner.class)
public final class GboardFloatingWebSearchContractTest {
    @Test
    public void defaultsAndValidationMatchTheProductContract() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = GboardFloatingWebSearchSettingsStore.preferences(context);
        preferences.edit().clear().commit();
        GboardFloatingWebSearchSettings.ensureDefaults(preferences);
        GboardFloatingWebSearchSettingsSnapshot snapshot =
                GboardFloatingWebSearchSettings.readSnapshotOrNull(preferences);

        Assert.assertNotNull(snapshot);
        Assert.assertTrue(snapshot.isEnabled());
        Assert.assertEquals("google", snapshot.getStartPageMode());
        Assert.assertEquals("https://www.google.com/", snapshot.getStartPageUrl());
        Assert.assertEquals(100, snapshot.getInitialHeightPercent());
        Assert.assertEquals("system_default", snapshot.getBrowserProvider());
        Assert.assertArrayEquals(new int[]{100, 90, 80, 70, 60, 50},
                GboardFloatingWebSearchSettings.ALLOWED_HEIGHT_PERCENTAGES);
        Assert.assertTrue(GboardFloatingWebSearchSettings.isAllowedHttpsUrl(
                "https://example.com/path"));
        Assert.assertFalse(GboardFloatingWebSearchSettings.isAllowedHttpsUrl(
                "http://example.com/"));
        Assert.assertFalse(GboardFloatingWebSearchSettings.isAllowedHttpsUrl("https:///path"));
    }

    @Test
    public void settingsScreenMatchesRowTextOrderDefaultsAndStackedPreviews() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = GboardFloatingWebSearchSettingsStore.preferences(context);
        preferences.edit().clear().commit();
        GboardFloatingWebSearchSettingsFeature feature =
                new GboardFloatingWebSearchSettingsFeature(context);
        GboardPatchesSettingsContract.Screen screen = feature.buildScreen(
                new TestHost(context));

        Assert.assertEquals("Floating Web Search", feature.getEntryTitle());
        Assert.assertEquals(
                "Open a browser-owned floating web page from Gboard. The selected browser "
                        + "supplies its cookies, login state, toolbar, and site compatibility.",
                feature.getEntrySummary());
        Assert.assertEquals("", screen.getHeaderSummary());
        Assert.assertEquals(2, screen.getSections().size());
        Assert.assertEquals(5, screen.getRows().size());

        GboardPatchesSettingsContract.ToggleRow enabled =
                (GboardPatchesSettingsContract.ToggleRow) screen.getRows().get(0);
        Assert.assertTrue(enabled.isChecked());
        Assert.assertEquals("Force-stop and restart Gboard for changes to take effect.",
                enabled.getSummary());
        Assert.assertEquals(GboardPatchesSettingsContract.PreviewLayout.STACKED,
                enabled.getPreviewSpec().getLayout());
        List<GboardPatchesSettingsContract.PreviewMedia> media =
                enabled.getPreviewSpec().getMediaItems();
        Assert.assertEquals(2, media.size());
        assertPreview(media.get(0),
                "settings-previews/websearch/01_floating_web_search_browser.png");
        assertPreview(media.get(1),
                "settings-previews/websearch/02_floating_web_search_access_point.png");

        Assert.assertEquals("Google",
                ((GboardPatchesSettingsContract.SelectorRow) screen.getRows().get(1))
                        .getCurrentValue());
        Assert.assertEquals("100%",
                ((GboardPatchesSettingsContract.SelectorRow) screen.getRows().get(2))
                        .getCurrentValue());
        Assert.assertTrue(screen.getRows().get(3).getSummary().contains(
                "Chrome, Edge, and Brave are recommended."));
        Assert.assertTrue(screen.getRows().get(4)
                instanceof GboardPatchesSettingsContract.CommandRow);
    }

    @Test
    public void launcherIsSelfContainedAndKeepsCleanupPathsIndependent() throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/websearch/"
                        + "GboardFloatingWebSearchLauncher.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertFalse(source.contains("import androidx.browser"));
        Assert.assertFalse(source.contains("WebView"));
        Assert.assertTrue(source.contains("CONNECTION_TIMEOUT_MS = 2_500L"));
        Assert.assertTrue(source.contains("mainHandler.removeCallbacks(timeout)"));
        Assert.assertTrue(source.contains("context.unbindService(this)"));
        Assert.assertTrue(source.contains("TRANSACTION_NEW_SESSION"));
        Assert.assertTrue(source.contains("TRANSACTION_WARMUP = 2"));
        Assert.assertTrue(source.contains("TRANSACTION_NEW_SESSION = 3"));
    }

    @Test
    public void sharedAccessPointRuntimeSynchronizesControllerCatalogBeforeRegistration()
            throws Exception {
        String source = new String(Files.readAllBytes(Path.of(
                "src/main/java/dev/jason/gboardpatches/extension/accesspoint/"
                        + "GboardAccessPointContributionsRuntime.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        int synchronization = source.indexOf(
                "synchronizeControllerOrderCatalog(controller, context);");
        int registration = source.indexOf(
                "GboardFloatingWebSearchAccessPointContribution.INSTANCE.register(");

        Assert.assertTrue(synchronization >= 0);
        Assert.assertTrue(registration > synchronization);
        Assert.assertTrue(source.contains("getDeclaredField(\"g\")"));
        Assert.assertTrue(source.contains("getDeclaredField(\"c\")"));
        Assert.assertTrue(source.contains(
                "includeOrderCatalog(context, stockCatalog)"));
    }

    private static void assertPreview(GboardPatchesSettingsContract.PreviewMedia media,
            String path) {
        Assert.assertEquals(path,
                ((GboardPatchesSettingsContract.PreviewImage) media).getAssetPath());
        Assert.assertTrue(Files.exists(Path.of("..", "..", "patches", "src", "main",
                "resources", path)));
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
            // Screen construction does not dispatch UI intents.
        }
    }
}
