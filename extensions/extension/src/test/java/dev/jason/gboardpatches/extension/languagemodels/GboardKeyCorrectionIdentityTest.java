package dev.jason.gboardpatches.extension.languagemodels;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.Files;
import dev.jason.gboardpatches.extension.languagemodels.GboardKeyCorrectionIdentity;

public class GboardKeyCorrectionIdentityTest {
    @Test public void onlyReviewedContentHashesIdentifyTheResourceVersion() {
        assertEquals(2024052201L, GboardKeyCorrectionIdentity.versionForHash("11333d811d060b6dd06de02ced8dd71717696a39228d7b083b697005aedbf248"));
        assertEquals(2020082801L, GboardKeyCorrectionIdentity.versionForHash("6802978e328616a2d7de1e23347cd7daf896ac0d0e2be5771f4921a86628692c"));
        assertEquals(0, GboardKeyCorrectionIdentity.versionForHash("unknown"));
    }

    @Test public void aModelLikeFilenameDoesNotIdentifyUnknownContent() throws Exception {
        var directory = Files.createTempDirectory("key-correction-identity");
        var file = directory.resolve("kc_pair_en_us_7900.tflite");
        try {
            Files.write(file, new byte[]{97, 98, 99});
            String hash = GboardKeyCorrectionIdentity.hash(file.toFile());
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
            assertEquals(0, GboardKeyCorrectionIdentity.versionForHash(hash));
            Files.write(file, new byte[262145]);
            assertEquals("", GboardKeyCorrectionIdentity.hash(file.toFile()));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
