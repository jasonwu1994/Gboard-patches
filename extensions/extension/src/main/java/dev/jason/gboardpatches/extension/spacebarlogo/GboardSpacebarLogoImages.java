package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Small portable images, independent of target drawable IDs and file permissions. */
public final class GboardSpacebarLogoImages {
    public static final int MAX_IMPORT_BYTES = 8 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static Bitmap googleBitmap;
    private static boolean googleLoaded;
    private static String cachedImageValue;
    private static Bitmap cachedImage;

    private GboardSpacebarLogoImages() { }

    /** Called once on import, off the UI thread. Never enlarges or crops the image. */
    public static String importImage(byte[] source) throws IOException {
        if (source == null || source.length == 0 || source.length > MAX_IMPORT_BYTES) {
            throw new IOException("Image is empty or too large");
        }
        BitmapFactory.Options bounds = bounds(source);
        if (!("image/png".equals(bounds.outMimeType)
                || "image/jpeg".equals(bounds.outMimeType)
                || "image/webp".equals(bounds.outMimeType))
                || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS
                || Math.max(bounds.outWidth, bounds.outHeight) > 16384) {
            throw new IOException("Unsupported image or dimensions");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        int edge = GboardSpacebarLogoContract.MAX_IMAGE_EDGE;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > edge * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (decoded == null) throw new IOException("Image could not be decoded");
        Bitmap scaled = decoded;
        try {
            float scale = Math.min(1f, (float) edge / Math.max(decoded.getWidth(), decoded.getHeight()));
            if (scale < 1f) {
                scaled = Bitmap.createScaledBitmap(decoded,
                        Math.max(1, Math.round(decoded.getWidth() * scale)),
                        Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
                    || output.size() > GboardSpacebarLogoContract.MAX_PNG_BYTES) {
                throw new IOException("Image could not be saved");
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } finally {
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }

    public static synchronized Drawable googleDrawable(Resources resources) {
        if (!googleLoaded) {
            googleLoaded = true;
            try {
                googleBitmap = decodePng(Base64.getDecoder().decode(GboardSpacebarLogoImageData.base64()));
            } catch (RuntimeException ignored) {
                // Missing or invalid image data leaves the stock language label visible.
            }
        }
        return drawable(resources, googleBitmap);
    }

    public static synchronized Drawable customDrawable(Resources resources, String imageValue) {
        String value = GboardSpacebarLogoContract.boundedImageValue(imageValue);
        if (!value.equals(cachedImageValue)) {
            cachedImageValue = value;
            cachedImage = null;
            try {
                if (!value.isEmpty()) cachedImage = decodePng(Base64.getDecoder().decode(value));
            } catch (IllegalArgumentException ignored) {
                // Invalid imported settings restore stock appearance; do not retry on each bind.
            }
        }
        return drawable(resources, cachedImage);
    }

    private static BitmapFactory.Options bounds(byte[] bytes) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return options;
    }

    private static Bitmap decodePng(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > GboardSpacebarLogoContract.MAX_PNG_BYTES) return null;
        BitmapFactory.Options bounds = bounds(bytes);
        if (!"image/png".equals(bounds.outMimeType) || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > GboardSpacebarLogoContract.MAX_IMAGE_EDGE
                || bounds.outHeight > GboardSpacebarLogoContract.MAX_IMAGE_EDGE) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static Drawable drawable(Resources resources, Bitmap bitmap) {
        // Share immutable pixels, never a tinted Drawable instance between views.
        return bitmap == null ? null : new BitmapDrawable(resources, bitmap);
    }
}
