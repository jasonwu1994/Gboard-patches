package dev.jason.gboardpatches.extension.settings.keyboardpreview;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import dev.jason.gboardpatches.extension.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardKeyboardPreviewControllerIconTest {
    @Test
    public void installedTargetWithReusedPinIdCannotReplaceLocalKeyboardIcon() throws Exception {
        LocalActivity activity = new LocalActivity();
        AtomicInteger targetLookups = new AtomicInteger();
        GboardKeyboardPreviewController controller = new GboardKeyboardPreviewController(
                activity, () -> {
                    targetLookups.incrementAndGet();
                    return List.of("com.google.android.inputmethod.latin");
                }, null, null, 0xff334455, 0xff112233);

        assertLocalKeyboardGlyph(activity, loadIcon(controller));
        Assert.assertEquals("installed target resources must not participate", 0, targetLookups.get());
        Assert.assertEquals(0, activity.targetContextRequests);
    }

    @Test
    public void missingOrBrokenTargetSupplierDoesNotPreventKeyboardIcon() throws Exception {
        LocalActivity activity = new LocalActivity();
        GboardKeyboardPreviewController controller = new GboardKeyboardPreviewController(
                activity, () -> { throw new AssertionError("target lookup is unrelated to this UI"); },
                null, null, 0xff334455, 0xff112233);

        assertLocalKeyboardGlyph(activity, loadIcon(controller));
    }

    private static void assertLocalKeyboardGlyph(LocalActivity activity, Drawable icon) {
        Assert.assertNotSame("merged APK R identifiers can resolve to unrelated target drawables",
                activity.local.pin, icon);
        Assert.assertTrue("no target or library resource ID is consulted", activity.local.drawableRequests.isEmpty());
        Assert.assertTrue(icon.getIntrinsicWidth() > 0);
        Assert.assertEquals(icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        icon.setBounds(0, 0, 960, 960);
        RecordingCanvas canvas = new RecordingCanvas();
        icon.draw(canvas);
        Assert.assertEquals(1, canvas.outlines);
        Assert.assertTrue("keyboard glyph includes multiple distinct keys", canvas.keys > 5);
    }

    private static final class RecordingCanvas extends Canvas {
        int outlines;
        int keys;
        @Override public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
            Assert.assertEquals(0xff112233, p.getColor());
            outlines++;
        }
        @Override public void drawRect(float l, float t, float r, float b, Paint p) {
            Assert.assertTrue(r > l && b > t);
            keys++;
        }
    }

    private static Drawable loadIcon(GboardKeyboardPreviewController controller) throws Exception {
        Method method = GboardKeyboardPreviewController.class.getDeclaredMethod("loadKeyboardDrawable");
        method.setAccessible(true);
        return (Drawable) method.invoke(controller);
    }

    private static final class LocalActivity extends Activity {
        final IconResources local = new IconResources(false);
        final IconResources target = new IconResources(true);
        int targetContextRequests;

        @Override public Resources getResources() { return local; }
        @Override public Resources.Theme getTheme() { return local.newTheme(); }
        @Override public Context createPackageContext(String packageName, int flags) {
            targetContextRequests++;
            return new android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
                @Override public Resources getResources() { return target; }
                @Override public Resources.Theme getTheme() { return target.newTheme(); }
            };
        }
    }

    private static final class IconResources extends Resources {
        final Drawable pin = new ColorDrawable(0xff445566);
        final List<Integer> drawableRequests = new ArrayList<>();
        final boolean isTarget;

        IconResources(boolean isTarget) {
            super(RuntimeEnvironment.getApplication().getAssets(),
                    RuntimeEnvironment.getApplication().getResources().getDisplayMetrics(),
                    RuntimeEnvironment.getApplication().getResources().getConfiguration());
            this.isTarget = isTarget;
        }

        @Override public int getColor(int id, Theme theme) { return 0xff000000; }
        @Override public Drawable getDrawable(int id, Theme theme) {
            drawableRequests.add(id);
            if (isTarget && id == 0x7f0804a6) return pin;
            if (!isTarget && id == R.drawable.ic_gboard_patches_keyboard) return pin;
            throw new NotFoundException("unexpected drawable " + Integer.toHexString(id));
        }
    }
}
