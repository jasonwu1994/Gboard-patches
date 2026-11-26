package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import dev.jason.gboardpatches.extension.core.input.GboardEditorActions;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GboardEditingAccessPointIconsTest {
    @Test public void rendersEveryEditorActionIconWithoutTargetDrawables() {
        Context context = RuntimeEnvironment.getApplication();
        for (EditorAction action : EditorAction.values()) {
            assertTrue("Missing spec: " + action.token,
                    GboardEditingAccessPointIcons.hasIcon(action.token));
            Bitmap bitmap = GboardEditingAccessPointIcons.renderBitmap(action.token, 72, 480);
            assertNotNull("Null bitmap for " + action.token, bitmap);
            assertEquals(72, bitmap.getWidth());
            assertEquals(72, bitmap.getHeight());
            assertEquals(480, bitmap.getDensity());

            int opaqueWhitePixels = 0;
            int transparentPixels = 0;
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int alpha = Color.alpha(pixel);
                    if (alpha == 0) {
                        transparentPixels++;
                    } else if (Color.red(pixel) == 255 && Color.green(pixel) == 255
                            && Color.blue(pixel) == 255) {
                        opaqueWhitePixels++;
                    }
                }
            }
            assertTrue("Icon " + action.token + " drew no white pixels", opaqueWhitePixels > 20);
            assertTrue("Icon " + action.token + " has no transparent margin", transparentPixels > 20);

            Icon icon = GboardEditingAccessPointIcons.createIcon(context, action.token);
            assertNotNull("Null Icon for " + action.token, icon);
            assertNotNull("Icon failed to load drawable for " + action.token,
                    icon.loadDrawable(context));
        }
        assertNull(GboardEditingAccessPointIcons.createIcon(context, "unknown_token"));
        assertNull(GboardEditingAccessPointIcons.createIcon(null, EditorAction.COPY.token));
        assertNull(GboardEditingAccessPointIcons.renderBitmap(EditorAction.COPY.token, 0, 480));
    }

    @Test public void selectAllIsNotGboardsDottedResizeFrame() {
        String token = EditorAction.SELECT_ALL.token;
        // Gboard's own select_all/resize glyph starts with its first corner dot.
        assertFalse(GboardEditingAccessPointIcons.pathData(token).startsWith("M131.5,188.5"));

        // The dotted frame leaves the middle empty; the text-box glyph draws an "A" there.
        Bitmap bitmap = GboardEditingAccessPointIcons.renderBitmap(token, 96, 480);
        int centreInk = 0;
        for (int y = 36; y < 60; y++) {
            for (int x = 36; x < 60; x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 128) centreInk++;
            }
        }
        assertTrue("Select All centre is empty: " + centreInk, centreInk > 40);

        // Solid corner handles: every corner of the 960 viewport's 120..270 boxes is inked.
        for (int[] p : new int[][] {{18, 18}, {77, 18}, {18, 77}, {77, 77}}) {
            assertTrue("Missing handle at " + p[0] + "," + p[1],
                    Color.alpha(bitmap.getPixel(p[0], p[1])) > 200);
        }
    }

    @Test public void sendKeyDispatchesSoftKeyboardDownAndUpEvents() {
        List<KeyEvent> dispatched = new ArrayList<>();
        InputConnection connection = (InputConnection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {InputConnection.class},
                (proxy, method, args) -> {
                    if ("sendKeyEvent".equals(method.getName())) {
                        dispatched.add((KeyEvent) args[0]);
                        return true;
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });

        for (EditorAction action : EditorAction.values()) {
            if (action.dispatchType != EditorAction.DispatchType.KEY_EVENT) continue;
            dispatched.clear();
            assertTrue(action.name(), GboardEditorActions.sendKey(connection, action.keyCode, () -> true));
            assertEquals(2, dispatched.size());
            KeyEvent down = dispatched.get(0);
            KeyEvent up = dispatched.get(1);
            assertEquals(KeyEvent.ACTION_DOWN, down.getAction());
            assertEquals(KeyEvent.ACTION_UP, up.getAction());
            assertEquals(action.keyCode, down.getKeyCode());
            assertEquals(action.keyCode, up.getKeyCode());
            assertEquals(KeyCharacterMap.VIRTUAL_KEYBOARD, down.getDeviceId());
            assertEquals(KeyCharacterMap.VIRTUAL_KEYBOARD, up.getDeviceId());
            int expectedFlags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
            assertEquals(expectedFlags, down.getFlags());
            assertEquals(expectedFlags, up.getFlags());
        }
    }
}
