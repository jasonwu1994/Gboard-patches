package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClipboardSyncServiceSourceTest {
    @Test
    public void bindFailureToastUsesShortPortErrorAndMainThreadPost() throws Exception {
        String source = new String(
                Files.readAllBytes(Path.of(
                        "src/main/java/dev/jason/gboardpatches/extension/webclipboard/"
                                + "ClipboardSyncService.java")),
                StandardCharsets.UTF_8);

        Assert.assertTrue(source.contains("Web Clipboard could not start on this port."));
        Assert.assertTrue(source.contains("new android.os.Handler(android.os.Looper.getMainLooper())"));
        Assert.assertTrue(source.contains("Toast.makeText("));
    }

    @Test
    public void monotonicClipTimestampPreventsSameMillisecondHistoryIdCollision() {
        long base = 1_700_000_000_000L;
        long first = ClipboardSyncService.nextMonotonicClipTimestampMs(base);
        long second = ClipboardSyncService.nextMonotonicClipTimestampMs(base);
        long third = ClipboardSyncService.nextMonotonicClipTimestampMs(base);

        Assert.assertTrue(second > first);
        Assert.assertTrue(third > second);
    }
}
