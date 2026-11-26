package dev.jason.gboardpatches.extension.webclipboard;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;

public final class WebClipboardMediaStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static byte[] samplePng(int width, int height) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        bytes[12] = 'I';
        bytes[13] = 'H';
        bytes[14] = 'D';
        bytes[15] = 'R';
        bytes[16] = (byte) ((width >>> 24) & 0xFF);
        bytes[17] = (byte) ((width >>> 16) & 0xFF);
        bytes[18] = (byte) ((width >>> 8) & 0xFF);
        bytes[19] = (byte) (width & 0xFF);
        bytes[20] = (byte) ((height >>> 24) & 0xFF);
        bytes[21] = (byte) ((height >>> 16) & 0xFF);
        bytes[22] = (byte) ((height >>> 8) & 0xFF);
        bytes[23] = (byte) (height & 0xFF);
        return bytes;
    }

    @Test
    public void detectsImageMagicBytesAndRejectsNonImages() {
        Assert.assertEquals("image/png",
                WebClipboardMediaStore.detectMimeType(samplePng(12, 34), "application/octet-stream"));
        Assert.assertNull(WebClipboardMediaStore.detectMimeType(
                "<svg></svg>".getBytes(StandardCharsets.UTF_8), "image/svg+xml"));
        Assert.assertNull(WebClipboardMediaStore.detectMimeType(
                "not an image".getBytes(StandardCharsets.UTF_8), "image/png"));
    }

    @Test
    public void storesReadsEvictsAndClearsDiskBackedMedia() throws Exception {
        File root = temporaryFolder.newFolder("media-store");
        WebClipboardMediaStore store = new WebClipboardMediaStore(root, 2, 1024 * 1024);

        WebClipboardMediaStore.MediaEntry first = store.put(samplePng(10, 20), "image/png", "one.png");
        WebClipboardMediaStore.MediaEntry second = store.put(samplePng(30, 40), "image/png", "two.png");
        Assert.assertEquals(10, first.width);
        Assert.assertEquals(20, first.height);
        Assert.assertArrayEquals(samplePng(10, 20), store.readBytes(first.id));
        Assert.assertNull(store.readBytes("../" + first.id));

        WebClipboardMediaStore.MediaEntry third = store.put(samplePng(50, 60), "image/png", "three.png");
        Assert.assertNull(store.get(first.id));
        Assert.assertNotNull(store.get(second.id));
        Assert.assertNotNull(store.get(third.id));

        store.clear();
        Assert.assertNull(store.get(second.id));
        Assert.assertNull(store.get(third.id));
        Assert.assertEquals(0, store.size());

        store.close();
    }
}
