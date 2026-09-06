package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.util.Objects;

import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpUserManager;

public final class LanFtpFileSystemFactory implements FileSystemFactory {
    private final LanFtpDocumentStore store;
    private final String ownerName;
    private final boolean readOnly;
    private final boolean allowAnonymous;

    public LanFtpFileSystemFactory(LanFtpDocumentStore store, String ownerName,
            boolean readOnly) {
        this(store, ownerName, readOnly, false);
    }

    public LanFtpFileSystemFactory(LanFtpDocumentStore store, String ownerName,
            boolean readOnly, boolean allowAnonymous) {
        this.store = Objects.requireNonNull(store, "store");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.readOnly = readOnly;
        this.allowAnonymous = allowAnonymous;
    }

    @Override
    public FileSystemView createFileSystemView(User user) throws FtpException {
        if (user == null || (!ownerName.equals(user.getName())
                && !(allowAnonymous
                        && LanFtpUserManager.ANONYMOUS_USERNAME.equals(user.getName())))) {
            throw new FtpException("FTP user does not match the configured filesystem owner");
        }
        return new LanFtpFileSystemView(store, ownerName, readOnly);
    }
}
