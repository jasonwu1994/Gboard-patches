package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class LanFtpPath {
    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_SEGMENT_LENGTH = 255;
    private static final String PRIVATE_PREFIX = ".lanftp-";

    private LanFtpPath() {
    }

    public static String resolve(String workingDirectory, String requestedPath) {
        String safeWorkingDirectory = normalizeInput(workingDirectory, "workingDirectory");
        String safeRequestedPath = normalizeInput(requestedPath, "requestedPath");
        String combined = safeRequestedPath.startsWith("/")
                ? safeRequestedPath
                : ("/".equals(safeWorkingDirectory)
                        ? "/" + safeRequestedPath
                        : safeWorkingDirectory + "/" + safeRequestedPath);

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : combined.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("FTP path escapes the export root");
                }
                segments.removeLast();
                continue;
            }
            validateSegment(segment);
            segments.addLast(segment);
        }

        StringBuilder result = new StringBuilder("/");
        Iterator<String> iterator = segments.iterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append('/');
            }
        }
        if (result.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("FTP path is too long");
        }
        return result.toString();
    }

    public static String parent(String absolutePath) {
        String normalized = resolve("/", absolutePath);
        int separator = normalized.lastIndexOf('/');
        return separator <= 0 ? "/" : normalized.substring(0, separator);
    }

    public static String name(String absolutePath) {
        String normalized = resolve("/", absolutePath);
        if ("/".equals(normalized)) {
            return "";
        }
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static String normalizeInput(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " is empty");
        }
        if (value.indexOf('\u0000') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(name + " contains an unsafe separator");
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("%2f") || lower.contains("%5c")) {
            throw new IllegalArgumentException(name + " contains an encoded separator");
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private static void validateSegment(String segment) {
        if (segment.length() > MAX_SEGMENT_LENGTH) {
            throw new IllegalArgumentException("FTP path segment is too long");
        }
        if (segment.regionMatches(true, 0, PRIVATE_PREFIX, 0, PRIVATE_PREFIX.length())) {
            throw new IllegalArgumentException("FTP path uses a reserved internal name");
        }
    }
}
