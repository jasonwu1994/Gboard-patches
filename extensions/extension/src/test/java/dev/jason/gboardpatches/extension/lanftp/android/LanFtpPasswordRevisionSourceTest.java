package dev.jason.gboardpatches.extension.lanftp.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpPasswordRevisionSourceTest {
    @Test
    public void revisionCrossesEveryProcessAndControllerBoundary() throws Exception {
        String preferences = read("lanftp/config/LanFtpPreferences.java");
        String command = read("lanftp/android/LanFtpServiceCommand.java");
        String service = read("lanftp/android/LanFtpService.java");
        String status = read("lanftp/android/LanFtpRuntimeStatus.java");
        String provider = read("lanftp/android/LanFtpStatusProvider.java");
        String controller = read("lanftp/android/LanFtpServerController.java");
        Assert.assertTrue(preferences.contains("PREF_KEY_PASSWORD_REVISION"));
        Assert.assertTrue(command.contains("EXTRA_PASSWORD_REVISION"));
        Assert.assertTrue(service.contains("activePasswordRevision = command.passwordRevision()"));
        Assert.assertTrue(status.contains("passwordRevision"));
        Assert.assertTrue(provider.contains("KEY_PASSWORD_REVISION"));
        Assert.assertTrue(controller.contains("KEY_PASSWORD_REVISION"));
    }

    private static String read(String relativePath) throws Exception {
        return new String(Files.readAllBytes(
                Path.of("src/main/java/dev/jason/gboardpatches/extension/")
                        .resolve(relativePath)), StandardCharsets.UTF_8);
    }
}
