package dev.jason.gboardpatches.extension.duckemail;

import android.view.inputmethod.InputConnection;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DuckEmailInputSessionTest {
    private final List<String> writes = new ArrayList<>();
    private InputConnection connection(boolean accepts) {
        return (InputConnection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{InputConnection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("commitText")) {
                        writes.add(args[0].toString());
                        assertEquals(1, args[1]);
                        return accepts;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
    }
    private static final class Editor implements DuckEmailInputSession.Editor {
        InputConnection connection;
        boolean shown = true;
        Editor(InputConnection connection) { this.connection = connection; }
        public InputConnection connection() { return connection; }
        public boolean shown() { return shown; }
    }
    @Test public void fillsTheRequestingEditorAndHonorsRejectedCommit() {
        DuckEmailInputSession session = new DuckEmailInputSession();
        assertNull(session.request());
        session.begin(new Editor(connection(true)));
        assertTrue(session.fill(session.request(), "alias@duck.com"));
        assertEquals(List.of("alias@duck.com"), writes);
        session.begin(new Editor(connection(false)));
        assertFalse(session.fill(session.request(), "other@duck.com"));
    }
    @Test public void neverFillsAfterSwitchingFieldOrHidingKeyboard() {
        DuckEmailInputSession session = new DuckEmailInputSession();
        Editor editor = new Editor(connection(true));
        session.begin(editor);
        DuckEmailInputSession.Request request = session.request();
        editor.connection = connection(true);
        assertFalse(session.fill(request, "alias@duck.com"));
        request = session.request();
        editor.shown = false;
        assertFalse(session.fill(request, "alias@duck.com"));
        assertTrue(writes.isEmpty());
    }
    @Test public void returningToSameEditorDoesNotReviveOldRequest() {
        DuckEmailInputSession session = new DuckEmailInputSession();
        Editor editor = new Editor(connection(true));
        session.begin(editor);
        DuckEmailInputSession.Request request = session.request();
        session.end();
        session.begin(editor);
        assertFalse(session.fill(request, "alias@duck.com"));
        assertTrue(writes.isEmpty());
    }
}
