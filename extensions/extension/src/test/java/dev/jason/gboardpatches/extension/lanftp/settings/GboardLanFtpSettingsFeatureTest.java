package dev.jason.gboardpatches.extension.lanftp.settings;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.jason.gboardpatches.extension.lanftp.android.LanFtpRuntimeStatus;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpServerState;
import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

public final class GboardLanFtpSettingsFeatureTest {
    private static LanFtpServerState state(LanFtpPreferences.Snapshot settings,
            LanFtpRuntimeStatus runtime) {
        return new LanFtpServerState(settings.toConfigSnapshot(), runtime.active,
                runtime.status, runtime.endpoint, runtime.clients, runtime.transferCount,
                runtime.resumeSupported, runtime.passwordRevision, true, true);
    }
    @Test
    public void screenContainsOnlyUserFacingSettingsAndLiveRuntimeDetails() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://storage/tree/primary%3ADownload", true,
                "gboard", "abcdef",
                50000, 50009, 4, 20 * 60_000L);
        LanFtpRuntimeStatus runtime = new LanFtpRuntimeStatus(
                true, "Running", "ftp://192.168.1.20:2121",
                List.of(new LanFtpSessionInfo(
                        "00000000-0000-0000-0000-000000000001", "192.168.1.8",
                        10L, 20L, "RETR", "/folder/video.mp4", "download",
                        25L * 1024L * 1024L, 100L * 1024L * 1024L,
                        Math.round(12.5d * 1024L * 1024L))),
                0, true);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, runtime), GboardLanFtpSettingsFeature.Callbacks.noop());

        Assert.assertEquals(List.of(
                "Runtime status", "Endpoint", "Copy connection details", "Connected clients",
                "Disconnect client 192.168.1.8",
                "Enabled", "Keep connection when screen is off",
                "Control port", "Passive port range",
                "Root folder", "Read-only access",
                "Allow anonymous login", "Username", "Password", "Regenerate password"),
                screen.getRows().stream().map(row -> row.getTitle().toString()).toList());
        Assert.assertEquals("Status", screen.getSections().get(0).getTitle());
        Assert.assertEquals(0L, screen.getRefreshPolicy().getIntervalMs());
        Assert.assertFalse(screen.getRefreshPolicy().isLiveStatusOnly());
        Assert.assertEquals("abcdef", row(screen, "Password",
                GboardPatchesSettingsContract.SelectorRow.class).getCurrentValue());
        Assert.assertTrue(screen.getStatusBlocks().isEmpty());
        Assert.assertEquals("FTP Server", screen.getToolbarTitle());
        Assert.assertTrue(screen.getRows().stream()
                .anyMatch(row -> row.getTitle().toString().contains("Passive")));
        Assert.assertTrue(row(screen, "Passive port range",
                GboardPatchesSettingsContract.SelectorRow.class)
                .getSummary().contains("larger range does not increase transfer speed"));
        Assert.assertFalse(screen.getRows().stream()
                .anyMatch(row -> row.getTitle().toString().contains("sessions")));
        Assert.assertFalse(screen.getRows().stream()
                .anyMatch(row -> row.getTitle().toString().contains("timeout")));
        String clientSummary = row(screen, "Disconnect client 192.168.1.8",
                GboardPatchesSettingsContract.CommandRow.class).getSummary();
        Assert.assertTrue(clientSummary.contains("Login time: "
                + LanFtpSettingsDisplayFormatter.formatTimestamp(10L)));
        Assert.assertTrue(clientSummary.contains(
                LanFtpSettingsDisplayFormatter.formatTimestamp(10L)
                        + "\nLast activity: "
                + LanFtpSettingsDisplayFormatter.formatTimestamp(20L)));
        Assert.assertTrue(clientSummary.contains("Downloading from phone"));
        Assert.assertTrue(clientSummary.contains("video.mp4"));
        Assert.assertFalse(clientSummary.contains("/folder/"));
        Assert.assertFalse(clientSummary.contains("RETR"));
        Assert.assertTrue(clientSummary.contains("25MB / 100MB (25%)"));
        Assert.assertTrue(clientSummary.contains("12.5MB/s"));
    }

    @Test
    public void traditionalChineseCopyOmitsRemovedRows()
            throws Exception {
        String traditionalChinese = new String(Files.readAllBytes(Path.of(
                "src/main/settings-text/gboard_settings_text.xml")),
                StandardCharsets.UTF_8);

        Assert.assertTrue(traditionalChinese.contains(
                "<translation locale=\"zh-Hant\">FTP 伺服器</translation>"));
        Assert.assertFalse(traditionalChinese.contains("下載續傳"));
        Assert.assertFalse(traditionalChinese.contains("保留的未完成上傳"));
        Assert.assertFalse(traditionalChinese.contains("純 FTP 未加密"));
        Assert.assertFalse(traditionalChinese.contains("登入與傳輸速度"));
    }

    @Test
    public void disabledServerKeepsOnlyEnableAndControlPortInteractive() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                false, 2121, "content://storage/tree/primary%3ADownload", false,
                "gboard", "abcdef", true, 50000, 50009, 4, 20 * 60_000L);
        LanFtpRuntimeStatus runtime = new LanFtpRuntimeStatus(
                false, "Stopped", "", List.of(), 0, true);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, runtime), GboardLanFtpSettingsFeature.Callbacks.noop());

        Assert.assertTrue(row(screen, "Enabled",
                GboardPatchesSettingsContract.ToggleRow.class).isEnabled());
        Assert.assertTrue(row(screen, "Control port",
                GboardPatchesSettingsContract.SelectorRow.class).isEnabled());
        Assert.assertEquals(List.of("Enabled", "Control port"), screen.getRows().stream()
                .filter(GboardPatchesSettingsContract.Row::isEnabled)
                .map(item -> item.getTitle().toString())
                .toList());
        Assert.assertEquals(0L, screen.getRefreshPolicy().getIntervalMs());
        Assert.assertFalse(screen.getRefreshPolicy().isLiveStatusOnly());
    }

    @Test
    public void transientRuntimeUsesEventsInsteadOfARefreshTimer() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                false, 2121, LanFtpPreferences.SHARED_STORAGE_ROOT_URI, false,
                "gboard", "abcdef", true, 50000, 50009, 4, 20 * 60_000L);
        LanFtpRuntimeStatus runtime = new LanFtpRuntimeStatus(
                true, "Stopping", "ftp://192.168.1.20:2121", List.of(), 0);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, runtime),
                GboardLanFtpSettingsFeature.Callbacks.noop());

        Assert.assertEquals(0L, screen.getRefreshPolicy().getIntervalMs());
        Assert.assertFalse(screen.getRefreshPolicy().isLiveStatusOnly());
    }

    @Test
    public void activeUploadShowsCurrentSpeedAndExplainsUnknownPercentage() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://storage/tree/primary%3ADownload", false,
                "gboard", "abcdef", true, 50000, 50009, 4, 20 * 60_000L);
        LanFtpRuntimeStatus runtime = new LanFtpRuntimeStatus(
                true, "Running", "ftp://192.168.1.20:2121",
                List.of(new LanFtpSessionInfo(
                        "id", "192.168.1.8", 10L, 20L, "STOR", "/video.mp4",
                        "upload", 28L * 1024L * 1024L, -1L,
                        Math.round(9.75d * 1024L * 1024L))),
                1);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, runtime), GboardLanFtpSettingsFeature.Callbacks.noop());
        String clientSummary = row(screen, "Disconnect client 192.168.1.8",
                GboardPatchesSettingsContract.CommandRow.class).getSummary();

        Assert.assertTrue(clientSummary.contains("28MB • 9.75MB/s • Total size unknown"));
        Assert.assertTrue(clientSummary.contains("Uploading to phone"));
        Assert.assertFalse(clientSummary.contains("STOR"));
        Assert.assertFalse(clientSummary.contains("%"));
    }

    @Test
    public void rootFolderRowRequestsDocumentTreeAndForwardsSelection() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        CapturingCallbacks callbacks = new CapturingCallbacks();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://old/tree", true, "gboard",
                "abcdef", 50000, 50009, 4, 1_200_000L);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, LanFtpRuntimeStatus.unavailable()), callbacks);
        row(screen, "Root folder", GboardPatchesSettingsContract.SelectorRow.class)
                .getAction().run();

        host.choiceConsumer.accept("choose-folder");
        Assert.assertEquals("content://old/tree", host.initialTreeUri);
        host.treeConsumer.accept("content://new/tree");
        Assert.assertEquals("content://new/tree", callbacks.rootTreeUri);
        Assert.assertEquals(1, host.refreshCount);
    }

    @Test
    public void invalidPassiveRangeReportsActionableMessageWithoutEscapingCallback() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://old/tree", true, "gboard",
                "abcdef", 50000, 50009, 4, 1_200_000L);
        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, LanFtpRuntimeStatus.unavailable()),
                GboardLanFtpSettingsFeature.Callbacks.noop());

        row(screen, "Passive port range", GboardPatchesSettingsContract.SelectorRow.class)
                .getAction().run();
        host.textConsumer.accept("not-a-range");
        Assert.assertEquals("Invalid passive port range", host.message);
        Assert.assertEquals(1, host.refreshCount);

        host.textConsumer.accept("50000-50040");
        Assert.assertTrue(host.message.contains("32 ports"));
        Assert.assertEquals(2, host.refreshCount);
    }

    @Test
    public void sharedStorageRootShowsPermissionEntryAndAnonymousToggle() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        CapturingCallbacks callbacks = new CapturingCallbacks();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, LanFtpPreferences.SHARED_STORAGE_ROOT_URI, false, "gboard",
                "abcdef", true,
                50000, 50009, 4, 1_200_000L);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, LanFtpRuntimeStatus.unavailable()), callbacks);
        Assert.assertEquals("/sdcard",
                row(screen, "Root folder", GboardPatchesSettingsContract.SelectorRow.class)
                        .getCurrentValue());
        row(screen, "All files access", GboardPatchesSettingsContract.CommandRow.class)
                .getAction().run();
        Assert.assertTrue(host.openedAllFilesAccess);
        row(screen, "Allow anonymous login", GboardPatchesSettingsContract.ToggleRow.class)
                .getToggleAction().accept(false);
        Assert.assertFalse(callbacks.allowAnonymous);
    }

    @Test
    public void screenOffProtectionOpensSystemBatteryPolicyAndHasTraditionalChineseCopy()
            throws Exception {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://old/tree", false, "gboard", "abcdef",
                true, 50000, 50009, 4, 1_200_000L);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, LanFtpRuntimeStatus.unavailable()),
                GboardLanFtpSettingsFeature.Callbacks.noop());
        GboardPatchesSettingsContract.CommandRow screenOffRow = row(
                screen, "Keep connection when screen is off",
                GboardPatchesSettingsContract.CommandRow.class);
        Assert.assertTrue(screenOffRow.getSummary().contains("No restrictions"));
        screenOffRow.getAction().run();
        Assert.assertTrue(host.openedBatteryOptimizationSettings);

        String traditionalChinese = new String(Files.readAllBytes(Path.of(
                "src/main/settings-text/gboard_settings_text.xml")),
                StandardCharsets.UTF_8);
        Assert.assertTrue(traditionalChinese.contains("關螢幕時保持連線"));
        Assert.assertTrue(traditionalChinese.contains("無限制"));
        Assert.assertTrue(traditionalChinese.contains("上傳至手機"));
        Assert.assertTrue(traditionalChinese.contains("從手機下載"));
        Assert.assertTrue(traditionalChinese.contains("閒置"));
    }

    @Test
    public void passwordRowAcceptsOnlySixLowercaseLetters() {
        GboardLanFtpSettingsFeature feature = GboardLanFtpSettingsFeature.forTests();
        CapturingHost host = new CapturingHost();
        CapturingCallbacks callbacks = new CapturingCallbacks();
        LanFtpPreferences.Snapshot settings = new LanFtpPreferences.Snapshot(
                true, 2121, "content://old/tree", false, "gboard", "abcdef",
                true, 50000, 50009, 4, 1_200_000L);

        GboardPatchesSettingsContract.Screen screen = feature.buildScreenForState(
                host, settings.toConfigSnapshot(), state(settings, LanFtpRuntimeStatus.unavailable()), callbacks);
        row(screen, "Password", GboardPatchesSettingsContract.SelectorRow.class)
                .getAction().run();
        host.textConsumer.accept("custom");
        Assert.assertEquals("custom", callbacks.password);
        Assert.assertEquals(1, host.refreshCount);

        callbacks.acceptPassword = false;
        host.textConsumer.accept("ABC123");
        Assert.assertEquals("Password must contain exactly six lowercase letters.", host.message);
        Assert.assertEquals(2, host.refreshCount);
    }

    private static <T extends GboardPatchesSettingsContract.Row> T row(
            GboardPatchesSettingsContract.Screen screen, String title, Class<T> rowType) {
        return screen.getRows().stream()
                .filter(item -> title.contentEquals(item.getTitle()))
                .filter(rowType::isInstance)
                .map(rowType::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing " + rowType.getSimpleName() + " titled " + title));
    }

    private static final class CapturingCallbacks
            implements GboardLanFtpSettingsFeature.Callbacks {
        private String rootTreeUri;
        private String kickedSessionId;
        private boolean allowAnonymous = true;
        private boolean acceptPassword = true;
        private String password;
        @Override public boolean setEnabled(boolean value) { return true; }
        @Override public boolean setControlPort(int value) { return true; }
        @Override public boolean setPassivePortRange(int start, int end) { return true; }
        @Override public boolean setRootTreeUri(String value) { rootTreeUri = value; return true; }
        @Override public boolean setReadOnly(boolean value) { return true; }
        @Override public boolean setAllowAnonymous(boolean value) {
            allowAnonymous = value;
            return true;
        }
        @Override public boolean setPassword(String value) {
            if (!acceptPassword) {
                return false;
            }
            password = value;
            return true;
        }
        @Override public String regeneratePassword() { return "replacement"; }
        @Override public boolean kickSession(String sessionId) {
            kickedSessionId = sessionId;
            return true;
        }
    }

    private static final class CapturingHost implements GboardPatchesSettingsContract.Host {
        private String initialTreeUri;
        private GboardPatchesSettingsContract.StringValueConsumer treeConsumer;
        private GboardPatchesSettingsContract.TextValueConsumer textConsumer;
        private GboardPatchesSettingsContract.StringValueConsumer choiceConsumer;
        private String message;
        private int refreshCount;
        private boolean openedAllFilesAccess;
        private boolean openedBatteryOptimizationSettings;
        @Override public Context getContext() { return null; }
        @Override public void refresh() { refreshCount++; }
        @Override public void openFeature(GboardPatchesSettingsContract.Feature feature) { }
        @Override public void showChoiceDialog(String title, String[] labels, String[] values,
                String currentValue, String customValue, Runnable customAction,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
            choiceConsumer = valueConsumer;
        }
        @Override public void showPositiveIntegerDialog(String title, String hint,
                int initialValue, GboardPatchesSettingsContract.PositiveIntegerConsumer consumer) { }
        @Override public void showTextInputDialog(String title, String hint, String initialValue,
                GboardPatchesSettingsContract.TextValueConsumer consumer) {
            textConsumer = consumer;
        }
        @Override public void showMessage(String value) { message = value; }
        @Override public void showPreviewDialog(
                GboardPatchesSettingsContract.PreviewSpec previewSpec) { }
        @Override public void showManagedDialog(
                GboardPatchesSettingsContract.ManagedDialogAction action) { }
        @Override public void createTextDocument(String fileName, String mimeType, String text,
                Runnable completionAction) { }
        @Override public void openTextDocument(String[] mimeTypes,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) { }
        @Override public void openDocumentTree(String initialTreeUri,
                GboardPatchesSettingsContract.StringValueConsumer valueConsumer) {
            this.initialTreeUri = initialTreeUri;
            treeConsumer = valueConsumer;
        }
        @Override public void openAllFilesAccessSettings(String unavailableMessage) {
            openedAllFilesAccess = true;
        }
        @Override public void openBatteryOptimizationSettings(String unavailableMessage) {
            openedBatteryOptimizationSettings = true;
        }
    }
}
