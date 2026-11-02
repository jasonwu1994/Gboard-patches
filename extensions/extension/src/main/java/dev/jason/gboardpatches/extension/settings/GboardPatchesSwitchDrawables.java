package dev.jason.gboardpatches.extension.settings;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

final class GboardPatchesSwitchDrawables {
    static final int TRACK_WIDTH_DP = 52;
    static final int TRACK_HEIGHT_DP = 32;
    static final int THUMB_BOX_DP = 32;
    static final int HORIZONTAL_PADDING_DP = 6;
    private static final long ANIMATION_DURATION_MS = 160L;

    private GboardPatchesSwitchDrawables() {
    }

    static final class Colors {
        final int checkedTrack;
        final int uncheckedTrack;
        final int uncheckedOutline;
        final int checkedThumb;
        final int uncheckedThumb;
        final int checkMark;

        Colors(int checkedTrack, int uncheckedTrack, int uncheckedOutline,
                int checkedThumb, int uncheckedThumb, int checkMark) {
            this.checkedTrack = checkedTrack;
            this.uncheckedTrack = uncheckedTrack;
            this.uncheckedOutline = uncheckedOutline;
            this.checkedThumb = checkedThumb;
            this.uncheckedThumb = uncheckedThumb;
            this.checkMark = checkMark;
        }
    }

    abstract static class AnimatedStateDrawable extends Drawable {
        final float density;
        final Colors colors;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float checkedProgress;
        float pressedProgress;
        private boolean checked;
        private boolean pressed;
        private ValueAnimator checkedAnimator;
        private ValueAnimator pressedAnimator;

        AnimatedStateDrawable(float density, Colors colors) {
            this.density = density;
            this.colors = colors;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean nextChecked = false;
            boolean nextPressed = false;
            boolean enabled = false;
            if (state != null) {
                for (int value : state) {
                    if (value == android.R.attr.state_checked) {
                        nextChecked = true;
                    } else if (value == android.R.attr.state_pressed) {
                        nextPressed = true;
                    } else if (value == android.R.attr.state_enabled) {
                        enabled = true;
                    }
                }
            }
            nextPressed = nextPressed && enabled;
            boolean changed = false;
            if (nextChecked != checked) {
                checked = nextChecked;
                checkedAnimator = animate(checkedAnimator, checkedProgress, checked ? 1f : 0f,
                        true);
                changed = true;
            }
            if (nextPressed != pressed) {
                pressed = nextPressed;
                pressedAnimator = animate(pressedAnimator, pressedProgress, pressed ? 1f : 0f,
                        false);
                changed = true;
            }
            return changed;
        }

        private ValueAnimator animate(ValueAnimator running, float from, float to,
                boolean checkedChannel) {
            if (running != null) {
                running.cancel();
            }
            if (!canAnimate()) {
                setProgress(checkedChannel, to);
                return null;
            }
            ValueAnimator animator = ValueAnimator.ofFloat(from, to);
            animator.setDuration(ANIMATION_DURATION_MS);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation ->
                    setProgress(checkedChannel, (float) animation.getAnimatedValue()));
            animator.start();
            return animator;
        }

        private void setProgress(boolean checkedChannel, float value) {
            if (checkedChannel) {
                checkedProgress = value;
            } else {
                pressedProgress = value;
            }
            invalidateSelf();
        }

        private boolean canAnimate() {
            Callback callback = getCallback();
            return callback instanceof View && ((View) callback).isAttachedToWindow();
        }

        int px(float valueDp) {
            return Math.round(valueDp * density);
        }

        @Override
        public boolean getPadding(Rect padding) {
            int horizontal = px(HORIZONTAL_PADDING_DP);
            padding.set(horizontal, 0, horizontal, 0);
            return true;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        @SuppressWarnings("deprecation")
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    static final class TrackDrawable extends AnimatedStateDrawable {
        private final RectF rect = new RectF();

        TrackDrawable(float density, Colors colors) {
            super(density, colors);
        }

        @Override
        public int getIntrinsicWidth() {
            return px(TRACK_WIDTH_DP);
        }

        @Override
        public int getIntrinsicHeight() {
            return px(TRACK_HEIGHT_DP);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            float height = Math.min(bounds.height(), px(TRACK_HEIGHT_DP));
            float top = bounds.centerY() - (height / 2f);
            rect.set(bounds.left, top, bounds.right, top + height);
            float radius = height / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(colors.uncheckedTrack, colors.checkedTrack, checkedProgress));
            canvas.drawRoundRect(rect, radius, radius, paint);
            float stroke = 2f * density;
            float inset = stroke / 2f;
            rect.inset(inset, inset);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(blend(colors.uncheckedOutline, colors.checkedTrack, checkedProgress));
            canvas.drawRoundRect(rect, radius - inset, radius - inset, paint);
        }
    }

    static final class ThumbDrawable extends AnimatedStateDrawable {
        private final Path checkPath = new Path();

        ThumbDrawable(float density, Colors colors) {
            super(density, colors);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public int getIntrinsicWidth() {
            return px(THUMB_BOX_DP);
        }

        @Override
        public int getIntrinsicHeight() {
            return px(THUMB_BOX_DP);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) {
                return;
            }
            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();
            float radiusDp = 8f + (4f * checkedProgress);
            radiusDp += (14f - radiusDp) * pressedProgress;
            float radius = radiusDp * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(colors.uncheckedThumb, colors.checkedThumb, checkedProgress));
            canvas.drawCircle(centerX, centerY, radius, paint);
            if (checkedProgress <= 0.01f) {
                return;
            }
            float iconSize = 16f * density;
            float scale = iconSize / 24f;
            float left = centerX - (iconSize / 2f);
            float top = centerY - (iconSize / 2f);
            checkPath.reset();
            checkPath.moveTo(left + (5f * scale), top + (12.5f * scale));
            checkPath.lineTo(left + (9.5f * scale), top + (17f * scale));
            checkPath.lineTo(left + (19f * scale), top + (7.5f * scale));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f * scale);
            int checkColor = colors.checkMark;
            paint.setColor(Color.argb(
                    Math.round(Color.alpha(checkColor) * checkedProgress),
                    Color.red(checkColor),
                    Color.green(checkColor),
                    Color.blue(checkColor)));
            canvas.drawPath(checkPath, paint);
        }
    }

    static int blend(int from, int to, float fraction) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        float inverse = 1f - clamped;
        return Color.argb(
                Math.round((Color.alpha(from) * inverse) + (Color.alpha(to) * clamped)),
                Math.round((Color.red(from) * inverse) + (Color.red(to) * clamped)),
                Math.round((Color.green(from) * inverse) + (Color.green(to) * clamped)),
                Math.round((Color.blue(from) * inverse) + (Color.blue(to) * clamped)));
    }
}
