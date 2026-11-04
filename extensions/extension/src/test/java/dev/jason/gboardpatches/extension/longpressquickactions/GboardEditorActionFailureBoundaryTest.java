package dev.jason.gboardpatches.extension.longpressquickactions;

import static org.junit.Assert.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.editingaccesspoints.EditorAction;
import dev.jason.gboardpatches.extension.editingaccesspoints.EditorActionSession;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowClipboardManager;

/** Exercises the actual caller catch boundaries, including failures after an editor side effect. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE,
        shadows = GboardEditorActionFailureBoundaryTest.FaultClipboard.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GboardEditorActionFailureBoundaryTest {
    private static final String TEXT = "boundary 測試 🧪";

    private enum Owner { TOOLBAR, LONG_PRESS, GLOBE }
    private enum Stage {
        CONNECTION, EDITOR_INFO, CLIPBOARD_SERVICE, CLIPBOARD_READ,
        BEGIN, FINISH, COMMIT, COMMIT_AFTER_WRITE, END
    }

    @After public void clearClipboardFault() { FaultClipboard.failure = null; }

    @Test public void connectionLookupFaultIsContained() throws Exception { verify(Stage.CONNECTION); }
    @Test public void editorInfoFaultIsContained() throws Exception { verify(Stage.EDITOR_INFO); }
    @Test public void clipboardServiceFaultIsContained() throws Exception { verify(Stage.CLIPBOARD_SERVICE); }
    @Test public void clipboardReadFaultIsContained() throws Exception { verify(Stage.CLIPBOARD_READ); }
    @Test public void beginFaultDoesNotSendText() throws Exception { verify(Stage.BEGIN); }
    @Test public void finishFaultStillEndsBatchWithoutSendingText() throws Exception { verify(Stage.FINISH); }
    @Test public void commitFaultEndsBatchWithoutRetry() throws Exception { verify(Stage.COMMIT); }
    @Test public void commitFaultAfterWritingDoesNotDuplicateText() throws Exception { verify(Stage.COMMIT_AFTER_WRITE); }
    @Test public void endFaultAfterWritingDoesNotDuplicateText() throws Exception { verify(Stage.END); }

    private void verify(Stage stage) throws Exception {
        for (Owner owner : Owner.values()) {
            for (Throwable failure : new Throwable[] {
                    new IllegalStateException("editor unavailable"),
                    new NoSuchMethodError("editor API unavailable") }) {
                Fixture fixture = new Fixture();
                fixture.stage = stage;
                fixture.failure = failure;
                FaultClipboard.failure = stage == Stage.CLIPBOARD_READ ? failure : null;

                dispatch(owner, fixture, true);

                boolean writesText = stage == Stage.COMMIT_AFTER_WRITE || stage == Stage.END;
                assertEquals(owner + ": " + stage, writesText ? TEXT : "", fixture.connection.output);
                assertEquals(stage == Stage.FINISH || stage == Stage.COMMIT
                        || writesText ? 1 : 0, fixture.connection.ends);
                assertEquals(stage == Stage.COMMIT || writesText ? 1 : 0, fixture.connection.commits);
                assertEquals(0, fixture.connection.menuActions);
                assertEquals(0, fixture.connection.keyEvents);

                // The next editor must work: reuse the toolbar owner, start a new gesture otherwise.
                fixture.stage = null;
                FaultClipboard.failure = null;
                fixture.connection = fixture.new RawConnection();
                dispatch(owner, fixture, false);
                assertEquals(TEXT, fixture.connection.output);
                assertEquals(1, fixture.connection.commits);
                assertEquals(1, fixture.connection.ends);
                assertEquals(0, fixture.connection.menuActions);
            }
        }
    }

    private void dispatch(Owner owner, Fixture fixture, boolean failing) throws Exception {
        switch (owner) {
            case TOOLBAR -> {
                assertEquals(!failing, fixture.toolbar.execute(EditorAction.PASTE, false));
            }
            case LONG_PRESS -> {
                Method method = GboardLongPressQuickActionsRuntime.class.getDeclaredMethod(
                        "consumeRecognizedContextMenuAction",
                        GboardLongPressQuickActionsRuntime.InputConnectionSupplier.class,
                        int.class, boolean.class, InputMethodService.class);
                method.setAccessible(true);
                assertEquals(Boolean.TRUE, method.invoke(null,
                        (GboardLongPressQuickActionsRuntime.InputConnectionSupplier)
                                fixture.ime::getCurrentInputConnection,
                        android.R.id.paste, false, fixture.ime));
            }
            case GLOBE -> {
                Method method = GboardGlobeDragRuntime.class.getDeclaredMethod(
                        "performShortcut", WeakReference.class, GboardEditingShortcutPolicy.Shortcut.class);
                method.setAccessible(true);
                Object tracker = new Object();
                GboardGlobeDragSession session = new GboardGlobeDragSession(
                        1, tracker, shortcut -> {
                            try {
                                return (Boolean) method.invoke(null, new WeakReference<>(fixture.ime), shortcut);
                            } catch (InvocationTargetException wrapped) {
                                throw wrapped.getCause();
                            }
                        }, 750L);
                GboardGlobeDragSession.CommitResult result = session.commitTarget(
                        GboardEditingShortcutPolicy.Shortcut.PASTE, 1_000L);
                assertTrue(result.consumed);
                assertEquals(!failing, result.actionSucceeded);
                assertSame(failing ? fixture.failure : null, result.failure);
                assertNull(session.tracker);
                assertFalse(session.hasActionableClaim());
                assertTrue(session.shouldConsumeGestureReplay(tracker, 1_100L));
            }
        }
    }

    @Implements(ClipboardManager.class)
    public static class FaultClipboard extends ShadowClipboardManager {
        static Throwable failure;

        @Implementation @Override public ClipData getPrimaryClip() {
            throwIfPresent(failure);
            return super.getPrimaryClip();
        }
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }

    private static final class Fixture {
        Stage stage;
        Throwable failure;
        final ProbeIme ime = new ProbeIme();
        RawConnection connection = new RawConnection();
        final EditorActionSession toolbar = new EditorActionSession();
        final EditorActionSession.Editor editor = new EditorActionSession.Editor() {
            @Override public InputConnection connection() { return ime.getCurrentInputConnection(); }
            @Override public InputMethodService inputMethodService() { return ime; }
        };

        Fixture() {
            FaultClipboard.failure = null;
            ime.getSystemService(ClipboardManager.class)
                    .setPrimaryClip(ClipData.newPlainText("test", TEXT));
            toolbar.begin(editor);
        }

        void failAt(Stage boundary) {
            if (stage == boundary) throwIfPresent(failure);
        }

        final class ProbeIme extends InputMethodService {
            final EditorInfo info = new EditorInfo();
            ProbeIme() {
                attachBaseContext(RuntimeEnvironment.getApplication());
                info.inputType = InputType.TYPE_NULL;
            }
            @Override public InputConnection getCurrentInputConnection() {
                failAt(Stage.CONNECTION);
                return connection;
            }
            @Override public EditorInfo getCurrentInputEditorInfo() {
                failAt(Stage.EDITOR_INFO);
                return info;
            }
            @Override public Object getSystemService(String name) {
                if (Context.CLIPBOARD_SERVICE.equals(name)) failAt(Stage.CLIPBOARD_SERVICE);
                return super.getSystemService(name);
            }
        }

        final class RawConnection extends BaseInputConnection {
            String output = "";
            int commits;
            int ends;
            int menuActions;
            int keyEvents;
            RawConnection() { super(new View(ime), true); }
            @Override public boolean beginBatchEdit() { failAt(Stage.BEGIN); return true; }
            @Override public boolean finishComposingText() { failAt(Stage.FINISH); return true; }
            @Override public boolean commitText(CharSequence value, int cursor) {
                commits++;
                failAt(Stage.COMMIT);
                output += value;
                failAt(Stage.COMMIT_AFTER_WRITE);
                return true;
            }
            @Override public boolean endBatchEdit() { ends++; failAt(Stage.END); return true; }
            @Override public boolean performContextMenuAction(int id) { menuActions++; return true; }
            @Override public boolean sendKeyEvent(android.view.KeyEvent event) { keyEvents++; return true; }
        }
    }
}
