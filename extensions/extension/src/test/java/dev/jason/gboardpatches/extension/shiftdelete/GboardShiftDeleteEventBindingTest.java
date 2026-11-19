package dev.jason.gboardpatches.extension.shiftdelete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;
import com.google.android.libraries.inputmethod.metadata.SoftKeyDef;
import org.junit.After;
import org.junit.Test;

public class GboardShiftDeleteEventBindingTest {
    @After public void clear() {
        GboardShiftDeleteRuntime.reset();
    }

    @Test public void renamedMembersResolveFromTheirShapes() throws Exception {
        GboardShiftDeleteEventBinding binding = GboardShiftDeleteEventBinding.resolve(Event.class);
        assertEquals("act", binding.action.getName());
        assertEquals("keys", binding.entries.getName());
        assertEquals("meta", binding.metadata.getName());
        assertEquals("hw", binding.hardwareEvent.getName());
        assertEquals("first", binding.code.getName());
        assertEquals("second", binding.flags.getName());
        assertEquals("why", binding.intention.getName());
        assertEquals("extra", binding.payload.getName());
        assertEquals("dup", binding.copy.getName());
    }

    @Test public void remapCopiesTheEventAndKeepsEverythingElse() throws Exception {
        GboardShiftDeleteEventBinding binding = GboardShiftDeleteEventBinding.resolve(Event.class);
        ShiftDeleteSession session = new ShiftDeleteSession();
        binding.remap(event(Action.DOWN, ShiftDeleteSession.SHIFT_DOWN, null), session::key);

        Event original = event(Action.PRESS, ShiftDeleteSession.DELETE, null);
        original.stamp = 1234;
        Event mapped = (Event) binding.remap(original, session::key);

        assertNotSame(original, mapped);
        assertNotSame(original.keys, mapped.keys);
        assertEquals(ShiftDeleteSession.DELETE, original.keys[0].first);
        assertEquals(ShiftDeleteSession.FORWARD_DELETE, mapped.keys[0].first);
        assertEquals(7, mapped.keys[0].second);
        assertSame(Intent.ONLY, mapped.keys[0].why);
        assertSame(original.meta, mapped.meta);
        assertEquals(1234, mapped.stamp);

        binding.remap(event(Action.UP, ShiftDeleteSession.SHIFT_UP, null), session::key);
        assertSame(original, binding.remap(original, session::key));
    }

    @Test public void payloadAndMissingMetadataCannotArmTheFeature() throws Exception {
        GboardShiftDeleteEventBinding binding = GboardShiftDeleteEventBinding.resolve(Event.class);
        ShiftDeleteSession session = new ShiftDeleteSession();
        binding.remap(event(Action.DOWN, ShiftDeleteSession.SHIFT_DOWN, "other.feature"), session::key);
        Event delete = event(Action.PRESS, ShiftDeleteSession.DELETE, null);
        assertSame(delete, binding.remap(delete, session::key));

        Event synthetic = event(Action.DOWN, ShiftDeleteSession.SHIFT_DOWN, null);
        synthetic.meta = null;
        binding.remap(synthetic, session::key);
        assertSame(delete, binding.remap(delete, session::key));
    }

    @Test public void foreignEventsAndNullPassThrough() throws Exception {
        GboardShiftDeleteEventBinding binding = GboardShiftDeleteEventBinding.resolve(Event.class);
        Object other = new Object();
        assertSame(other, binding.remap(other, (action, code) -> 0));
        assertNull(binding.remap(null, (action, code) -> 0));
    }

    @Test public void ambiguousOrIncompleteShapesFailClosed() {
        NoSuchFieldException ambiguous = assertThrows(NoSuchFieldException.class,
                () -> GboardShiftDeleteEventBinding.resolve(AmbiguousEvent.class));
        assertTrue(ambiguous.getMessage(), ambiguous.getMessage().contains("KeyEvent is ambiguous"));
        assertThrows(NoSuchFieldException.class,
                () -> GboardShiftDeleteEventBinding.resolve(Object.class));
        assertThrows(NoSuchMethodException.class,
                () -> GboardShiftDeleteEventBinding.resolve(NoCopyEvent.class));
    }

    @Test public void theRuntimeEntryNeverThrowsAndKeepsTheEventType() {
        Object event = event(Action.PRESS, ShiftDeleteSession.DELETE, null);
        assertSame(event, GboardShiftDeleteRuntime.remapInput(null, event));
        assertSame(event, GboardShiftDeleteRuntime.remapInput("not a service", event));
        assertNull(GboardShiftDeleteRuntime.remapInput("not a service", null));
        assertNull(GboardShiftDeleteRuntime.failure());
    }

    private static Event event(Action action, int code, Object payload) {
        Event event = new Event();
        event.act = action;
        event.keys = new Entry[] {new Entry(code, Intent.ONLY, payload, 7)};
        event.meta = new SoftKeyDef();
        return event;
    }

    enum Action { PRESS, LONG_PRESS, DOWN, UP }
    enum Intent { ONLY }

    static final class Entry {
        final int first;
        final Intent why;
        final Object extra;
        private int second;

        Entry(int first, Intent why, Object extra, int second) {
            this.first = first;
            this.why = why;
            this.extra = extra;
            this.second = second;
        }
    }

    static final class Event {
        Action act;
        Entry[] keys;
        SoftKeyDef meta;
        KeyEvent hw;
        long stamp;

        static Event dup(Event source) {
            Event copy = new Event();
            copy.act = source.act;
            copy.keys = source.keys.clone();
            copy.meta = source.meta;
            copy.hw = source.hw;
            copy.stamp = source.stamp;
            return copy;
        }
    }

    static final class AmbiguousEvent {
        Action act;
        Entry[] keys;
        SoftKeyDef meta;
        KeyEvent hw;
        KeyEvent other;

        static AmbiguousEvent dup(AmbiguousEvent source) {
            return source;
        }
    }

    static final class NoCopyEvent {
        Action act;
        Entry[] keys;
        SoftKeyDef meta;
        KeyEvent hw;
    }
}
