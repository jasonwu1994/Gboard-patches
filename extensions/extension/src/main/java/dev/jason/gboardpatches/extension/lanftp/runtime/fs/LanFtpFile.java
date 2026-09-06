package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.ftpserver.ftplet.FtpFile;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

public final class LanFtpFile implements FtpFile {
    private final LanFtpDocumentStore store;
    private final LanFtpUploadLifecycle uploadLifecycle;
    private final String absolutePath;
    private final String ownerName;
    private final boolean readOnly;
    private final LanFtpDocument listingSnapshot;

    public LanFtpFile(LanFtpDocumentStore store, String absolutePath, String ownerName,
            boolean readOnly) {
        this(store, absolutePath, ownerName, readOnly, null);
    }

    LanFtpFile(LanFtpDocumentStore store, String absolutePath, String ownerName,
            boolean readOnly, LanFtpDocument listingSnapshot) {
        this.store = Objects.requireNonNull(store, "store");
        this.uploadLifecycle = store instanceof LanFtpUploadLifecycle lifecycle
                ? lifecycle
                : null;
        this.absolutePath = LanFtpPath.resolve("/", absolutePath);
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.readOnly = readOnly;
        this.listingSnapshot = listingSnapshot;
    }

    @Override
    public String getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public String getName() {
        String name = LanFtpPath.name(absolutePath);
        return name.isEmpty() ? "/" : name;
    }

    @Override
    public boolean isHidden() {
        return getName().startsWith(".") && !"/".equals(getName());
    }

    @Override
    public boolean isDirectory() {
        LanFtpDocument document = document();
        return document != null && document.isDirectory();
    }

    @Override
    public boolean isFile() {
        LanFtpDocument document = document();
        return document != null && !document.isDirectory();
    }

    @Override
    public boolean doesExist() {
        return document() != null;
    }

    @Override
    public boolean isReadable() {
        LanFtpDocument document = document();
        return document != null && document.isReadable();
    }

    @Override
    public boolean isWritable() {
        if (readOnly) {
            return false;
        }
        LanFtpDocument document = document();
        if (document != null) {
            return document.isWritable();
        }
        LanFtpDocument parent = document(LanFtpPath.parent(absolutePath));
        return parent != null && parent.isDirectory() && parent.isWritable();
    }

    @Override
    public boolean isRemovable() {
        LanFtpDocument document = document();
        return !readOnly && !"/".equals(absolutePath) && document != null
                && document.isRemovable();
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public String getGroupName() {
        return ownerName;
    }

    @Override
    public int getLinkCount() {
        return isDirectory() ? 3 : 1;
    }

    @Override
    public long getLastModified() {
        LanFtpDocument document = document();
        return document == null ? 0L : document.lastModified();
    }

    @Override
    public boolean setLastModified(long lastModified) {
        if (!isWritable()) {
            return false;
        }
        try {
            return store.setLastModified(absolutePath, lastModified);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public long getSize() {
        LanFtpDocument document = document();
        return document == null || document.isDirectory() ? 0L : document.size();
    }

    @Override
    public Object getPhysicalFile() {
        return absolutePath;
    }

    @Override
    public boolean mkdir() {
        if (!isWritable() || doesExist()) {
            return false;
        }
        try {
            return store.createDirectory(absolutePath);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public boolean delete() {
        if (!isRemovable()) {
            return false;
        }
        try {
            return store.delete(absolutePath);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public boolean move(FtpFile destination) {
        if (readOnly || !(destination instanceof LanFtpFile)) {
            return false;
        }
        LanFtpFile target = (LanFtpFile) destination;
        if (store != target.store || target.readOnly || !doesExist() || !isReadable()
                || !target.isWritable()) {
            return false;
        }
        try {
            return store.move(absolutePath, target.absolutePath);
        } catch (IOException ignored) {
            return false;
        }
    }

    @Override
    public List<? extends FtpFile> listFiles() {
        LanFtpDocument document = document();
        if (document == null || !document.isDirectory() || !document.isReadable()) {
            return null;
        }
        try {
            List<FtpFile> result = new ArrayList<>();
            for (LanFtpDocument child : store.list(absolutePath)) {
                try {
                    result.add(new LanFtpFile(
                            store, child.absolutePath(), ownerName, readOnly, child));
                } catch (IllegalArgumentException ignored) {
                    // Hide provider entries that use server-reserved or unsafe names.
                }
            }
            return result;
        } catch (IOException ignored) {
            return null;
        }
    }

    @Override
    public OutputStream createOutputStream(long offset) throws IOException {
        if (readOnly) {
            throw new IOException("FTP export is read-only");
        }
        if (!isWritable() || isDirectory()) {
            throw new IOException("FTP path is not writable: " + absolutePath);
        }
        return store.openOutputStream(absolutePath, offset);
    }

    @Override
    public InputStream createInputStream(long offset) throws IOException {
        if (!isFile() || !isReadable()) {
            throw new IOException("FTP path is not a readable file: " + absolutePath);
        }
        return store.openInputStream(absolutePath, offset);
    }

    public void completeUpload() throws IOException {
        if (uploadLifecycle != null) {
            uploadLifecycle.completeUpload(absolutePath);
        }
    }

    public boolean abortUpload() {
        if (uploadLifecycle != null) {
            try {
                uploadLifecycle.abortUpload(absolutePath);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        }
        return true;
    }

    public boolean isUploadIncomplete() {
        if (uploadLifecycle != null) {
            try {
                return uploadLifecycle.isUploadIncomplete(absolutePath);
            } catch (IOException ignored) {
                return false;
            }
        }
        return false;
    }

    private LanFtpDocument document() {
        return listingSnapshot == null ? document(absolutePath) : listingSnapshot;
    }

    private LanFtpDocument document(String path) {
        try {
            return store.stat(path);
        } catch (IOException ignored) {
            return null;
        }
    }
}
