package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** Honest feature advertisement without Apache defaults such as AUTH TLS or MODE Z. */
final class LanFtpFeatCommand extends AbstractCommand {
    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        session.resetState();
        session.write(new DefaultFtpReply(211,
                "Extensions supported\n"
                        + " SIZE\n"
                        + " MDTM\n"
                        + " REST STREAM\n"
                        + " MLST Size;Modify;Type;Perm\n"
                        + " UTF8\n"
                        + " TVFS\n"
                        + "End"));
    }
}
