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
        // Transparent inside the artwork's bounds (not a full border), so nothing is cropped.
        for (int y = 0; y < 150; y++) for (int x = 0; x < 150; x++) source.setPixel(x, y, Color.TRANSPARENT);
        String encoded = GboardSpacebarLogoImages.importImage(bytes(source, Bitmap.CompressFormat.PNG));
        byte[] png = Base64.getDecoder().decode(encoded);
        Bitmap result = BitmapFactory.decodeByteArray(png, 0, png.length);
        // Height-bound: 300 px tall fits the 256 px box height.
        assertEquals(512, result.getWidth());
        assertEquals(256, result.getHeight());
        assertEquals(0, Color.alpha(result.getPixel(1, 1)));
        assertEquals(Color.RED, result.getPixel(400, 128));
        assertTrue(png.length <= GboardSpacebarLogoContract.MAX_IMAGE_BYTES);
    }

    @Test public void wideImagesAreBoundByTheBoxWidth() throws Exception {
        Bitmap source = Bitmap.createBitmap(4000, 500, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLUE);
        byte[] stored = Base64.getDecoder().decode(
                GboardSpacebarLogoImages.importImage(bytes(source, Bitmap.CompressFormat.PNG)));
        BitmapFactory.Options bounds = decodeBounds(stored);
        assertEquals(1024, bounds.outWidth);
        assertEquals(128, bounds.outHeight);
        assertEquals(1f, GboardSpacebarLogoImages.fitScale(1024, 256), 0f);
        assertEquals(1f, GboardSpacebarLogoImages.fitScale(10, 10), 0f);
    }

    @Test public void photosTooLargeForPngFallBackToBoundedWebpThatStillRenders() throws Exception {
        Bitmap source = Bitmap.createBitmap(2048, 512, Bitmap.Config.ARGB_8888);
        java.util.Random random = new java.util.Random(7);
        for (int y = 0; y < 512; y++) {
            for (int x = 0; x < 2048; x++) source.setPixel(x, y, 0xFF000000 | random.nextInt(0xFFFFFF));
        }
        String encoded = GboardSpacebarLogoImages.importImage(bytes(source, Bitmap.CompressFormat.PNG));
        byte[] stored = Base64.getDecoder().decode(encoded);
        BitmapFactory.Options bounds = decodeBounds(stored);
        assertEquals("image/webp", bounds.outMimeType);
        assertEquals(1024, bounds.outWidth);
        assertEquals(256, bounds.outHeight);
        assertTrue(stored.length <= GboardSpacebarLogoContract.MAX_IMAGE_BYTES);
        assertTrue(encoded.length() <= GboardSpacebarLogoContract.MAX_BASE64_LENGTH);
        BitmapDrawable drawable = (BitmapDrawable) GboardSpacebarLogoImages.customDrawable(
                context.getResources(), encoded);
        assertNotNull(drawable);
        assertEquals(1024, drawable.getBitmap().getWidth());
    }

    @Test public void transparentMarginsAreCroppedOnImportAndForOlderStoredImages() throws Exception {
        // A square canvas whose artwork only fills rows 52..203, like a padded logo PNG.
        Bitmap padded = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        for (int y = 52; y <= 203; y++) for (int x = 0; x < 256; x++) padded.setPixel(x, y, Color.YELLOW);
        byte[] stored = Base64.getDecoder().decode(
                GboardSpacebarLogoImages.importImage(bytes(padded, Bitmap.CompressFormat.PNG)));
        BitmapFactory.Options bounds = decodeBounds(stored);
        assertEquals(256, bounds.outWidth);
        assertEquals(152, bounds.outHeight);

        // A value saved by an earlier build, still padded, is cropped when it is decoded.
        String earlier = Base64.getEncoder().encodeToString(bytes(padded, Bitmap.CompressFormat.PNG));
        Bitmap shown = ((BitmapDrawable) GboardSpacebarLogoImages.customDrawable(
                context.getResources(), earlier)).getBitmap();
        assertEquals(256, shown.getWidth());
        assertEquals(152, shown.getHeight());
        assertEquals(Color.YELLOW, shown.getPixel(128, 76));

        Bitmap empty = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        assertSame(empty, GboardSpacebarLogoImages.trimTransparentBorders(empty));
        Bitmap opaque = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        opaque.eraseColor(Color.BLACK);
        assertSame(opaque, GboardSpacebarLogoImages.trimTransparentBorders(opaque));
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

    @Test public void sizePercentScalesTheKeyInnerBoxAndReleasesItsLayoutListenerWhenDisabled()
            throws Exception {
        int spaceId = View.generateViewId(), labelId = View.generateViewId();
        var bindings = new GboardSpacebarLogoRuntime.ViewBindings(spaceId, labelId, 0, 0, 0);
        FrameLayout spacebar = newSpacebar();
        spacebar.setId(spaceId);
        TextView label = new TextView(context);
        label.setId(labelId);
        spacebar.addView(label);
        String png = image(Color.RED);
        var half = new GboardSpacebarLogoRuntime.SettingsSnapshot(true, false, 0,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, png, false, 50);
        GboardSpacebarLogoRuntime.afterSoftKeyBound(spacebar, true, half, bindings);
        ImageView overlay = (ImageView) spacebar.getChildAt(1);
        assertEquals(ImageView.ScaleType.FIT_CENTER, overlay.getScaleType());
        spacebar.setLayoutParams(new FrameLayout.LayoutParams(300, 40));
        spacebar.layout(0, 0, 300, 40);
        assertEquals(150, overlay.getLayoutParams().width);
        assertEquals(20, overlay.getLayoutParams().height);
        spacebar.setPadding(10, 4, 10, 4);
        spacebar.setLayoutParams(new FrameLayout.LayoutParams(320, 48));
        spacebar.layout(0, 0, 320, 48);
        assertEquals(150, overlay.getLayoutParams().width);
        assertEquals(20, overlay.getLayoutParams().height);
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(
                new GboardSpacebarLogoRuntime.SettingsSnapshot(true, true, Color.RED,
                        GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR, "", false, 100));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        overlay = (ImageView) spacebar.getChildAt(1);
        assertEquals(320, spacebar.getWidth());
        assertEquals(48, spacebar.getHeight());
        assertEquals(300, overlay.getLayoutParams().width);
        assertEquals(40, overlay.getLayoutParams().height);
        int priorWidth = overlay.getLayoutParams().width;
        GboardSpacebarLogoRuntime.refreshBoundSpacebars(snapshot(false,
                GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE, png, false));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, spacebar.getChildCount());
        assertEquals(View.VISIBLE, label.getVisibility());
        spacebar.layout(0, 0, 300, 8);
        assertEquals(priorWidth, overlay.getLayoutParams().width);
        assertEquals(60, snapshot(true, GboardSpacebarLogoContract.ICON_GOOGLE_G, "", false).sizePercent);
        assertEquals(100, new GboardSpacebarLogoRuntime.SettingsSnapshot(true, true, 0,
                GboardSpacebarLogoContract.ICON_GOOGLE_G, "", false, Integer.MAX_VALUE).sizePercent);
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

    private static BitmapFactory.Options decodeBounds(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        return bounds;
    }

    private static byte[] bytes(Bitmap bitmap, Bitmap.CompressFormat format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(bitmap.compress(format, 100, output));
        return output.toByteArray();
    }
}
