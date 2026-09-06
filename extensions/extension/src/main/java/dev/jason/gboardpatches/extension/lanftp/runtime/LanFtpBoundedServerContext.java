package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.util.LinkedHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.ftpserver.ftpletcontainer.impl.DefaultFtpletContainer;
import org.apache.ftpserver.impl.DefaultFtpServerContext;
import org.apache.mina.filter.executor.OrderedThreadPoolExecutor;

/** Apache FTP context with an owned, bounded control-event executor. */
final class LanFtpBoundedServerContext extends DefaultFtpServerContext {
    private final ThreadPoolExecutor executor;

    LanFtpBoundedServerContext(DefaultFtpServerContext source, int maxThreads,
            int maxPendingEvents, int maxControlConnections) {
        if (source == null) {
            throw new IllegalArgumentException("source context is required");
        }
        setConnectionConfig(source.getConnectionConfig());
        setUserManager(source.getUserManager());
        setFileSystemManager(source.getFileSystemManager());
        setCommandFactory(source.getCommandFactory());
        setListeners(new LinkedHashMap<>(source.getListeners()));
        setFtpletContainer(new DefaultFtpletContainer(
                new LinkedHashMap<>(source.getFtpletContainer().getFtplets())));
        setMessageResource(source.getMessageResource());
        setFtpStatistics(source.getFtpStatistics());
        executor = new OrderedThreadPoolExecutor(
                0,
                Math.max(1, maxThreads),
                30L,
                TimeUnit.SECONDS,
                new LanFtpControlQueueLimiter(
                        maxPendingEvents, maxControlConnections));
    }

    @Override public synchronized ThreadPoolExecutor getThreadPoolExecutor() {
        return executor;
    }

    @Override public void dispose() {
        try {
            super.dispose();
        } finally {
            executor.shutdownNow();
        }
    }
}
