package dev.jason.gboardpatches.extension.frostedglass;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import java.util.function.Consumer;

/**
 * A layer stack whose blur layer only reaches a Canvas able to record it.
 *
 * <p>BackgroundBlurDrawable calls {@code Canvas.drawRenderNode}, which throws on anything but a
 * RecordingCanvas. The IME window is redrawn into other canvases: Gboard's CopyImageView locks a
 * software SurfaceView Canvas and draws the whole content view, and platform snapshots do the
 * same. {@link #draw} runs outside every lifecycle try/catch, so a failure here kills the IME.
 */
class GboardFrostedGlassLayerDrawable extends LayerDrawable {
    private final Drawable blur;
    private Consumer<Throwable> blurFailureListener;
    private boolean blurFailed;

    GboardFrostedGlassLayerDrawable(Drawable[] layers, Drawable blur) {
        super(layers);
        this.blur = blur;
    }

    /** Runs at most once, from inside {@link #draw}; it must only schedule work. */
    final void setBlurFailureListener(Consumer<Throwable> listener) {
        blurFailureListener = listener;
    }

    @Override public void draw(Canvas canvas) {
        boolean drawBlur = !blurFailed && canvas.isHardwareAccelerated();
        for (int i = 0, count = getNumberOfLayers(); i < count; i++) {
            Drawable layer = getDrawable(i);
            if (layer == null) continue;
            if (layer != blur) layer.draw(canvas);
            else if (drawBlur) drawBlurLayer(canvas);
        }
    }

    private void drawBlurLayer(Canvas canvas) {
        int saved = canvas.save();
        try {
            blur.draw(canvas);
        } catch (Throwable failure) {
            blurFailed = true;
            Consumer<Throwable> listener = blurFailureListener;
            blurFailureListener = null;
            if (listener != null) {
                try {
                    listener.accept(failure);
                } catch (Throwable ignored) {
                    // Announcing a failed blur must not fail the frame that hit it.
                }
            }
        } finally {
            canvas.restoreToCount(saved);
        }
    }
}
