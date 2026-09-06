package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** Allows stream mode only; MODE Z compression is deliberately unavailable. */
final class LanFtpModeCommand extends AbstractCommand {
    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        String argument = request.getArgument();
        if (argument != null && !argument.isBlank()
                && Character.toUpperCase(argument.charAt(0)) == 'S') {
            session.getDataConnection().setZipMode(false);
            session.write(new DefaultFtpReply(200, "MODE S enabled."));
            return;
        }
        session.getDataConnection().setZipMode(false);
        session.write(new DefaultFtpReply(504, "Only MODE S is supported."));
    }
}
