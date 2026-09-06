package dev.jason.gboardpatches.extension.lanftp.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class LanFtpNetworkResolver {
    private LanFtpNetworkResolver() {
    }

    public static Snapshot resolve(Context context) {
        if (context == null) {
            return null;
        }
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) {
                return null;
            }
            Network network = manager.getActiveNetwork();
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (network == null || capabilities == null
                    || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return null;
            }
            LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) {
                return null;
            }
            List<InetAddress> addresses = new ArrayList<>();
            for (LinkAddress address : properties.getLinkAddresses()) {
                addresses.add(address.getAddress());
            }
            Optional<InetAddress> selected = chooseUsableAddress(addresses);
            return selected.map(inetAddress -> new Snapshot(
                    manager, network, inetAddress, properties.getInterfaceName())).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Optional<InetAddress> chooseUsableAddress(
            Collection<InetAddress> addresses) {
        if (addresses == null) {
            return Optional.empty();
        }
        Optional<InetAddress> ipv6Candidate = Optional.empty();
        for (InetAddress address : addresses) {
            if (address == null
                    || address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()) {
                continue;
            }
            if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                return Optional.of(address);
            }
            if (address instanceof Inet6Address
                    && !address.isMulticastAddress()
                    && isUniqueLocalIpv6(address)
                    && ipv6Candidate.isEmpty()) {
                ipv6Candidate = Optional.of(address);
            }
        }
        return ipv6Candidate;
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    public static String formatEndpoint(InetAddress address, int port) {
        if (address == null) {
            return "";
        }
        String host = address.getHostAddress();
        if (address instanceof Inet6Address) {
            host = "[" + host + "]";
        }
        return "ftp://" + host + ":" + port;
    }

    public static final class Snapshot {
        public final ConnectivityManager manager;
        public final Network network;
        public final InetAddress address;
        public final String interfaceName;

        Snapshot(ConnectivityManager manager, Network network, InetAddress address,
                String interfaceName) {
            this.manager = manager;
            this.network = network;
            this.address = address;
            this.interfaceName = interfaceName == null ? "" : interfaceName;
        }

        public String endpoint(int port) {
            return formatEndpoint(address, port);
        }
    }
}
