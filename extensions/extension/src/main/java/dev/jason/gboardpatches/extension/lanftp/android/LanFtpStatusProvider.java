package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Private cross-process view of the FTP runtime state. Querying it never starts the FTP service. */
public final class LanFtpStatusProvider extends ContentProvider {
    public static final String AUTHORITY_SUFFIX = ".lan_ftp_status";
    private static final String PROGRESS_PATH = "progress";
    public static final String METHOD_GET_STATUS = "get_status";
    public static final String METHOD_KICK_SESSION = "kick_session";
    public static final String KEY_ACTIVE = "active";
    public static final String KEY_STATUS = "status";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_CLIENT_COUNT = "client_count";
    public static final String KEY_CLIENTS = "clients";
    public static final String KEY_TRANSFER_COUNT = "transfer_count";
    public static final String KEY_RESUME_SUPPORTED = "resume_supported";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_PASSWORD_REVISION = "password_revision";
    public static final String KEY_STARTUP_FAILED = "startup_failed";

    public static Uri contentUri(android.content.Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is required");
        }
        return Uri.parse("content://" + context.getPackageName() + AUTHORITY_SUFFIX);
    }

    public static Uri progressUri(android.content.Context context) {
        return contentUri(context).buildUpon().appendPath(PROGRESS_PATH).build();
    }

    public static boolean isProgressUri(Uri uri) {
        return uri != null && PROGRESS_PATH.equals(uri.getLastPathSegment());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        try {
            return handleCall(method, arg, extras);
        } catch (Throwable throwable) {
            if (METHOD_GET_STATUS.equals(method)) {
                return toBundle(LanFtpRuntimeStatus.unavailable());
            }
            if (METHOD_KICK_SESSION.equals(method)) {
                Bundle result = new Bundle();
                result.putBoolean(KEY_SUCCESS, false);
                return result;
            }
            return null;
        }
    }

    private Bundle handleCall(String method, String arg, Bundle extras) {
        if (METHOD_KICK_SESSION.equals(method)) {
            Bundle result = new Bundle();
            result.putBoolean(KEY_SUCCESS, LanFtpService.kickSession(arg));
            return result;
        }
        if (!METHOD_GET_STATUS.equals(method)) {
            return null;
        }
        LanFtpRuntimeStatus published = LanFtpRuntimeRegistry.snapshot();
        LanFtpRuntimeStatus runtime = runtimeForServiceState(
                published, LanFtpService.isServiceCreated());
        if (runtime != published) {
            LanFtpRuntimeRegistry.publish(runtime);
        }
        return toBundle(runtime);
    }

    static LanFtpRuntimeStatus runtimeForServiceState(
            LanFtpRuntimeStatus runtime, boolean serviceCreated) {
        LanFtpRuntimeStatus safeRuntime = runtime == null
                ? LanFtpRuntimeStatus.stopped()
                : runtime;
        if (serviceCreated || safeRuntime.startupFailed) {
            return safeRuntime;
        }
        if (safeRuntime.active
                || "Starting".equals(safeRuntime.status)
                || "Stopping".equals(safeRuntime.status)) {
            return LanFtpRuntimeStatus.stopped();
        }
        return safeRuntime;
    }

    static Bundle toBundle(LanFtpRuntimeStatus snapshot) {
        Bundle result = new Bundle();
        result.putBoolean(KEY_ACTIVE, snapshot.active);
        result.putBoolean(KEY_STARTUP_FAILED, snapshot.startupFailed);
        result.putString(KEY_STATUS, snapshot.status);
        result.putString(KEY_ENDPOINT, snapshot.endpoint);
        result.putInt(KEY_CLIENT_COUNT, snapshot.clientCount());
        result.putInt(KEY_TRANSFER_COUNT, snapshot.transferCount);
        result.putBoolean(KEY_RESUME_SUPPORTED, snapshot.resumeSupported);
        result.putLong(KEY_PASSWORD_REVISION, snapshot.passwordRevision);
        result.putParcelableArrayList(
                KEY_CLIENTS, LanFtpSessionBundleCodec.encode(snapshot.clients));
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) { return 0; }
}
