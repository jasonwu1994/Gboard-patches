package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.command.CommandFactoryFactory;
import org.apache.ftpserver.command.CommandFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.DefaultFtpServer;
import org.apache.ftpserver.impl.DefaultFtpServerContext;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;

public final class LanFtpEngine implements AutoCloseable {
    private static final int MAX_LOGIN_FAILURES = 3;
    private static final int LOGIN_FAILURE_DELAY_MILLIS = 500;
    private static final int DATA_CONNECTION_IDLE_SECONDS = 60;
    private static final int MAX_ACTIVE_TRANSFERS = 2;
    private static final int MAX_PENDING_CONTROL_EVENTS = 64;
    private static final int MAX_PEER_LOGIN_FAILURES_PER_MINUTE = 5;
    private static final int MAX_GLOBAL_LOGIN_FAILURES_PER_MINUTE = 20;
    private static final String LISTENER_NAME = "lan-ftp";
    private static final String[] REVIEWED_STANDARD_COMMANDS = {
            "USER", "PASS", "QUIT", "PWD", "CWD", "CDUP",
            "PASV", "EPSV", "LIST", "NLST", "MLST", "MLSD",
            "SIZE", "MDTM", "MFMT", "REST", "DELE", "MKD", "RMD",
            "RNFR", "RNTO", "NOOP", "SYST", "STRU", "OPTS"
    };

    private final LanFtpServerConfig config;
    private final String bindAddress;
    private final FileSystemFactory fileSystemFactory;
    private final LanFtpActivityObserver activityObserver;
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean startAllowed = new AtomicBoolean(true);
    private final AtomicLong quiesceEpoch = new AtomicLong();

    private volatile FtpServer server;
    private volatile Listener listener;
    private volatile LanFtpTransferCoordinator transferCoordinator;
    private volatile ThreadPoolExecutor sessionExecutor;

    public LanFtpEngine(LanFtpServerConfig config, String bindAddress,
            FileSystemFactory fileSystemFactory) {
        this(config, bindAddress, fileSystemFactory, LanFtpActivityObserver.NONE);
    }

    public LanFtpEngine(LanFtpServerConfig config, String bindAddress,
            FileSystemFactory fileSystemFactory, LanFtpActivityObserver activityObserver) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.bindAddress = requireAddress(bindAddress);
        this.fileSystemFactory = java.util.Objects.requireNonNull(
                fileSystemFactory, "fileSystemFactory");
        this.activityObserver = activityObserver == null
                ? LanFtpActivityObserver.NONE
                : activityObserver;
    }

    public void start() throws FtpException {
        synchronized (lifecycleLock) {
            if (!startAllowed.get()) {
                throw new FtpException("LAN FTP engine generation was quiesced");
            }
            if (server != null && !server.isStopped()) {
                return;
            }
            FtpServerFactory serverFactory = new FtpServerFactory();
            serverFactory.setMessageResource(LanFtpMessageResource.create());
            LanFtpTransferCoordinator configuredTransfers =
                    new LanFtpTransferCoordinator(activityObserver, MAX_ACTIVE_TRANSFERS);
            serverFactory.setConnectionConfig(buildConnectionConfig());
            serverFactory.setUserManager(new LanFtpUserManager(
                    config.username(),
                    config.password(),
                    config.allowAnonymous(),
                    config.readOnly(),
                    config.idleTimeoutSeconds(),
                    config.maxSessions()));
            serverFactory.setCommandFactory(buildCommandFactory(configuredTransfers));
            serverFactory.setFileSystem(fileSystemFactory);
            serverFactory.setFtplets(Collections.singletonMap(
                    "lan-ftp-activity",
                    new LanFtpActivityFtplet(
                            activityObserver,
                            new LanFtpLoginRateLimiter(
                                    MAX_PEER_LOGIN_FAILURES_PER_MINUTE,
                                    MAX_GLOBAL_LOGIN_FAILURES_PER_MINUTE,
                                    60_000L),
                            configuredTransfers)));

            Listener configuredListener = buildListener();
            // FtpServerFactory starts with a wildcard default listener on port 21. Replace the
            // entire map so the configured Wi-Fi address/control port is the only socket.
            serverFactory.setListeners(Collections.singletonMap(
                    LISTENER_NAME, configuredListener));
            DefaultFtpServer factoryServer =
                    (DefaultFtpServer) serverFactory.createServer();
            LanFtpBoundedServerContext boundedContext = new LanFtpBoundedServerContext(
                    (DefaultFtpServerContext) factoryServer.getServerContext(),
                    sessionThreadCount(),
                    MAX_PENDING_CONTROL_EVENTS,
                    config.maxSessions());
            DefaultFtpServer configuredServer = new DefaultFtpServer(boundedContext);
            ThreadPoolExecutor configuredSessionExecutor =
                    configuredServer.getServerContext().getThreadPoolExecutor();
            try {
                configuredServer.start();
                listener = configuredListener;
                transferCoordinator = configuredTransfers;
                sessionExecutor = configuredSessionExecutor;
                server = configuredServer;
                if (!startAllowed.get()) {
                    listener = null;
                    transferCoordinator = null;
                    sessionExecutor = null;
                    server = null;
                    quiesceResources(
                            configuredListener, configuredTransfers, configuredSessionExecutor);
                    closeTransfers(configuredTransfers);
                    stopServer(configuredServer, configuredSessionExecutor);
                    throw new FtpException("LAN FTP engine generation was quiesced");
                }
            } catch (Throwable throwable) {
                closeTransfers(configuredTransfers);
                stopServer(configuredServer, configuredSessionExecutor);
                if (throwable instanceof FtpException ftpException) {
                    throw ftpException;
                }
                throw new FtpException(
                        "Could not start LAN FTP server: "
                                + throwable.getClass().getSimpleName()
                                + ": " + throwable.getMessage(),
                        throwable);
            }
        }
    }

    public void stop() {
        synchronized (lifecycleLock) {
            long observedQuiesceEpoch = quiesceEpoch.get();
            boolean restoreStart = startAllowed.compareAndSet(true, false);
            FtpServer current = server;
            Listener currentListener = listener;
            LanFtpTransferCoordinator currentTransfers = transferCoordinator;
            ThreadPoolExecutor currentSessionExecutor = sessionExecutor;
            server = null;
            listener = null;
            transferCoordinator = null;
            sessionExecutor = null;
            quiesceResources(currentListener, currentTransfers, currentSessionExecutor);
            closeTransfers(currentTransfers);
            stopServer(current, currentSessionExecutor);
            if (restoreStart && quiesceEpoch.get() == observedQuiesceEpoch) {
                startAllowed.set(true);
            }
        }
    }

    /** Immediately rejects new work and closes listeners/sockets without awaiting workers. */
    public void quiesce() {
        quiesceEpoch.incrementAndGet();
        startAllowed.set(false);
        quiesceResources(listener, transferCoordinator, sessionExecutor);
    }

    public boolean isStopped() {
        FtpServer current = server;
        return current == null || current.isStopped();
    }

    public int connectedClientCount() {
        return activeSessions().size();
    }

    public boolean kickSession(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        for (FtpIoSession session : activeSessions()) {
            if (!sessionId.equals(session.getSessionId())) {
                continue;
            }
            try {
                session.getDataConnection().closeDataConnection();
            } catch (Throwable ignored) {
                // Closing the control connection below remains authoritative.
            }
            try {
                session.closeNow();
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    public List<LanFtpSessionInfo> sessionInfos() {
        List<LanFtpSessionInfo> result = new ArrayList<>();
        for (FtpIoSession session : activeSessions()) {
            if (!session.isLoggedIn()) {
                continue;
            }
            String peer = "unknown";
            try {
                if (session.getRemoteAddress() instanceof InetSocketAddress address
                        && address.getAddress() != null) {
                    peer = address.getAddress().getHostAddress();
                }
            } catch (Throwable ignored) {
                // An opaque session id still permits safe kick when peer metadata is unavailable.
            }
            result.add(new LanFtpSessionInfo(
                    session.getSessionId().toString(),
                    peer,
                    session.getLoginTime() == null ? 0L : session.getLoginTime().getTime(),
                    session.getLastAccessTime() == null ? 0L : session.getLastAccessTime().getTime(),
                    attributeText(session, LanFtpSessionInfo.ATTRIBUTE_COMMAND, "IDLE"),
                    attributeText(session, LanFtpSessionInfo.ATTRIBUTE_PATH, ""),
                    attributeText(session, LanFtpSessionInfo.ATTRIBUTE_DIRECTION, "idle"),
                    attributeLong(session, LanFtpSessionInfo.ATTRIBUTE_BYTES),
                    attributeLong(session, LanFtpSessionInfo.ATTRIBUTE_TOTAL_BYTES, -1L),
                    currentBytesPerSecond(session)));
        }
        return List.copyOf(result);
    }

    private static long currentBytesPerSecond(FtpIoSession session) {
        long sampleNanos = attributeLong(
                session, LanFtpSessionInfo.ATTRIBUTE_SPEED_SAMPLE_NANOS, -1L);
        if (sampleNanos < 0L || System.nanoTime() - sampleNanos > 2_000_000_000L) {
            return 0L;
        }
        return attributeLong(session, LanFtpSessionInfo.ATTRIBUTE_BYTES_PER_SECOND);
    }

    @Override
    public void close() {
        stop();
    }

    static CommandFactory buildCommandFactory(LanFtpTransferCoordinator transfers) {
        CommandFactory defaults = new CommandFactoryFactory().createCommandFactory();
        CommandFactoryFactory reviewed = new CommandFactoryFactory();
        reviewed.setUseDefaultCommands(false);
        for (String name : REVIEWED_STANDARD_COMMANDS) {
            org.apache.ftpserver.command.Command command = defaults.getCommand(name);
            if (command == null) {
                throw new IllegalStateException("Apache FTP command is unavailable: " + name);
            }
            reviewed.addCommand(name, command);
        }
        reviewed.addCommand("STOR", new LanFtpStorCommand(transfers));
        reviewed.addCommand("RETR", new LanFtpRetrCommand(transfers));
        reviewed.addCommand("ABOR", new LanFtpAborCommand(transfers));
        reviewed.addCommand("PASV", new LanFtpPasvCommand());
        reviewed.addCommand("APPE", new LanFtpRejectedCommand("APPE"));
        reviewed.addCommand("STOU", new LanFtpRejectedCommand("STOU"));
        reviewed.addCommand("PORT", new LanFtpRejectedCommand("PORT"));
        reviewed.addCommand("EPRT", new LanFtpRejectedCommand("EPRT"));
        reviewed.addCommand("TYPE", new LanFtpTypeCommand());
        reviewed.addCommand("MODE", new LanFtpModeCommand());
        reviewed.addCommand("FEAT", new LanFtpFeatCommand());
        return reviewed.createCommandFactory();
    }

    private org.apache.ftpserver.ConnectionConfig buildConnectionConfig() {
        ConnectionConfigFactory factory = new ConnectionConfigFactory();
        factory.setAnonymousLoginEnabled(config.allowAnonymous());
        factory.setMaxAnonymousLogins(config.allowAnonymous() ? config.maxSessions() : 0);
        factory.setMaxLogins(config.maxSessions());
        factory.setMaxLoginFailures(MAX_LOGIN_FAILURES);
        factory.setLoginFailureDelay(LOGIN_FAILURE_DELAY_MILLIS);
        factory.setMaxThreads(sessionThreadCount());
        return factory.createConnectionConfig();
    }

    private int sessionThreadCount() {
        return Math.max(4, config.maxSessions() * 2);
    }

    private Listener buildListener() {
        DataConnectionConfigurationFactory dataFactory =
                new DataConnectionConfigurationFactory();
        dataFactory.setActiveEnabled(false);
        dataFactory.setActiveIpCheck(true);
        dataFactory.setPassiveIpCheck(true);
        dataFactory.setPassiveAddress(bindAddress);
        dataFactory.setPassiveExternalAddress(bindAddress);
        dataFactory.setPassivePorts(passivePortRange());
        dataFactory.setIdleTime(DATA_CONNECTION_IDLE_SECONDS);

        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(config.controlPort());
        listenerFactory.setServerAddress(bindAddress);
        listenerFactory.setIdleTimeout(config.idleTimeoutSeconds());
        listenerFactory.setSessionFilter(
                new LanFtpConnectionAdmissionFilter(config.maxSessions()));
        listenerFactory.setDataConnectionConfiguration(
                dataFactory.createDataConnectionConfiguration());
        return listenerFactory.createListener();
    }

    private String passivePortRange() {
        if (config.passivePortStart() == config.passivePortEnd()) {
            return Integer.toString(config.passivePortStart());
        }
        return config.passivePortStart() + "-" + config.passivePortEnd();
    }

    private Set<FtpIoSession> activeSessions() {
        Listener current = listener;
        if (current == null || current.isStopped()) {
            return Collections.emptySet();
        }
        try {
            return current.getActiveSessions();
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    private static void closeSessions(Listener currentListener) {
        if (currentListener == null) {
            return;
        }
        Set<FtpIoSession> sessions;
        try {
            sessions = Set.copyOf(currentListener.getActiveSessions());
        } catch (Throwable ignored) {
            return;
        }
        for (FtpIoSession session : sessions) {
            try {
                session.getDataConnection().closeDataConnection();
            } catch (Throwable ignored) {
                // Closing the control socket below is still authoritative.
            }
            try {
                session.closeNow();
            } catch (Throwable ignored) {
                // The listener and executor shutdown below remain fail-closed.
            }
        }
    }

    private static void stopServer(FtpServer current,
            ThreadPoolExecutor currentSessionExecutor) {
        if (currentSessionExecutor != null) {
            try {
                currentSessionExecutor.shutdownNow();
            } catch (Throwable ignored) {
                // Server stop below must still run after an executor failure.
            }
        }
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable ignored) {
                // Continue into the owned executor shutdown.
            }
        }
        if (currentSessionExecutor == null) {
            return;
        }
        try {
            currentSessionExecutor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
            // Service shutdown is fail-closed and idempotent.
        }
    }

    private static void stopListener(Listener currentListener) {
        if (currentListener == null) {
            return;
        }
        try {
            currentListener.stop();
        } catch (Throwable ignored) {
            // The server-level stop repeats this idempotently.
        }
    }

    private static void quiesceResources(Listener currentListener,
            LanFtpTransferCoordinator currentTransfers,
            ThreadPoolExecutor currentSessionExecutor) {
        stopListener(currentListener);
        closeSessions(currentListener);
        if (currentTransfers != null) {
            try {
                currentTransfers.quiesce();
            } catch (Throwable ignored) {
                // Continue closing the independently owned control executor.
            }
        }
        if (currentSessionExecutor != null) {
            try {
                currentSessionExecutor.shutdownNow();
            } catch (Throwable ignored) {
                // stopServer repeats both server and executor teardown.
            }
        }
    }

    private static void closeTransfers(LanFtpTransferCoordinator currentTransfers) {
        if (currentTransfers == null) {
            return;
        }
        try {
            currentTransfers.close();
        } catch (Throwable ignored) {
            // Server and control-executor teardown remain authoritative.
        }
    }

    private static String attributeText(FtpIoSession session, String key, String fallback) {
        Object value = session.getAttribute(key);
        return value instanceof String ? (String) value : fallback;
    }

    private static long attributeLong(FtpIoSession session, String key) {
        return attributeLong(session, key, 0L);
    }

    private static long attributeLong(FtpIoSession session, String key, long fallback) {
        Object value = session.getAttribute(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static String requireAddress(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("bindAddress is empty");
        }
        return value;
    }
}
