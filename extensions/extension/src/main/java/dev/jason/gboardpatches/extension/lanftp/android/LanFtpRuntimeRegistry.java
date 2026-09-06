package dev.jason.gboardpatches.extension.lanftp.android;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local runtime state published by the FTP service for the private provider. */
public final class LanFtpRuntimeRegistry {
    private static final AtomicReference<LanFtpRuntimeStatus> CURRENT =
            new AtomicReference<>(LanFtpRuntimeStatus.stopped());

    private LanFtpRuntimeRegistry() {
    }

    public static LanFtpRuntimeStatus snapshot() {
        return CURRENT.get();
    }

    public static boolean publish(LanFtpRuntimeStatus snapshot) {
        LanFtpRuntimeStatus safe = snapshot == null
                ? LanFtpRuntimeStatus.stopped()
                : snapshot;
        return !safe.equals(CURRENT.getAndSet(safe));
    }

    public static boolean reset() {
        return publish(LanFtpRuntimeStatus.stopped());
    }
}
