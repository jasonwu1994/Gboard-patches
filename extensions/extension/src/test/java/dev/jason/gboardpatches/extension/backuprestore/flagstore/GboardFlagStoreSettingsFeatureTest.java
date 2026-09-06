package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GboardFlagStoreSettingsFeatureTest {
    @Test
    public void importsAreNotRestrictedByTheObservedFileCombination() throws Exception {
        String feature = source("src/main/java/dev/jason/gboardpatches/extension/"
                + "backuprestore/flagstore/GboardFlagStoreSettingsFeature.java");
        String manager = source("src/main/java/dev/jason/gboardpatches/extension/"
                + "backuprestore/flagstore/GboardFlagStoreManager.java");

        assertFalse(feature.contains("isCompatible("));
        assertFalse(feature.contains("gboard_flag_store_replacement_backend_mismatch"));
        assertFalse(manager.contains("validateBackendCompatibility"));
    }

    @Test
    public void mixedFormatCopyExplainsAmbiguityAndAllFileTimesAreShown()
            throws Exception {
        String strings = source("src/main/settings-text/gboard_settings_text.xml");
        String feature = source("src/main/java/dev/jason/gboardpatches/extension/"
                + "backuprestore/flagstore/GboardFlagStoreSettingsFeature.java");

        assertTrue(strings.contains(
                "cannot be determined which format Gboard will ultimately read"));
        assertTrue(strings.contains("無法確認 Gboard 最後會讀取哪一種格式"));
        assertTrue(feature.contains("getProtoStoreLastModified()"));
        assertTrue(feature.contains("getLegacyOverrideLastModified()"));
        assertTrue(feature.contains("getLegacyValueLastModified()"));
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
