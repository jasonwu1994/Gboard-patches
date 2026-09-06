package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LanFtpAllFilesAccessSourceTest {
    @Test
    public void permissionsAreCheckedAtStartupWithoutPeriodicRuntimeRevalidation()
            throws Exception {
        String access = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpAllFilesAccess.java");
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");
        String controller = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpServerController.java");

        Assert.assertTrue(access.contains("Build.VERSION.SDK_INT < Build.VERSION_CODES.R"));
        Assert.assertTrue(access.contains("return false;"));
        Assert.assertTrue(access.contains("Environment.isExternalStorageManager()"));
        Assert.assertTrue(service.contains("!LanFtpAllFilesAccess.isGranted(appContext)"));
        Assert.assertTrue(service.contains("!LanFtpAllFilesAccess.isGranted(this)"));
        Assert.assertFalse(service.contains("SHARED_STORAGE_ACCESS_CHECK_INTERVAL_MS"));
        Assert.assertFalse(service.contains("nextRootAccessCheckIntervalMs()"));
        Assert.assertFalse(service.contains("scheduleImmediateRootAccessCheck()"));
        Assert.assertFalse(service.contains("hasCurrentRootAccess()"));
        Assert.assertTrue(service.contains(
                "mainHandler.postDelayed(idleCheck, IDLE_TIMEOUT_CHECK_INTERVAL_MS)"));
        Assert.assertTrue(service.contains("mainHandler.removeCallbacks(idleCheck);"));
        Assert.assertTrue(controller.contains(
                "boolean localNetworkAccess = runtime.active || hasLocalNetworkAccess();"));
        Assert.assertTrue(controller.contains(
                "boolean allFilesAccess = runtime.active || hasAllFilesAccess();"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
