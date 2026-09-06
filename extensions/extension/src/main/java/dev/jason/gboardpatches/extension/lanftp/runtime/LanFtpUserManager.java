package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.apache.ftpserver.ftplet.Authentication;
import org.apache.ftpserver.ftplet.AuthenticationFailedException;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication;
import org.apache.ftpserver.usermanager.AnonymousAuthentication;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;

public final class LanFtpUserManager implements UserManager {
    public static final String ANONYMOUS_USERNAME =
            LanFtpCredentialPolicy.ANONYMOUS_USERNAME;
    private final String username;
    private final byte[] usernameBytes;
    private final byte[] passwordBytes;
    private final User user;
    private final boolean allowAnonymous;
    private final User anonymousUser;

    public LanFtpUserManager(String username, String password, boolean readOnly,
            int idleTimeoutSeconds, int maxSessions) {
        this(username, password, false, readOnly, idleTimeoutSeconds, maxSessions);
    }

    public LanFtpUserManager(String username, String password, boolean allowAnonymous,
            boolean readOnly, int idleTimeoutSeconds, int maxSessions) {
        this.username = requireText(username, "username");
        this.allowAnonymous = allowAnonymous;
        String safePassword = requireText(password, "password");
        usernameBytes = this.username.getBytes(StandardCharsets.UTF_8);
        passwordBytes = safePassword.getBytes(StandardCharsets.UTF_8);

        user = createUser(this.username, readOnly, idleTimeoutSeconds, maxSessions);
        anonymousUser = createUser(
                ANONYMOUS_USERNAME, readOnly, idleTimeoutSeconds, maxSessions);
    }

    private static User createUser(String username, boolean readOnly,
            int idleTimeoutSeconds, int maxSessions) {
        BaseUser configuredUser = new BaseUser();
        configuredUser.setName(username);
        configuredUser.setEnabled(true);
        configuredUser.setHomeDirectory("/");
        configuredUser.setMaxIdleTime(idleTimeoutSeconds);
        List<Authority> authorities = new ArrayList<>();
        authorities.add(new ConcurrentLoginPermission(maxSessions, maxSessions));
        if (!readOnly) {
            authorities.add(new WritePermission());
        }
        configuredUser.setAuthorities(authorities);
        return configuredUser;
    }

    @Override
    public User getUserByName(String candidate) {
        if (usernamesMatch(candidate)) {
            return user;
        }
        return anonymousMatches(candidate) ? anonymousUser : null;
    }

    @Override
    public String[] getAllUserNames() {
        return allowAnonymous
                ? new String[]{username, ANONYMOUS_USERNAME}
                : new String[]{username};
    }

    @Override
    public void delete(String candidate) throws FtpException {
        throw new FtpException("LAN FTP users are immutable while the server is running");
    }

    @Override
    public void save(User candidate) throws FtpException {
        throw new FtpException("LAN FTP users are immutable while the server is running");
    }

    @Override
    public boolean doesExist(String candidate) {
        return usernamesMatch(candidate) || anonymousMatches(candidate);
    }

    @Override
    public User authenticate(Authentication authentication)
            throws AuthenticationFailedException {
        if (authentication instanceof AnonymousAuthentication) {
            if (allowAnonymous) {
                return anonymousUser;
            }
            throw new AuthenticationFailedException("Anonymous login is disabled");
        }
        if (!(authentication instanceof UsernamePasswordAuthentication credentials)) {
            throw new AuthenticationFailedException("Unsupported authentication method");
        }
        byte[] candidateUsername = safeBytes(credentials.getUsername());
        byte[] candidatePassword = safeBytes(credentials.getPassword());
        if (allowAnonymous && MessageDigest.isEqual(
                safeBytes(ANONYMOUS_USERNAME), candidateUsername)) {
            return anonymousUser;
        }
        boolean accepted = MessageDigest.isEqual(usernameBytes, candidateUsername)
                & MessageDigest.isEqual(passwordBytes, candidatePassword);
        if (!accepted) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        return user;
    }

    @Override
    public String getAdminName() {
        return "";
    }

    @Override
    public boolean isAdmin(String candidate) {
        return false;
    }

    private boolean usernamesMatch(String candidate) {
        return MessageDigest.isEqual(usernameBytes, safeBytes(candidate));
    }

    private boolean anonymousMatches(String candidate) {
        return allowAnonymous && MessageDigest.isEqual(
                safeBytes(ANONYMOUS_USERNAME), safeBytes(candidate));
    }

    private static byte[] safeBytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is empty");
        }
        return value;
    }
}
