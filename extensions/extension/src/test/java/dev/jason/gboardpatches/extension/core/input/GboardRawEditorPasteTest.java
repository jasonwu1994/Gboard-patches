package dev.jason.gboardpatches.extension.core.input;

import static org.junit.Assert.*;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.editingaccesspoints.EditorAction;
import dev.jason.gboardpatches.extension.editingaccesspoints.EditorActionSession;
import dev.jason.gboardpatches.extension.longpressquickactions.GboardEditingShortcutPolicy;
import dev.jason.gboardpatches.extension.longpressquickactions.GboardGlobeDragRuntime;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
public class GboardRawEditorPasteTest {
    private ProbeIme ime;
    private RawConnection connection;
    private ClipboardManager clipboard;

    @Before public void setUp() {
        ime = new ProbeIme();
        connection = new RawConnection();
        ime.connection = connection;
        ime.info = new EditorInfo();
        ime.info.inputType = InputType.TYPE_NULL;
        clipboard = ime.getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("test", "Termux 測試 🧪"));
    }

    @Test public void acceptedButIgnoredContextMenuStillPastesExactlyOnce() {
        assertTrue(paste());
        assertEquals("Termux 測試 🧪", connection.output.toString());
        assertEquals(Arrays.asList("begin", "finish", "commit", "end"), connection.calls);
    }

    @Test public void rawClassWithTermuxWorkaroundFlagsAlsoCommitsText() {
        ime.info.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        assertTrue(paste());
        assertEquals("Termux 測試 🧪", connection.output.toString());
    }

    @Test public void richEditorKeepsNativePasteWithoutReadingClipboard() {
        ime.info.inputType = InputType.TYPE_CLASS_TEXT;
        clipboard.clearPrimaryClip();
        assertTrue(paste());
        assertEquals(Arrays.asList("menu:" + android.R.id.paste), connection.calls);
    }

    @Test public void absentEditorInfoKeepsExistingContextMenuPath() {
        ime.info = null;
        assertTrue(paste());
        assertEquals(Arrays.asList("menu:" + android.R.id.paste), connection.calls);
    }

    @Test public void missingOrEmptyClipDoesNotEditOrSendMenuPaste() {
        clipboard.clearPrimaryClip();
        assertFalse(paste());
        clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""));
        assertFalse(paste());
        assertTrue(connection.calls.isEmpty());
    }

    @Test public void uriOnlyClipIsNotTurnedIntoTerminalText() {
        clipboard.setPrimaryClip(ClipData.newRawUri("media", Uri.parse("content://test/image")));
        assertFalse(paste());
        assertTrue(connection.calls.isEmpty());
    }

    @Test public void whitespaceIsPastedWithoutTrimming() {
        clipboard.setPrimaryClip(ClipData.newPlainText("spaces", "  \t "));
        assertTrue(paste());
        assertEquals("  \t ", connection.output.toString());
    }

    @Test public void changedConnectionDuringFinishStopsCommitAndBalancesBatch() {
        connection.onFinish = () -> ime.connection = new RawConnection();
        assertFalse(paste());
        assertEquals(Arrays.asList("begin", "finish", "end"), connection.calls);
        assertEquals("", connection.output.toString());
    }

    @Test public void sessionInvalidatedDuringFinishStopsCommit() {
        boolean[] current = {true};
        connection.onFinish = () -> current[0] = false;
        assertFalse(GboardEditorActions.perform(ime, android.R.id.paste, true, () -> current[0]));
        assertEquals(Arrays.asList("begin", "finish", "end"), connection.calls);
    }

    @Test public void rejectedCommitDoesNotTryAnotherPastePath() {
        connection.acceptCommit = false;
        assertFalse(paste());
        assertEquals(Arrays.asList("begin", "finish", "commit", "end"), connection.calls);
        assertEquals("", connection.output.toString());
    }

    @Test public void throwingCommitBalancesBatchWithoutRetry() {
        connection.throwOnCommit = true;
        assertThrows(IllegalStateException.class, this::paste);
        assertEquals(Arrays.asList("begin", "finish", "commit", "end"), connection.calls);
    }

    @Test public void rawCopyCutStillTrySelectAllOnceButNeverDeleteUnobservableText() {
        for (int action : new int[] {android.R.id.copy, android.R.id.cut}) {
            connection.calls.clear();
            assertFalse(GboardEditorActions.perform(ime, action, true, () -> true));
            assertEquals(Arrays.asList("selection", "menu:" + android.R.id.selectAll,
                    "selection"), connection.calls);
        }
    }

    @Test public void toolbarSessionSuppliesImeForRawPasteAndRejectsInvalidatedSession() {
        EditorActionSession session = new EditorActionSession();
        EditorActionSession.Editor editor = new EditorActionSession.Editor() {
            @Override public InputConnection connection() { return ime.connection; }
            @Override public InputMethodService inputMethodService() { return ime; }
        };
        session.begin(editor);
        assertTrue(session.execute(EditorAction.PASTE, true));
        assertEquals("Termux 測試 🧪", connection.output.toString());
        connection.calls.clear();
        connection.onFinish = session::end;
        assertFalse(session.execute(EditorAction.PASTE, true));
        assertEquals(Arrays.asList("begin", "finish", "end"), connection.calls);
        assertEquals("Termux 測試 🧪", connection.output.toString());
    }

    @Test public void editorInfoReplacedDuringFinishStopsCommit() {
        connection.onFinish = () -> ime.info = new EditorInfo();
        assertFalse(paste());
        assertEquals(Arrays.asList("begin", "finish", "end"), connection.calls);
    }

    @Test public void globeActionStageUsesRawPasteDispatcher() throws Exception {
        assertTrue(globe(GboardEditingShortcutPolicy.Shortcut.PASTE));
        assertEquals("Termux 測試 🧪", connection.output.toString());
        assertEquals(Arrays.asList("begin", "finish", "commit", "end"), connection.calls);
    }

    @Test public void globeCopyWithoutAutoSelectKeepsSelectionGuard() throws Exception {
        assertFalse(globe(GboardEditingShortcutPolicy.Shortcut.COPY));
        assertEquals(Arrays.asList("selection"), connection.calls);
    }

    private boolean globe(GboardEditingShortcutPolicy.Shortcut shortcut) throws Exception {
        Method method = GboardGlobeDragRuntime.class.getDeclaredMethod("performShortcut",
                WeakReference.class, GboardEditingShortcutPolicy.Shortcut.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, new WeakReference<>(ime), shortcut);
    }

    private boolean paste() {
        return GboardEditorActions.perform(ime, android.R.id.paste, true, () -> true);
    }

    public static class ProbeIme extends InputMethodService {
        InputConnection connection;
        EditorInfo info;
        public ProbeIme() { attachBaseContext(RuntimeEnvironment.getApplication()); }
        @Override public InputConnection getCurrentInputConnection() { return connection; }
        @Override public EditorInfo getCurrentInputEditorInfo() { return info; }
    }

    // Models the observed terminal/remote contract: menu delivery returns true without editing;
    // actual terminal output is separate from BaseInputConnection's temporary Editable.
    private class RawConnection extends BaseInputConnection {
        final StringBuilder output = new StringBuilder();
        final List<String> calls = new ArrayList<>();
        Runnable onFinish = () -> {};
        boolean acceptCommit = true;
        boolean throwOnCommit;
        RawConnection() { super(new View(ime), true); }
        @Override public boolean performContextMenuAction(int id) {
            calls.add("menu:" + id);
            return true;
        }
        @Override public CharSequence getSelectedText(int flags) {
            calls.add("selection");
            return null;
        }
        @Override public boolean beginBatchEdit() { calls.add("begin"); return true; }
        @Override public boolean endBatchEdit() { calls.add("end"); return true; }
        @Override public boolean finishComposingText() {
            calls.add("finish");
            onFinish.run();
            return true;
        }
        @Override public boolean commitText(CharSequence text, int cursor) {
            calls.add("commit");
            if (throwOnCommit) throw new IllegalStateException("commit unavailable");
            if (acceptCommit) output.append(text);
            return acceptCommit;
        }
    }
}
