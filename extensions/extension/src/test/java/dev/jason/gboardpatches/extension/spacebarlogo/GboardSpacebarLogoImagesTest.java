package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.Context;
import android.app.Activity;
import android.os.Looper;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GboardSpacebarLogoImagesTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void importFitsAspectRatioAndPreservesTransparencyAndColor() throws Exception {
        Bitmap source = Bitmap.createBitmap(600, 300, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.RED);
        for (int y = 0; y < 300; y++) for (int x = 0; x < 150; x++) source.setPixel(x, y, Color.TRANSPARENT);
        String encoded = GboardSpacebarLogoImages.importImage(bytes(source, Bitmap.CompressFormat.PNG));
        byte[] png = Base64.getDecoder().decode(encoded);
        Bitmap result = BitmapFactory.decodeByteArray(png, 0, png.length);
        assertEquals(96, result.getWidth());
        assertEquals(48, result.getHeight());
        assertEquals(0, Color.alpha(result.getPixel(1, 1)));
        assertEquals(Color.RED, result.getPixel(80, 24));
        assertTrue(png.length <= GboardSpacebarLogoContract.MAX_PNG_BYTES);
    }

    @Test public void jpegAndWebpBecomeStaticPngWithoutEnlarging() throws Exception {
        Bitmap source = Bitmap.createBitmap(24, 12, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLUE);
        for (Bitmap.CompressFormat format : new Bitmap.CompressFormat[] {
                Bitmap.CompressFormat.JPEG, Bitmap.CompressFormat.WEBP}) {
            String encoded = GboardSpacebarLogoImages.importImage(bytes(source, format));
            byte[] data = Base64.getDecoder().decode(encoded);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            assertEquals("image/png", bounds.outMimeType);
            assertEquals(24, bounds.outWidth);
            assertEquals(12, bounds.outHeight);
        }
    }

    @Test public void invalidAndOversizedImportsAreRejectedBeforeSaving() throws Exception {
        assertThrows(IOException.class, () -> GboardSpacebarLogoImages.importImage(new byte[0]));
        assertThrows(IOException.class, () -> GboardSpacebarLogoImages.importImage("<svg/>".getBytes()));
        assertThrows(IOException.class, () -> GboardSpacebarLogoImages.importImage(
                new byte[GboardSpacebarLogoImages.MAX_IMPORT_BYTES + 1]));
        Bitmap tooWide = Bitmap.createBitmap(17000, 1, Bitmap.Config.ARGB_8888);
        assertThrows(IOException.class, () -> GboardSpacebarLogoImages.importImage(
                bytes(tooWide, Bitmap.CompressFormat.PNG)));
    }

    @Test public void cachedPixelsHaveIndependentTintAndReplacementInvalidatesCache() throws Exception {
        String red = image(Color.RED);
        BitmapDrawable first = (BitmapDrawable) GboardSpacebarLogoImages.customDrawable(context.getResources(), red);
        BitmapDrawable second = (BitmapDrawable) GboardSpacebarLogoImages.customDrawable(context.getResources(), red);
        assertNotSame(first, second);
        assertSame(first.getBitmap(), second.getBitmap());
        first.setTint(Color.GREEN);
        BitmapDrawable original = (BitmapDrawable) GboardSpacebarLogoImages.customDrawable(context.getResources(), red);
        assertEquals(Color.RED, original.getBitmap().getPixel(1, 1));
        BitmapDrawable blue = (BitmapDrawable) GboardSpacebarLogoImages.customDrawable(context.getResources(), image(Color.BLUE));
        assertNotSame(first.getBitmap(), blue.getBitmap());
        assertEquals(Color.BLUE, blue.getBitmap().getPixel(1, 1));
        assertNull(GboardSpacebarLogoImages.customDrawable(context.getResources(), "broken-base64"));
        assertNull(GboardSpacebarLogoImages.customDrawable(context.getResources(), "A".repeat(
                GboardSpacebarLogoContract.MAX_BASE64_LENGTH + 1)));
    }

    @Test public void originalTintReplacementAndDisableRestoreTheStockLabel() throws Exception {
        int spaceId = View.generateViewId(), labelId = View.generateViewId();
        var bindings = new GboardSpacebarLogoRuntime.ViewBindings(spaceId, labelId, 0, 0, 0);
        FrameLayout spacebar = newSpacebar();
        spacebar.setId(spaceId);
        TextView label = new TextView(context);
        label.setId(labelId);
        label.setText("English");
        spacebar.addView(label);
        var original = snapshot(true, GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, image(Color.RED), false);
        GboardSpacebarLogoRuntime.beforeSoftKeyBound(spacebar, bindings);
        GboardSpacebarLogoRuntime.afterSoftKeyBound(spacebar, true, original, bindings);
        assertEquals(View.INVISIBLE, label.getVisibility());
        assertEquals(2, spacebar.getChildCount());
        ImageView overlay = (ImageView) spacebar.getChildAt(1);
        assertFalse(overlay.isClickable());
        assertFalse(original.shouldTint());
        var tinted = snapshot(true, GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, image(Color.RED), true);
        assertTrue(tinted.shouldTint());
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(tinted);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(2, spacebar.getChildCount());
        Drawable tintedDrawable = ((ImageView) spacebar.getChildAt(1)).getDrawable();
        Bitmap rendered = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        tintedDrawable.setBounds(0, 0, 12, 12);
        tintedDrawable.draw(new Canvas(rendered));
        assertEquals(Color.GREEN, rendered.getPixel(6, 6));
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(original);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Drawable originalDrawable = ((ImageView) spacebar.getChildAt(1)).getDrawable();
        originalDrawable.setBounds(0, 0, 12, 12);
        originalDrawable.draw(new Canvas(rendered));
        assertEquals(Color.RED, rendered.getPixel(6, 6));
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(snapshot(true,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, "corrupt", false));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, label.getVisibility());
        assertEquals(1, spacebar.getChildCount());
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(original);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(snapshot(false,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, original.customImage, false));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, label.getVisibility());
        assertEquals(1, spacebar.getChildCount());
    }

    @Test public void officialColorImageIsPackagedAt96pxAndNeverTinted() {
        BitmapDrawable google = (BitmapDrawable) GboardSpacebarLogoImages.googleDrawable(context.getResources());
        assertNotNull(google);
        assertEquals(96, google.getBitmap().getWidth());
        assertEquals(96, google.getBitmap().getHeight());
        assertFalse(snapshot(true, GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR, "", true).shouldTint());
        assertTrue(snapshot(true, GboardSpacebarLogoContract.ICON_HEART, "", false).shouldTint());
    }

    @Test public void sizeRefreshFitsTheKeyAndReleasesItsLayoutListenerWhenDisabled() throws Exception {
        int spaceId = View.generateViewId(), labelId = View.generateViewId();
        var bindings = new GboardSpacebarLogoRuntime.ViewBindings(spaceId, labelId, 0, 0, 0);
        FrameLayout spacebar = newSpacebar();
        spacebar.setId(spaceId);
        TextView label = new TextView(context);
        label.setId(labelId);
        spacebar.addView(label);
        String png = image(Color.RED);
        float density = context.getResources().getDisplayMetrics().density;
        var size32 = new GboardSpacebarLogoRuntime.SettingsSnapshot(true, false, 0,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, png, false, 32);
        GboardSpacebarLogoRuntime.afterSoftKeyBound(spacebar, true, size32, bindings);
        ImageView overlay = (ImageView) spacebar.getChildAt(1);
        assertEquals(Math.round(32 * density), overlay.getLayoutParams().width);
        spacebar.layout(0, 0, 300, 18);
        assertEquals(18, overlay.getLayoutParams().width);
        assertEquals(18, overlay.getLayoutParams().height);
        spacebar.setLayoutParams(new FrameLayout.LayoutParams(300, 200));
        spacebar.layout(0, 0, 300, 200);
        assertEquals(Math.round(32 * density), overlay.getLayoutParams().width);
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(
                new GboardSpacebarLogoRuntime.SettingsSnapshot(true, true, Color.RED,
                        GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR, "", false, 28));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        overlay = (ImageView) spacebar.getChildAt(1);
        assertEquals(Math.round(28 * density), overlay.getLayoutParams().width);
        assertEquals(300, spacebar.getWidth());
        assertEquals(200, spacebar.getHeight());
        int priorSize = overlay.getLayoutParams().width;
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(snapshot(false,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, png, false));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, spacebar.getChildCount());
        assertEquals(View.VISIBLE, label.getVisibility());
        spacebar.layout(0, 0, 300, 8);
        assertEquals(priorSize, overlay.getLayoutParams().width);
        assertEquals(24, snapshot(true, GboardSpacebarLogoContract.ICON_GOOGLE_G, "", false).sizeDp);
        assertEquals(24, new GboardSpacebarLogoRuntime.SettingsSnapshot(true, true, 0,
                GboardSpacebarLogoContract.ICON_GOOGLE_G, "", false, Integer.MAX_VALUE).sizeDp);
    }

    private FrameLayout newSpacebar() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(context);
        activity.setContentView(root);
        return root;
    }

    private static GboardSpacebarLogoRuntime.SettingsSnapshot snapshot(boolean enabled,
            String icon, String image, boolean tint) {
        return new GboardSpacebarLogoRuntime.SettingsSnapshot(enabled, true, Color.GREEN, icon, image, tint);
    }

    private static String image(int color) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        return GboardSpacebarLogoImages.importImage(bytes(bitmap, Bitmap.CompressFormat.PNG));
    }

    private static byte[] bytes(Bitmap bitmap, Bitmap.CompressFormat format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(format, 100, output));
        return output.toByteArray();
    }
}
