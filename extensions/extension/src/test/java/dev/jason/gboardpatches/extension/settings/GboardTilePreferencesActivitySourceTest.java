package dev.jason.gboardpatches.extension.settings;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardTilePreferencesActivitySourceTest {
    @Test
    public void genericTilePreferencesActivityDispatchesFromServiceMetadata()
            throws Exception {
        String activitySource = new String(
                Files.readAllBytes(Path.of(
                        "src/main/java/dev/jason/gboardpatches/extension/settings/"
                                + "GboardTilePreferencesActivity.java")),
                StandardCharsets.UTF_8);

        Assert.assertTrue(activitySource.contains("extends Activity"));
        Assert.assertTrue(activitySource.contains("Intent.EXTRA_COMPONENT_NAME"));
        Assert.assertTrue(activitySource.contains("getServiceInfo("));
        Assert.assertTrue(activitySource.contains("META_DATA_NAVIGATION_PATH"));
        Assert.assertTrue(activitySource.contains("EXTRA_NAVIGATION_PATH"));
        Assert.assertTrue(activitySource.contains("startActivity("));
        Assert.assertTrue(activitySource.contains("finish();"));
        Assert.assertFalse(activitySource.contains("WebClipboard"));
        Assert.assertFalse(activitySource.contains("LanFtp"));
    }
}
