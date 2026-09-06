package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;
import java.io.OutputStream;
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

/** STOR implementation that promotes a closed staging file before emitting reply 226. */
final class LanFtpStorCommand extends AbstractCommand {
    private static final long ABOR_AFTER_DATA_CLOSE_GRACE_MILLIS = 500L;
    private final LanFtpTransferCoordinator transfers;

    LanFtpStorCommand(LanFtpTransferCoordinator transfers) {
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
                LanFtpDataCommandSupport.reply(session, 501, "STOR requires a file path.");
                return;
            }
            if (offset != 0L) {
                LanFtpDataCommandSupport.reply(session, 550,
                        "Upload resume is disabled; restart the upload from byte 0.");
                return;
            }
            if (!LanFtpDataCommandSupport.hasPassiveEndpoint(session)) {
                LanFtpDataCommandSupport.reply(
                        session, 503, "PASV or EPSV must be issued before STOR.");
                return;
            }

            FtpFile file;
            try {
                file = session.getFileSystemView().getFile(requestedPath);
            } catch (Exception exception) {
                LanFtpDataCommandSupport.reply(session, 550, "Invalid FTP upload path.");
                return;
            }
            if (file == null || !file.isWritable() || file.isDirectory()) {
                LanFtpDataCommandSupport.reply(
                        session, 550, "FTP upload target is not writable.");
                return;
            }

            DataConnection dataConnection =
                    LanFtpDataCommandSupport.openPassiveConnection(session, "STOR");
            if (dataConnection == null) {
                return;
            }
            if (!transfers.submit(session, "STOR", requestedPath, "upload", -1L,
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
            try (OutputStream output = LanFtpTransferProgress.output(
                    session, file.createOutputStream(offset))) {
                transferred = dataConnection.transferFromClient(
                        session.getFtpletSession(), output);
            }
            if (transfers.awaitCancellationRequest(
                    session, ABOR_AFTER_DATA_CLOSE_GRACE_MILLIS)) {
                boolean discarded = abort(file);
                LanFtpDataCommandSupport.reply(
                        session, discarded ? 426 : 451, discarded
                                ? "Upload cancelled; incomplete data was discarded."
                                : "Upload cancelled; incomplete data cleanup failed.");
                return;
            }
            try {
                complete(file);
            } catch (IOException exception) {
                boolean discarded = abort(file);
                LanFtpDataCommandSupport.reply(
                        session, discarded ? 551 : 451,
                        "Upload received but could not be safely promoted"
                                + (discarded ? "; incomplete data was discarded: "
                                        : "; incomplete data cleanup failed: ")
                                + LanFtpDataCommandSupport.safeReason(exception));
                return;
            }
            session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_BYTES, transferred);
            LanFtpDataCommandSupport.reply(
                    session, 226, "Transfer complete (" + transferred + " bytes).");
        } catch (SocketException exception) {
            boolean discarded = abort(file);
            LanFtpDataCommandSupport.reply(
                    session, discarded ? 426 : 451, discarded
                            ? "Data connection closed; incomplete data was discarded."
                            : "Data connection closed; incomplete data cleanup failed.");
        } catch (IOException exception) {
            boolean discarded = abort(file);
            int code = !discarded ? 451
                    : transfers.isCancellationRequested(session) ? 426 : 551;
            LanFtpDataCommandSupport.reply(
                    session, code, "Upload failed; incomplete data "
                            + (discarded ? "was discarded: " : "cleanup failed: ")
                            + LanFtpDataCommandSupport.safeReason(exception));
        } catch (Throwable throwable) {
            boolean discarded = abort(file);
            LanFtpDataCommandSupport.reply(
                    session, 451, discarded
                            ? "Upload failed; incomplete data was discarded."
                            : "Upload failed; incomplete data cleanup failed.");
        } finally {
            LanFtpDataCommandSupport.finish(session);
        }
    }

    private static void complete(FtpFile file) throws IOException {
        if (file instanceof LanFtpFile lanFile) {
            lanFile.completeUpload();
        }
    }

    private static boolean abort(FtpFile file) {
        if (file instanceof LanFtpFile lanFile) {
            return lanFile.abortUpload();
        }
        return true;
    }

}
