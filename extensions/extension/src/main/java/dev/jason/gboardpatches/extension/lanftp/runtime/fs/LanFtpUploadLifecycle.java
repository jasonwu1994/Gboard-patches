package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.IOException;

/** Optional store capability for atomic upload promotion and fail-closed cleanup. */
public interface LanFtpUploadLifecycle {
    void completeUpload(String absolutePath) throws IOException;

    void abortUpload(String absolutePath) throws IOException;

    default boolean isUploadIncomplete(String absolutePath) throws IOException {
        return false;
    }
}
