package dev.jason.gboardpatches.extension.webclipboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * ContentProvider for serving cached desktop-to-phone clipboard images to local applications
 * via secure read-only parcel file descriptors.
 */
public final class WebClipboardImageProvider extends ContentProvider {
    public static final String IMAGE_CACHE_SUBDIR = "web_clipboard_images";

    private static final Pattern SAFE_FILENAME_PATTERN =
            Pattern.compile("^(desktop_clip_|sync_)\\d+\\.(png|jpg|jpeg|webp|gif)$");

    /**
     * Returns the ContentProvider authority, formatted so it does not contain the exact
     * package name string to avoid Gboard's internal clip suppression filter.
     */
    public static String getAuthority(Context context) {
        String pkg = context != null ? context.getPackageName() : "com.google.android.inputmethod.latin";
        return "webclipboard." + pkg.replace('.', '_') + ".images";
    }

    /**
     * Constructs a content URI for a cached image file.
     *
     * @param context Application context.
     * @param file Target file within the image cache.
     * @return Content URI addressing the file through this provider.
     */
    public static Uri getUriForFile(Context context, File file) {
        if (context == null || file == null) {
            return null;
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(getAuthority(context))
                .appendPath(file.getName())
                .build();
    }

    /**
     * Obtains the dedicated image cache directory for clipboard images.
     *
     * @param context Application context.
     * @return File directory handle for the image cache.
     */
    public static File getImageCacheDir(Context context) {
        if (context == null) {
            return null;
        }
        File cacheDir = new File(context.getCacheDir(), IMAGE_CACHE_SUBDIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return cacheDir;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || uri == null) {
            throw new FileNotFoundException("Context or URI unavailable");
        }
        File file = resolveValidatedFile(context, uri);
        if (file == null || !file.exists() || !file.isFile()) {
            throw new FileNotFoundException("File not found or invalid: " + uri);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        if (uri == null) {
            return "application/octet-stream";
        }
        String path = uri.getPath();
        if (path == null) {
            return "application/octet-stream";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        String type = getType(uri);
        if (type != null && (mimeTypeFilter == null || mimeTypeFilter.equals("*/*") || mimeTypeFilter.startsWith("image/"))) {
            return new String[] { type };
        }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null || uri == null) {
            return null;
        }
        File file = resolveValidatedFile(context, uri);
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        String[] cols = projection != null ? projection : new String[] {
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE,
                "_data"
        };
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(file.getName());
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(file.length());
            } else if ("_data".equals(col) || "data".equals(col)) {
                row.add(file.getAbsolutePath());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    private static File resolveValidatedFile(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }
        String fileName = uri.getLastPathSegment();
        if (fileName == null || !SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
            return null;
        }
        File imageDir = getImageCacheDir(context);
        if (imageDir == null) {
            return null;
        }
        try {
            File targetFile = new File(imageDir, fileName).getCanonicalFile();
            File canonicalDir = imageDir.getCanonicalFile();
            if (!targetFile.toPath().startsWith(canonicalDir.toPath())) {
                return null;
            }
            return targetFile;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
