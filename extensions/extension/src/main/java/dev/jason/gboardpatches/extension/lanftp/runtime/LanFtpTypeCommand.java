package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DataType;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** Accepts common TYPE commands while keeping all file payloads byte-exact binary. */
final class LanFtpTypeCommand extends AbstractCommand {
    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        String argument = request.getArgument();
        if (argument == null || argument.isBlank()) {
            session.write(new DefaultFtpReply(501, "TYPE requires A or I."));
            return;
        }
        char type = Character.toUpperCase(argument.charAt(0));
        if (type != 'A' && type != 'I') {
            session.write(new DefaultFtpReply(504, "Only TYPE A and TYPE I are supported."));
            return;
        }
        session.setDataType(DataType.BINARY);
        session.write(new DefaultFtpReply(
                200, "TYPE " + type + " accepted; file payloads remain binary."));
    }
}
