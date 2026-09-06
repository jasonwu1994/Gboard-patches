package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface LanFtpDocumentStore {
    int MAX_DIRECTORY_ENTRIES = 10_000;

    LanFtpDocument stat(String absolutePath) throws IOException;

    List<LanFtpDocument> list(String absolutePath) throws IOException;

    boolean createDirectory(String absolutePath) throws IOException;

    boolean delete(String absolutePath) throws IOException;

    boolean move(String sourcePath, String destinationPath) throws IOException;

    boolean setLastModified(String absolutePath, long lastModified) throws IOException;

    InputStream openInputStream(String absolutePath, long offset) throws IOException;

    OutputStream openOutputStream(String absolutePath, long offset) throws IOException;

    boolean supportsRandomAccess();
}
