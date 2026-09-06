package dev.jason.gboardpatches.extension.lanftp.runtime.fs;

import android.content.ContentResolver;
import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPath;

public final class SafLanFtpDocumentStore implements LanFtpDocumentStore {
    private static final int MAX_DIRECTORY_ENTRIES =
            LanFtpDocumentStore.MAX_DIRECTORY_ENTRIES;
    private static final int RANDOM_ACCESS_UNKNOWN = 0;
    private static final int RANDOM_ACCESS_SUPPORTED = 1;
    private static final int RANDOM_ACCESS_UNSUPPORTED = 2;
    private static final String FILE_MIME_TYPE = "application/octet-stream";
    private static final String[] DOCUMENT_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
    };

    private final ContentResolver resolver;
    private final Uri treeUri;
    private final Uri rootUri;
    private final boolean readOnly;
    private final Runnable grantFailureListener;
    private final AtomicBoolean grantFailureNotified = new AtomicBoolean();
    private final AtomicInteger randomAccessState =
            new AtomicInteger(RANDOM_ACCESS_UNKNOWN);

    public SafLanFtpDocumentStore(Context context, Uri treeUri, boolean readOnly) {
        this(context, treeUri, readOnly, null);
    }

    public SafLanFtpDocumentStore(Context context, Uri treeUri, boolean readOnly,
            Runnable grantFailureListener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(treeUri, "treeUri");
        Context applicationContext = context.getApplicationContext();
        this.resolver = (applicationContext == null ? context : applicationContext)
                .getContentResolver();
        this.treeUri = treeUri;
        this.rootUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri));
        this.readOnly = readOnly;
        this.grantFailureListener = grantFailureListener == null
                ? () -> { }
                : grantFailureListener;
    }

    @Override
    public LanFtpDocument stat(String absolutePath) throws IOException {
        requirePersistedGrant(false);
        String path = canonicalPath(absolutePath);
        ResolvedDocument document = resolve(path);
        return document == null ? null : metadata(path, document);
    }

    @Override
    public List<LanFtpDocument> list(String absolutePath) throws IOException {
        requirePersistedGrant(false);
        String path = canonicalPath(absolutePath);
        ResolvedDocument parent = resolve(path);
        if (parent == null || !parent.isDirectory()) {
            return List.of();
        }
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parent.uri,
                parent.documentId);
        List<LanFtpDocument> children = new ArrayList<>();
        try (Cursor cursor = resolver.query(
                childrenUri, DOCUMENT_PROJECTION, null, null, null)) {
            if (cursor == null) {
                throw new IOException("Document provider returned no directory cursor");
            }
            int visited = 0;
            while (cursor.moveToNext()) {
                if (visited >= MAX_DIRECTORY_ENTRIES) {
                    throw new IOException("Directory entry limit exceeded for " + path);
                }
                visited++;
                ResolvedDocument child = readDocument(cursor, parent.uri);
                try {
                    String childPath = LanFtpPath.resolve(path, child.displayName);
                    children.add(metadata(childPath, child));
                } catch (IllegalArgumentException ignored) {
                    // Internal/reserved and unsafe provider names are never exposed to FTP.
                }
            }
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to list SAF directory " + path, exception);
        }
        return children;
    }

    @Override
    public boolean createDirectory(String absolutePath) throws IOException {
        requireWritable();
        requirePersistedGrant(true);
        String path = canonicalPath(absolutePath);
        if ("/".equals(path) || resolve(path) != null) {
            return false;
        }
        ResolvedDocument parent = resolveWritableParent(path);
        try {
            return DocumentsContract.createDocument(
                    resolver,
                    parent.uri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    LanFtpPath.name(path)) != null;
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to create SAF directory " + path, exception);
        }
    }

    @Override
    public boolean delete(String absolutePath) throws IOException {
        requireWritable();
        requirePersistedGrant(true);
        String path = canonicalPath(absolutePath);
        if ("/".equals(path)) {
            return false;
        }
        ResolvedDocument document = resolve(path);
        if (document == null || !document.isRemovable()) {
            return false;
        }
        try {
            return DocumentsContract.deleteDocument(resolver, document.uri);
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to delete SAF document " + path, exception);
        }
    }

    @Override
    public boolean move(String sourcePath, String destinationPath) throws IOException {
        requireWritable();
        requirePersistedGrant(true);
        String source = canonicalPath(sourcePath);
        String destination = canonicalPath(destinationPath);
        if ("/".equals(source) || source.equals(destination) || resolve(destination) != null) {
            return false;
        }
        ResolvedDocument sourceDocument = resolve(source);
        if (sourceDocument == null || sourceDocument.parentUri == null) {
            return false;
        }
        if (sourceDocument.isDirectory() && destination.startsWith(source + "/")) {
            return false;
        }
        ResolvedDocument destinationParent = resolveWritableParent(destination);
        String destinationName = LanFtpPath.name(destination);
        boolean sameParent = sourceDocument.parentUri.equals(destinationParent.uri);
        try {
            Uri resultUri = sourceDocument.uri;
            if (!sameParent) {
                resultUri = DocumentsContract.moveDocument(
                        resolver,
                        sourceDocument.uri,
                        sourceDocument.parentUri,
                        destinationParent.uri);
                if (resultUri == null) {
                    return false;
                }
            }
            if (!sourceDocument.displayName.equals(destinationName)) {
                resultUri = DocumentsContract.renameDocument(resolver, resultUri, destinationName);
            }
            return resultUri != null;
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to move SAF document " + source + " to " + destination,
                    exception);
        }
    }

    @Override
    public boolean setLastModified(String absolutePath, long lastModified) throws IOException {
        requireWritable();
        requirePersistedGrant(true);
        canonicalPath(absolutePath);
        // DocumentsContract has no portable client API for changing COLUMN_LAST_MODIFIED.
        return false;
    }

    @Override
    public InputStream openInputStream(String absolutePath, long offset) throws IOException {
        requirePersistedGrant(false);
        requireValidOffset(offset);
        String path = canonicalPath(absolutePath);
        ResolvedDocument document = resolve(path);
        if (document == null || document.isDirectory()) {
            throw new IOException("SAF path is not a file: " + path);
        }
        if (offset > document.size) {
            throw new IOException("FTP restart offset exceeds file size: " + path);
        }
        ParcelFileDescriptor descriptor = openDescriptor(document.uri, "r", path);
        try {
            seekIfRequired(descriptor, offset, path, false);
            return new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
        } catch (IOException exception) {
            closeAfterFailure(descriptor, exception);
            throw exception;
        }
    }

    @Override
    public OutputStream openOutputStream(String absolutePath, long offset) throws IOException {
        requireWritable();
        requirePersistedGrant(true);
        requireValidOffset(offset);
        String path = canonicalPath(absolutePath);
        ResolvedDocument document = resolve(path);
        if (document == null) {
            if (offset != 0L) {
                throw new IOException("Cannot resume a missing SAF document: " + path);
            }
            ResolvedDocument parent = resolveWritableParent(path);
            Uri createdUri;
            try {
                createdUri = DocumentsContract.createDocument(
                        resolver, parent.uri, FILE_MIME_TYPE, LanFtpPath.name(path));
            } catch (RuntimeException exception) {
                throw providerFailure("Unable to create SAF document " + path, exception);
            }
            if (createdUri == null) {
                throw new IOException("Document provider did not create " + path);
            }
            document = queryDocument(createdUri, parent.uri);
        }
        if (document.isDirectory() || !document.isWritable()) {
            throw new IOException("SAF path is not a writable file: " + path);
        }
        if (offset > document.size) {
            throw new IOException("FTP restart offset exceeds partial file size: " + path);
        }
        ParcelFileDescriptor descriptor = openDescriptor(
                document.uri,
                offset == 0L ? "rwt" : "rw",
                path);
        try {
            seekIfRequired(descriptor, offset, path, true);
            return new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
        } catch (IOException exception) {
            closeAfterFailure(descriptor, exception);
            throw exception;
        }
    }

    @Override
    public boolean supportsRandomAccess() {
        return randomAccessState.get() != RANDOM_ACCESS_UNSUPPORTED;
    }

    private ResolvedDocument resolve(String absolutePath) throws IOException {
        if ("/".equals(absolutePath)) {
            return queryDocument(rootUri, null);
        }
        ResolvedDocument current = queryDocument(rootUri, null);
        String[] segments = absolutePath.substring(1).split("/");
        for (String segment : segments) {
            ResolvedDocument child = findChild(current, segment);
            if (child == null) {
                return null;
            }
            current = child;
        }
        return current;
    }

    private ResolvedDocument findChild(ResolvedDocument parent, String displayName)
            throws IOException {
        if (!parent.isDirectory()) {
            return null;
        }
        String normalizedName = Normalizer.normalize(displayName, Normalizer.Form.NFC);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parent.uri,
                parent.documentId);
        ResolvedDocument match = null;
        try (Cursor cursor = resolver.query(
                childrenUri, DOCUMENT_PROJECTION, null, null, null)) {
            if (cursor == null) {
                throw new IOException("Document provider returned no child cursor");
            }
            while (cursor.moveToNext()) {
                ResolvedDocument child = readDocument(cursor, parent.uri);
                String candidate = Normalizer.normalize(child.displayName, Normalizer.Form.NFC);
                if (candidate.equals(normalizedName)) {
                    if (match != null) {
                        throw new IOException("Document provider returned ambiguous normalized names");
                    }
                    match = child;
                }
            }
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to resolve SAF child " + displayName, exception);
        }
        return match;
    }

    private ResolvedDocument resolveWritableParent(String absolutePath) throws IOException {
        ResolvedDocument parent = resolve(LanFtpPath.parent(absolutePath));
        if (parent == null || !parent.isDirectory() || !parent.canCreateChildren()) {
            throw new IOException("SAF parent is not writable: " + absolutePath);
        }
        return parent;
    }

    private ResolvedDocument queryDocument(Uri uri, Uri parentUri) throws IOException {
        try (Cursor cursor = resolver.query(uri, DOCUMENT_PROJECTION, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IOException("Document provider returned no metadata for " + uri);
            }
            return readDocument(cursor, parentUri);
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to query SAF document " + uri, exception);
        }
    }

    private ResolvedDocument readDocument(Cursor cursor, Uri parentUri) throws IOException {
        String documentId = requiredString(cursor,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID);
        String displayName = requiredString(cursor,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME);
        String mimeType = requiredString(cursor,
                DocumentsContract.Document.COLUMN_MIME_TYPE);
        long size = optionalLong(cursor, DocumentsContract.Document.COLUMN_SIZE);
        long lastModified = optionalLong(cursor,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED);
        long flags = optionalLong(cursor, DocumentsContract.Document.COLUMN_FLAGS);
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId);
        return new ResolvedDocument(
                uri, parentUri, documentId, displayName, mimeType, size, lastModified, flags);
    }

    private LanFtpDocument metadata(String path, ResolvedDocument document) {
        boolean writable = !readOnly && (document.isDirectory()
                ? document.canCreateChildren()
                : document.isWritable());
        return new LanFtpDocument(
                path,
                document.isDirectory(),
                document.isDirectory() ? 0L : document.size,
                document.lastModified,
                true,
                writable,
                !readOnly && !"/".equals(path) && document.isRemovable());
    }

    private ParcelFileDescriptor openDescriptor(Uri uri, String mode, String path)
            throws IOException {
        try {
            ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, mode);
            if (descriptor == null) {
                throw new IOException("Document provider returned no descriptor for " + path);
            }
            return descriptor;
        } catch (RuntimeException exception) {
            throw providerFailure("Unable to open SAF document " + path, exception);
        }
    }

    private void seekIfRequired(ParcelFileDescriptor descriptor, long offset, String path,
            boolean truncateAtOffset)
            throws IOException {
        if (offset == 0L) {
            return;
        }
        try {
            if (truncateAtOffset) {
                Os.ftruncate(descriptor.getFileDescriptor(), offset);
            }
            long actual = Os.lseek(descriptor.getFileDescriptor(), offset, OsConstants.SEEK_SET);
            if (actual != offset) {
                throw new IOException("Document provider sought to an unexpected offset for "
                        + path);
            }
            randomAccessState.set(RANDOM_ACCESS_SUPPORTED);
        } catch (ErrnoException exception) {
            randomAccessState.set(RANDOM_ACCESS_UNSUPPORTED);
            throw new IOException(
                    "Document provider does not support seek at non-zero offset for " + path,
                    exception);
        }
    }

    private void requireWritable() throws IOException {
        if (readOnly) {
            throw new IOException("FTP export is read-only");
        }
    }

    private void requirePersistedGrant(boolean requireWrite) throws IOException {
        boolean granted = false;
        try {
            for (UriPermission permission : resolver.getPersistedUriPermissions()) {
                if (treeUri.equals(permission.getUri())
                        && permission.isReadPermission()
                        && (!requireWrite || permission.isWritePermission())) {
                    granted = true;
                    break;
                }
            }
        } catch (Throwable ignored) {
            // Treat an unreadable permission registry exactly like a revoked grant.
        }
        if (granted) {
            return;
        }
        if (grantFailureNotified.compareAndSet(false, true)) {
            try {
                grantFailureListener.run();
            } catch (Throwable ignored) {
                // Permission failure remains authoritative even if reporting fails.
            }
        }
        throw new IOException(requireWrite
                ? "Persisted SAF write permission is unavailable"
                : "Persisted SAF read permission is unavailable");
    }

    private static void requireValidOffset(long offset) throws IOException {
        if (offset < 0L) {
            throw new IOException("FTP transfer offset cannot be negative");
        }
    }

    private static String canonicalPath(String path) throws IOException {
        try {
            return LanFtpPath.resolve("/", path);
        } catch (IllegalArgumentException exception) {
            throw ioFailure("Unsafe FTP path", exception);
        }
    }

    private static String requiredString(Cursor cursor, String column) throws IOException {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) {
            throw new IOException("Document provider omitted " + column);
        }
        String value = cursor.getString(index);
        if (value == null || value.isEmpty()) {
            throw new IOException("Document provider returned an empty " + column);
        }
        return value;
    }

    private static long optionalLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private static IOException ioFailure(String message, Exception cause) {
        return new IOException(message, cause);
    }

    private IOException providerFailure(String message, RuntimeException cause) {
        if (isProviderUnavailable(cause) && grantFailureNotified.compareAndSet(false, true)) {
            try {
                grantFailureListener.run();
            } catch (Throwable ignored) {
                // The originating provider failure remains authoritative.
            }
        }
        return new IOException(message, cause);
    }

    private static boolean isProviderUnavailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("DeadObjectException")
                    || name.contains("RemoteException")
                    || current instanceof SecurityException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void closeAfterFailure(ParcelFileDescriptor descriptor, IOException failure) {
        try {
            descriptor.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static final class ResolvedDocument {
        private final Uri uri;
        private final Uri parentUri;
        private final String documentId;
        private final String displayName;
        private final String mimeType;
        private final long size;
        private final long lastModified;
        private final long flags;

        private ResolvedDocument(Uri uri, Uri parentUri, String documentId, String displayName,
                String mimeType, long size, long lastModified, long flags) {
            this.uri = uri;
            this.parentUri = parentUri;
            this.documentId = documentId;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.size = size;
            this.lastModified = lastModified;
            this.flags = flags;
        }

        private boolean isDirectory() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
        }

        private boolean isWritable() {
            return hasFlag(DocumentsContract.Document.FLAG_SUPPORTS_WRITE);
        }

        private boolean canCreateChildren() {
            return hasFlag(DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE);
        }

        private boolean isRemovable() {
            return hasFlag(DocumentsContract.Document.FLAG_SUPPORTS_DELETE);
        }

        private boolean hasFlag(long flag) {
            return (flags & flag) != 0L;
        }
    }
}
