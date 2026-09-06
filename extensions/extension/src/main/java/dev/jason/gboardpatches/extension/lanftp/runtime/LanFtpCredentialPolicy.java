package dev.jason.gboardpatches.extension.lanftp.runtime;

import java.security.SecureRandom;

/** Portable account and password policy shared by configuration, settings and runtime. */
public final class LanFtpCredentialPolicy {
    public static final String USERNAME = "gboard";
    public static final String ANONYMOUS_USERNAME = "anonymous";
    public static final int PASSWORD_LENGTH = 6;

    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private LanFtpCredentialPolicy() {
    }

    public static boolean isValidPassword(String value) {
        if (value == null || value.length() != PASSWORD_LENGTH) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 'a' || character > 'z') {
                return false;
            }
        }
        return true;
    }

    public static String normalizePassword(String value) {
        String normalized = value == null ? "" : value.trim();
        return isValidPassword(normalized) ? normalized : "";
    }

    public static String newPassword() {
        char[] password = new char[PASSWORD_LENGTH];
        for (int index = 0; index < password.length; index++) {
            password[index] = LOWERCASE[RANDOM.nextInt(LOWERCASE.length)];
        }
        return new String(password);
    }

}
