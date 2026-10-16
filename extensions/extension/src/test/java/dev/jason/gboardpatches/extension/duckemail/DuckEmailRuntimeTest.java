package dev.jason.gboardpatches.extension.duckemail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DuckEmailRuntimeTest {
    private Context context;
    private DuckEmailStore store;
    private ClipboardManager clipboard;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(DuckEmailStore.FILE, 0).edit().clear().commit();
        store = new DuckEmailStore(context);
        clipboard = context.getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("Existing", "keep this"));
    }

    @Test public void defaultAndDisabledOptionPreserveExistingClipboard() {
        assertFalse(DuckEmailRuntime.copyGeneratedAddress(context, "new@duck.com"));
        assertEquals("keep this", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        store.copyGeneratedEmail(true);
        store.copyGeneratedEmail(false);
        assertFalse(DuckEmailRuntime.copyGeneratedAddress(context, "new@duck.com"));
        assertEquals("keep this", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test public void enablingOptionCopiesEachAddressWithoutRestarting() {
        store.copyGeneratedEmail(true);
        assertTrue(DuckEmailRuntime.copyGeneratedAddress(context, "first@duck.com"));
        assertEquals("first@duck.com", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        assertTrue(DuckEmailRuntime.copyGeneratedAddress(context, "second@duck.com"));
        assertEquals("second@duck.com", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }

    @Test public void unavailableClipboardDoesNotEscapeIntoEditorDelivery() {
        store.copyGeneratedEmail(true);
        Context denied = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                if (Context.CLIPBOARD_SERVICE.equals(name)) throw new SecurityException("Denied");
                return super.getSystemService(name);
            }
        };
        assertFalse(DuckEmailRuntime.copyGeneratedAddress(denied, "new@duck.com"));
        Context unavailable = new ContextWrapper(context) {
            @Override public Object getSystemService(String name) {
                return Context.CLIPBOARD_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        assertFalse(DuckEmailRuntime.copyGeneratedAddress(unavailable, "new@duck.com"));
        assertEquals("keep this", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    }
}
