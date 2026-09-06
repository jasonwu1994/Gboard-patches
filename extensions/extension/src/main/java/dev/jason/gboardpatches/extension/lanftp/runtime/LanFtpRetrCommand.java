package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.DataConnection;
import org.apache.ftpserver.ftplet.DataType;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;

import dev.jason.gboardpatches.extension.lanftp.runtime.fs.LanFtpFile;

/** Binary RETR with long-offset seek and throttled in-flight byte publication. */
final class LanFtpRetrCommand extends AbstractCommand {
    private final LanFtpTransferCoordinator transfers;

    LanFtpRetrCommand(LanFtpTransferCoordinator transfers) {
        this.transfers = transfers;
    }

    @Override
    public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request)
            throws IOException, FtpException {
        long offset = session.getFileOffset();
        String requestedPath = request.getArgument();
        boolean handedOff = false;
        try {
            if (requestedPath == null || requestedPath.isBlank()) {
                LanFtpDataCommandSupport.reply(session, 501, "RETR requires a file path.");
                return;
            }
            if (!LanFtpDataCommandSupport.hasPassiveEndpoint(session)) {
                LanFtpDataCommandSupport.reply(
                        session, 503, "PASV or EPSV must be issued before RETR.");
                return;
            }
            FtpFile file;
            try {
                file = session.getFileSystemView().getFile(requestedPath);
            } catch (Exception exception) {
                LanFtpDataCommandSupport.reply(session, 550, "Invalid FTP download path.");
                return;
            }
            if (file instanceof LanFtpFile lanFile && lanFile.isUploadIncomplete()) {
                LanFtpDataCommandSupport.reply(
                        session, 550, "FTP upload is still active; retry after it finishes.");
                return;
            }
            if (file == null || !file.doesExist() || !file.isFile() || !file.isReadable()) {
                LanFtpDataCommandSupport.reply(
                        session, 550, "FTP download target is not a readable file.");
                return;
            }
            if (offset < 0L || offset > file.getSize()) {
                LanFtpDataCommandSupport.reply(
                        session, 551, "FTP restart offset exceeds file size.");
                return;
            }

            DataConnection dataConnection =
                    LanFtpDataCommandSupport.openPassiveConnection(session, "RETR");
            if (dataConnection == null) {
                return;
            }
            if (!transfers.submit(session, "RETR", requestedPath, "download",
                    file.getSize() - offset,
                    () -> transfer(session, file, dataConnection, offset))) {
                LanFtpDataCommandSupport.reply(
                        session, 450, "Too many active LAN FTP transfers; retry later.");
                return;
            }
            handedOff = true;
        } finally {
            if (!handedOff) {
                LanFtpDataCommandSupport.finish(session);
            }
        }
    }

    private void transfer(FtpIoSession session, FtpFile file,
            DataConnection dataConnection, long offset) {
        try {
            long transferred;
            session.setDataType(DataType.BINARY);
            try (InputStream input = LanFtpTransferProgress.input(
                    session, file.createInputStream(offset))) {
                transferred = dataConnection.transferToClient(session.getFtpletSession(), input);
            }
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES, transferred);
            LanFtpDataCommandSupport.reply(
                    session, 226, "Transfer complete (" + transferred + " bytes).");
        } catch (SocketException exception) {
            LanFtpDataCommandSupport.reply(
                    session, 426, "Data connection closed during download.");
        } catch (IOException exception) {
            int code = transfers.isCancellationRequested(session) ? 426 : 551;
            LanFtpDataCommandSupport.reply(
                    session, code, "Download failed: "
                            + LanFtpDataCommandSupport.safeReason(exception));
        } finally {
            LanFtpDataCommandSupport.finish(session);
        }
    }
}
