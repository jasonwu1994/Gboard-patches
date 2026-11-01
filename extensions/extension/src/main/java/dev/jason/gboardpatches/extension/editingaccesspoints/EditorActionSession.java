package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.inputmethod.InputConnection;
import android.inputmethodservice.InputMethodService;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import dev.jason.gboardpatches.extension.core.input.GboardEditorActions;

/** No connection is retained across clicks. All production calls run on the IME main thread. */
public final class EditorActionSession {
    public interface Editor {
        InputConnection connection();
        default InputMethodService inputMethodService() { return null; }
    }

    private WeakReference<Editor> active = new WeakReference<>(null);
    private long generation;

    public void begin(Editor editor) {
        generation++;
        active = new WeakReference<>(editor);
    }

    public void end() {
        generation++;
        active.clear();
    }

    public boolean execute(EditorAction action) {
        return execute(action, false);
    }

    public boolean execute(EditorAction action, boolean selectAllWhenUnselected) {
        Editor editor = active.get();
        long expectedGeneration = generation;
        if (editor == null || action == null) {
            return false;
        }
        try {
            InputConnection connection = editor.connection();
            BooleanSupplier current = () -> editor.connection() == connection
                    && generation == expectedGeneration && active.get() == editor;
            InputMethodService service = editor.inputMethodService();
            return service == null
                    ? GboardEditorActions.perform(connection, action.contextMenuId,
                            selectAllWhenUnselected, current)
                    : GboardEditorActions.perform(service, action.contextMenuId,
                            selectAllWhenUnselected, current);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
