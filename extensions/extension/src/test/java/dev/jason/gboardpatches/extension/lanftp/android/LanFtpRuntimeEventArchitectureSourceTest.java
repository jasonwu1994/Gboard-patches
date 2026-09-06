package dev.jason.gboardpatches.extension.lanftp.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpRuntimeEventArchitectureSourceTest {
    @Test
    public void runtimeUsesVisibleOnlyCrossProcessEventsWithoutFixedPolling()
            throws Exception {
        Path main = Path.of("src/main/java/dev/jason/gboardpatches/extension");
        String publisher = read(main.resolve(
                "lanftp/android/LanFtpRuntimeEventPublisher.java"));
        String observer = read(main.resolve(
                "lanftp/android/LanFtpRuntimeObserver.java"));
        String service = read(main.resolve("lanftp/android/LanFtpService.java"));
        String feature = read(main.resolve(
                "lanftp/settings/GboardLanFtpSettingsFeature.java"));
        String contract = read(main.resolve(
                "settings/GboardPatchesSettingsContract.java"));
        String activity = read(main.resolve(
                "settings/GboardPatchesSettingsActivity.java"));

        Assert.assertTrue(publisher.contains("notifyChange("));
        Assert.assertTrue(publisher.contains("progressUri("));
        Assert.assertTrue(observer.contains("registerContentObserver("));
        Assert.assertTrue(observer.contains("unregisterContentObserver("));
        Assert.assertTrue(observer.contains("handler.removeCallbacks("));
        Assert.assertTrue(observer.contains("PROGRESS_REFRESH_MIN_INTERVAL_MS = 1_000L"));

        Assert.assertTrue(service.contains("LanFtpRuntimeStatus.stopping()"));
        Assert.assertTrue(service.contains("publishRunningStateLocked(true)"));
        Assert.assertTrue(service.contains("publishRunningStateLocked(false)"));
        Assert.assertTrue(feature.contains("public void onVisible("));
        Assert.assertTrue(feature.contains("public void onHidden("));
        Assert.assertTrue(feature.contains("LanFtpRuntimeObserver"));
        Assert.assertFalse(feature.contains("STATUS_REFRESH_INTERVAL_MS"));
        Assert.assertFalse(feature.contains("RefreshPolicy.liveStatusOnly("));

        Assert.assertTrue(contract.contains("default void onVisible(FeatureHost host)"));
        Assert.assertTrue(contract.contains("default void onHidden(FeatureHost host)"));
        Assert.assertTrue(activity.contains("syncVisibleFeatureLifecycle()"));
        Assert.assertFalse(activity.contains("requestVisibleFeatureRefresh("));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
