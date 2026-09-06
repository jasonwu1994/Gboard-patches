package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.util.Objects;

/** Publishes one process-local runtime snapshot and one cross-process change signal. */
final class LanFtpRuntimeEventPublisher {
    private final ContentResolver resolver;
    private final Uri statusUri;
    private final Uri progressUri;

    LanFtpRuntimeEventPublisher(Context context) {
        Context safeContext = Objects.requireNonNull(context, "context");
        resolver = safeContext.getContentResolver();
        statusUri = LanFtpStatusProvider.contentUri(safeContext);
        progressUri = LanFtpStatusProvider.progressUri(safeContext);
    }

    void publish(LanFtpRuntimeStatus snapshot, boolean progress) {
        if (!LanFtpRuntimeRegistry.publish(snapshot)) {
            return;
        }
        notifyChanged(progress ? progressUri : statusUri);
    }

    void reset() {
        if (LanFtpRuntimeRegistry.reset()) {
            notifyChanged(statusUri);
        }
    }

    private void notifyChanged(Uri uri) {
        try {
            resolver.notifyChange(uri, null);
        } catch (Throwable ignored) {
            // The provider remains queryable if an OEM drops a best-effort change signal.
        }
    }
}
