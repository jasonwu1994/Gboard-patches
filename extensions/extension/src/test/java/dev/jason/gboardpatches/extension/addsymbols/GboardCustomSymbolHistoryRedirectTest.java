package dev.jason.gboardpatches.extension.addsymbols;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class GboardCustomSymbolHistoryRedirectTest {
    @Test public void acceptsOnlyStringM2CaseSevenAndExactOwnedSink() {
        Object sink = new Object();
        AtomicInteger writes = new AtomicInteger();
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, writes::incrementAndGet));
        for (Object wrong : new Object[] {5, 8, 14, "7", null}) {
            assertFalse(GboardCustomSymbolHistoryRedirect.write(wrong, true, sink, sink, writes::incrementAndGet));
        }
        assertFalse(GboardCustomSymbolHistoryRedirect.write(7, false, sink, sink, writes::incrementAndGet));
        assertFalse(GboardCustomSymbolHistoryRedirect.write(7, true, sink, new Object(), writes::incrementAndGet));
        assertFalse(GboardCustomSymbolHistoryRedirect.write(7, true, null, null, writes::incrementAndGet));
        assertEquals(1, writes.get());
    }

    @Test public void nestedStockAndFooterAreInertAndOuterCustomStillOwnsItsWrite() {
        Object sink = new Object();
        AtomicInteger custom = new AtomicInteger();
        AtomicInteger official = new AtomicInteger();
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, () -> {
            assertFalse(GboardCustomSymbolHistoryRedirect.write(7, false, sink, sink, official::incrementAndGet));
            assertFalse(GboardCustomSymbolHistoryRedirect.write(8, true, sink, sink, official::incrementAndGet));
            custom.incrementAndGet();
        }));
        assertEquals(1, custom.get());
        assertEquals(0, official.get());
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, custom::incrementAndGet));
        assertEquals(2, custom.get());
    }

    @Test public void sameSinkReentryIsConsumedWithoutWritingTwice() {
        Object sink = new Object();
        AtomicInteger writes = new AtomicInteger();
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, () -> {
            writes.incrementAndGet();
            assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, writes::incrementAndGet));
        }));
        assertEquals(1, writes.get());
    }

    @Test public void nestedCustomKeyboardHasIndependentSinkAndExceptionCleanup() {
        Object outer = new Object();
        Object inner = new Object();
        AtomicInteger writes = new AtomicInteger();
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, outer, outer, () -> {
            try {
                GboardCustomSymbolHistoryRedirect.write(7, true, inner, inner, () -> { throw new IllegalStateException("fixture"); });
                fail("expected inner failure");
            } catch (IllegalStateException expected) { }
            assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, inner, inner, writes::incrementAndGet));
            writes.incrementAndGet();
        }));
        assertEquals(2, writes.get());
    }

    @Test public void throwingOuterWriteCannotLeaveHistoryClaimed() {
        Object sink = new Object();
        try {
            GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, () -> { throw new IllegalArgumentException("fixture"); });
            fail("expected failure");
        } catch (IllegalArgumentException expected) { }
        AtomicInteger writes = new AtomicInteger();
        assertTrue(GboardCustomSymbolHistoryRedirect.write(7, true, sink, sink, writes::incrementAndGet));
        assertEquals(1, writes.get());
    }
}
