package dev.jason.gboardpatches.extension.core.input;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.function.BooleanSupplier;

/** Public Android editor actions. A true result means dispatched, not confirmed by the app. */
public final class GboardEditorActions {
    private GboardEditorActions() {}

    public static boolean perform(InputMethodService service, int actionId,
            boolean selectAllWhenUnselected, BooleanSupplier isCurrentEditor) {
        if (service == null || actionId == 0 || !isCurrentEditor.getAsBoolean()) {
            return false;
        }
        InputConnection connection = service.getCurrentInputConnection();
        EditorInfo info = service.getCurrentInputEditorInfo();
        if (connection == null) {
            return false;
        }
        BooleanSupplier current = () -> isCurrentEditor.getAsBoolean()
                && service.getCurrentInputConnection() == connection
                && service.getCurrentInputEditorInfo() == info;
        if (actionId == android.R.id.paste && info != null
                && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL) {
            // Raw editors may acknowledge context-menu delivery without implementing Paste.
            // Choose the text route before sending anything; never send a second paste as fallback.
            return pasteRawText(service, connection, current);
        }
        return perform(connection, actionId, selectAllWhenUnselected, current);
    }

    private static boolean pasteRawText(InputMethodService service, InputConnection connection,
            BooleanSupplier isCurrentEditor) {
        ClipboardManager clipboard = service.getSystemService(ClipboardManager.class);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return false;
        }
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null) {
            return false; // Media/URI-only clips must not become terminal input.
        }
        String text = value.toString();
        if (text.isEmpty() || !isCurrentEditor.getAsBoolean()) {
            return false;
        }
        connection.beginBatchEdit();
        try {
            if (!isCurrentEditor.getAsBoolean()) {
                return false;
            }
            connection.finishComposingText();
            return isCurrentEditor.getAsBoolean() && connection.commitText(text, 1);
        } finally {
            connection.endBatchEdit();
        }
    }

    public static boolean perform(InputConnection connection, int actionId,
            boolean selectAllWhenUnselected, BooleanSupplier isCurrentEditor) {
        if (connection == null || actionId == 0 || !isCurrentEditor.getAsBoolean()) {
            return false;
        }
        if (actionId == android.R.id.copy || actionId == android.R.id.cut) {
            CharSequence selected = connection.getSelectedText(0);
            // Editor callbacks can replace the session while a selection query is in flight.
            if (!isCurrentEditor.getAsBoolean()) {
                return false;
            }
            if (selected == null || selected.length() == 0) {
                if (!selectAllWhenUnselected
                        || !connection.performContextMenuAction(android.R.id.selectAll)
                        || !isCurrentEditor.getAsBoolean()) {
                    return false;
                }
                selected = connection.getSelectedText(0);
                if (selected == null || selected.length() == 0
                        || !isCurrentEditor.getAsBoolean()) {
                    return false;
                }
            }
        }
        return connection.performContextMenuAction(actionId);
    }
}
