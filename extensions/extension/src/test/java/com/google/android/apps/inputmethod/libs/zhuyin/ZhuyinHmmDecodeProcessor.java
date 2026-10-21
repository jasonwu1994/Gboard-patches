package com.google.android.apps.inputmethod.libs.zhuyin;

import java.util.Collections;
import java.util.Iterator;

/** Mirrors the target's composing, iterator and commit reflection contracts. */
public class ZhuyinHmmDecodeProcessor {
    public android.content.Context u;
    public final Model m = new Model();
    public String committed;
    public int cursorPosition;
    public boolean reset;

    protected void aC(String text, int cursor, boolean clear) {
        committed = text;
        cursorPosition = cursor;
        reset = clear;
        m.composing = "";
    }

    public static final class Model {
        public String composing = "ㄉ";
        public String e() { return composing; }
        public Iterator<?> f() { return Collections.singletonList("stock candidate").iterator(); }
    }
}
