package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** ABOR actively closes the data socket and waits for descriptor cleanup before 226. */
final class LanFtpAborCommand extends AbstractCommand {
    private static final long CANCEL_TIMEOUT_MILLIS = 5_000L;
    private final LanFtpTransferCoordinator transfers;

    LanFtpAborCommand(LanFtpTransferCoordinator transfers) {
        this.transfers = transfers;
    }

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        boolean completed = transfers.cancelAndAwait(session, CANCEL_TIMEOUT_MILLIS);
        LanFtpDataCommandSupport.reply(
                session,
                completed ? 226 : 451,
                completed
                        ? "ABOR completed; data connection closed."
                        : "ABOR timed out while closing the data task.");
        LanFtpDataCommandSupport.finish(session);
    }
}
