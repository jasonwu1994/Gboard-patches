package dev.jason.gboardpatches.extension.webclipboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class WebClipboardMediaStore {
    public static final int MAX_IMAGE_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_MAX_ITEMS = 20;
    public static final long DEFAULT_MAX_TOTAL_BYTES = 48L * 1024L * 1024L;

    public static final String MIME_PNG = "image/png";
    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_WEBP = "image/webp";
    public static final String MIME_GIF = "image/gif";

    private static final AtomicLong NEXT_ID_SEQUENCE = new AtomicLong(1L);

    private final File storageDir;
    private final int maxItems;
    private final long maxTotalBytes;
    private final Object lock = new Object();
    private final LinkedHashMap<String, StoredMedia> entries = new LinkedHashMap<>();
    private long totalBytes;

    public WebClipboardMediaStore() {
        this(null, DEFAULT_MAX_ITEMS, DEFAULT_MAX_TOTAL_BYTES);
    }

    public WebClipboardMediaStore(File storageDir) {
        this(storageDir, DEFAULT_MAX_ITEMS, DEFAULT_MAX_TOTAL_BYTES);
    }

    public WebClipboardMediaStore(File storageDir, int maxItems, long maxTotalBytes) {
        this.storageDir = storageDir;
        this.maxItems = Math.max(1, maxItems);
        this.maxTotalBytes = Math.max(MAX_IMAGE_BYTES, maxTotalBytes);
    }

    public static final class MediaEntry {
        public final String id;
        public final String mimeType;
        public final String sha256;
        public final int sizeBytes;
        public final int width;
        public final int height;
        public final String fileName;
        public final long createdAtMs;

        MediaEntry(String id, String mimeType, String sha256, int sizeBytes, int width,
                int height, String fileName, long createdAtMs) {
            this.id = id;
            this.mimeType = mimeType;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.width = width;
            this.height = height;
            this.fileName = fileName;
            this.createdAtMs = createdAtMs;
        }
    }

    public MediaEntry put(byte[] bytes, String declaredMimeType, String rawFileName)
            throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty_image");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("payload_too_large");
        }
        String mimeType = detectMimeType(bytes, declaredMimeType);
        if (mimeType == null) {
            throw new IllegalArgumentException("unsupported_image_type");
        }
        String sha256 = sha256Hex(bytes);
        int[] dimensions = parseDimensions(bytes, mimeType);
        int width = dimensions[0];
        int height = dimensions[1];
        String id = "img-" + NEXT_ID_SEQUENCE.getAndIncrement() + "-" + shortHash(sha256);
        String fileName = sanitizeFileName(rawFileName, mimeType, id);
        long nowMs = System.currentTimeMillis();
        MediaEntry entry = new MediaEntry(
                id,
                mimeType,
                sha256,
                bytes.length,
                width,
                height,
                fileName,
                nowMs);

        File persistedFile = null;
        byte[] memoryCopy = null;
        if (storageDir != null) {
            if (!storageDir.exists() && !storageDir.mkdirs() && !storageDir.isDirectory()) {
                throw new IOException("Unable to create media cache directory");
            }
            persistedFile = new File(storageDir, id + extensionForMimeType(mimeType));
            writeFile(persistedFile, bytes);
        } else {
            memoryCopy = bytes.clone();
        }

        synchronized (lock) {
            entries.put(id, new StoredMedia(entry, persistedFile, memoryCopy));
            totalBytes += bytes.length;
            evictOverflowLocked();
        }
        return entry;
    }

    public MediaEntry get(String id) {
        if (!isSafeId(id)) {
            return null;
        }
        synchronized (lock) {
            StoredMedia stored = entries.get(id);
            return stored == null ? null : stored.entry;
        }
    }

    public byte[] readBytes(String id) throws IOException {
        if (!isSafeId(id)) {
            return null;
        }
        StoredMedia stored;
        synchronized (lock) {
            stored = entries.get(id);
        }
        if (stored == null) {
            return null;
        }
        if (stored.file != null) {
            if (!stored.file.isFile()) {
                return null;
            }
            return readFile(stored.file);
        }
        return stored.memoryBytes == null ? null : stored.memoryBytes.clone();
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public void clear() {
        synchronized (lock) {
            for (StoredMedia stored : entries.values()) {
                deleteQuietly(stored.file);
            }
            entries.clear();
            totalBytes = 0L;
        }
        cleanupResidualFiles();
    }

    public void close() {
        clear();
        if (storageDir != null) {
            deleteQuietly(storageDir);
        }
    }

    private void evictOverflowLocked() {
        Iterator<Map.Entry<String, StoredMedia>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()
                && (entries.size() > maxItems || totalBytes > maxTotalBytes)) {
            StoredMedia oldest = iterator.next().getValue();
            iterator.remove();
            totalBytes = Math.max(0L, totalBytes - oldest.entry.sizeBytes);
            deleteQuietly(oldest.file);
        }
    }

    private void cleanupResidualFiles() {
        if (storageDir == null || !storageDir.isDirectory()) {
            return;
        }
        File[] children = storageDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteQuietly(child);
        }
    }

    public static String detectMimeType(byte[] bytes, String declaredMimeType) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        String sniffed = sniffMagicMimeType(bytes);
        if (sniffed != null) {
            return sniffed;
        }
        String normalizedDeclared = normalizeSupportedMimeType(declaredMimeType);
        if (normalizedDeclared != null) {
            // Only accept a declared raster MIME type when magic bytes match; reject disguised payloads.
            return null;
        }
        return null;
    }

    public static String normalizeSupportedMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.US);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        if ("image/png".equals(normalized)) {
            return MIME_PNG;
        }
        if ("image/jpeg".equals(normalized) || "image/jpg".equals(normalized)) {
            return MIME_JPEG;
        }
        if ("image/webp".equals(normalized)) {
            return MIME_WEBP;
        }
        if ("image/gif".equals(normalized)) {
            return MIME_GIF;
        }
        return null;
    }

    public static String extensionForMimeType(String mimeType) {
        String normalized = normalizeSupportedMimeType(mimeType);
        if (MIME_JPEG.equals(normalized)) {
            return ".jpg";
        }
        if (MIME_WEBP.equals(normalized)) {
            return ".webp";
        }
        if (MIME_GIF.equals(normalized)) {
            return ".gif";
        }
        return ".png";
    }

    public static String sanitizeFileName(String rawFileName, String mimeType, String fallbackStem) {
        String extension = extensionForMimeType(mimeType);
        String stem = fallbackStem == null || fallbackStem.isBlank() ? "image" : fallbackStem;
        if (rawFileName == null || rawFileName.isBlank()) {
            return stem + extension;
        }
        String trimmed = rawFileName.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash >= 0) {
            trimmed = trimmed.substring(slash + 1);
        }
        StringBuilder cleaned = new StringBuilder();
        for (int index = 0; index < trimmed.length() && cleaned.length() < 96; index++) {
            char current = trimmed.charAt(index);
            if ((current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '-'
                    || current == '_'
                    || current == '.'
                    || current == ' ') {
                cleaned.append(current);
            }
        }
        String result = cleaned.toString().trim();
        while (result.startsWith(".")) {
            result = result.substring(1).trim();
        }
        if (result.isEmpty()) {
            return stem + extension;
        }
        String lower = result.toLowerCase(Locale.US);
        if (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif")) {
            return result;
        }
        return result + extension;
    }

    public static boolean isSafeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 96) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char current = id.charAt(index);
            boolean allowed = (current >= 'a' && current <= 'z')
                    || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9')
                    || current == '-'
                    || current == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(Character.forDigit((current >> 4) & 0xF, 16));
                builder.append(Character.forDigit(current & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return "0";
        }
        return hash.length() <= 10 ? hash : hash.substring(0, 10);
    }

    private static String sniffMagicMimeType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A) {
            return MIME_PNG;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF) {
            return MIME_JPEG;
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8'
                && (bytes[4] == '7' || bytes[4] == '9')
                && bytes[5] == 'a') {
            return MIME_GIF;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return MIME_WEBP;
        }
        return null;
    }

    public static int[] parseDimensions(byte[] bytes, String mimeType) {
        if (bytes == null || mimeType == null) {
            return new int[] {0, 0};
        }
        if (MIME_PNG.equals(mimeType)) {
            return parsePngDimensions(bytes);
        }
        if (MIME_GIF.equals(mimeType)) {
            return parseGifDimensions(bytes);
        }
        if (MIME_JPEG.equals(mimeType)) {
            return parseJpegDimensions(bytes);
        }
        if (MIME_WEBP.equals(mimeType)) {
            return parseWebpDimensions(bytes);
        }
        return new int[] {0, 0};
    }

    private static int[] parsePngDimensions(byte[] bytes) {
        if (bytes.length < 24) {
            return new int[] {0, 0};
        }
        int width = readIntBe(bytes, 16);
        int height = readIntBe(bytes, 20);
        return sanitizeDimensions(width, height);
    }

    private static int[] parseGifDimensions(byte[] bytes) {
        if (bytes.length < 10) {
            return new int[] {0, 0};
        }
        int width = readShortLe(bytes, 6);
        int height = readShortLe(bytes, 8);
        return sanitizeDimensions(width, height);
    }

    private static int[] parseJpegDimensions(byte[] bytes) {
        int offset = 2;
        while (offset + 4 <= bytes.length) {
            while (offset < bytes.length && (bytes[offset] & 0xFF) != 0xFF) {
                offset++;
            }
            while (offset < bytes.length && (bytes[offset] & 0xFF) == 0xFF) {
                offset++;
            }
            if (offset >= bytes.length) {
                break;
            }
            int marker = bytes[offset++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD8)) {
                continue;
            }
            if (offset + 2 > bytes.length) {
                break;
            }
            int segmentLength = readShortBe(bytes, offset);
            if (segmentLength < 2 || offset + segmentLength > bytes.length) {
                break;
            }
            boolean isSof = (marker >= 0xC0 && marker <= 0xC3)
                    || (marker >= 0xC5 && marker <= 0xC7)
                    || (marker >= 0xC9 && marker <= 0xCB)
                    || (marker >= 0xCD && marker <= 0xCF);
            if (isSof && segmentLength >= 7) {
                int height = readShortBe(bytes, offset + 3);
                int width = readShortBe(bytes, offset + 5);
                return sanitizeDimensions(width, height);
            }
            offset += segmentLength;
        }
        return new int[] {0, 0};
    }

    private static int[] parseWebpDimensions(byte[] bytes) {
        if (bytes.length < 30) {
            return new int[] {0, 0};
        }
        String chunk = new String(bytes, 12, 4);
        if ("VP8X".equals(chunk) && bytes.length >= 30) {
            int width = 1 + readInt24Le(bytes, 24);
            int height = 1 + readInt24Le(bytes, 27);
            return sanitizeDimensions(width, height);
        }
        if ("VP8 ".equals(chunk) && bytes.length >= 30) {
            int width = readShortLe(bytes, 26) & 0x3FFF;
            int height = readShortLe(bytes, 28) & 0x3FFF;
            return sanitizeDimensions(width, height);
        }
        if ("VP8L".equals(chunk) && bytes.length >= 25 && (bytes[20] & 0xFF) == 0x2F) {
            int b0 = bytes[21] & 0xFF;
            int b1 = bytes[22] & 0xFF;
            int b2 = bytes[23] & 0xFF;
            int b3 = bytes[24] & 0xFF;
            int width = 1 + (((b1 & 0x3F) << 8) | b0);
            int height = 1 + (((b3 & 0x0F) << 10) | (b2 << 2) | ((b1 & 0xC0) >> 6));
            return sanitizeDimensions(width, height);
        }
        return new int[] {0, 0};
    }

    private static int[] sanitizeDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > 65_536 || height > 65_536) {
            return new int[] {0, 0};
        }
        return new int[] {width, height};
    }

    private static int readIntBe(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static int readShortBe(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static int readShortLe(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static int readInt24Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16);
    }

    private static void writeFile(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.flush();
        }
    }

    private static byte[] readFile(File file) throws IOException {
        long length = file.length();
        if (length <= 0 || length > MAX_IMAGE_BYTES) {
            return null;
        }
        byte[] result = new byte[(int) length];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < result.length) {
                int count = input.read(result, offset, result.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            if (offset != result.length) {
                return null;
            }
        }
        return result;
    }

    private static void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable ignored) {
            // Best effort cleanup.
        }
    }

    private static final class StoredMedia {
        final MediaEntry entry;
        final File file;
        final byte[] memoryBytes;

        StoredMedia(MediaEntry entry, File file, byte[] memoryBytes) {
            this.entry = entry;
            this.file = file;
            this.memoryBytes = memoryBytes;
        }
    }
}
