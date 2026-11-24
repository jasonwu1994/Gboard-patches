package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.view.KeyEvent;

/** Android editor semantics; independent of keyboard language and Gboard transport codes. */
public enum EditorAction {
    SELECT_ALL("text_edit_select_all", DispatchType.CONTEXT_MENU, android.R.id.selectAll, true),
    COPY("text_edit_copy", DispatchType.CONTEXT_MENU, android.R.id.copy, true),
    CUT("text_edit_cut", DispatchType.CONTEXT_MENU, android.R.id.cut, true),
    PASTE("text_edit_paste", DispatchType.CONTEXT_MENU, android.R.id.paste, true),
    FORWARD_DELETE("text_edit_forward_delete", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_FORWARD_DEL, false),
    PASTE_PLAIN_TEXT("text_edit_paste_plain_text", DispatchType.CONTEXT_MENU, android.R.id.pasteAsPlainText, false),
    HOME("text_edit_home", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_MOVE_HOME, false),
    END("text_edit_end", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_MOVE_END, false),
    TAB("text_edit_tab", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_TAB, false),
    ARROW_LEFT("text_edit_arrow_left", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_DPAD_LEFT, false),
    ARROW_RIGHT("text_edit_arrow_right", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_DPAD_RIGHT, false),
    ARROW_UP("text_edit_arrow_up", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_DPAD_UP, false),
    ARROW_DOWN("text_edit_arrow_down", DispatchType.KEY_EVENT, KeyEvent.KEYCODE_DPAD_DOWN, false);

    public enum DispatchType {
        CONTEXT_MENU,
        KEY_EVENT
    }

    public final String token;
    public final DispatchType dispatchType;
    public final int contextMenuId;
    public final int keyCode;
    public final boolean defaultEnabled;

    EditorAction(String token, DispatchType dispatchType, int code, boolean defaultEnabled) {
        this.token = token;
        this.dispatchType = dispatchType;
        this.contextMenuId = dispatchType == DispatchType.CONTEXT_MENU ? code : 0;
        this.keyCode = dispatchType == DispatchType.KEY_EVENT ? code : 0;
        this.defaultEnabled = defaultEnabled;
    }
}
