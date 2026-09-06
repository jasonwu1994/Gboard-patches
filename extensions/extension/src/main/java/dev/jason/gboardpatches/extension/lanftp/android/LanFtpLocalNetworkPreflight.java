package dev.jason.gboardpatches.extension.lanftp.android;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/** Detects Android 16 opt-in local-network restrictions before Apache owns the runtime. */
final class LanFtpLocalNetworkPreflight {
    private LanFtpLocalNetworkPreflight() {
    }

    static void verifyLanListenerAllowed(InetAddress address) throws IOException {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()) {
            throw new IOException("A private Wi-Fi address is required for LAN FTP");
        }
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(address, 0));
        } catch (SecurityException | IOException exception) {
            throw new IOException(
                    "Android local-network protection denied a Wi-Fi listener; grant local "
                            + "network access or disable the Android 16 RESTRICT_LOCAL_NETWORK "
                            + "compat test for this target",
                    exception);
        }
    }
}
