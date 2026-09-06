package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.content.Intent;

import java.util.Objects;

import dev.jason.gboardpatches.extension.lanftp.config.LanFtpPreferences;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;

/** Sole Bundle codec for the immutable settings-to-service start snapshot. */
final class LanFtpServiceCommand {
    static final String ACTION_START_OR_UPDATE =
            "dev.jason.gboardpatches.action.LAN_FTP_START_OR_UPDATE";
    static final String ACTION_STOP = "dev.jason.gboardpatches.action.LAN_FTP_STOP";

    private static final String EXTRA_CONTROL_PORT = "controlPort";
    private static final String EXTRA_PASSIVE_START = "passiveStart";
    private static final String EXTRA_PASSIVE_END = "passiveEnd";
    private static final String EXTRA_MAX_SESSIONS = "maxSessions";
    private static final String EXTRA_IDLE_TIMEOUT_MS = "idleTimeoutMs";
    private static final String EXTRA_READ_ONLY = "readOnly";
    private static final String EXTRA_ALLOW_ANONYMOUS = "allowAnonymous";
    private static final String EXTRA_USERNAME = "username";
    private static final String EXTRA_PASSWORD = "password";
    private static final String EXTRA_PASSWORD_REVISION = "passwordRevision";
    private static final String EXTRA_ROOT_TREE_URI = "rootTreeUri";

    private LanFtpServiceCommand() {
    }

    static Intent startIntent(Context context, LanFtpServerConfigSnapshot snapshot,
            LanFtpPreferences.Snapshot privateSnapshot) {
        if (privateSnapshot == null
                || !snapshot.password().equals(privateSnapshot.password)
                || snapshot.passwordRevision() != privateSnapshot.passwordRevision) {
            throw new IllegalArgumentException("LAN FTP private credentials are unavailable");
        }
        return new Intent(context, LanFtpService.class)
                .setAction(ACTION_START_OR_UPDATE)
                .putExtra(EXTRA_CONTROL_PORT, snapshot.controlPort())
                .putExtra(EXTRA_PASSIVE_START, snapshot.passivePortStart())
                .putExtra(EXTRA_PASSIVE_END, snapshot.passivePortEnd())
                .putExtra(EXTRA_MAX_SESSIONS, snapshot.maxSessions())
                .putExtra(EXTRA_IDLE_TIMEOUT_MS, snapshot.idleTimeoutMs())
                .putExtra(EXTRA_READ_ONLY, snapshot.readOnly())
                .putExtra(EXTRA_ALLOW_ANONYMOUS, snapshot.allowAnonymous())
                .putExtra(EXTRA_USERNAME, snapshot.username())
                .putExtra(EXTRA_PASSWORD, snapshot.password())
                .putExtra(EXTRA_PASSWORD_REVISION, snapshot.passwordRevision())
                .putExtra(EXTRA_ROOT_TREE_URI, snapshot.rootTreeUri());
    }

    static Intent stopIntent(Context context) {
        return new Intent(context, LanFtpService.class).setAction(ACTION_STOP);
    }

    static Decoded decode(Intent intent) {
        if (intent == null || !ACTION_START_OR_UPDATE.equals(intent.getAction())) {
            throw new IllegalArgumentException("Intent is not a LAN FTP start snapshot");
        }
        long idleTimeoutMs = intent.getLongExtra(
                EXTRA_IDLE_TIMEOUT_MS, LanFtpPreferences.DEFAULT_IDLE_TIMEOUT_MS);
        LanFtpServerConfig config = new LanFtpServerConfig(
                intent.getIntExtra(
                        EXTRA_CONTROL_PORT, LanFtpPreferences.DEFAULT_CONTROL_PORT),
                intent.getIntExtra(
                        EXTRA_PASSIVE_START, LanFtpPreferences.DEFAULT_PASSIVE_PORT_START),
                intent.getIntExtra(
                        EXTRA_PASSIVE_END, LanFtpPreferences.DEFAULT_PASSIVE_PORT_END),
                intent.getIntExtra(
                        EXTRA_MAX_SESSIONS, LanFtpPreferences.DEFAULT_MAX_SESSIONS),
                LanFtpPreferences.DEFAULT_CONTROL_SESSION_IDLE_TIMEOUT_SECONDS,
                intent.getBooleanExtra(
                        EXTRA_READ_ONLY, LanFtpPreferences.DEFAULT_READ_ONLY),
                intent.getBooleanExtra(
                        EXTRA_ALLOW_ANONYMOUS, LanFtpPreferences.DEFAULT_ALLOW_ANONYMOUS),
                intent.getStringExtra(EXTRA_USERNAME),
                intent.getStringExtra(EXTRA_PASSWORD),
                intent.getStringExtra(EXTRA_ROOT_TREE_URI));
        return new Decoded(config, Math.max(30_000L, idleTimeoutMs),
                Math.max(0L, intent.getLongExtra(EXTRA_PASSWORD_REVISION, 0L)));
    }

    static final class Decoded {
        private final LanFtpServerConfig config;
        private final long idleTimeoutMs;
        private final long passwordRevision;

        Decoded(LanFtpServerConfig config, long idleTimeoutMs, long passwordRevision) {
            this.config = config;
            this.idleTimeoutMs = idleTimeoutMs;
            this.passwordRevision = passwordRevision;
        }

        LanFtpServerConfig config() {
            return config;
        }

        long idleTimeoutMs() {
            return idleTimeoutMs;
        }

        long passwordRevision() {
            return passwordRevision;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Decoded that)) {
                return false;
            }
            return idleTimeoutMs == that.idleTimeoutMs
                    && passwordRevision == that.passwordRevision
                    && Objects.equals(config, that.config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config, idleTimeoutMs, passwordRevision);
        }

        @Override
        public String toString() {
            return "Decoded[config=" + config + ", idleTimeoutMs=" + idleTimeoutMs
                    + ", passwordRevision=" + passwordRevision + "]";
        }
    }
}
