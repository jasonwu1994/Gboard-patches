package dev.jason.gboardpatches.extension.backuprestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class GboardExportFileNamesTest {
    @Test
    public void timestampIsInsertedBeforeExtension() throws Exception {
        Date localDate = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
                .parse("20260830_201415");

        assertEquals("gboard-patches-backup_20260830_201415.json",
                GboardExportFileNames.timestamped("gboard-patches-backup.json", localDate));
    }

    @Test
    public void timestampedImportResolvesToCanonicalName() {
        assertEquals("flag_override.xml", GboardExportFileNames.canonicalImportedName(
                "folder/flag_override_20260830_201415.xml", "flag_override.xml"));
        assertEquals("flags_jetpack_data_store.pb",
                GboardExportFileNames.canonicalImportedName(
                        "flags_jetpack_data_store_20260830_201415.pb",
                        "flags_jetpack_data_store.pb"));
        assertNull(GboardExportFileNames.canonicalImportedName(
                "flag_override_20260830.xml", "flag_override.xml"));
    }
}
