package dev.jason.gboardpatches.extension.clipboard.header;

import android.util.SparseArray;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Builds the same index-to-model selection used by native checkbox clicks. Never deletes. */
public final class GboardClipboardSelectionBinding implements GboardClipboardHeaderActions.Selection {
    private final Field adapter, recycler, items, selected, editing, selectedPins, recyclerAdapter;
    private final Method type, pinned, special, resetSelection, notifyData, updateToolbar;
    private volatile boolean usable = true;

    static GboardClipboardSelectionBinding resolve(ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> keyboard = Class.forName(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardKeyboard", false, loader);
        Class<?> adapterClass = Class.forName("fqn", false, loader);
        Class<?> clip = Class.forName("fpk", false, loader);
        Class<?> base = Class.forName("jt", false, loader);
        Class<?> list = Class.forName("android.support.v7.widget.RecyclerView", false, loader);
        return new GboardClipboardSelectionBinding(keyboard, adapterClass, clip, base, list);
    }

    public GboardClipboardSelectionBinding(Class<?> keyboard, Class<?> adapterClass, Class<?> clip,
            Class<?> base, Class<?> list) throws ReflectiveOperationException {
        if (!base.isAssignableFrom(adapterClass) || !View.class.isAssignableFrom(list)) {
            throw new NoSuchFieldException("Clipboard selection hierarchy mismatch");
        }
        adapter = GboardClipboardHeaderBinding.field(keyboard, "c", adapterClass, false);
        recycler = GboardClipboardHeaderBinding.field(adapterClass, "j", list, false);
        items = GboardClipboardHeaderBinding.field(adapterClass, "n", List.class, false);
        selected = GboardClipboardHeaderBinding.field(adapterClass, "g", SparseArray.class, false);
        editing = GboardClipboardHeaderBinding.field(adapterClass, "f", boolean.class, false);
        selectedPins = GboardClipboardHeaderBinding.field(adapterClass, "h", int.class, false);
        recyclerAdapter = GboardClipboardHeaderBinding.field(list, "k", base, false);
        type = GboardClipboardHeaderBinding.method(clip, "g", int.class);
        pinned = GboardClipboardHeaderBinding.method(clip, "n", boolean.class);
        special = GboardClipboardHeaderBinding.method(clip, "o", boolean.class);
        resetSelection = GboardClipboardHeaderBinding.method(adapterClass, "D", void.class);
        notifyData = GboardClipboardHeaderBinding.method(base, "gC", void.class);
        updateToolbar = GboardClipboardHeaderBinding.method(keyboard, "K", void.class);
    }

    private Object currentAdapter(Object keyboard) throws ReflectiveOperationException {
        Object value = adapter.get(keyboard);
        Object list = value == null ? null : recycler.get(value);
        return list instanceof View view && view.isAttachedToWindow()
                && recyclerAdapter.get(list) == value ? value : null;
    }

    private boolean isClip(Object clip) throws ReflectiveOperationException {
        return clip != null && ((Integer) type.invoke(clip)) == 0 && !((Boolean) special.invoke(clip));
    }

    private boolean isPinned(Object clip) throws ReflectiveOperationException {
        return (Boolean) pinned.invoke(clip);
    }

    @Override public boolean usable() { return usable; }

    @Override public boolean hasSelectableItems(Object keyboard, boolean excludePinned)
            throws ReflectiveOperationException {
        Object value = currentAdapter(keyboard);
        if (value == null) return false;
        for (Object clip : (List<?>) items.get(value)) {
            if (isClip(clip) && (!excludePinned || !isPinned(clip))) return true;
        }
        return false;
    }

    @Override public void selectAll(Object keyboard, boolean excludePinned)
            throws ReflectiveOperationException {
        Object value = currentAdapter(keyboard);
        if (!usable || value == null || !editing.getBoolean(value)) return;
        List<?> models = (List<?>) items.get(value);
        SparseArray<Object> replacement = new SparseArray<>();
        int pinnedCount = 0;
        for (int index = 0; index < models.size(); index++) {
            Object clip = models.get(index);
            if (!isClip(clip)) continue;
            boolean pinnedClip = isPinned(clip);
            if (excludePinned && pinnedClip) continue;
            replacement.put(index, clip);
            if (pinnedClip) pinnedCount++;
        }
        if (replacement.size() == 0) return;
        // The native reset can replace the map. Read the current map after it runs.
        try {
            resetSelection.invoke(value);
            if (selectedPins.getInt(value) != 0) {
                throw new IllegalStateException("clipboard reset left a nonzero pinned count");
            }
            editing.setBoolean(value, true);
            SparseArray<Object> selection = currentSelection(value);
            if (selection == null) {
                throw new IllegalStateException("clipboard selection unavailable after reset");
            }
            for (int i = 0; i < replacement.size(); i++) {
                selection.put(replacement.keyAt(i), replacement.valueAt(i));
            }
            selectedPins.setInt(value, pinnedCount);
            String mismatch = verify(value, replacement, pinnedCount);
            if (mismatch != null) {
                throw new IllegalStateException("clipboard selection contract mismatch: " + mismatch);
            }
            notifyData.invoke(value);
            updateToolbar.invoke(keyboard);
        } catch (Throwable failure) {
            usable = false;
            clearFailedSelection(value, keyboard, failure);
            throw failure;
        }
    }

    @SuppressWarnings("unchecked")
    private SparseArray<Object> currentSelection(Object value) throws IllegalAccessException {
        return (SparseArray<Object>) selected.get(value);
    }

    private void clearFailedSelection(Object value, Object keyboard, Throwable failure) {
        attemptCleanup(failure, () -> resetSelection.invoke(value));
        // Reset can fail or replace the map. Clean up the state that native code now sees.
        attemptCleanup(failure, () -> {
            SparseArray<Object> selection = currentSelection(value);
            if (selection != null) selection.clear();
        });
        attemptCleanup(failure, () -> selectedPins.setInt(value, 0));
        attemptCleanup(failure, () -> editing.setBoolean(value, true));
        attemptCleanup(failure, () -> notifyData.invoke(value));
        attemptCleanup(failure, () -> updateToolbar.invoke(keyboard));
    }

    private static void attemptCleanup(Throwable failure, Cleanup step) {
        try {
            step.run();
        } catch (Throwable cleanup) {
            if (cleanup != failure) failure.addSuppressed(cleanup);
        }
    }

    private interface Cleanup {
        void run() throws ReflectiveOperationException;
    }

    /** Read-back of the native state the official delete and toolbar will consume. */
    private String verify(Object value, SparseArray<Object> expected, int expectedPins)
            throws ReflectiveOperationException {
        SparseArray<Object> selection = currentSelection(value);
        if (selection == null) return "selection unavailable";
        if (!editing.getBoolean(value)) return "edit flag cleared";
        int storedPins = selectedPins.getInt(value);
        if (storedPins != expectedPins) return "pinned count " + storedPins;
        if (selection.size() != expected.size()) {
            return "selected " + selection.size() + " != expected " + expected.size();
        }
        List<?> models = (List<?>) items.get(value);
        int actualPins = 0;
        for (int i = 0; i < selection.size(); i++) {
            int index = selection.keyAt(i);
            Object clip = selection.valueAt(i);
            if (clip != expected.get(index) || index < 0 || index >= models.size()
                    || models.get(index) != clip || !isClip(clip)) {
                return "entry " + index;
            }
            if (isPinned(clip)) actualPins++;
        }
        return actualPins == expectedPins ? null : "pinned models " + actualPins;
    }
}
