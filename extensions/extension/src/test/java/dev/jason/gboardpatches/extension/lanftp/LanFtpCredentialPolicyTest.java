package dev.jason.gboardpatches.extension.lanftp;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpCredentialPolicy;

public final class LanFtpCredentialPolicyTest {
    @Test
    public void fixedAccountUsesSixLowercaseLetters() {
        Assert.assertEquals("gboard", LanFtpCredentialPolicy.USERNAME);
        Assert.assertTrue(LanFtpCredentialPolicy.isValidPassword("abcdef"));
        Assert.assertFalse(LanFtpCredentialPolicy.isValidPassword("abcde"));
        Assert.assertFalse(LanFtpCredentialPolicy.isValidPassword("abcdefg"));
        Assert.assertFalse(LanFtpCredentialPolicy.isValidPassword("abc1ef"));
        Assert.assertFalse(LanFtpCredentialPolicy.isValidPassword("ABCDEF"));
    }

    @Test
    public void generatedPasswordsMatchThePolicyAndVary() {
        Set<String> values = new HashSet<>();
        for (int index = 0; index < 32; index++) {
            String password = LanFtpCredentialPolicy.newPassword();
            Assert.assertTrue(password.matches("[a-z]{6}"));
            values.add(password);
        }
        Assert.assertTrue(values.size() > 1);
    }
}
