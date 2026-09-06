package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** Explicit fail-closed replacement for commands excluded by the product contract. */
final class LanFtpRejectedCommand extends AbstractCommand {
    private final String command;

    LanFtpRejectedCommand(String command) {
        this.command = command;
    }

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        try {
            session.write(new DefaultFtpReply(
                    502, command + " is not supported by LAN FTP Server."));
        } finally {
            session.resetState();
            try {
                session.getDataConnection().closeDataConnection();
            } catch (Throwable ignored) {
                // Rejection must also close any passive listener prepared by the client.
            }
        }
    }
}
