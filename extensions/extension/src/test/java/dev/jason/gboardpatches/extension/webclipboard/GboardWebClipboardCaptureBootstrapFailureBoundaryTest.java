package dev.jason.gboardpatches.extension.webclipboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardWebClipboardCaptureBootstrapFailureBoundaryTest {
    @Test
    public void afterLatinImeOnCreateAbsorbsInvalidReceivers() {
        GboardWebClipboardCaptureBootstrap.afterLatinImeOnCreate(null);
        GboardWebClipboardCaptureBootstrap.afterLatinImeOnCreate("not-a-context");
    }

    @Test
    public void afterClipboardProviderInsertAbsorbsNullWrongTypeAndInvalidValues() {
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert(null, null);
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert("wrong", "wrong");

        ContentProvider stubProvider = new ContentProvider() {
            @Override
            public boolean onCreate() {
                return false;
            }

            @Override
            public Cursor query(Uri uri, String[] projection, String selection,
                    String[] selectionArgs, String sortOrder) {
                return null;
            }

            @Override
            public String getType(Uri uri) {
                return null;
            }

            @Override
            public Uri insert(Uri uri, ContentValues values) {
                return null;
            }

            @Override
            public int delete(Uri uri, String selection, String[] selectionArgs) {
                return 0;
            }

            @Override
            public int update(Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
                return 0;
            }
        };
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert(
                stubProvider,
                new Object());

        Context appContext = RuntimeEnvironment.getApplication();
        ContentProvider attachedProvider = new ContentProvider() {
            @Override
            public boolean onCreate() {
                return true;
            }

            @Override
            public Cursor query(Uri uri, String[] projection, String selection,
                    String[] selectionArgs, String sortOrder) {
                return null;
            }

            @Override
            public String getType(Uri uri) {
                return null;
            }

            @Override
            public Uri insert(Uri uri, ContentValues values) {
                return null;
            }

            @Override
            public int delete(Uri uri, String selection, String[] selectionArgs) {
                return 0;
            }

            @Override
            public int update(Uri uri, ContentValues values, String selection,
                    String[] selectionArgs) {
                return 0;
            }
        };
        attachedProvider.attachInfo(appContext, null);

        ContentValues selfIncoming = new ContentValues();
        selfIncoming.put("uri", "content://com.google.android.inputmethod.latin.fileprovider/clipboard_image/web-clip-1.png");
        selfIncoming.put("text", "");
        selfIncoming.put("timestamp", System.currentTimeMillis());
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert(attachedProvider, selfIncoming);

        ContentValues staleRow = new ContentValues();
        staleRow.put("uri", "content://com.google.android.inputmethod.latin.fileprovider/clipboard_image/old.png");
        staleRow.put("text", "");
        staleRow.put("timestamp", System.currentTimeMillis() - 120_000L);
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert(attachedProvider, staleRow);

        ContentValues freshRow = new ContentValues();
        freshRow.put("uri", "content://com.google.android.inputmethod.latin.fileprovider/clipboard_image/fresh.png");
        freshRow.put("text", "");
        freshRow.put("timestamp", System.currentTimeMillis());
        GboardWebClipboardCaptureBootstrap.afterClipboardProviderInsert(attachedProvider, freshRow);
    }
}
