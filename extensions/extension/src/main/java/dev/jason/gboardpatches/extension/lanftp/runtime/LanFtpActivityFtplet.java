package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ftpserver.ftplet.DefaultFtplet;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.FtpletResult;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.DataTransferFtpReply;

final class LanFtpActivityFtplet extends DefaultFtplet {
    private final LanFtpActivityObserver observer;
    private final LanFtpLoginRateLimiter loginRateLimiter;
    private final LanFtpTransferCoordinator transfers;
    private final Set<UUID> sessions = ConcurrentHashMap.newKeySet();

    LanFtpActivityFtplet(LanFtpActivityObserver observer,
            LanFtpLoginRateLimiter loginRateLimiter,
            LanFtpTransferCoordinator transfers) {
        this.observer = observer == null ? LanFtpActivityObserver.NONE : observer;
        this.loginRateLimiter = loginRateLimiter;
        this.transfers = transfers;
    }

    @Override
    public FtpletResult beforeCommand(FtpSession session, FtpRequest request)
            throws FtpException, IOException {
        try {
            if (isCommand(request, "PASS") && loginRateLimiter != null
                    && !loginRateLimiter.isAllowed(peer(session), System.currentTimeMillis())) {
                session.write(new DefaultFtpReply(
                        421, "Too many failed FTP logins from this LAN; retry later."));
                return FtpletResult.SKIP;
            }
            FtpletResult result = super.beforeCommand(session, request);
            return result == null ? FtpletResult.DEFAULT : result;
        } catch (Throwable ignored) {
            return containCallbackFailure(session);
        }
    }

    @Override
    public FtpletResult afterCommand(FtpSession session, FtpRequest request, FtpReply reply)
            throws FtpException, IOException {
        try {
            boolean terminalTransferReply = isTransferCommand(request)
                    && reply != null
                    && reply.getCode() >= 200;
            if (session != null && reply instanceof DataTransferFtpReply transferReply) {
                session.setAttribute(
                        LanFtpSessionInfo.ATTRIBUTE_BYTES, transferReply.getBytesTransferred());
                if (transferReply.getFile() != null) {
                    session.setAttribute(LanFtpSessionInfo.ATTRIBUTE_PATH,
                            transferReply.getFile().getAbsolutePath());
                }
            }
            if (terminalTransferReply) {
                notifySessionActivity();
            }
            if (isCommand(request, "PASS") && loginRateLimiter != null) {
                if (reply != null && reply.getCode() == 230) {
                    loginRateLimiter.recordSuccess(peer(session));
                    if (session != null && sessions.add(session.getSessionId())) {
                        notifySessionCount();
                    }
                } else if (reply != null && reply.getCode() >= 400) {
                    loginRateLimiter.recordFailure(peer(session), System.currentTimeMillis());
                }
            }
            FtpletResult result = super.afterCommand(session, request, reply);
            return result == null ? FtpletResult.DEFAULT : result;
        } catch (Throwable ignored) {
            return containCallbackFailure(session);
        }
    }

    @Override
    public FtpletResult onConnect(FtpSession session) {
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onDisconnect(FtpSession session) {
        try {
            if (session != null && sessions.remove(session.getSessionId())) {
                notifySessionCount();
            }
            if (session != null && transfers != null) {
                transfers.cancelAndAwait(session.getSessionId(), 5_000L);
            }
        } catch (Throwable ignored) {
            // A disconnect callback must not escape into the FTP I/O thread.
        } finally {
            try {
                LanFtpConnectionAdmissionFilter.release(session);
            } catch (Throwable ignored) {
                // Admission cleanup is best effort after the connection is already closing.
            }
        }
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onUploadStart(FtpSession session, FtpRequest request)
            throws FtpException, IOException {
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onUploadEnd(FtpSession session, FtpRequest request)
            throws FtpException, IOException {
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onDownloadStart(FtpSession session, FtpRequest request)
            throws FtpException, IOException {
        return FtpletResult.DEFAULT;
    }

    @Override
    public FtpletResult onDownloadEnd(FtpSession session, FtpRequest request)
            throws FtpException, IOException {
        return FtpletResult.DEFAULT;
    }

    @Override
    public void destroy() {
        try {
            sessions.clear();
            notifySessionCount();
        } catch (Throwable ignored) {
            // Container shutdown must continue even if callback bookkeeping is corrupt.
        }
    }

    private FtpletResult containCallbackFailure(FtpSession session) {
        try {
            if (session != null && sessions.remove(session.getSessionId())) {
                notifySessionCount();
            }
        } catch (Throwable ignored) {
            // Continue fail-closed cleanup.
        }
        try {
            if (session != null && transfers != null) {
                transfers.cancelAndAwait(session.getSessionId(), 5_000L);
            }
        } catch (Throwable ignored) {
            // Continue fail-closed cleanup.
        }
        try {
            LanFtpConnectionAdmissionFilter.release(session);
        } catch (Throwable ignored) {
            // Returning DISCONNECT remains the final containment boundary.
        }
        return FtpletResult.DISCONNECT;
    }

    private void notifySessionCount() {
        try {
            observer.onSessionCountChanged(sessions.size());
        } catch (Throwable ignored) {
            // Observer failure must never escape into the FTP command path.
        }
    }

    private void notifySessionActivity() {
        try {
            observer.onSessionActivityChanged();
        } catch (Throwable ignored) {
            // Observer failure must never escape into the FTP command path.
        }
    }

    private static boolean isCommand(FtpRequest request, String command) {
        return request != null && command.equalsIgnoreCase(request.getCommand());
    }

    private static boolean isTransferCommand(FtpRequest request) {
        return isCommand(request, "STOR") || isCommand(request, "RETR");
    }

    private static String peer(FtpSession session) {
        if (session == null || session.getClientAddress() == null
                || session.getClientAddress().getAddress() == null) {
            return "unknown";
        }
        return session.getClientAddress().getAddress().getHostAddress();
    }

}
