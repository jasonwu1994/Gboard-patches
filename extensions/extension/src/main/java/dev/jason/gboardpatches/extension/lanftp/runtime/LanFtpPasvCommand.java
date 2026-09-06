package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.command.impl.PASV;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

/** Keeps legacy PASV on IPv4 and requires EPSV for IPv6 control sessions. */
final class LanFtpPasvCommand extends AbstractCommand {
    private final PASV ipv4Delegate = new PASV();

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        if (!isIpv6(session.getLocalAddress()) && !isIpv6(session.getRemoteAddress())) {
            ipv4Delegate.execute(session, context, request);
            return;
        }
        try {
            session.write(new DefaultFtpReply(
                    522, "Network protocol not supported by PASV; use EPSV."));
        } finally {
            session.resetState();
            try {
                session.getDataConnection().closeDataConnection();
            } catch (Throwable ignored) {
                // Rejection must not leave an earlier passive listener open.
            }
        }
    }

    private static boolean isIpv6(SocketAddress address) {
        return address instanceof InetSocketAddress socket
                && socket.getAddress() instanceof Inet6Address;
    }
}
