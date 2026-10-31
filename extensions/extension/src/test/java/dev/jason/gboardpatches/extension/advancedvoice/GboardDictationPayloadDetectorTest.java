package dev.jason.gboardpatches.extension.advancedvoice;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GboardDictationPayloadDetectorTest {
    private static final String MORPHE_PACKAGE =
            "com.touchtype.swiftkey";
    private static final String OFFICIAL_PACKAGE =
            "com.google.android.inputmethod.latin";
    private static final String DICTATION_ENTRY =
            "lib/arm64-v8a/libdictation_jni.so";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsPayloadFusedIntoBaseApk() throws Exception {
        File baseApk = createArchive("base.apk", DICTATION_ENTRY);

        GboardDictationPayloadDetector.Detection detection =
                GboardDictationPayloadDetector.inspectPackageArchives(
                        MORPHE_PACKAGE,
                        Collections.singletonList(baseApk.getAbsolutePath()));

        assertEquals(GboardDictationPayloadDetector.Status.PRESENT, detection.getStatus());
        assertEquals(MORPHE_PACKAGE, detection.getPackageName());
    }

    @Test
    public void detectsPayloadInsideInstalledSplitApk() throws Exception {
        File baseApk = createArchive("base-without-payload.apk", "assets/base.txt");
        File splitApk = createArchive("split_dictation_feature_split.apk", DICTATION_ENTRY);

        GboardDictationPayloadDetector.Detection detection =
                GboardDictationPayloadDetector.inspectPackageArchives(
                        OFFICIAL_PACKAGE,
                        Arrays.asList(baseApk.getAbsolutePath(), splitApk.getAbsolutePath()));

        assertEquals(GboardDictationPayloadDetector.Status.PRESENT, detection.getStatus());
        assertEquals(OFFICIAL_PACKAGE, detection.getPackageName());
    }

    @Test
    public void readableArchivesWithoutExactEntryAreAbsent() throws Exception {
        File baseApk = createArchive("wrong-path.apk", "assets/libdictation_jni.so");
        File splitApk = createArchive(
                "wrong-name.apk",
                "lib/arm64-v8a/libdictation_jni.so.bak");

        GboardDictationPayloadDetector.Detection detection =
                GboardDictationPayloadDetector.inspectPackageArchives(
                        OFFICIAL_PACKAGE,
                        Arrays.asList(baseApk.getAbsolutePath(), splitApk.getAbsolutePath()));

        assertEquals(GboardDictationPayloadDetector.Status.ABSENT, detection.getStatus());
    }

    @Test
    public void unreadableArchivePreventsAnAbsentClaim() throws Exception {
        File unreadableArchive = temporaryFolder.newFolder("not-an-apk");

        GboardDictationPayloadDetector.Detection detection =
                GboardDictationPayloadDetector.inspectPackageArchives(
                        MORPHE_PACKAGE,
                        Collections.singletonList(unreadableArchive.getAbsolutePath()));

        assertEquals(GboardDictationPayloadDetector.Status.UNKNOWN, detection.getStatus());
    }

    @Test
    public void presentPayloadWinsOverAnUnreadableSibling() throws Exception {
        File unreadableArchive = temporaryFolder.newFolder("unreadable-split");
        File payloadArchive = createArchive("payload-base.apk", DICTATION_ENTRY);

        GboardDictationPayloadDetector.Detection detection =
                GboardDictationPayloadDetector.inspectPackageArchives(
                        MORPHE_PACKAGE,
                        Arrays.asList(
                                unreadableArchive.getAbsolutePath(),
                                payloadArchive.getAbsolutePath()));

        assertEquals(GboardDictationPayloadDetector.Status.PRESENT, detection.getStatus());
    }

    private File createArchive(String name, String... entryNames) throws Exception {
        File archive = temporaryFolder.newFile(name);
        try (ZipOutputStream output =
                     new ZipOutputStream(new FileOutputStream(archive))) {
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                output.write(new byte[]{1});
                output.closeEntry();
            }
        }
        return archive;
    }
}

