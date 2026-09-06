package dev.jason.gboardpatches.extension.lanftp.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpControllerArchitectureSourceTest {
    @Test
    public void settingsTileAndNotificationUseOnlyTheControllerSeam() throws Exception {
        String settings = read("lanftp/settings/GboardLanFtpSettingsFeature.java");
        String tile = read("lanftp/android/LanFtpTileService.java");
        String notification = read("lanftp/android/LanFtpNotificationController.java");
        for (String caller : new String[]{settings, tile, notification}) {
            Assert.assertTrue(caller.contains("LanFtpServerController"));
            Assert.assertFalse(caller.contains("LanFtpPreferences"));
            Assert.assertFalse(caller.contains("LanFtpStatusProvider"));
            Assert.assertFalse(caller.contains("LanFtpServiceCommand"));
            Assert.assertFalse(caller.contains("LanFtpEngine"));
        }
        Assert.assertTrue(settings.contains("settings.withControlPort(value)"));
        Assert.assertTrue(settings.contains("controller.applyConfig("));
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(
                Path.of("src/main/java/dev/jason/gboardpatches/extension/")
                        .resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
