package dev.jason.gboardpatches.extension.lanftp;

import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.impl.WriteRequest;
import org.junit.Assert;
import org.junit.Test;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpUserManager;

public final class LanFtpUserManagerTest {
    private static final String PASSWORD = "abcdef";

    @Test
    public void authenticatesOnlyTheConfiguredUserAndPassword() throws Exception {
        LanFtpUserManager manager = new LanFtpUserManager(
                "ftpuser", PASSWORD, false, true, 900, 4);

        User user = manager.authenticate(
                new UsernamePasswordAuthentication("ftpuser", PASSWORD));

        Assert.assertEquals("ftpuser", user.getName());
        Assert.assertTrue(user.getEnabled());
        Assert.assertEquals(900, user.getMaxIdleTime());
        assertAuthenticationRejected(manager,
                new UsernamePasswordAuthentication("ftpuser", PASSWORD + "x"));
        assertAuthenticationRejected(manager,
                new UsernamePasswordAuthentication("other", PASSWORD));
        assertAuthenticationRejected(manager, new AnonymousAuthentication());
    }

    @Test
    public void anonymousLoginIsRejectedWhileDisabled() {
        LanFtpUserManager manager = new LanFtpUserManager(
                "ftpuser", PASSWORD, false, true, 900, 4);

        assertAuthenticationRejected(manager,
                new UsernamePasswordAuthentication("anonymous", ""));
        assertAuthenticationRejected(manager, new AnonymousAuthentication());
    }

    @Test
    public void anonymousLoginAcceptsEmptyOrPlaceholderPasswordWhileEnabled() throws Exception {
        LanFtpUserManager manager = new LanFtpUserManager(
                "ftpuser", PASSWORD, true, true, 900, 4);

        Assert.assertEquals("anonymous", manager.authenticate(
                new UsernamePasswordAuthentication("anonymous", "")).getName());
        Assert.assertEquals("anonymous", manager.authenticate(
                new UsernamePasswordAuthentication("anonymous", "guest@example.invalid")).getName());
        Assert.assertEquals("anonymous", manager.authenticate(
                new AnonymousAuthentication()).getName());
        assertAuthenticationRejected(manager,
                new UsernamePasswordAuthentication("ftpuser", "wrong-password"));
    }

    @Test
    public void anonymousProfileUsesConfiguredWriteAuthority() throws Exception {
        LanFtpUserManager readOnly = new LanFtpUserManager(
                "ftpuser", PASSWORD, true, true, 900, 4);
        LanFtpUserManager writable = new LanFtpUserManager(
                "ftpuser", PASSWORD, true, false, 900, 4);

        User readOnlyUser = readOnly.getUserByName("anonymous");
        User writableUser = writable.getUserByName("anonymous");

        Assert.assertNull(readOnlyUser.authorize(new WriteRequest("/upload.bin")));
        Assert.assertNotNull(writableUser.authorize(new WriteRequest("/upload.bin")));
    }

    @Test
    public void readOnlyProfileCannotAuthorizeWrites() throws Exception {
        LanFtpUserManager readOnly = new LanFtpUserManager("ftpuser", PASSWORD, true, 900, 4);
        LanFtpUserManager writable = new LanFtpUserManager("ftpuser", PASSWORD, false, 900, 4);

        User readOnlyUser = readOnly.getUserByName("ftpuser");
        User writableUser = writable.getUserByName("ftpuser");

        Assert.assertNull(readOnlyUser.authorize(new WriteRequest("/upload.bin")));
        Assert.assertNotNull(writableUser.authorize(new WriteRequest("/upload.bin")));
    }

    private static void assertAuthenticationRejected(LanFtpUserManager manager,
            org.apache.ftpserver.ftplet.Authentication authentication) {
        try {
            manager.authenticate(authentication);
            Assert.fail("Expected FTP authentication to fail");
        } catch (AuthenticationFailedException expected) {
            // Expected.
        }
    }
}
