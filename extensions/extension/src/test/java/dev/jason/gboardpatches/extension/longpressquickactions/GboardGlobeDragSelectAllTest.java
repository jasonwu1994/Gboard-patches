package dev.jason.gboardpatches.extension.longpressquickactions;

import static org.junit.Assert.*;

import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.editingaccesspoints.GboardEditingAccessPointsSettings;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardGlobeDragSelectAllTest {
    private static final GboardEditingShortcutPolicy.Shortcut[] ACTIONS = {
            GboardEditingShortcutPolicy.Shortcut.COPY, GboardEditingShortcutPolicy.Shortcut.CUT };
    private Fixture fixture;

    @Before public void setUp() {
        fixture = new Fixture();
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, false);
    }

    @After public void clearSettings() {
        GboardLongPressQuickActionsRuntimeSettings.clearEnabledOverrideForTest();
        GboardPatchesSettings.preferences(fixture.ime).edit()
                .remove(GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED).commit();
    }

    @Test public void globeCopyAndCutUseEnabledLongPressSelectAllOption() throws Throwable {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        for (GboardEditingShortcutPolicy.Shortcut action : ACTIONS) {
            fixture.reset();
            assertTrue(perform(action));
            assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll,
                    "read", "menu:" + action.contextMenuActionId), fixture.calls);
        }
    }

    @Test public void disabledOptionKeepsSelectionRequired() throws Throwable {
        for (GboardEditingShortcutPolicy.Shortcut action : ACTIONS) {
            fixture.reset();
            assertFalse(perform(action));
            assertEquals(Arrays.asList("read"), fixture.calls);
        }
    }

    @Test public void toolbarOptionAloneDoesNotEnableGlobeSelectAll() throws Throwable {
        GboardPatchesSettings.preferences(fixture.ime).edit().putBoolean(
                GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED, true).commit();
        assertFalse(perform(GboardEditingShortcutPolicy.Shortcut.CUT));
        assertEquals(Arrays.asList("read"), fixture.calls);
    }

    @Test public void longPressOptionWorksWhenToolbarOptionIsOff() throws Throwable {
        GboardPatchesSettings.preferences(fixture.ime).edit().putBoolean(
                GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED, false).commit();
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        assertTrue(perform(GboardEditingShortcutPolicy.Shortcut.COPY));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll,
                "read", "menu:" + android.R.id.copy), fixture.calls);
    }

    @Test public void existingSelectionIsNotExpanded() throws Throwable {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        fixture.selection = "part";
        assertTrue(perform(GboardEditingShortcutPolicy.Shortcut.CUT));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.cut), fixture.calls);
    }

    @Test public void rejectedSelectAllDoesNotCopyOrCut() throws Throwable {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        fixture.acceptSelectAll = false;
        assertFalse(perform(GboardEditingShortcutPolicy.Shortcut.CUT));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll), fixture.calls);
    }

    @Test public void rawEditorWithoutObservableSelectionIsNeverCut() throws Throwable {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        fixture.ime.info.inputType = InputType.TYPE_NULL;
        fixture.selectedAfterSelectAll = null;
        assertFalse(perform(GboardEditingShortcutPolicy.Shortcut.CUT));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll, "read"), fixture.calls);
    }

    @Test public void replacedEditorAfterSelectAllStopsAction() throws Throwable {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        fixture.onSelectAll = () -> fixture.ime.connection = null;
        assertFalse(perform(GboardEditingShortcutPolicy.Shortcut.CUT));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll), fixture.calls);
    }

    @Test public void selectAllFailureIsContainedByGestureOwnerWithoutReplay() {
        GboardLongPressQuickActionsRuntimeSettings.setOverrideForTest(true, true, true);
        IllegalStateException failure = new IllegalStateException("selection unavailable");
        fixture.onSelectAll = () -> { throw failure; };
        Object tracker = new Object();
        GboardGlobeDragSession session = new GboardGlobeDragSession(
                1, tracker, this::perform, 750L);
        GboardGlobeDragSession.CommitResult result = session.commitTarget(
                GboardEditingShortcutPolicy.Shortcut.CUT, 1_000L);
        assertTrue(result.consumed);
        assertFalse(result.actionSucceeded);
        assertSame(failure, result.failure);
        assertNull(session.tracker);
        assertTrue(session.shouldConsumeGestureReplay(tracker, 1_100L));
        assertEquals(Arrays.asList("read", "menu:" + android.R.id.selectAll), fixture.calls);
    }

    private boolean perform(GboardEditingShortcutPolicy.Shortcut action) throws Throwable {
        Method method = GboardGlobeDragRuntime.class.getDeclaredMethod(
                "performShortcut", WeakReference.class, GboardEditingShortcutPolicy.Shortcut.class);
        method.setAccessible(true);
        try {
            return (Boolean) method.invoke(null, new WeakReference<>(fixture.ime), action);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    private static final class Fixture {
        final ProbeIme ime = new ProbeIme();
        final List<String> calls = new ArrayList<>();
        String selection;
        String selectedAfterSelectAll = "whole field";
        boolean acceptSelectAll = true;
        Runnable onSelectAll = () -> {};
        final InputConnection connection = (InputConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {InputConnection.class}, (proxy, method, args) -> {
                    if ("getSelectedText".equals(method.getName())) {
                        calls.add("read");
                        return selection;
                    }
                    if ("performContextMenuAction".equals(method.getName())) {
                        int id = (Integer) args[0];
                        calls.add("menu:" + id);
                        if (id == android.R.id.selectAll) {
                            onSelectAll.run();
                            if (acceptSelectAll) selection = selectedAfterSelectAll;
                            return acceptSelectAll;
                        }
                        return true;
                    }
                    throw new AssertionError("Unexpected editor action: " + method);
                });
        Fixture() { ime.connection = connection; }
        void reset() { selection = null; calls.clear(); }
    }

    private static final class ProbeIme extends InputMethodService {
        InputConnection connection;
        final EditorInfo info = new EditorInfo();
        ProbeIme() {
            attachBaseContext(RuntimeEnvironment.getApplication());
            info.inputType = InputType.TYPE_CLASS_TEXT;
        }
        @Override public InputConnection getCurrentInputConnection() { return connection; }
        @Override public EditorInfo getCurrentInputEditorInfo() { return info; }
    }
}
