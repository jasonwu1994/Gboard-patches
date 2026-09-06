package dev.jason.gboardpatches.extension.lanftp.android;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpNoRetainedUploadSourceTest {
    @Test
    public void retainedUploadScanningAndManagementAreAbsent() throws Exception {
        Path main = Path.of("src/main/java/dev/jason/gboardpatches/extension/lanftp");
        Assert.assertFalse(Files.exists(main.resolve(
                "android/LanFtpPartialCleanup.java")));
        Assert.assertFalse(Files.exists(main.resolve(
                "android/LanFtpRetainedPartialStatusCache.java")));
        Assert.assertFalse(Files.exists(main.resolve(
                "android/LanFtpStorageGate.java")));
        Assert.assertFalse(Files.exists(main.resolve(
                "runtime/fs/LanFtpRetainedPartialSummary.java")));
        Assert.assertFalse(Files.exists(main.resolve(
                "runtime/fs/LanFtpTraversalSession.java")));
        Assert.assertFalse(Files.exists(main.resolve(
                "runtime/fs/LanFtpTraversalListing.java")));

        String service = new String(Files.readAllBytes(
                main.resolve("android/LanFtpService.java")), StandardCharsets.UTF_8);
        String provider = new String(Files.readAllBytes(
                main.resolve("android/LanFtpStatusProvider.java")), StandardCharsets.UTF_8);
        String settings = new String(Files.readAllBytes(
                main.resolve("settings/GboardLanFtpSettingsFeature.java")),
                StandardCharsets.UTF_8);
        String serviceCommand = new String(Files.readAllBytes(
                main.resolve("android/LanFtpServiceCommand.java")),
                StandardCharsets.UTF_8);
        String documentStore = new String(Files.readAllBytes(
                main.resolve("runtime/fs/LanFtpDocumentStore.java")),
                StandardCharsets.UTF_8);
        Assert.assertFalse(service.contains("retainedPartial"));
        Assert.assertFalse(service.contains("cleanupAllPartials"));
        Assert.assertFalse(provider.contains("CLEAR_PARTIAL"));
        Assert.assertFalse(provider.contains("RETAINED_PARTIAL"));
        Assert.assertFalse(settings.contains("Retained partial uploads"));
        Assert.assertFalse(settings.contains("clearRetainedPartials"));
        Assert.assertFalse(serviceCommand.contains("metadataAuthKey"));
        Assert.assertFalse(serviceCommand.contains("credentialGeneration"));
        Assert.assertFalse(documentStore.contains("openTraversal"));
    }
}
