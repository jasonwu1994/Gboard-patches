package dev.jason.gboardpatches.extension.spacebarlogo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

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

    /** Called once on import, off the UI thread. Never enlarges; crops only transparent margins. */
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
        float targetScale = fitScale(bounds.outWidth, bounds.outHeight);
        // Subsample only while the decode stays at least twice the target, for a clean final filter.
        while (bounds.outWidth / (options.inSampleSize * 2f) >= bounds.outWidth * targetScale * 2f
                && bounds.outHeight / (options.inSampleSize * 2f) >= bounds.outHeight * targetScale * 2f) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (decoded == null) throw new IOException("Image could not be decoded");
        Bitmap trimmed = trimTransparentBorders(decoded);
        Bitmap scaled = trimmed;
        try {
            float scale = fitScale(trimmed.getWidth(), trimmed.getHeight());
            if (scale < 1f) {
                scaled = Bitmap.createScaledBitmap(trimmed,
                        Math.max(1, Math.min(GboardSpacebarLogoContract.MAX_IMAGE_WIDTH,
                                Math.round(trimmed.getWidth() * scale))),
                        Math.max(1, Math.min(GboardSpacebarLogoContract.MAX_IMAGE_HEIGHT,
                                Math.round(trimmed.getHeight() * scale))), true);
            }
            return Base64.getEncoder().encodeToString(encodeBounded(scaled));
        } finally {
            if (scaled != trimmed) scaled.recycle();
            if (trimmed != decoded) trimmed.recycle();
            decoded.recycle();
        }
    }

    /** Fits inside MAX_IMAGE_WIDTH x MAX_IMAGE_HEIGHT; never enlarges. */
    static float fitScale(int width, int height) {
        return Math.min(1f, Math.min(
                (float) GboardSpacebarLogoContract.MAX_IMAGE_WIDTH / width,
                (float) GboardSpacebarLogoContract.MAX_IMAGE_HEIGHT / height));
    }

    /**
     * Crops fully transparent rows and columns around the artwork, so the size percent applies
     * to what is visible: at 100% the drawn shape, not its empty canvas, touches the key edges.
     * Returns the same bitmap when there is nothing to crop or the image is fully transparent.
     */
    static Bitmap trimTransparentBorders(Bitmap bitmap) {
        if (bitmap == null || !bitmap.hasAlpha()) return bitmap;
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] row = new int[width];
        int top = -1, bottom = -1, left = width, right = -1;
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            int first = -1, last = -1;
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) != 0) {
                    if (first < 0) first = x;
                    last = x;
                }
            }
            if (first < 0) continue;
            if (top < 0) top = y;
            bottom = y;
            left = Math.min(left, first);
            right = Math.max(right, last);
        }
        if (top < 0 || (top == 0 && left == 0 && bottom == height - 1 && right == width - 1)) {
            return bitmap;
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }

    /** Lossless PNG first; photos that are too large fall back to WebP, which keeps alpha. */
    private static byte[] encodeBounded(Bitmap bitmap) throws IOException {
        byte[] png = compress(bitmap, Bitmap.CompressFormat.PNG, 100);
        if (png != null && png.length <= GboardSpacebarLogoContract.MAX_IMAGE_BYTES) return png;
        Bitmap.CompressFormat webp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        for (int quality = 90; quality >= 50; quality -= 10) {
            byte[] encoded = compress(bitmap, webp, quality);
            if (encoded != null && encoded.length <= GboardSpacebarLogoContract.MAX_IMAGE_BYTES) {
                return encoded;
            }
        }
        throw new IOException("Image could not be saved");
    }

    private static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return bitmap.compress(format, quality, output) ? output.toByteArray() : null;
    }

    public static synchronized Drawable googleDrawable(Resources resources) {
        if (!googleLoaded) {
            googleLoaded = true;
            try {
                googleBitmap = decodeStored(Base64.getDecoder().decode(GboardSpacebarLogoImageData.base64()));
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
                if (!value.isEmpty()) {
                    // Images imported before trimming may still carry transparent margins.
                    Bitmap stored = decodeStored(Base64.getDecoder().decode(value));
                    cachedImage = trimTransparentBorders(stored);
                    if (stored != null && cachedImage != stored) stored.recycle();
                }
            } catch (RuntimeException ignored) {
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

    /** Accepts only what importImage stores: a bounded PNG or WebP within the size box. */
    private static Bitmap decodeStored(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > GboardSpacebarLogoContract.MAX_IMAGE_BYTES) return null;
        BitmapFactory.Options bounds = bounds(bytes);
        if (!("image/png".equals(bounds.outMimeType) || "image/webp".equals(bounds.outMimeType))
                || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > GboardSpacebarLogoContract.MAX_IMAGE_WIDTH
                || bounds.outHeight > GboardSpacebarLogoContract.MAX_IMAGE_HEIGHT) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static Drawable drawable(Resources resources, Bitmap bitmap) {
        // Share immutable pixels, never a tinted Drawable instance between views.
        return bitmap == null ? null : new BitmapDrawable(resources, bitmap);
    }
}
