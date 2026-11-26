package dev.jason.gboardpatches.extension.clipboard;

import dev.jason.gboardpatches.extension.clipboard.header.GboardClipboardSelectionBinding;

import android.app.Activity;
import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/** Executes the actual reflection adapter against native-shaped checkbox/toolbar fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardClipboardSelectionBindingTest {
    Activity activity;
    Keyboard keyboard;
    Adapter adapter;
    GboardClipboardSelectionBinding binding;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        keyboard = new Keyboard();
        adapter = keyboard.c;
        adapter.j = new Recycler(activity);
        adapter.j.k = adapter;
        adapter.f = true;
        activity.setContentView(adapter.j);
        binding = new GboardClipboardSelectionBinding(Keyboard.class, Adapter.class,
                Clip.class, Base.class, Recycler.class);
    }

    @After public void tearDown() { activity.finish(); }

    @Test public void replacesSelectionWithAllLoadedRealUnpinnedIncludingOffscreenAndImages()
            throws Exception {
        Clip recentHeader = new Clip(1, false, false);
        Clip pinnedHeader = new Clip(2, false, false);
        Clip tipsHeader = new Clip(3, false, false);
        Clip first = new Clip(0, false, false);
        Clip image = new Clip(0, false, false);
        Clip pin = new Clip(0, true, false);
        Clip tip = new Clip(0, false, true);
        adapter.n.addAll(java.util.Arrays.asList(recentHeader, first, image, pinnedHeader, pin, tipsHeader, tip));
        for (int i = 0; i < 1000; i++) adapter.n.add(3, new Clip(0, false, false));
        adapter.g.put(1004, pin);
        adapter.h = 1;
        adapter.pending.add(1004);
        Assert.assertTrue(binding.hasSelectableItems(keyboard, true));
        binding.selectAll(keyboard, true);
        Assert.assertEquals(1002, adapter.g.size());
        Assert.assertSame(first, adapter.g.get(1));
        Assert.assertSame(image, adapter.g.get(2));
        Assert.assertTrue(adapter.f);
        Assert.assertEquals(0, adapter.h);
        Assert.assertTrue(adapter.pending.isEmpty());
        Assert.assertEquals(2, keyboard.toolbarState);
        Assert.assertEquals(1002, keyboard.selectedCount);
        Assert.assertEquals(1, adapter.notifies);
        for (int i = 0; i < adapter.g.size(); i++) {
            Clip clip = (Clip) adapter.g.valueAt(i);
            Assert.assertEquals(0, clip.type);
            Assert.assertFalse(clip.pin || clip.special);
            Assert.assertSame(clip, adapter.n.get(adapter.g.keyAt(i)));
        }
        binding.selectAll(keyboard, true);
        Assert.assertEquals(1002, adapter.g.size());
        Assert.assertEquals(0, adapter.h);
        // Simulate the official delete consuming this same selection; adapter never invokes it.
        Assert.assertEquals(0, keyboard.deleteCalls);
        Assert.assertTrue(keyboard.originalDeleteSelection().stream().noneMatch(clip -> clip.pin || clip.special));
        Assert.assertEquals(1, keyboard.deleteCalls);
        Assert.assertTrue(adapter.n.contains(pin));
    }

    @Test public void selectsPinsAndUpdatesTheNativeCountWhenExclusionIsOff() throws Exception {
        Clip pin = new Clip(0, true, false);
        Clip secondPin = new Clip(0, true, false);
        Clip recent = new Clip(0, false, false);
        Clip special = new Clip(0, false, true);
        adapter.n.addAll(List.of(new Clip(1, false, false), pin, recent, secondPin, special));
        Assert.assertTrue(binding.hasSelectableItems(keyboard, false));

        binding.selectAll(keyboard, false);

        Assert.assertEquals(3, adapter.g.size());
        Assert.assertEquals(2, adapter.h);
        Assert.assertEquals(3, keyboard.selectedCount);
        Assert.assertEquals(3, keyboard.toolbarState);
        Assert.assertEquals(0, keyboard.deleteCalls);
        Assert.assertEquals(List.of(pin, recent, secondPin), keyboard.originalDeleteSelection());

        binding.selectAll(keyboard, true);

        Assert.assertEquals(1, adapter.g.size());
        Assert.assertSame(recent, adapter.g.get(2));
        Assert.assertEquals(0, adapter.h);
        Assert.assertEquals(2, keyboard.toolbarState);
    }

    @Test public void manualDeselectThenSelectAllRestoresOnlyCurrentUnpinnedModels() throws Exception {
        Clip first = new Clip(0, false, false), second = new Clip(0, false, false);
        adapter.n.addAll(List.of(first, second));
        binding.selectAll(keyboard, true);
        adapter.g.remove(0);
        first.pin = true;
        binding.selectAll(keyboard, true);
        Assert.assertEquals(1, adapter.g.size());
        Assert.assertSame(second, adapter.g.get(1));
        Assert.assertEquals(0, adapter.h);
    }

    @Test public void emptyAndPinnedOnlyHaveNoSelectableItemsAndNeverCallDelete() throws Exception {
        Assert.assertFalse(binding.hasSelectableItems(keyboard, true));
        adapter.n.add(new Clip(0, true, false));
        adapter.n.add(new Clip(0, false, true));
        adapter.n.add(new Clip(1, false, false));
        Assert.assertFalse(binding.hasSelectableItems(keyboard, true));
        binding.selectAll(keyboard, true);
        Assert.assertEquals(0, adapter.g.size());
        Assert.assertEquals(0, adapter.resets);
        Assert.assertEquals(0, adapter.notifies);
        Assert.assertEquals(0, keyboard.deleteCalls);
        Assert.assertTrue(binding.hasSelectableItems(keyboard, false));
    }

    @Test public void nativeBrowseModeDetachedAndReplacedAdapterRejectMutation() throws Exception {
        adapter.n.add(new Clip(0, false, false));
        adapter.f = false;
        binding.selectAll(keyboard, true);
        Assert.assertEquals(0, adapter.resets);
        adapter.f = true;
        adapter.j.k = new Base();
        Assert.assertFalse(binding.hasSelectableItems(keyboard, true));
        binding.selectAll(keyboard, true);
        Assert.assertEquals(0, adapter.resets);
        adapter.j.k = adapter;
        activity.setContentView(new View(activity));
        Assert.assertFalse(binding.hasSelectableItems(keyboard, true));
        binding.selectAll(keyboard, true);
        Assert.assertEquals(0, adapter.resets);
    }

    @Test public void predicateFailureBeforeMutationPreservesOriginalSelection() throws Exception {
        Clip original = new Clip(0, true, false);
        adapter.g.put(9, original);
        adapter.pending.add(9);
        adapter.h = 1;
        Clip invalid = new Clip(0, false, false);
        invalid.fail = true;
        adapter.n.add(invalid);
        try { binding.selectAll(keyboard, true); Assert.fail(); }
        catch (java.lang.reflect.InvocationTargetException expected) { }
        Assert.assertSame(original, adapter.g.get(9));
        Assert.assertEquals(1, adapter.h);
        Assert.assertEquals(List.of(9), adapter.pending);
        Assert.assertEquals(0, adapter.resets);
    }

    @Test public void resetThatNoLongerClearsPinnedCountRejectsAndClearsSelection() throws Exception {
        adapter.n.addAll(List.of(new Clip(0, false, false), new Clip(0, true, false)));
        adapter.g.put(1, adapter.n.get(1));
        adapter.h = 1;
        adapter.driftKeepsPinnedCount = true;
        Assert.assertTrue(binding.usable());
        try { binding.selectAll(keyboard, true); Assert.fail(); }
        catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("pinned count"));
        }
        Assert.assertFalse(binding.usable());
        Assert.assertEquals(0, adapter.g.size());
        Assert.assertTrue(adapter.f);
        Assert.assertEquals(1, keyboard.toolbarState);
        Assert.assertEquals(0, keyboard.deleteCalls);
    }

    @Test public void resetThatNoLongerClearsSelectionRejectsInsteadOfMergingStaleEntries()
            throws Exception {
        Clip stalePin = new Clip(0, true, false);
        adapter.n.addAll(List.of(new Clip(0, false, false), stalePin));
        adapter.g.put(1, stalePin);
        adapter.driftKeepsSelection = true;
        try { binding.selectAll(keyboard, true); Assert.fail(); }
        catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("selected 2 != expected 1"));
        }
        Assert.assertFalse(binding.usable());
        Assert.assertEquals(0, adapter.g.size());
        Assert.assertEquals(0, adapter.h);
        Assert.assertEquals(1, keyboard.toolbarState);
        Assert.assertEquals(0, keyboard.deleteCalls);
    }

    @Test public void writesToTheCurrentMapWhenResetReplacesIt() throws Exception {
        Clip recent = new Clip(0, false, false);
        Clip pin = new Clip(0, true, false);
        adapter.n.addAll(List.of(recent, pin));
        adapter.g.put(1, pin);
        adapter.h = 1;
        adapter.replaceSelectionOnReset = true;
        SparseArray<Object> oldSelection = adapter.g;

        binding.selectAll(keyboard, true);

        Assert.assertNotSame(oldSelection, adapter.g);
        Assert.assertSame(recent, adapter.g.get(0));
        Assert.assertEquals(List.of(recent), keyboard.originalDeleteSelection());
        Assert.assertEquals(1, keyboard.selectedCount);
        Assert.assertTrue(binding.usable());
    }

    @Test public void failedVerificationClearsEvenTheMapInstalledByTheCleanupReset()
            throws Exception {
        Clip pin = new Clip(0, true, false);
        adapter.n.addAll(List.of(new Clip(0, false, false), pin));
        adapter.g.put(1, pin);
        adapter.h = 1;
        adapter.replaceSelectionOnReset = true;
        adapter.driftKeepsSelection = true;
        adapter.driftKeepsPinnedCount = true;

        try { binding.selectAll(keyboard, true); Assert.fail(); }
        catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("pinned count"));
        }
        Assert.assertEquals(2, adapter.resets);
        Assert.assertEquals(0, adapter.g.size());
        Assert.assertEquals(0, adapter.h);
        Assert.assertEquals(0, keyboard.selectedCount);
        Assert.assertFalse(binding.usable());
    }

    @Test public void errorDuringSelectionWriteClearsPartialSelectionAndRefreshesStock()
            throws Exception {
        adapter.n.addAll(List.of(new Clip(0, false, false), new Clip(0, true, false)));
        AssertionError fault = new AssertionError("second insertion failed");
        adapter.g = new SparseArray<>() {
            @Override public void put(int key, Object value) {
                if (key == 1) throw fault;
                super.put(key, value);
            }
        };

        Assert.assertSame(fault, Assert.assertThrows(AssertionError.class,
                () -> binding.selectAll(keyboard, false)));
        Assert.assertFalse(binding.usable());
        Assert.assertEquals(0, adapter.g.size());
        Assert.assertEquals(0, adapter.h);
        Assert.assertTrue(adapter.f);
        Assert.assertEquals(1, adapter.notifies);
        Assert.assertEquals(1, keyboard.toolbarState);
    }

    public static class Base {
        int notifies;
        public final void gC() { notifies++; }
    }
    public static final class Adapter extends Base {
        public Recycler j;
        public final List<Object> n = new ArrayList<>();
        public SparseArray<Object> g = new SparseArray<>();
        public boolean f;
        int h, resets;
        boolean driftKeepsPinnedCount, driftKeepsSelection, replaceSelectionOnReset;
        final List<Integer> pending = new ArrayList<>();
        public final void D() {
            resets++;
            f = false;
            if (!driftKeepsPinnedCount) h = 0;
            if (replaceSelectionOnReset) {
                g = driftKeepsSelection ? g.clone() : new SparseArray<>();
            } else if (!driftKeepsSelection) {
                g.clear();
            }
            pending.clear();
        }
    }
    public static final class Keyboard {
        public Adapter c = new Adapter();
        int toolbarState, selectedCount, deleteCalls;
        public final void K() {
            selectedCount = c.g.size();
            toolbarState = selectedCount == 0 ? 1 : c.h == 0 ? 2 : 3;
        }
        List<Clip> originalDeleteSelection() {
            deleteCalls++;
            List<Clip> result = new ArrayList<>();
            for (int i = 0; i < c.g.size(); i++) result.add((Clip) c.g.valueAt(i));
            return result;
        }
    }
    public static final class Recycler extends View {
        public Base k;
        Recycler(Context context) { super(context); }
    }
    public static final class Clip {
        final int type;
        boolean pin, special, fail;
        Clip(int type, boolean pin, boolean special) { this.type = type; this.pin = pin; this.special = special; }
        public final int g() { if (fail) throw new IllegalStateException("fixture"); return type; }
        public final boolean n() { return pin; }
        public final boolean o() { return special; }
    }
}
