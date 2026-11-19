package dev.jason.gboardpatches.extension.shiftdelete;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShiftDeleteEnabledCacheTest {
    @Test public void definitiveReadIsKeptForTheProcess() {
        for (Boolean stored : new Boolean[] {Boolean.TRUE, Boolean.FALSE}) {
            ShiftDeleteEnabledCache cache = new ShiftDeleteEnabledCache();
            AtomicInteger reads = new AtomicInteger();
            for (long now = 0; now < 10_000; now += 7) {
                assertEquals(stored, cache.get(now, () -> { reads.incrementAndGet(); return stored; }));
            }
            assertEquals(1, reads.get());
            assertEquals(stored, cache.cached());
        }
    }

    @Test public void unavailableReadReportsOffAndRetriesSparingly() {
        ShiftDeleteEnabledCache cache = new ShiftDeleteEnabledCache();
        AtomicInteger reads = new AtomicInteger();
        Boolean[] next = {null};
        for (long now = 0; now < ShiftDeleteEnabledCache.RETRY_MS; now += 10) {
            assertFalse(cache.get(now, () -> { reads.incrementAndGet(); return next[0]; }));
        }
        assertEquals(1, reads.get());
        assertNull(cache.cached());
        next[0] = Boolean.TRUE;
        assertTrue(cache.get(ShiftDeleteEnabledCache.RETRY_MS,
                () -> { reads.incrementAndGet(); return next[0]; }));
        assertEquals(2, reads.get());
        assertEquals(Boolean.TRUE, cache.cached());
    }
}
