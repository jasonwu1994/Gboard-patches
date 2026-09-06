package dev.jason.gboardpatches.extension.lanftp.android;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpServerConfig;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;

public final class LanFtpPlainValueTypesTest {
    private static final Class<?>[] VALUE_TYPES = {
            LanFtpNotificationContent.class,
            LanFtpServerConfigSnapshot.class,
            LanFtpServerState.class,
            LanFtpServiceCommand.Decoded.class,
            LanFtpSessionInfo.class,
            LanFtpServerConfig.class,
    };

    @Test
    public void portableValuesDoNotRequireRecordRuntimeSupport() {
        for (Class<?> type : VALUE_TYPES) {
            Assert.assertTrue(type.getName(), Modifier.isFinal(type.getModifiers()));
            Assert.assertFalse(type.getName(), type.isRecord());
            Assert.assertSame(type.getName(), Object.class, type.getSuperclass());
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Assert.assertTrue(field.toString(), Modifier.isPrivate(field.getModifiers()));
                Assert.assertTrue(field.toString(), Modifier.isFinal(field.getModifiers()));
            }
        }
    }

    @Test
    public void convertedValuesRetainValueSemantics() {
        LanFtpServerConfig firstConfig = config();
        LanFtpServerConfig secondConfig = config();
        assertValue(firstConfig, secondConfig);
        Assert.assertFalse(firstConfig.toString().contains("secret"));

        LanFtpSessionInfo firstSession = session();
        LanFtpSessionInfo secondSession = session();
        assertValue(firstSession, secondSession);

        LanFtpServerConfigSnapshot firstSnapshot = snapshot();
        LanFtpServerConfigSnapshot secondSnapshot = snapshot();
        assertValue(firstSnapshot, secondSnapshot);
        Assert.assertFalse(firstSnapshot.toString().contains("secret"));

        LanFtpServerState firstState = state(firstSnapshot, firstSession);
        LanFtpServerState secondState = state(secondSnapshot, secondSession);
        assertValue(firstState, secondState);

        LanFtpNotificationContent firstContent =
                new LanFtpNotificationContent("ready", 25, false);
        LanFtpNotificationContent secondContent =
                new LanFtpNotificationContent("ready", 25, false);
        assertValue(firstContent, secondContent);

        LanFtpServiceCommand.Decoded firstDecoded =
                new LanFtpServiceCommand.Decoded(firstConfig, 30_000L, 4L);
        LanFtpServiceCommand.Decoded secondDecoded =
                new LanFtpServiceCommand.Decoded(secondConfig, 30_000L, 4L);
        assertValue(firstDecoded, secondDecoded);
    }

    @Test
    public void stateDefensivelyCopiesClientList() {
        LanFtpServerState state = state(snapshot(), session());
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> state.clients().add(session()));
    }

    private static LanFtpServerConfig config() {
        return new LanFtpServerConfig(2121, 50000, 50009, 4, 300,
                false, true, "gboard", "secret", "file:///sdcard");
    }

    private static LanFtpSessionInfo session() {
        return new LanFtpSessionInfo("1", "192.168.1.2", 10L, 20L,
                "STOR", "/file.bin", "upload", 30L, 40L, 50L);
    }

    private static LanFtpServerConfigSnapshot snapshot() {
        return new LanFtpServerConfigSnapshot(false, 2121, "file:///sdcard", false,
                "gboard", "secret", 4L, true, 50000, 50009, 4, 300_000L);
    }

    private static LanFtpServerState state(
            LanFtpServerConfigSnapshot snapshot, LanFtpSessionInfo session) {
        return new LanFtpServerState(snapshot, false, "Stopped", "", List.of(session),
                0, true, 4L, true, true);
    }

    private static void assertValue(Object first, Object second) {
        Assert.assertNotSame(first, second);
        Assert.assertEquals(first, second);
        Assert.assertEquals(first.hashCode(), second.hashCode());
        Assert.assertEquals(first.toString(), second.toString());
    }
}
