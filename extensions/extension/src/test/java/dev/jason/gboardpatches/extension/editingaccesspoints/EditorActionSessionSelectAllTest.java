package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class EditorActionSessionSelectAllTest {
    private static final EditorAction[] ACTIONS = {EditorAction.COPY, EditorAction.CUT};

    @Test public void enabledOptionSelectsAllOnceBeforeCopyOrCut() {
        for (EditorAction action : ACTIONS) {
            Fixture fixture = new Fixture();
            assertTrue(fixture.session.execute(action, true));
            fixture.assertCalls("read1", android.R.id.selectAll, "read2", action.contextMenuId);
        }
    }

    @Test public void disabledOptionDoesNotSelectAll() {
        for (EditorAction action : ACTIONS) {
            Fixture fixture = new Fixture();
            assertFalse(fixture.session.execute(action, false));
            fixture.assertCalls("read1");
        }
    }

    @Test public void existingSelectionAndWhitespaceDoNotTriggerSelectAll() {
        for (EditorAction action : ACTIONS) {
            for (String selected : new String[] {"selected", " "}) {
                Fixture fixture = new Fixture();
                fixture.before = selected;
                assertTrue(fixture.session.execute(action, true));
                fixture.assertCalls("read1", action.contextMenuId);
            }
        }
    }

    @Test public void emptyOrRejectedSelectAllStopsWithoutRetry() {
        for (EditorAction action : ACTIONS) {
            Fixture empty = new Fixture();
            empty.after = "";
            assertFalse(empty.session.execute(action, true));
            empty.assertCalls("read1", android.R.id.selectAll, "read2");
            Fixture rejected = new Fixture();
            rejected.selectAllAccepted = false;
            assertFalse(rejected.session.execute(action, true));
            rejected.assertCalls("read1", android.R.id.selectAll);
        }
    }

    @Test public void connectionReplacementAtEitherReadOrSelectAllStopsFurtherEdits() {
        for (EditorAction action : ACTIONS) {
            for (String boundary : new String[] {"read1", "selectAll", "read2"}) {
                Fixture fixture = new Fixture();
                fixture.boundary = boundary;
                InputConnection replacement = new Fixture().connection;
                fixture.onBoundary = () -> fixture.connection = replacement;
                assertFalse(fixture.session.execute(action, true));
                fixture.assertStoppedAtBoundary();
            }
        }
    }

    @Test public void newSessionAtEitherReadOrSelectAllStopsEvenWithSameConnection() {
        for (EditorAction action : ACTIONS) {
            for (String boundary : new String[] {"read1", "selectAll", "read2"}) {
                Fixture fixture = new Fixture();
                fixture.boundary = boundary;
                fixture.onBoundary = () -> fixture.session.begin(fixture.editor);
                assertFalse(fixture.session.execute(action, true));
                fixture.assertStoppedAtBoundary();
            }
        }
    }

    @Test public void readAndSelectAllExceptionsAreContained() {
        for (EditorAction action : ACTIONS) {
            for (String boundary : new String[] {"read1", "selectAll", "read2"}) {
                Fixture fixture = new Fixture();
                fixture.boundary = boundary;
                fixture.onBoundary = () -> { throw new IllegalStateException("editor failure"); };
                assertFalse(fixture.session.execute(action, true));
                fixture.assertStoppedAtBoundary();
            }
        }
    }

    private static final class Fixture {
        final EditorActionSession session = new EditorActionSession();
        final List<Object> calls = new ArrayList<>();
        InputConnection connection;
        final EditorActionSession.Editor editor = () -> connection;
        String before;
        String after = "all text";
        String boundary;
        Runnable onBoundary;
        boolean selectAllAccepted = true;
        int reads;

        Fixture() {
            connection = (InputConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {InputConnection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getSelectedText")) {
                            String current = "read" + ++reads;
                            calls.add(current);
                            visit(current);
                            return reads == 1 ? before : after;
                        }
                        if (method.getName().equals("performContextMenuAction")) {
                            int action = (Integer) args[0];
                            calls.add(action);
                            if (action == android.R.id.selectAll) {
                                visit("selectAll");
                                return selectAllAccepted;
                            }
                            return true;
                        }
                        throw new AssertionError("Unexpected InputConnection call: " + method);
                    });
            session.begin(editor);
        }

        void visit(String current) {
            if (current.equals(boundary)) onBoundary.run();
        }

        void assertStoppedAtBoundary() {
            if (boundary.equals("read1")) assertCalls("read1");
            else if (boundary.equals("selectAll")) assertCalls("read1", android.R.id.selectAll);
            else assertCalls("read1", android.R.id.selectAll, "read2");
        }

        void assertCalls(Object... expected) { assertEquals(Arrays.asList(expected), calls); }
    }
}
