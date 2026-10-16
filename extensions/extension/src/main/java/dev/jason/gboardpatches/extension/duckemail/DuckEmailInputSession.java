package dev.jason.gboardpatches.extension.duckemail;

import android.view.inputmethod.InputConnection;
import java.lang.ref.WeakReference;

/** Main-thread editor guard, including transitions back to the same field. */
final class DuckEmailInputSession {
    interface Editor {
        InputConnection connection();
        boolean shown();
    }
    static final class Request {
        final long generation;
        final WeakReference<InputConnection> connection;
        Request(long generation, InputConnection connection) {
            this.generation = generation;
            this.connection = new WeakReference<>(connection);
        }
    }
    private Editor editor;
    private long generation;

    void begin(Editor editor) { end(); this.editor = editor; }
    void end() { generation++; editor = null; }
    Request request() {
        InputConnection connection = editor == null ? null : editor.connection();
        return connection == null ? null : new Request(generation, connection);
    }
    boolean fill(Request request, String address) {
        if (request == null || editor == null || request.generation != generation || !editor.shown()) {
            return false;
        }
        InputConnection connection = request.connection.get();
        if (connection == null || editor.connection() != connection) return false;
        connection.finishComposingText();
        return connection.commitText(address, 1);
    }
}
