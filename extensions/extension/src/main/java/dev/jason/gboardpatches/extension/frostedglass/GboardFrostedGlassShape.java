package dev.jason.gboardpatches.extension.frostedglass;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

final class GboardFrostedGlassShape {
    final Rect bounds;
    final float topLeft;
    final float topRight;
    final float bottomLeft;
    final float bottomRight;

    private GboardFrostedGlassShape(Rect bounds, float tl, float tr, float bl, float br) {
        this.bounds = new Rect(bounds);
        topLeft = tl;
        topRight = tr;
        bottomLeft = bl;
        bottomRight = br;
    }

    static GboardFrostedGlassShape rectangle(Rect bounds) {
        return new GboardFrostedGlassShape(bounds, 0, 0, 0, 0);
    }

    static GboardFrostedGlassShape resolve(View view, Drawable original) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        ViewOutlineProvider provider = view.getOutlineProvider();
        if (view.getClipToOutline() && provider != null
                && provider != ViewOutlineProvider.BACKGROUND) {
            Outline outline = new Outline();
            provider.getOutline(view, outline);
            return fromOutline(outline);
        }
        if (original == null) {
            return new GboardFrostedGlassShape(new Rect(0, 0, view.getWidth(), view.getHeight()),
                    0, 0, 0, 0);
        }
        Drawable source = original;
        for (int depth = 0; depth < 16 && source instanceof DrawableWrapper wrapper; depth++) {
            source = wrapper.getDrawable();
        }
        if (source instanceof GradientDrawable gradient) {
            if (gradient.getShape() != GradientDrawable.RECTANGLE) return null;
            Rect outer = original.getBounds();
            Rect inner = source.getBounds();
            Rect bounds = outer.isEmpty() || inner.isEmpty()
                    ? new Rect(0, 0, view.getWidth(), view.getHeight())
                    : new Rect(inner.left - outer.left, inner.top - outer.top,
                            view.getWidth() - (outer.right - inner.right),
                            view.getHeight() - (outer.bottom - inner.bottom));
            if (bounds.isEmpty()) return null;
            float[] radii = gradient.getCornerRadii();
            if (radii != null) {
                if (radii.length != 8) return null;
                for (int i = 0; i < 8; i += 2) {
                    if (!valid(radii[i]) || radii[i] != radii[i + 1]) return null;
                }
                return new GboardFrostedGlassShape(bounds, radii[0], radii[2], radii[6], radii[4]);
            }
            float radius = gradient.getCornerRadius();
            return valid(radius) ? new GboardFrostedGlassShape(bounds,
                    radius, radius, radius, radius) : null;
        }
        Outline outline = new Outline();
        original.getOutline(outline);
        return fromOutline(outline);
    }

    private static GboardFrostedGlassShape fromOutline(Outline outline) {
        Rect rect = new Rect();
        float radius = outline.getRadius();
        if (!outline.getRect(rect) || rect.isEmpty() || !valid(radius)) return null;
        return new GboardFrostedGlassShape(rect, radius, radius, radius, radius);
    }

    private static boolean valid(float radius) {
        return Float.isFinite(radius) && radius >= 0;
    }

    boolean sameGeometry(GboardFrostedGlassShape other) {
        return other != null && bounds.equals(other.bounds) && topLeft == other.topLeft
                && topRight == other.topRight && bottomLeft == other.bottomLeft
                && bottomRight == other.bottomRight;
    }

    boolean isUniform() {
        return topLeft == topRight && topLeft == bottomLeft && topLeft == bottomRight;
    }

    GboardFrostedGlassShape withoutBottomCorners() {
        return new GboardFrostedGlassShape(bounds, topLeft, topRight, 0, 0);
    }

    float[] pathRadii() {
        return new float[]{topLeft, topLeft, topRight, topRight,
                bottomRight, bottomRight, bottomLeft, bottomLeft};
    }

    Path path(float offsetX, float offsetY) {
        RectF rect = new RectF(bounds);
        rect.offset(offsetX, offsetY);
        Path path = new Path();
        path.addRoundRect(rect, pathRadii(), Path.Direction.CW);
        return path;
    }
}
