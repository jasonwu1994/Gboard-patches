package dev.jason.gboardpatches.extension.lanftp.android;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

/** Sole Android IPC codec for the atomic LAN FTP session value. */
final class LanFtpSessionBundleCodec {
    private static final String ID = "id";
    private static final String PEER = "peer";
    private static final String LOGIN_TIME = "login_time";
    private static final String LAST_ACTIVITY = "last_activity";
    private static final String COMMAND = "command";
    private static final String PATH = "path";
    private static final String DIRECTION = "direction";
    private static final String BYTES = "bytes";
    private static final String TOTAL_BYTES = "total_bytes";
    private static final String BYTES_PER_SECOND = "bytes_per_second";

    private LanFtpSessionBundleCodec() {
    }

    static ArrayList<Bundle> encode(List<LanFtpSessionInfo> sessions) {
        ArrayList<Bundle> result = new ArrayList<>();
        if (sessions == null) {
            return result;
        }
        for (LanFtpSessionInfo session : sessions) {
            if (session == null) {
                continue;
            }
            Bundle item = new Bundle();
            item.putString(ID, session.id());
            item.putString(PEER, session.peer());
            item.putLong(LOGIN_TIME, session.loginTimeMillis());
            item.putLong(LAST_ACTIVITY, session.lastActivityMillis());
            item.putString(COMMAND, session.command());
            item.putString(PATH, session.path());
            item.putString(DIRECTION, session.direction());
            item.putLong(BYTES, session.bytesTransferred());
            item.putLong(TOTAL_BYTES, session.totalBytes());
            item.putLong(BYTES_PER_SECOND, session.bytesPerSecond());
            result.add(item);
        }
        return result;
    }

    static List<LanFtpSessionInfo> decode(ArrayList<Bundle> bundles) {
        List<LanFtpSessionInfo> result = new ArrayList<>();
        if (bundles == null) {
            return result;
        }
        for (Bundle item : bundles) {
            if (item != null) {
                result.add(new LanFtpSessionInfo(
                        item.getString(ID, ""),
                        item.getString(PEER, "unknown"),
                        item.getLong(LOGIN_TIME, 0L),
                        item.getLong(LAST_ACTIVITY, 0L),
                        item.getString(COMMAND, "IDLE"),
                        item.getString(PATH, ""),
                        item.getString(DIRECTION, "idle"),
                        item.getLong(BYTES, 0L),
                        item.getLong(TOTAL_BYTES, -1L),
                        item.getLong(BYTES_PER_SECOND, 0L)));
            }
        }
        return result;
    }
}
