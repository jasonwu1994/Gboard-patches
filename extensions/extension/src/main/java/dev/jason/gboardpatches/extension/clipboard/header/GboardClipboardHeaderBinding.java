package dev.jason.gboardpatches.extension.clipboard.header;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.function.Consumer;
import java.util.function.Function;

/** Resolves the clipboard header and its edit action for the admitted target. */
public final class GboardClipboardHeaderBinding implements GboardClipboardHeaderActions.Access {
    // Resource entry names survive resource-id renumbering between releases. The title TextView's
    // own entry name is stripped, so it is found by layout order (see findTitle).
    static final String ICONS_NAME = "key_pos_clipboard_icons";
    static final String CONTROL_ANCHOR_NAME = "key_pos_clipboard_batch_pin";
    public final Method open, close, state, dataChanged, touch;
    private final Field adapter, recycler, owner, recyclerAdapter, panelV2;
    private final Method headerView;
    private final Object headerKind, bodyKind;
    private final Class<?> softKeyboard, softKey;
    private final GboardClipboardSelectionBinding selection;

    private final Function<Context, GboardClipboardHeaderActions.Settings> settings;
    private final Consumer<Throwable> reporter;

    public GboardClipboardHeaderBinding(ClassLoader loader,
            Function<Context, GboardClipboardHeaderActions.Settings> settings,
            Consumer<Throwable> reporter) throws ReflectiveOperationException {
        this.settings = settings;
        this.reporter = reporter;
        Class<?> keyboard = Class.forName(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardKeyboard", false, loader);
        Class<?> baseKeyboard = Class.forName(
                "com.google.android.libraries.inputmethod.keyboard.impl.Keyboard", false, loader);
        Class<?> adapterClass = Class.forName("fqn", false, loader);
        Class<?> adapterBase = Class.forName("jt", false, loader);
        Class<?> kind = Class.forName("qcl", false, loader);
        Class<?> recyclerClass = Class.forName("android.support.v7.widget.RecyclerView", false, loader);
        softKeyboard = Class.forName(
                "com.google.android.libraries.inputmethod.widgets.SoftKeyboardView", false, loader);
        softKey = resolveTitleExclusion(loader);
        if (!baseKeyboard.isAssignableFrom(keyboard)
                || !adapterBase.isAssignableFrom(adapterClass)
                || !View.class.isAssignableFrom(recyclerClass)
                || !android.view.ViewGroup.class.isAssignableFrom(softKeyboard)) {
            throw new NoSuchFieldException("Clipboard header hierarchy mismatch");
        }
        adapter = field(keyboard, "c", adapterClass, false);
        recycler = field(adapterClass, "j", recyclerClass, false);
        owner = field(adapterClass, "w", keyboard, false);
        recyclerAdapter = field(recyclerClass, "k", adapterBase, false);
        panelV2 = field(keyboard, "o", boolean.class, false);
        headerKind = field(kind, "a", kind, true).get(null);
        bodyKind = field(kind, "b", kind, true).get(null);
        headerView = method(baseKeyboard, "eN", View.class, kind);
        open = method(keyboard, "e", void.class, EditorInfo.class, Object.class);
        close = method(keyboard, "h", void.class);
        state = method(keyboard, "M", void.class, int.class, int.class);
        dataChanged = method(adapterClass, "M", void.class, boolean.class);
        touch = method(softKeyboard, "dispatchTouchEvent", boolean.class, MotionEvent.class);
        selection = GboardClipboardSelectionBinding.resolve(loader);
    }

    private static Class<?> resolveTitleExclusion(ClassLoader loader) {
        try {
            Class<?> softKey = Class.forName(
                    "com.google.android.libraries.inputmethod.widgets.SoftKeyView", false, loader);
            return View.class.isAssignableFrom(softKey) ? softKey : null;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return null;
        }
    }

    public void attach(Object keyboard) throws ReflectiveOperationException {
        Object header = actionRoot(keyboard);
        if (!(header instanceof View view) || !softKeyboard.isInstance(view)) return;
        int iconsId = GboardClipboardHeaderLayout.idByName(view, ICONS_NAME);
        int anchorId = GboardClipboardHeaderLayout.idByName(view, CONTROL_ANCHOR_NAME);
        if (iconsId == 0 || anchorId == 0) {
            throw new NoSuchFieldException("Clipboard header id names missing");
        }
        View icons = view.findViewById(iconsId);
        View anchor = view.findViewById(anchorId);
        Object value = adapter.get(keyboard);
        Object list = value == null ? null : recycler.get(value);
        if (!(icons instanceof LinearLayout row)) return;
        TextView text = softKey == null ? null
                : GboardClipboardHeaderLayout.findTitle(view, row, softKey);
        if (anchor == null || anchor.getParent() != row) return;
        if (!(list instanceof View body)) return;
        GboardClipboardHeaderActions.attach(keyboard, view, row, anchor, text, body,
                this, selection);
    }

    private Object actionRoot(Object keyboard) throws ReflectiveOperationException {
        // On the v2 panel, stock hides the header and puts the editing controls in the body.
        return headerView.invoke(keyboard, panelV2.getBoolean(keyboard) ? bodyKind : headerKind);
    }

    public Object keyboardForAdapter(Object value) throws IllegalAccessException {
        return owner.get(value);
    }

    @Override public boolean isCurrent(Object keyboard, View header)
            throws ReflectiveOperationException {
        Object value = adapter.get(keyboard);
        Object list = value == null ? null : recycler.get(value);
        return actionRoot(keyboard) == header
                && list instanceof View view && view.isAttachedToWindow()
                && recyclerAdapter.get(list) == value;
    }

    @Override public GboardClipboardHeaderActions.Settings settings(Context context) {
        return settings.apply(context);
    }

    @Override public void report(Throwable failure) {
        reporter.accept(failure);
    }

    static Field field(Class<?> type, String name, Class<?> expected, boolean isStatic)
            throws NoSuchFieldException {
        Field result = type.getDeclaredField(name);
        if (result.getType() != expected || Modifier.isStatic(result.getModifiers()) != isStatic) {
            throw new NoSuchFieldException(type.getName() + "." + name);
        }
        result.setAccessible(true);
        return result;
    }

    static Method method(Class<?> type, String name, Class<?> result, Class<?>... params)
            throws NoSuchMethodException {
        Method value = type.getDeclaredMethod(name, params);
        if (value.getReturnType() != result || Modifier.isStatic(value.getModifiers())) {
            throw new NoSuchMethodException(type.getName() + "." + name);
        }
        value.setAccessible(true);
        return value;
    }
}
