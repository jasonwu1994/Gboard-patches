package dev.jason.gboardpatches.extension.lanftp.runtime;

public interface LanFtpActivityObserver {
    LanFtpActivityObserver NONE = new LanFtpActivityObserver() { };

    default void onSessionCountChanged(int sessionCount) { }

    default void onTransferCountChanged(int transferCount) { }

    default void onSessionActivityChanged() { }
}
