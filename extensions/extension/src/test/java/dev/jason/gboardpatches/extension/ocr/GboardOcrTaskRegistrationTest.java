package dev.jason.gboardpatches.extension.ocr;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class GboardOcrTaskRegistrationTest {
    @Test public void realVoidRegistrationDeliversCompletionWithoutInventingAReturnedTask() throws Throwable {
        VoidTask task = new VoidTask();
        AtomicReference<Object> completed = new AtomicReference<>();
        GboardOcrModuleInstaller.registerCompletionListener(task, CompletionListener.class, completed::set);
        assertEquals(1, task.registrations);
        assertSame(task, completed.get());
        assertEquals(void.class, GboardOcrModuleInstaller
                .resolveCompletionRegistration(VoidTask.class, CompletionListener.class).getReturnType());
    }

    @Test public void fluentLookalikeIsRejectedBeforeRegistration() throws Throwable {
        FluentTask task = new FluentTask();
        assertThrows(NoSuchMethodException.class, () -> GboardOcrModuleInstaller.registerCompletionListener(
                task, CompletionListener.class, ignored -> fail("must not register")));
        assertEquals(0, task.registrations);
    }

    @Test public void staticAndWrongArgumentRegistrationFailClosed() throws Throwable {
        StaticTask.registrations = 0;
        assertThrows(NoSuchMethodException.class, () -> GboardOcrModuleInstaller.registerCompletionListener(
                new StaticTask(), CompletionListener.class, ignored -> fail("must not register")));
        assertEquals(0, StaticTask.registrations);
        assertThrows(NoSuchMethodException.class, () -> GboardOcrModuleInstaller.registerCompletionListener(
                new WrongArgumentTask(), CompletionListener.class, ignored -> fail("must not register")));
    }

    @Test public void completionFailureDoesNotEscapeIntoTheTaskDispatcher() throws Throwable {
        VoidTask task = new VoidTask();
        AtomicInteger callbacks = new AtomicInteger();
        GboardOcrModuleInstaller.registerCompletionListener(task, CompletionListener.class, ignored -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("controlled test callback failure");
        });
        assertEquals(1, task.registrations);
        assertEquals(1, callbacks.get());
    }

    public interface CompletionListener { void a(VoidTask task); }
    public static class VoidTask {
        int registrations;
        public void l(CompletionListener listener) { registrations++; listener.a(this); }
    }
    public static class FluentTask {
        int registrations;
        public FluentTask l(CompletionListener listener) { registrations++; return this; }
    }
    public static class StaticTask {
        static int registrations;
        public static void l(CompletionListener listener) { registrations++; }
    }
    public static class WrongArgumentTask { public void l(Object listener) { } }
}
