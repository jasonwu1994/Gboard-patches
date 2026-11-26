package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EditorActionSessionTest {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger last = new AtomicInteger();
    private final List<KeyEvent> keyEvents = new ArrayList<>();

    private InputConnection connection(String selected, boolean accepted, Runnable onSelection) {
        return (InputConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {InputConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getSelectedText")) {
                        if (onSelection != null) onSelection.run();
                        return selected;
                    }
                    if (method.getName().equals("performContextMenuAction")) {
                        calls.incrementAndGet();
                        last.set((Integer) args[0]);
                        return accepted;
                    }
                    if (method.getName().equals("sendKeyEvent")) {
                        keyEvents.add((KeyEvent) args[0]);
                        return accepted;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }

    @Test public void dispatchesEachActionExactlyOnce() {
        for (EditorAction action : EditorAction.values()) {
            EditorActionSession session = new EditorActionSession();
            InputConnection connection = connection("文字 text", true, null);
            EditorActionSession.Editor editor = () -> connection;
            session.begin(editor);
            int beforeContext = calls.get();
            int beforeKeys = keyEvents.size();
            assertTrue(action.name(), session.execute(action, false));
            if (action.dispatchType == EditorAction.DispatchType.CONTEXT_MENU) {
                assertEquals(beforeContext + 1, calls.get());
                assertEquals(action.contextMenuId, last.get());
                assertEquals(beforeKeys, keyEvents.size());
            } else {
                assertEquals(beforeContext, calls.get());
                assertEquals(beforeKeys + 2, keyEvents.size());
            }
        }
    }

    @Test public void copyAndCutRejectEmptyButAllowWhitespace() {
        for (EditorAction action : new EditorAction[] {EditorAction.COPY, EditorAction.CUT}) {
            for (String selected : new String[] {null, "", " "}) {
                EditorActionSession session = new EditorActionSession();
                InputConnection connection = connection(selected, true, null);
                EditorActionSession.Editor editor = () -> connection;
                session.begin(editor);
                assertEquals(" ".equals(selected), session.execute(action, false));
            }
        }
        assertEquals(2, calls.get());
    }

    @Test public void hiddenAndReplacedSessionsCannotDispatch() {
        EditorActionSession session = new EditorActionSession();
        InputConnection connection = connection("selected", true, session::end);
        EditorActionSession.Editor editor = () -> connection;
        session.begin(editor);
        assertFalse(session.execute(EditorAction.CUT, false));
        assertFalse(session.execute(EditorAction.PASTE, false));
        assertFalse(session.execute(EditorAction.FORWARD_DELETE, false));
        assertEquals(0, calls.get());
        assertEquals(0, keyEvents.size());
    }

    @Test public void obtainsNewConnectionOnEveryClick() {
        EditorActionSession session = new EditorActionSession();
        InputConnection[] current = {connection("", false, null)};
        EditorActionSession.Editor editor = () -> current[0];
        session.begin(editor);
        assertFalse(session.execute(EditorAction.PASTE, false));
        assertFalse(session.execute(EditorAction.FORWARD_DELETE, false));
        current[0] = connection("", true, null);
        assertTrue(session.execute(EditorAction.PASTE, false));
        assertTrue(session.execute(EditorAction.FORWARD_DELETE, false));
        current[0] = null;
        assertFalse(session.execute(EditorAction.PASTE, false));
        assertFalse(session.execute(EditorAction.FORWARD_DELETE, false));
        assertEquals(2, calls.get());
        // Failed down stops before up (1), succeeded down+up sends 2 -> total 3.
        assertEquals(3, keyEvents.size());
    }

    @Test public void connectionChangeDuringSelectionRejectsCut() {
        EditorActionSession session = new EditorActionSession();
        InputConnection[] current = new InputConnection[1];
        current[0] = connection("text", true,
                () -> current[0] = connection("other", true, null));
        EditorActionSession.Editor editor = () -> current[0];
        session.begin(editor);
        assertFalse(session.execute(EditorAction.CUT, false));
        assertEquals(0, calls.get());
    }

    @Test public void editorFailureIsContained() {
        EditorActionSession session = new EditorActionSession();
        EditorActionSession.Editor editor = () -> { throw new IllegalStateException(); };
        session.begin(editor);
        assertFalse(session.execute(EditorAction.SELECT_ALL, false));
        assertFalse(session.execute(EditorAction.FORWARD_DELETE, false));
    }
}
