package dev.jason.gboardpatches.extension.clipboard;

import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

public final class GboardClipboardColumnLayouts {
    public interface Access {
        Object manager(View recycler) throws ReflectiveOperationException;
        int count(Object manager) throws ReflectiveOperationException;
        void setCount(Object manager, int count) throws ReflectiveOperationException;
    }
    private static final Map<Object, Entry> LAYOUTS = new WeakHashMap<>();
    private static volatile Snapshot latest;

    private GboardClipboardColumnLayouts() { }
    public static final class Snapshot {
        final boolean clipboardEnabled;final int clipboardColumnCount;
        public Snapshot(boolean enabled,int count){clipboardEnabled=enabled;clipboardColumnCount=count;}
    }

    public static void watch(Object keyboard, View recycler, Object manager,
            int stockCount, Access access, Snapshot snapshot) {
        if (keyboard == null || recycler == null || manager == null || stockCount < 1) return;
        Entry entry = new Entry(recycler, manager, stockCount, access);
        try {
            int current = access.count(manager);
            if (snapshot.clipboardEnabled && current == snapshot.clipboardColumnCount
                    && current != stockCount) entry.applied = current;
        } catch (ReflectiveOperationException failure) { return; }
        synchronized (LAYOUTS) {
            Entry old = LAYOUTS.put(keyboard, entry);
            if (old != null) old.active = false;
        }
        latest = snapshot;
        schedule(entry);
    }

    public static void settingsPublished(Snapshot snapshot) {
        latest = snapshot;
        ArrayList<Entry> entries;
        synchronized (LAYOUTS) { entries = new ArrayList<>(LAYOUTS.values()); }
        for (Entry entry : entries) schedule(entry);
    }

    private static void schedule(Entry entry) {
        View recycler = entry.recycler.get();
        if (recycler != null) recycler.post(() -> apply(entry));
    }

    private static void apply(Entry entry) {
        View recycler = entry.recycler.get();
        Object manager = entry.manager.get();
        Snapshot snapshot = latest;
        if (!entry.active || recycler == null || manager == null || snapshot == null
                || !recycler.isAttachedToWindow()) return;
        try {
            if (entry.access.manager(recycler) != manager) return;
            int current = entry.access.count(manager);
            if (entry.applied != null && current != entry.applied) {
                entry.stockCount = current;
                entry.applied = null;
            }
            int desired = snapshot.clipboardColumnCount;
            if (!snapshot.clipboardEnabled || desired < 1 || desired > 3) {
                if (entry.applied != null && current == entry.applied) {
                    entry.access.setCount(manager, entry.stockCount);
                    entry.applied = null;
                }
                return;
            }
            if (current != desired) entry.access.setCount(manager, desired);
            entry.applied = desired;
        } catch (ReflectiveOperationException | RuntimeException failure) {
        }
    }

    private static final class Entry {
        final WeakReference<View> recycler;
        final WeakReference<Object> manager;
        final Access access;
        int stockCount;
        Integer applied;
        volatile boolean active = true;
        Entry(View recycler, Object manager, int stockCount, Access access) {
            this.recycler = new WeakReference<>(recycler);
            this.manager = new WeakReference<>(manager);
            this.stockCount = stockCount;
            this.access = access;
        }
    }
}
