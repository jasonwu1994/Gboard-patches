package dev.jason.gboardpatches.extension.lanftp;

import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Optional;

import dev.jason.gboardpatches.extension.lanftp.android.LanFtpNetworkResolver;

public final class LanFtpNetworkResolverTest {
    @Test
    public void choosesPrivateIpv4AndRejectsLoopbackOrPublicAddresses() throws Exception {
        Optional<InetAddress> chosen = LanFtpNetworkResolver.chooseUsableAddress(Arrays.asList(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("192.168.1.42")));

        Assert.assertTrue(chosen.isPresent());
        Assert.assertEquals("192.168.1.42", chosen.get().getHostAddress());
        Assert.assertFalse(LanFtpNetworkResolver.chooseUsableAddress(Arrays.asList(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("8.8.8.8"))).isPresent());
    }

    @Test
    public void prefersPrivateIpv4EvenWhenUsableIpv6AppearsFirst() throws Exception {
        Optional<InetAddress> chosen = LanFtpNetworkResolver.chooseUsableAddress(Arrays.asList(
                InetAddress.getByName("2001:db8::42"),
                InetAddress.getByName("192.168.1.42")));

        Assert.assertTrue(chosen.isPresent());
        Assert.assertEquals("192.168.1.42", chosen.get().getHostAddress());
    }

    @Test
    public void acceptsUniqueLocalIpv6OnlyWifiAddressAndBracketsItsEndpoint() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("fd12:3456::42");

        Optional<InetAddress> chosen = LanFtpNetworkResolver.chooseUsableAddress(
                Arrays.asList(ipv6));

        Assert.assertTrue(chosen.isPresent());
        Assert.assertEquals("ftp://[fd12:3456:0:0:0:0:0:42]:2121",
                LanFtpNetworkResolver.formatEndpoint(chosen.get(), 2121));
    }

    @Test
    public void rejectsGloballyRoutableIpv6Address() throws Exception {
        Assert.assertFalse(LanFtpNetworkResolver.chooseUsableAddress(Arrays.asList(
                InetAddress.getByName("2001:4860:4860::8888"))).isPresent());
    }

    @Test
    public void rejectsUnusableIpv6Addresses() throws Exception {
        Assert.assertFalse(LanFtpNetworkResolver.chooseUsableAddress(Arrays.asList(
                InetAddress.getByName("::"),
                InetAddress.getByName("::1"),
                InetAddress.getByName("fe80::42"))).isPresent());
    }
}
