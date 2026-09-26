package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.inputmethod.InputConnection;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardLongPressQuickActions1803RuntimeTest {
    private static final String RUNTIME_CLASS =
            "dev.jason.gboardpatches.extension.longpressquickactions."
                    + "GboardLongPressQuickActions1803Runtime";

    @Test
    public void attemptsExactContextMenuActionAndConsumesEvenWhenTargetReturnsFalse()
            throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        AtomicInteger receivedAction = new AtomicInteger(0);
        InputConnection connection = proxyConnection((proxy, method, args) -> {
            if ("getSelectedText".equals(method.getName())) {
                return "selected";
            }
            if ("performContextMenuAction".equals(method.getName())) {
                calls.incrementAndGet();
                receivedAction.set(((Integer) args[0]).intValue());
                return Boolean.FALSE;
            }
            return defaultValue(method.getReturnType());
        });

        Assert.assertTrue(attemptContextMenuAction(connection, 0x1020021));
        Assert.assertEquals(1, calls.get());
        Assert.assertEquals(0x1020021, receivedAction.get());
    }

    @Test
    public void refusesMissingConnectionAndInvalidActionId() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        InputConnection connection = proxyConnection((proxy, method, args) -> {
            if ("performContextMenuAction".equals(method.getName())) {
                calls.incrementAndGet();
                return Boolean.TRUE;
            }
            return defaultValue(method.getReturnType());
        });

        Assert.assertFalse(attemptContextMenuAction(null, 0x102001f));
        Assert.assertFalse(attemptContextMenuAction(connection, 0));
        Assert.assertEquals(0, calls.get());
    }

    @Test
    public void copyAndCutRejectEmptySelectionBeforeDispatch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InputConnection connection = proxyConnection((proxy, method, args) -> {
            if ("getSelectedText".equals(method.getName())) {
                return "";
            }
            if ("performContextMenuAction".equals(method.getName())) {
                calls.incrementAndGet();
                return Boolean.TRUE;
            }
            return defaultValue(method.getReturnType());
        });

        Assert.assertFalse(attemptContextMenuAction(connection, android.R.id.copy));
        Assert.assertFalse(attemptContextMenuAction(connection, android.R.id.cut));
        Assert.assertEquals(0, calls.get());
    }

    @Test
    public void recognizedActionConsumesEditorFailure() {
        InputConnection connection = proxyConnection((proxy, method, args) -> {
            if ("performContextMenuAction".equals(method.getName())) {
                throw new IllegalStateException("editor failed");
            }
            return defaultValue(method.getReturnType());
        });

        Assert.assertTrue(GboardLongPressQuickActions1803Runtime
                .consumeRecognizedContextMenuAction(connection, android.R.id.paste));
    }

    @Test
    public void recognizedActionConsumesConnectionLookupFailure() {
        Assert.assertTrue(GboardLongPressQuickActions1803Runtime
                .consumeRecognizedContextMenuAction(() -> {
                    throw new IllegalStateException("connection lookup failed");
                }, android.R.id.paste));
    }

    @Test
    public void disabledBindRestoresOnlyKnownPatchedMetadata() {
        Object original = new Object();
        Object patched = new Object();
        Object unrelated = new Object();

        GboardLongPressQuickActions1803Runtime.rememberPatchedMetadata(original, patched, 0);

        Assert.assertSame(original,
                GboardLongPressQuickActions1803Runtime.metadataForBind(false, patched));
        Assert.assertSame(original,
                GboardLongPressQuickActions1803Runtime.metadataForBind(false, original));
        Assert.assertSame(unrelated,
                GboardLongPressQuickActions1803Runtime.metadataForBind(false, unrelated));
        Assert.assertSame(patched,
                GboardLongPressQuickActions1803Runtime.metadataForBind(true, patched));
    }

    @Test
    public void disabledModeConsumesOnlyMatchingEventsFromInjectedMetadata() {
        Object original = new Object();
        Object patched = new Object();
        GboardLongPressQuickActions1803Runtime.rememberPatchedMetadata(original, patched, 0);

        Assert.assertTrue(GboardLongPressQuickActions1803Runtime
                .shouldConsumeDisabledInjectedEvent(false, patched, true));
        Assert.assertFalse(GboardLongPressQuickActions1803Runtime
                .shouldConsumeDisabledInjectedEvent(false, patched, false));
        Assert.assertFalse(GboardLongPressQuickActions1803Runtime
                .shouldConsumeDisabledInjectedEvent(false, original, true));
        Assert.assertFalse(GboardLongPressQuickActions1803Runtime
                .shouldConsumeDisabledInjectedEvent(true, patched, true));
    }

    @Test
    public void metadataCacheFailureReturnsIncomingMetadata() {
        Object metadata = new Object() {
            @Override
            public int hashCode() {
                throw new IllegalStateException("metadata hash failed");
            }
        };
        GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        try {
            GboardLongPressQuickActionsRuntimeSettings.setEnabledOverrideForTest(true);

            Assert.assertSame(
                    metadata,
                    GboardLongPressQuickActions1803Runtime.maybePatchMetadata(metadata, null));
        } finally {
            GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        }
    }

    @Test
    public void changingPositionRestoresOriginalAndRejectsStaleCachedMetadata() {
        Object original = new Object();
        Object patchedFirst = new Object();
        GboardLongPressQuickActions1803Runtime.rememberPatchedMetadata(
                original, patchedFirst, GboardLongPressQuickActionsSettings.POSITION_FIRST);

        Assert.assertSame(patchedFirst,
                GboardLongPressQuickActions1803Runtime.metadataForPosition(
                        patchedFirst, GboardLongPressQuickActionsSettings.POSITION_FIRST));
        Assert.assertSame(patchedFirst,
                GboardLongPressQuickActions1803Runtime.cachedMetadataForPosition(
                        original, GboardLongPressQuickActionsSettings.POSITION_FIRST));
        Assert.assertSame(original,
                GboardLongPressQuickActions1803Runtime.metadataForPosition(
                        patchedFirst, GboardLongPressQuickActionsSettings.POSITION_LAST));
        Assert.assertNull(GboardLongPressQuickActions1803Runtime.cachedMetadataForPosition(
                original, GboardLongPressQuickActionsSettings.POSITION_LAST));
    }

    private static boolean attemptContextMenuAction(InputConnection connection, int actionId)
            throws Exception {
        try {
            Class<?> runtime = Class.forName(RUNTIME_CLASS);
            Method method = runtime.getDeclaredMethod(
                    "attemptContextMenuAction", InputConnection.class, int.class);
            method.setAccessible(true);
            return ((Boolean) method.invoke(
                    null, connection, Integer.valueOf(actionId))).booleanValue();
        } catch (ClassNotFoundException missing) {
            Assert.fail("missing GboardLongPressQuickActions1803Runtime");
            throw missing;
        }
    }

    private static InputConnection proxyConnection(InvocationHandler handler) {
        return (InputConnection) Proxy.newProxyInstance(
                GboardLongPressQuickActions1803RuntimeTest.class.getClassLoader(),
                new Class<?>[] {InputConnection.class},
                handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        return null;
    }
}
