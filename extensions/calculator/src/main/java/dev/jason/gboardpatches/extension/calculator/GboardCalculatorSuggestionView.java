package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Owns calculator suggestion placement and rendering. */
final class GboardCalculatorSuggestionView {
    static final String SUGGESTION_TAG = "gboard-patches-calculator-suggestion";
    private static final int SUGGESTION_HEIGHT_DP = 44;

    private GboardCalculatorSuggestionView() {
    }

    interface Actions {
        void accept(boolean insertEquation);

        void copy();
    }

    static Placement resolvePlacement(View inputView) {
        if (!(inputView instanceof FrameLayout host)) {
            return null;
        }
        int[] hostLocation = new int[2];
        host.getLocationOnScreen(hostLocation);
        int keyboardTop = findKeyboardPanelTop(
                host, host, hostLocation[1], Integer.MAX_VALUE, 0);
        if (keyboardTop == Integer.MAX_VALUE) {
            return null;
        }
        int topMargin = Math.max(0,
                keyboardTop - hostLocation[1] + dp(host.getContext(), 4));
        return new Placement(host, topMargin);
    }

    static Handle show(Placement placement,
            GboardCalculatorSuggestion.Candidate candidate, Actions actions) {
        FrameLayout host = placement.host;
        Context context = host.getContext();
        Palette palette = Palette.from(context);
        LinearLayout root = new LinearLayout(context);
        root.setTag(SUGGESTION_TAG);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setElevation(dp(context, 24));
        Handle handle = new Handle(host, root, actions);

        HorizontalScrollView equation = equationSuggestion(
                context, palette, candidate, handle);
        LinearLayout.LayoutParams equationParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.7f);
        equationParams.setMarginEnd(dp(context, 4));
        root.addView(equation, equationParams);

        TextView result = resultSuggestion(context, palette, candidate, handle);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        resultParams.setMarginStart(dp(context, 4));
        root.addView(result, resultParams);

        removeFrom(host);
        int availableWidth = Math.max(dp(context, 240), host.getWidth() - dp(context, 96));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                availableWidth, dp(context, SUGGESTION_HEIGHT_DP));
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = placement.topMargin;
        host.addView(root, params);
        root.bringToFront();
        return handle;
    }

    static void removeFrom(FrameLayout host) {
        if (host == null) {
            return;
        }
        for (int index = host.getChildCount() - 1; index >= 0; index--) {
            View child = host.getChildAt(index);
            if (child != null && SUGGESTION_TAG.equals(child.getTag())) {
                host.removeViewAt(index);
            }
        }
    }

    private static HorizontalScrollView equationSuggestion(Context context, Palette palette,
            GboardCalculatorSuggestion.Candidate candidate, Handle handle) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        styleSuggestion(scroll, context, palette);
        scroll.setContentDescription(GboardCalculatorText.localized(
                "算式 " + candidate.getEquation() + "，可左右捲動，點擊輸入完整算式，長按複製答案",
                "Equation " + candidate.getEquation()
                        + ", scroll horizontally, tap to enter the complete equation, long press to copy the answer"));
        bindSuggestionActions(scroll, handle, true);

        TextView label = label(context, candidate.getEquation(), 17f, palette.primaryText);
        label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        label.setSingleLine(true);
        label.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        bindSuggestionActions(label, handle, true);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        scroll.addView(label, labelParams);
        scroll.post(() -> GboardCalculatorSafety.run(
                () -> scroll.fullScroll(View.FOCUS_RIGHT)));
        return scroll;
    }

    private static TextView resultSuggestion(Context context, Palette palette,
            GboardCalculatorSuggestion.Candidate candidate, Handle handle) {
        TextView result = label(context, candidate.getResult(), 18f, palette.primaryText);
        result.setGravity(Gravity.CENTER);
        result.setMaxLines(1);
        result.setHorizontallyScrolling(false);
        result.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        result.setAutoSizeTextTypeUniformWithConfiguration(
                10, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        styleSuggestion(result, context, palette);
        result.setContentDescription(GboardCalculatorText.localized(
                "答案 " + candidate.getResult() + "，點擊輸入答案，長按複製",
                "Answer " + candidate.getResult() + ", tap to enter the answer, long press to copy"));
        bindSuggestionActions(result, handle, false);
        return result;
    }

    private static void bindSuggestionActions(View view, Handle handle,
            boolean insertEquation) {
        view.setOnClickListener(clicked -> handle.accept(insertEquation));
        view.setOnLongClickListener(longClicked -> {
            handle.copy();
            return true;
        });
    }

    private static void styleSuggestion(View view, Context context, Palette palette) {
        view.setBackground(rounded(palette.surface, dp(context, 20)));
        view.setElevation(dp(context, 24));
        view.setClickable(true);
        view.setFocusable(true);
    }

    private static int findKeyboardPanelTop(FrameLayout host, View current,
            int hostScreenTop, int bestScreenTop, int depth) {
        if (current == null || depth > 16 || current.getVisibility() != View.VISIBLE
                || !current.isAttachedToWindow()) {
            return bestScreenTop;
        }
        if (current != host
                && current.getWidth() >= Math.round(host.getWidth() * 0.85f)
                && current.getHeight() >= dp(host.getContext(), 160)) {
            int[] location = new int[2];
            current.getLocationOnScreen(location);
            int hostBottom = hostScreenTop + host.getHeight();
            int minimumTop = hostScreenTop + host.getHeight() / 3;
            if (location[1] >= minimumTop
                    && location[1] + current.getHeight()
                    >= hostBottom - dp(host.getContext(), 48)) {
                bestScreenTop = Math.min(bestScreenTop, location[1]);
            }
        }
        if (current instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                bestScreenTop = findKeyboardPanelTop(
                        host, group.getChildAt(index), hostScreenTop,
                        bestScreenTop, depth + 1);
            }
        }
        return bestScreenTop;
    }

    private static TextView label(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT);
        view.setSingleLine(false);
        return view;
    }

    private static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radiusPx);
        return background;
    }

    private static int dp(Context context, int value) {
        float density = context != null
                ? context.getResources().getDisplayMetrics().density : 1f;
        return Math.max(1, Math.round(value * density));
    }

    static final class Placement {
        private final FrameLayout host;
        private final int topMargin;

        private Placement(FrameLayout host, int topMargin) {
            this.host = host;
            this.topMargin = topMargin;
        }

        FrameLayout host() {
            return host;
        }
    }

    static final class Handle {
        private final FrameLayout host;
        private final LinearLayout root;
        private final Actions actions;

        private Handle(FrameLayout host, LinearLayout root, Actions actions) {
            this.host = host;
            this.root = root;
            this.actions = actions;
        }

        private void accept(boolean insertEquation) {
            GboardCalculatorSafety.run(() -> actions.accept(insertEquation));
        }

        private void copy() {
            GboardCalculatorSafety.run(actions::copy);
        }

        boolean isAttachedTo(FrameLayout expectedHost) {
            return host == expectedHost && root.getParent() == host;
        }

        void close() {
            GboardCalculatorSafety.run(() -> {
                if (root.getParent() instanceof ViewGroup parent) {
                    parent.removeView(root);
                }
            });
        }
    }

    private static final class Palette {
        private final int surface;
        private final int primaryText;

        private Palette(int surface, int primaryText) {
            this.surface = surface;
            this.primaryText = primaryText;
        }

        private static Palette from(Context context) {
            boolean dark = context != null && (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return dark
                    ? new Palette(0xff303134, Color.WHITE)
                    : new Palette(0xfff1f3f4, 0xff202124);
        }
    }
}
