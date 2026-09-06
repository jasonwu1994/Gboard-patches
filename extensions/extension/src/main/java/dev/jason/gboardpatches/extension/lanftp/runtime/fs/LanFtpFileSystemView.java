package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.util.Objects;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

public final class LanFtpFileSystemView implements FileSystemView {
    private final LanFtpDocumentStore store;
    private final String ownerName;
    private final boolean readOnly;
    private String workingDirectory = "/";

    public LanFtpFileSystemView(LanFtpDocumentStore store, String ownerName, boolean readOnly) {
        this.store = Objects.requireNonNull(store, "store");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.readOnly = readOnly;
    }

    @Override
    public FtpFile getHomeDirectory() {
        return file("/");
    }

    @Override
    public FtpFile getWorkingDirectory() {
        return file(workingDirectory);
    }

    @Override
    public boolean changeWorkingDirectory(String requestedPath) throws FtpException {
        FtpFile target = getFile(requestedPath);
        if (!target.doesExist() || !target.isDirectory() || !target.isReadable()) {
            return false;
        }
        workingDirectory = target.getAbsolutePath();
        return true;
    }

    @Override
    public FtpFile getFile(String requestedPath) throws FtpException {
        try {
            return file(LanFtpPath.resolve(workingDirectory, requestedPath));
        } catch (IllegalArgumentException exception) {
            throw new FtpException("Unsafe FTP path", exception);
        }
    }

    @Override
    public boolean isRandomAccessible() {
        return store.supportsRandomAccess();
    }

    @Override
    public void dispose() {
        // The store belongs to the service, not to an individual FTP session.
    }

    private LanFtpFile file(String absolutePath) {
        return new LanFtpFile(store, absolutePath, ownerName, readOnly);
    }
}
