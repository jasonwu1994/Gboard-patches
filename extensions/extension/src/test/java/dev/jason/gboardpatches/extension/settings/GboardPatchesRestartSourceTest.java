package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardPatchesRestartSourceTest {
    @Test
    public void toolbarUsesSingleArrowThemeColorTooltipAndReverseRotation() throws Exception {
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesSettingsActivity.java");

        Assert.assertTrue(activity.contains("buildRestartButton()"));
        Assert.assertTrue(activity.contains(
                "paint.setColor(restartPendingCount > 0 ? palette.restartPending : palette.accent)"));
        Assert.assertTrue(activity.contains(
                "new LinearLayout.LayoutParams(dp(48), dp(48))"));
        Assert.assertTrue(activity.contains("setTooltipText(restartLabel)"));
        Assert.assertTrue(activity.contains("ObjectAnimator.ofFloat("));
        Assert.assertTrue(activity.contains("ValueAnimator.INFINITE"));
        Assert.assertTrue(activity.contains("-360f"));
        Assert.assertFalse(activity.contains("showRestartDialog"));
    }

    @Test
    public void requestRestartsDirectlyIntoCurrentPatchesPage() throws Exception {
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesSettingsActivity.java");
        String normalizedActivity = activity.replace("\r\n", "\n");
        String navigation = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesNavigationPath.java");

        Assert.assertTrue(activity.contains("Intent.makeRestartActivityTask"));
        Assert.assertTrue(activity.contains("System.exit(0)"));
        Assert.assertTrue(activity.contains(
                "new ComponentName(context, GboardPatchesSettingsActivity.class)"));
        Assert.assertTrue(activity.contains(
                "restartIntent.putStringArrayListExtra("));
        Assert.assertTrue(activity.contains("GboardPatchesNavigationPath.capture("));
        Assert.assertTrue(activity.contains("state.getBackStack(), state.getCurrent()"));
        Assert.assertTrue(activity.contains("restoreNavigationPathFromIntent"));
        Assert.assertTrue(normalizedActivity.contains(
                "private void requestGboardRestart() {\n        try {"));
        Assert.assertTrue(activity.contains("private void handleRestartFailure("));
        Assert.assertTrue(activity.contains(
                "A restart failure must never escape into the host app."));
        Assert.assertFalse(activity.contains("AlarmManager"));
        Assert.assertFalse(activity.contains("PendingIntent"));
        Assert.assertFalse(activity.contains(
                "getLaunchIntentForPackage(getPackageName())"));
        Assert.assertTrue(navigation.contains("feature.getClass().getName()"));
        Assert.assertTrue(navigation.contains("getNavigationChildren()"));
    }

    @Test
    public void restartGuardsStockCrashRecoveryCleanupAndRestoresAfterStartup()
            throws Exception {
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardPatchesSettingsActivity.java");
        String guard = read("src/main/java/dev/jason/gboardpatches/extension/settings/"
                + "GboardRestartCrashRecoveryGuard.java");

        int superOnCreate = activity.indexOf("super.onCreate(savedInstanceState);");
        int deferredRestore = activity.indexOf(
                "scheduleRestartCrashRecoveryGuardRestore()");
        int prepare = activity.indexOf(
                "GboardRestartCrashRecoveryGuard.prepare(restartContext)");
        int schedule = activity.indexOf("screenRefreshHandler.postDelayed(", prepare);

        Assert.assertTrue(superOnCreate >= 0);
        Assert.assertTrue(deferredRestore > superOnCreate);
        Assert.assertTrue(prepare >= 0);
        Assert.assertTrue(schedule > prepare);
        Assert.assertTrue(activity.contains(
                "RESTART_CRASH_RECOVERY_RESTORE_DELAY_MS = 2000L"));
        Assert.assertTrue(activity.contains(
                "() -> restoreRestartCrashRecoveryGuard(restoreContext)"));
        Assert.assertTrue(activity.contains(
                "RESTART_CRASH_RECOVERY_RESTORE_DELAY_MS);"));
        Assert.assertTrue(guard.contains("createDeviceProtectedStorageContext()"));
        Assert.assertTrue(guard.contains(
                "PreferenceManager.getDefaultSharedPreferences(stockContext)"));
    }

    @Test
    public void restartCopyIsLocalized() throws Exception {
        String settingsText = read("src/main/settings-text/gboard_settings_text.xml");

        Assert.assertTrue(settingsText.contains(
                "<translation locale=\"en\">Restart Gboard</translation>"));
        Assert.assertTrue(settingsText.contains(
                "<translation locale=\"zh-Hant\">重新啟動 Gboard</translation>"));
        Assert.assertTrue(settingsText.contains("gboard_patches_restart_failed"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
