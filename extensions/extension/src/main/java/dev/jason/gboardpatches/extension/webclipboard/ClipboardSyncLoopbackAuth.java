package dev.jason.gboardpatches.extension.webclipboard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class ClipboardSyncLoopbackAuth {
    static final String CHALLENGE_QUERY = "loopbackChallenge";
    static final String PROOF_FIELD = "loopbackProof";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MAX_CHALLENGE_LENGTH = 128;

    private ClipboardSyncLoopbackAuth() {
    }

    static String proof(String token, String challenge) {
        if (!hasUsableToken(token) || !hasUsableChallenge(challenge)) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return hex(mac.doFinal(challenge.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable ignored) {
            return "";
        }
    }

    static boolean proofMatches(String token, String challenge, String suppliedProof) {
        String expectedProof = proof(token, challenge);
        if (expectedProof.isEmpty() || suppliedProof == null || suppliedProof.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedProof.getBytes(StandardCharsets.UTF_8),
                suppliedProof.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasUsableToken(String token) {
        return token != null
                && !token.isEmpty()
                && token.indexOf('\r') < 0
                && token.indexOf('\n') < 0;
    }

    private static boolean hasUsableChallenge(String challenge) {
        if (challenge == null || challenge.isEmpty()
                || challenge.length() > MAX_CHALLENGE_LENGTH) {
            return false;
        }
        for (int index = 0; index < challenge.length(); index++) {
            char current = challenge.charAt(index);
            if (current <= ' ' || current == '\u007f') {
                return false;
            }
        }
        return true;
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(Character.forDigit((current >> 4) & 0xF, 16));
            builder.append(Character.forDigit(current & 0xF, 16));
        }
        return builder.toString();
    }
}

