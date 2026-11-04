package dev.jason.gboardpatches.extension.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Folds one view in and out of a list, as Compose's expandVertically and shrinkVertically do:
 * the content keeps its full size and stays at the bottom edge while the frame's height opens or
 * closes over it, so it slides in and out under a clip instead of being squeezed. A gap above the
 * content folds along with it.
 */
final class GboardFoldFrame extends ViewGroup {
    static final long DURATION_MS = 220L;
    /** Material LinearOutSlowIn, Morphe Manager's default tween easing. */
    static final TimeInterpolator EASING = new PathInterpolator(0f, 0f, 0.2f, 1f);

    private float fraction = 1f;
    private int gap;
    private boolean open = true;
    private ValueAnimator foldAnimator;
    private ValueAnimator gapAnimator;

    GboardFoldFrame(Context context) {
        super(context);
    }

    View getContent() {
        return getChildCount() == 0 ? null : getChildAt(0);
    }

    void setContent(View content) {
        removeAllViews();
        if (content.getParent() instanceof ViewGroup parent) {
            parent.removeView(content);
        }
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    boolean isOpen() {
        return open;
    }

    /** Opens or closes the frame; {@code onClosed} runs once a close finishes. */
    void fold(boolean open, boolean animate, Runnable onClosed) {
        this.open = open;
        if (foldAnimator != null) {
            foldAnimator.cancel();
            foldAnimator = null;
        }
        float target = open ? 1f : 0f;
        if (open) {
            setVisibility(VISIBLE);
        }
        if (!animate || fraction == target && getAlpha() == target) {
            applyFold(target);
            finishFold(open, onClosed);
            return;
        }
        float fromFraction = fraction;
        float fromAlpha = getAlpha();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(EASING);
        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            fraction = fromFraction + (target - fromFraction) * progress;
            setAlpha(fromAlpha + (target - fromAlpha) * progress);
            requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    foldAnimator = null;
                    finishFold(open, onClosed);
                }
            }
        });
        foldAnimator = animator;
        animator.start();
    }

    void setGap(int gap, boolean animate) {
        if (gapAnimator != null) {
            gapAnimator.cancel();
            gapAnimator = null;
        }
        if (!animate || this.gap == gap) {
            this.gap = gap;
            requestLayout();
            return;
        }
        gapAnimator = ValueAnimator.ofInt(this.gap, gap);
        gapAnimator.setDuration(DURATION_MS);
        gapAnimator.setInterpolator(EASING);
        gapAnimator.addUpdateListener(animation -> {
            this.gap = (int) animation.getAnimatedValue();
            requestLayout();
        });
        gapAnimator.start();
    }

    void cancelAnimations() {
        if (foldAnimator != null) {
            foldAnimator.cancel();
            foldAnimator = null;
        }
        if (gapAnimator != null) {
            gapAnimator.cancel();
            gapAnimator = null;
        }
    }

    private void applyFold(float value) {
        fraction = value;
        setAlpha(value);
        requestLayout();
    }

    private void finishFold(boolean open, Runnable onClosed) {
        if (open) {
            return;
        }
        setVisibility(GONE);
        if (onClosed != null) {
            onClosed.run();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int full = gap;
        View content = getContent();
        if (content != null && content.getVisibility() != GONE) {
            content.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            full += content.getMeasuredHeight();
        }
        setMeasuredDimension(width, Math.round(full * fraction));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View content = getContent();
        if (content == null) {
            return;
        }
        int height = bottom - top;
        content.layout(0, height - content.getMeasuredHeight(), right - left, height);
    }
}
