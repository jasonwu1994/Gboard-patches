package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.ftplet.DataConnection;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.IODataConnectionFactory;

/** Shared passive data-command setup, replies, error text, and fail-closed cleanup. */
final class LanFtpDataCommandSupport {
    private static final long PRELIMINARY_REPLY_WAIT_MILLIS = 10_000L;

    private LanFtpDataCommandSupport() {
    }

    static boolean hasPassiveEndpoint(FtpIoSession session) {
        return !(session.getDataConnection() instanceof IODataConnectionFactory factory)
                || factory.getInetAddress() != null;
    }

    static DataConnection openPassiveConnection(FtpIoSession session, String command)
            throws FtpException {
        session.write(new DefaultFtpReply(
                150, "Opening passive data connection for " + command + "."))
                .awaitUninterruptibly(PRELIMINARY_REPLY_WAIT_MILLIS);
        try {
            return session.getDataConnection().openConnection();
        } catch (Exception exception) {
            reply(session, 425, "Could not open the passive data connection.");
            return null;
        }
    }

    static void reply(FtpIoSession session, int code, String message) {
        session.write(new DefaultFtpReply(code, message));
    }

    static String safeReason(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message.replace('\r', ' ').replace('\n', ' ');
    }

    static void finish(FtpIoSession session) {
        try {
            session.resetState();
        } catch (Throwable ignored) {
            // A broken session state must not skip authoritative data-socket cleanup.
        } finally {
            try {
                session.getDataConnection().closeDataConnection();
            } catch (Throwable ignored) {
                // Command completion remains fail-closed even if MINA cleanup misbehaves.
            }
        }
    }
}
