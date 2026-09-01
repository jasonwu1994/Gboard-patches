package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/** Applies a pending replacement before Gboard's application onCreate body. */
public final class GboardFlagStoreInitProvider extends ContentProvider {
    private static final String TAG = "GboardPatches";

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        try {
            GboardFlagStoreColdStartInitializer.initialize(context);
        } catch (Throwable failure) {
            Log.w(TAG, "Flag-store cold-start initialization failed", failure);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("Flag-store initializer has no query surface");
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Flag-store initializer has no insert surface");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Flag-store initializer has no delete surface");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        throw new UnsupportedOperationException("Flag-store initializer has no update surface");
    }
}
