package dev.jason.gboardpatches.extension.clipboard;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class GboardClipboardCountdownLifecycleTest {
    private GboardClipboardRuntimeBehaviorTest fixture;
    private ActivityController<Activity> controller;
    private FrameLayout parent;
    private CountingLabel label;
    private Object adapter;
    private Object holder;
    private List<Object> items;
    private GboardClipboardUiHookAdapter ui;
    private GboardClipboardRuntimeSupport support;

    @Before public void setUp() throws Exception {
        fixture = new GboardClipboardRuntimeBehaviorTest();
        fixture.setUp();
        settings(3_600_000L);
        ui = (GboardClipboardUiHookAdapter) invoke("newUiAdapter", new Class<?>[0]);
        Field field = GboardClipboardUiHookAdapter.class.getDeclaredField("support");
        field.setAccessible(true);
        support = (GboardClipboardRuntimeSupport) field.get(ui);
        controller = Robolectric.buildActivity(Activity.class).setup();
        parent = new FrameLayout(controller.get());
        label = new CountingLabel(controller.get());
        parent.addView(label);
        controller.get().setContentView(parent);
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(label.isAttachedToWindow());
        assertTrue(label.isShown());
        Object clip = newClip(false, false, System.currentTimeMillis());
        @SuppressWarnings("unchecked") List<Object> assembly = (List<Object>) invoke(
                "assemblyWithRecent", new Class<?>[] {List.class}, Arrays.asList(clip));
        items = assembly;
        adapter = invoke("newAdapter", new Class<?>[] {List.class, int.class, int.class}, items, 1, 0);
        holder = invoke("construct", new Class<?>[] {String.class, Class[].class, Object[].class},
                "fql", new Class<?>[] {TextView.class}, new Object[] {label});
    }

    @After public void tearDown() throws Exception {
        if (ui != null) {
            Method clear = GboardClipboardUiHookAdapter.class.getDeclaredMethod("clearCountdownBinding", TextView.class);
            clear.setAccessible(true);
            for (TextView view : new ArrayList<>(support.activeCountdownByTextView.keySet())) clear.invoke(ui, view);
        }
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void visibleFiniteCardKeepsOneTimer() {
        bind("payload");
        int posts = label.posts;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(posts + 2, label.posts);
    }

    @Test public void hiddenAttachedCardStopsAndResumesWithoutRebind() {
        bind("payload");
        parent.setVisibility(View.GONE);
        layout();
        assertTrue(label.isAttachedToWindow());
        assertFalse(label.isShown());
        assertIdle();
        parent.setVisibility(View.VISIBLE);
        layout();
        assertResumed();
    }

    @Test public void hiddenWindowStopsAndResumesWithoutLayoutChange() {
        bind("payload");
        label.windowVisibility = View.GONE;
        label.getViewTreeObserver().dispatchOnPreDraw();
        assertIdle();
        label.windowVisibility = View.VISIBLE;
        label.getViewTreeObserver().dispatchOnPreDraw();
        assertResumed();
    }

    @Test public void lastPendingTickStopsEvenWithoutVisibilityEvent() {
        bind("payload");
        label.windowVisibility = View.GONE;
        assertIdle();
        label.windowVisibility = View.VISIBLE;
        label.getViewTreeObserver().dispatchOnPreDraw();
        assertResumed();
    }

    @Test public void hiddenBindDoesNotStartTimer() {
        parent.setVisibility(View.GONE);
        bind("payload");
        assertEquals(0, label.posts);
        assertIdle();
        parent.setVisibility(View.VISIBLE);
        layout();
        assertResumed();
    }

    @Test public void permanentRetentionNeverStartsTimer() {
        settings(-1L);
        bind("payload");
        assertTrue(label.getText().toString().contains("[inf]"));
        assertEquals(0, label.posts);
        assertIdle();
    }

    @Test public void unchangedTextAndFramesDoNotRewriteOrRestartTimer() {
        settings(-1L);
        bind("payload");
        GboardClipboardRuntimeSupport.CountdownBinding binding = support.activeCountdownByTextView.get(label);
        int writes = label.writes;
        ui.onCountdownTick(binding);
        for (int i = 0; i < 20; i++) {
            label.getViewTreeObserver().dispatchOnPreDraw();
            layout();
        }
        assertEquals(writes, label.writes);
        assertEquals(0, label.posts);
    }

    @Test public void unchangedVisibleFramesDoNotPostponeFiniteTimer() {
        bind("payload");
        int posts = label.posts;
        int writes = label.writes;
        for (int i = 0; i < 20; i++) {
            label.getViewTreeObserver().dispatchOnPreDraw();
            layout();
        }
        assertEquals(posts, label.posts);
        assertEquals(writes, label.writes);
        assertResumed();
    }

    @Test public void detachCancelsAndDropsActiveOwnershipThenReattachResumes() {
        bind("payload");
        Object binding = support.activeCountdownByTextView.get(label);
        parent.removeView(label);
        assertFalse(support.activeCountdownByTextView.containsKey(label));
        assertSame(binding, support.detachedCountdownByTextView.get(label).get());
        assertIdle();
        parent.addView(label);
        layout();
        assertTrue(support.activeCountdownByTextView.containsKey(label));
        assertResumed();
    }

    @Test public void bindingHasOnlyWeakViewAndTreeReferences() throws Exception {
        bind("payload");
        Object binding = support.activeCountdownByTextView.get(label);
        for (Field field : binding.getClass().getDeclaredFields()) {
            assertFalse("strong view/tree field: " + field,
                    View.class.isAssignableFrom(field.getType())
                    || ViewTreeObserver.class.isAssignableFrom(field.getType()));
        }
        Field reference = binding.getClass().getDeclaredField("textViewReference");
        reference.setAccessible(true);
        assertSame(label, ((WeakReference<?>) reference.get(binding)).get());
    }

    @Test public void staleCallbackCannotChangeReboundCard() {
        bind("payload-A");
        Runnable old = support.activeCountdownByTextView.get(label);
        bind("payload-B");
        String rendered = label.getText().toString();
        assertTrue(rendered.endsWith("payload-B"));
        int posts = label.posts;
        old.run();
        assertEquals(rendered, label.getText().toString());
        assertEquals(posts, label.posts);
        assertResumed();
    }

    @Test public void detachedRebindForHeaderCannotResurrectOldCard() {
        bind("payload-A");
        Runnable old = support.activeCountdownByTextView.get(label);
        parent.removeView(label);
        items.set(1, invoke("header", new Class<?>[] {String.class}, "a"));
        label.setText("header");
        ui.afterItemBind(adapter, holder, 1);
        int posts = label.posts;
        parent.addView(label);
        layout();
        old.run();
        assertEquals("header", label.getText().toString());
        assertFalse(support.activeCountdownByTextView.containsKey(label));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals(posts, label.posts);
    }

    @Test public void detachedRebindForNewCardRestartsOnlyNewTimer() {
        bind("payload-A");
        Runnable old = support.activeCountdownByTextView.get(label);
        parent.removeView(label);
        bind("payload-B");
        Object replacement = support.activeCountdownByTextView.get(label);
        parent.addView(label);
        layout();
        assertSame(replacement, support.activeCountdownByTextView.get(label));
        int posts = label.posts;
        old.run();
        assertEquals(posts, label.posts);
        assertTrue(label.getText().toString().endsWith("payload-B"));
        assertResumed();
    }

    @Test public void disablingCountdownStopsVisibleTimerAndRestoresPayload() {
        bind("payload");
        controller.get().getSharedPreferences(GboardClipboardSettings.PREF_FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(GboardClipboardSettings.PREF_KEY_CLIPBOARD_SHOW_COUNTDOWN, false).commit();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("payload", label.getText().toString());
        assertIdle();
    }

    @Test public void pinnedSpecialAndExpiredLabelsHaveNoTimer() {
        for (Object clip : new Object[] {
                newClip(true, false, System.currentTimeMillis()),
                newClip(false, true, System.currentTimeMillis()),
                newClip(false, false, System.currentTimeMillis() - 7_200_000L)}) {
            items.set(1, clip);
            bind("payload");
            assertEquals(0, label.posts);
        }
        assertIdle();
    }

    private Object newClip(boolean pinned, boolean special, long timestamp) {
        return invoke("newClip", new Class<?>[] {long.class, long.class, int.class, boolean.class, boolean.class},
                101L, timestamp, 0, pinned, special);
    }

    private void bind(String text) {
        ui.beforeItemBind(adapter, holder, 1);
        label.setText(text);
        ui.afterItemBind(adapter, holder, 1);
        assertTrue(label.getText().toString().contains("["));
    }

    private void settings(long ttl) {
        invoke("setRuntimePreferences", new Class<?>[] {long.class, int.class, int.class,
                boolean.class, boolean.class, boolean.class, String.class, int.class},
                ttl, 100, 5, true, false, false, "newest_first", 2);
    }

    private void layout() { parent.getViewTreeObserver().dispatchOnGlobalLayout(); }

    private void assertIdle() {
        int writes = label.writes;
        int posts = label.posts;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("inactive label must not be rewritten", writes, label.writes);
        assertEquals("inactive label must not poll", posts, label.posts);
    }

    private void assertResumed() {
        int posts = label.posts;
        assertTrue("visible finite countdown must have a timer", posts > 0);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(posts + 1, label.posts);
    }

    private Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = fixture.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(fixture, args);
        } catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static final class CountingLabel extends TextView {
        int writes;
        int posts;
        int windowVisibility = View.VISIBLE;
        CountingLabel(Context context) {
            super(context);
            addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                public void onTextChanged(CharSequence s, int start, int before, int count) { writes++; }
                public void afterTextChanged(Editable text) { }
            });
        }
        @Override public boolean postDelayed(Runnable action, long delay) {
            if (delay == 1_000L) posts++;
            return super.postDelayed(action, delay);
        }
        @Override public int getWindowVisibility() { return windowVisibility; }
    }
}
