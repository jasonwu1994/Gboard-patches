package dev.jason.gboardpatches.extension.webclipboard;
import org.junit.Test;
import static org.junit.Assert.*;
public final class WebClipboardLocalNetworkPermissionTest {
    @Test public void target37RequiresRuntimeLanGrantOnlyOnAndroid17OrLater() {
        assertTrue(WebClipboardLocalNetworkPermission.isRequired(37,37));
        assertTrue(WebClipboardLocalNetworkPermission.isRequired(38,38));
        assertFalse(WebClipboardLocalNetworkPermission.isRequired(36,37));
        assertFalse(WebClipboardLocalNetworkPermission.isRequired(37,36));
    }
    @Test public void missingContextFailsClosed(){assertFalse(WebClipboardLocalNetworkPermission.isGranted(null));}
}
