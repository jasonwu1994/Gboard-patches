package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpLocalNetworkPermissionTest {
    @Test
    public void runtimePermissionStartsAtAndroid17ForTarget37() {
        Assert.assertFalse(LanFtpLocalNetworkPermission.isRuntimePermissionRequired(36, 37));
        Assert.assertFalse(LanFtpLocalNetworkPermission.isRuntimePermissionRequired(37, 36));
        Assert.assertTrue(LanFtpLocalNetworkPermission.isRuntimePermissionRequired(37, 37));
        Assert.assertTrue(LanFtpLocalNetworkPermission.isRuntimePermissionRequired(38, 37));
    }
}
