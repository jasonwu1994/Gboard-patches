package dev.jason.gboardpatches.extension.lanftp.settings;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardLanFtpSafSourceTest {
    @Test
    public void settingsHostUsesPersistableOpenDocumentTreeGrant() throws Exception {
        String contract = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesSettingsContract.java");
        String activity = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "settings/GboardPatchesSettingsActivity.java");

        Assert.assertTrue(contract.contains("void openDocumentTree("));
        Assert.assertTrue(activity.contains("Intent.ACTION_OPEN_DOCUMENT_TREE"));
        Assert.assertTrue(activity.contains("Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION"));
        Assert.assertTrue(activity.contains("takePersistableUriPermission("));
        Assert.assertTrue(activity.contains("pendingDocumentTreeConsumer = null"));
    }

    @Test
    public void safStoreRevalidatesPersistedGrantAtEveryOperationBoundary() throws Exception {
        String store = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/fs/SafLanFtpDocumentStore.java");

        Assert.assertTrue(store.contains("private void requirePersistedGrant("));
        Assert.assertTrue(count(store, "requirePersistedGrant(false);") >= 3);
        Assert.assertTrue(count(store, "requirePersistedGrant(true);") >= 5);
        Assert.assertTrue(store.contains("grantFailureListener.run()"));
        Assert.assertTrue(store.contains("isProviderUnavailable("));
        Assert.assertTrue(store.contains("providerFailure("));
    }

    @Test
    public void interactiveSafListingHasAHardEntryLimit() throws Exception {
        String store = read("src/main/java/dev/jason/gboardpatches/extension/"
                + "lanftp/runtime/fs/SafLanFtpDocumentStore.java");

        Assert.assertTrue(store.contains("MAX_DIRECTORY_ENTRIES"));
        Assert.assertTrue(store.contains("Directory entry limit exceeded"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
