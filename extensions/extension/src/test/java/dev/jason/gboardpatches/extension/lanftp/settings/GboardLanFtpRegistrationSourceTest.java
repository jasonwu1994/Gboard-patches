package dev.jason.gboardpatches.extension.lanftp.settings;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardLanFtpRegistrationSourceTest {
    @Test
    public void featureIsRegisteredAtTheSettingsRoot() throws Exception {
        String registry = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesSettingsFeatureRegistry.java");

        Assert.assertTrue(registry.contains("new GboardLanFtpSettingsFeature(context)"));
        Assert.assertFalse(registry.contains("GboardClipboardFamilySettingsFeature(context), "
                + "new GboardLanFtpSettingsFeature"));
    }

    @Test
    public void markerRegistryOrderAndDynamicProviderAuthorityAreDeclared() throws Exception {
        String availability = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesFeatureAvailability.java");
        String registry = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesSettingsFeatureRegistry.java");
        String provider = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpStatusProvider.java");

        Assert.assertTrue(availability.contains(
                "dev.jason.gboardpatches.feature.lan_ftp_server"));
        int clipboard = registry.indexOf("new GboardClipboardSettingsFeature()");
        int ftp = registry.indexOf("new GboardLanFtpSettingsFeature(context)");
        int settingsHomepage = registry.indexOf("new GboardSettingsHomepageSettingsFeature()");
        Assert.assertTrue(clipboard >= 0);
        Assert.assertTrue(clipboard < ftp);
        Assert.assertTrue(ftp < settingsHomepage);
        Assert.assertTrue(provider.contains("AUTHORITY_SUFFIX = \".lan_ftp_status\""));
        Assert.assertTrue(provider.contains("context.getPackageName() + AUTHORITY_SUFFIX"));
        Assert.assertFalse(provider.contains("BuildConfig.APPLICATION_ID"));
    }

    @Test
    public void transferSpeedCrossesThePrivateProcessStatusCodec() throws Exception {
        String codec = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpSessionBundleCodec.java");

        Assert.assertTrue(codec.contains(
                "item.putLong(BYTES_PER_SECOND, session.bytesPerSecond())"));
        Assert.assertTrue(codec.contains(
                "item.getLong(BYTES_PER_SECOND, 0L)"));
    }

    @Test
    public void stalledTransferSchedulesARefreshThatExpiresTheDisplayedSpeed()
            throws Exception {
        String service = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/android/LanFtpService.java");

        Assert.assertTrue(service.contains(
                "TRANSFER_SPEED_EXPIRY_REFRESH_MS = 2_100L"));
        Assert.assertTrue(service.contains(
                "mainHandler.removeCallbacks(transferSpeedExpiryRefresh)"));
        Assert.assertTrue(service.contains(
                "mainHandler.postDelayed(\n"
                        + "                transferSpeedExpiryRefresh, "
                        + "TRANSFER_SPEED_EXPIRY_REFRESH_MS)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
