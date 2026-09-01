package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Version-neutral target-context module for inspecting, staging, and atomically replacing
 * Gboard flag-store files.
 */
public final class GboardFlagStoreManager {
    private static final String PENDING_MARKER = "pending";
    private static final String LAST_RESULT_FILE = "last_result.txt";
    private static final long MAX_IMPORT_BYTES = 512L * 1024L;

    private GboardFlagStoreManager() {
    }

    public static Inspection inspect(Context context) {
        Context appContext = GboardFlagStoreFiles.applicationContext(context);
        File protoStore = GboardFlagStoreFiles.targetFile(appContext,
                GboardFlagStoreFiles.PROTO_FILE);
        File legacyOverride = GboardFlagStoreFiles.targetFile(appContext,
                GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE);
        File legacyValue = GboardFlagStoreFiles.targetFile(appContext,
                GboardFlagStoreFiles.LEGACY_VALUE_FILE);
        GboardFlagStorageStatus status = GboardFlagStorageStatus.fromPresence(
                protoStore.isFile(), legacyOverride.isFile(), legacyValue.isFile(),
                protoStore.lastModified(), legacyOverride.lastModified(),
                legacyValue.lastModified());
        File staging = GboardFlagStoreFiles.stagingDirectory(appContext);
        return new Inspection(status, stagedFiles(staging), readLastResult(staging));
    }

    public static byte[] export(Context context, String name) throws Exception {
        File source = GboardFlagStoreFiles.targetFile(context,
                GboardFlagStoreFiles.requireCanonicalName(name));
        if (!source.isFile()) {
            throw new IllegalArgumentException("Requested flag-store file does not exist");
        }
        return readBytes(source);
    }

    public static byte[] read(Context context, String name) throws Exception {
        File source = GboardFlagStoreFiles.targetFile(context,
                GboardFlagStoreFiles.requireCanonicalName(name));
        return source.isFile() ? readBytes(source) : new byte[0];
    }

    public static Inspection stage(Context context, String name, byte[] data) throws Exception {
        Context appContext = GboardFlagStoreFiles.applicationContext(context);
        String canonicalName = GboardFlagStoreFiles.requireCanonicalName(name);
        validatePayload(canonicalName, data);
        File staging = GboardFlagStoreFiles.stagingDirectory(appContext);
        if (!staging.isDirectory() && !staging.mkdirs()) {
            throw new IllegalStateException("Unable to create flag-store staging directory");
        }
        clearPendingFiles(staging);
        writeAtomically(new File(staging, canonicalName), data);
        writeAtomically(new File(staging, PENDING_MARKER),
                "pending".getBytes(StandardCharsets.UTF_8));
        return inspect(appContext);
    }

    public static void applyPendingAtColdStart(Context context) {
        Context appContext = GboardFlagStoreFiles.applicationContext(context);
        File staging = GboardFlagStoreFiles.stagingDirectory(appContext);
        if (!new File(staging, PENDING_MARKER).isFile()) {
            return;
        }
        List<String> staged = stagedFiles(staging);
        if (staged.isEmpty()) {
            writeLastResult(staging, "failed: pending marker has no staged files");
            return;
        }
        List<String> touched = new ArrayList<>();
        try {
            for (String name : staged) {
                File target = GboardFlagStoreFiles.targetFile(appContext, name);
                File rollback = new File(staging, name + ".rollback");
                File absent = new File(staging, name + ".absent");
                deleteIfExists(rollback);
                deleteIfExists(absent);
                if (target.isFile()) {
                    copyAndSync(target, rollback);
                } else {
                    writeBytes(absent, new byte[0]);
                }
                touched.add(name);
                atomicReplace(new File(staging, name), target);
                verifySameBytes(new File(staging, name), target);
            }
            for (String name : staged) {
                deleteIfExists(new File(staging, name));
                deleteIfExists(new File(staging, name + ".rollback"));
                deleteIfExists(new File(staging, name + ".absent"));
            }
            deleteIfExists(new File(staging, PENDING_MARKER));
            writeLastResult(staging, "success: " + String.join(", ", staged));
        } catch (Throwable failure) {
            StringBuilder rollbackFailures = new StringBuilder();
            for (String name : touched) {
                try {
                    File target = GboardFlagStoreFiles.targetFile(appContext, name);
                    File rollback = new File(staging, name + ".rollback");
                    File absent = new File(staging, name + ".absent");
                    if (rollback.isFile()) {
                        atomicReplace(rollback, target);
                    } else if (absent.isFile()) {
                        deleteIfExists(target);
                    }
                } catch (Throwable rollbackFailure) {
                    if (rollbackFailures.length() > 0) {
                        rollbackFailures.append("; ");
                    }
                    rollbackFailures.append(name).append(": ")
                            .append(rollbackFailure.getClass().getSimpleName())
                            .append(": ").append(String.valueOf(rollbackFailure.getMessage()));
                }
            }
            String result = "failed: " + failure.getClass().getSimpleName()
                    + ": " + String.valueOf(failure.getMessage());
            if (rollbackFailures.length() > 0) {
                result += "; rollback failed: " + rollbackFailures;
            }
            writeLastResult(staging, result);
        }
    }

    static void validatePayload(String name, byte[] data) throws Exception {
        GboardFlagStoreFiles.requireCanonicalName(name);
        if (data == null || data.length == 0 || data.length > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("Flag-store file size is invalid");
        }
        if (GboardFlagStoreFiles.isProto(name)) {
            validateProto(data);
            return;
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        String rootName = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(data))
                .getDocumentElement()
                .getTagName();
        if (!"map".equals(rootName)) {
            throw new IllegalArgumentException("SharedPreferences XML root must be <map>");
        }
    }

    private static void validateProto(byte[] data) {
        int offset = 0;
        boolean mapEntrySeen = false;
        while (offset < data.length) {
            Varint tag = readVarint(data, offset);
            offset = tag.next;
            int field = (int) (tag.value >>> 3);
            int wire = (int) (tag.value & 7L);
            if (field <= 0) {
                throw new IllegalArgumentException("PB contains an invalid field number");
            }
            if (field == 1 && wire == 2) {
                mapEntrySeen = true;
            }
            offset = skipValue(data, offset, wire);
        }
        if (!mapEntrySeen) {
            throw new IllegalArgumentException("PB does not contain a Gboard flag map entry");
        }
    }

    private static int skipValue(byte[] data, int offset, int wire) {
        return switch (wire) {
            case 0 -> readVarint(data, offset).next;
            case 1 -> checkedAdvance(data, offset, 8);
            case 2 -> {
                Varint size = readVarint(data, offset);
                if (size.value > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("PB length is too large");
                }
                yield checkedAdvance(data, size.next, (int) size.value);
            }
            case 5 -> checkedAdvance(data, offset, 4);
            default -> throw new IllegalArgumentException("Unsupported PB wire type: " + wire);
        };
    }

    private static int checkedAdvance(byte[] data, int offset, int count) {
        long end = (long) offset + count;
        if (count < 0 || end > data.length) {
            throw new IllegalArgumentException("PB is truncated");
        }
        return (int) end;
    }

    private static Varint readVarint(byte[] data, int offset) {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (offset >= data.length) {
                throw new IllegalArgumentException("PB varint is truncated");
            }
            int next = data[offset++] & 0xff;
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                return new Varint(value, offset);
            }
        }
        throw new IllegalArgumentException("PB varint is malformed");
    }

    private static List<String> stagedFiles(File staging) {
        if (!staging.isDirectory()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (String name : GboardFlagStoreFiles.canonicalNames()) {
            if (new File(staging, name).isFile()) {
                names.add(name);
            }
        }
        return names;
    }

    private static void clearPendingFiles(File staging) throws Exception {
        for (String name : GboardFlagStoreFiles.canonicalNames()) {
            deleteIfExists(new File(staging, name));
            deleteIfExists(new File(staging, name + ".rollback"));
            deleteIfExists(new File(staging, name + ".absent"));
        }
        deleteIfExists(new File(staging, PENDING_MARKER));
    }

    private static void writeAtomically(File target, byte[] data) throws Exception {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create parent directory");
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        writeBytes(temporary, data);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static void atomicReplace(File source, File target) throws Exception {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create flag-store directory");
        }
        File temporary = new File(parent, target.getName() + ".gboard-patches.tmp");
        copyAndSync(source, temporary);
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    private static void copyAndSync(File source, File target) throws Exception {
        try (FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }
    }

    private static void writeBytes(File target, byte[] data) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(data);
            output.getFD().sync();
        }
    }

    private static byte[] readBytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void verifySameBytes(File expected, File actual) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedDigest = digest.digest(readBytes(expected));
        digest.reset();
        byte[] actualDigest = digest.digest(readBytes(actual));
        if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
            throw new IllegalStateException("Flag-store read-back digest mismatch");
        }
    }

    private static String readLastResult(File staging) {
        File result = new File(staging, LAST_RESULT_FILE);
        if (!result.isFile()) {
            return "";
        }
        try {
            return new String(readBytes(result), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void writeLastResult(File staging, String value) {
        try {
            if (!staging.isDirectory() && !staging.mkdirs()) {
                return;
            }
            writeAtomically(new File(staging, LAST_RESULT_FILE),
                    value.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // Result persistence must never prevent Gboard startup.
        }
    }

    private static void deleteIfExists(File file) throws Exception {
        Files.deleteIfExists(file.toPath());
    }

    public static final class Inspection {
        private final GboardFlagStorageStatus status;
        private final List<String> stagedFiles;
        private final String lastApplyResult;

        Inspection(GboardFlagStorageStatus status, List<String> stagedFiles,
                String lastApplyResult) {
            this.status = status;
            this.stagedFiles = Collections.unmodifiableList(new ArrayList<>(stagedFiles));
            this.lastApplyResult = lastApplyResult;
        }

        public GboardFlagStorageStatus getStatus() { return status; }
        public List<String> getStagedFiles() { return stagedFiles; }
        public String getLastApplyResult() { return lastApplyResult; }
    }

    private static final class Varint {
        final long value;
        final int next;

        Varint(long value, int next) {
            this.value = value;
            this.next = next;
        }
    }
}
