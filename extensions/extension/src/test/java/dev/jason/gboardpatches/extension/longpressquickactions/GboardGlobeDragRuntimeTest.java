package dev.jason.gboardpatches.extension.longpressquickactions;

import android.view.inputmethod.InputConnection;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardGlobeDragRuntimeTest {
    @Test
    public void exactMarkerDownWithPatchedMetadataArmsSession() {
        Assert.assertSame(GboardGlobeDragRuntime.MarkerDownDisposition.ARM_SESSION,
                GboardGlobeDragRuntime.markerDownDisposition(true, true));
    }

    @Test
    public void exactMarkerDownWithUnknownMetadataIsConsumedWithoutArmingSession() {
        Assert.assertSame(GboardGlobeDragRuntime.MarkerDownDisposition.CONSUME_ONLY,
                GboardGlobeDragRuntime.markerDownDisposition(true, false));
    }

    @Test
    public void nonMarkerEventPassesThroughRegardlessOfMetadataIdentity() {
        Assert.assertSame(GboardGlobeDragRuntime.MarkerDownDisposition.PASS_THROUGH,
                GboardGlobeDragRuntime.markerDownDisposition(false, false));
        Assert.assertSame(GboardGlobeDragRuntime.MarkerDownDisposition.PASS_THROUGH,
                GboardGlobeDragRuntime.markerDownDisposition(false, true));
    }

    @Test
    public void controlChordRetriesKeyUpAndReleasesControlAfterEditorThrows() {
        AtomicInteger events = new AtomicInteger();
        InputConnection connection = (InputConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {InputConnection.class},
                (proxy, method, args) -> {
                    if ("sendKeyEvent".equals(method.getName())) {
                        int call = events.incrementAndGet();
                        if (call == 3) {
                            throw new RuntimeException("key-up failed");
                        }
                        return Boolean.TRUE;
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });

        try {
            GboardGlobeDragRuntime.sendControlChord(connection, 54, 1_000L);
            Assert.fail("expected editor failure");
        } catch (RuntimeException expected) {
            Assert.assertEquals("key-up failed", expected.getMessage());
        }

        Assert.assertEquals(5, events.get());
    }
}
