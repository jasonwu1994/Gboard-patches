package dev.jason.gboardpatches.extension.calculator;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows calculator results as real Gboard suggestions, so Gboard lays them out and themes them.
 *
 * <p>The patch calls in from Gboard's candidate controller, suggestion strip and event dispatch,
 * all on Gboard's main thread. Candidates are recognised by identity, so no Gboard types or
 * obfuscated names are needed here.
 */
public final class GboardCalculatorCandidates {
    // The result slot fits its text, but stays easy to tap and leaves the equation at least half.
    private static final float MIN_RESULT_SHARE = 0.3f;
    private static final float MAX_RESULT_SHARE = 0.5f;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Gboard's candidate controller, which the patch makes a Runnable that shows suggestions again.
    private static WeakReference<Runnable> controller = new WeakReference<>(null);
    private static WeakReference<Object> equationCandidate = new WeakReference<>(null);
    private static WeakReference<Object> resultCandidate = new WeakReference<>(null);
    private static WeakReference<View> strip = new WeakReference<>(null);
    // The calculation in the strip, or null while Gboard's own suggestions show.
    private static GboardCalculatorSuggestion.Candidate shown;
    // Set while refreshIfOutdated asks Gboard for suggestions, so the calculation it has just
    // read is not read from the editor again.
    private static GboardCalculatorSuggestion.Candidate refreshing;

    private GboardCalculatorCandidates() {
    }

    /**
     * Called when Gboard shows a keyboard view, such as the letters after the symbols, the
     * unfolded layout, or the keyboard reopening. Each view has its own controller, which starts
     * with the toolbar showing, and Gboard does not ask for suggestions again, so the calculator
     * shows its pair there once Gboard has finished showing the view.
     */
    public static void onControllerActivated(Object candidatesController) {
        if (!(candidatesController instanceof Runnable refresh)) {
            return;
        }
        controller = new WeakReference<>(refresh);
        MAIN.post(() -> GboardCalculatorSafety.run(() -> {
            if (controller.get() == refresh) {
                refreshIfOutdated(GboardCalculatorRuntime.currentCandidate());
            }
        }));
    }

    /**
     * Called when Gboard is about to show a batch of suggestions. Returns the equation and result
     * to show instead, or null to keep Gboard's.
     */
    public static String[] texts(Object candidatesController) {
        if (candidatesController instanceof Runnable refresh && controller.get() != refresh) {
            controller = new WeakReference<>(refresh);
        }
        shown = refreshing != null ? refreshing : GboardCalculatorRuntime.currentCandidate();
        return shown != null ? new String[] {shown.getEquation(), shown.getResult()} : null;
    }

    /** Remembers the Gboard candidates built from {@link #texts} and returns them in strip order. */
    public static List<Object> show(Object equation, Object result) {
        equationCandidate = new WeakReference<>(equation);
        resultCandidate = new WeakReference<>(result);
        List<Object> candidates = new ArrayList<>(2);
        candidates.add(equation);
        candidates.add(result);
        return candidates;
    }

    /** True when the tapped candidate is a calculator one, which has replaced the calculation. */
    public static boolean onCandidateSelected(Object candidate) {
        boolean isEquation = candidate != null && candidate == equationCandidate.get();
        if (!isEquation && (candidate == null || candidate != resultCandidate.get())) {
            return false;
        }
        GboardCalculatorSafety.run(() -> GboardCalculatorRuntime.acceptCurrent(isEquation));
        // Consumed even if the text changed meanwhile, so Gboard never inserts the raw text.
        return true;
    }

    /**
     * Called after Gboard sizes the strip's slots for three suggestions, for a new batch or when
     * the strip changes width, such as when a foldable is unfolded.
     * The two calculator slots share the whole strip instead: the result gets the width its text
     * needs, within limits, and the equation the rest. Gboard sizes every slot again for its next
     * batch, so nothing needs restoring.
     */
    public static void afterCandidatesBound(Object boundStrip) {
        if (shown != null && boundStrip instanceof ViewGroup group) {
            if (strip.get() != group) {
                strip = new WeakReference<>(group);
            }
            String result = shown.getResult();
            GboardCalculatorSafety.run(() -> split(group, result));
        }
    }

    private static void split(ViewGroup group, String result) {
        List<View> slots = new ArrayList<>();
        for (int index = 0; index < group.getChildCount(); index++) {
            View slot = group.getChildAt(index);
            if (slot.getVisibility() != View.GONE && slot.getLayoutParams().width > 0) {
                slots.add(slot);
            }
        }
        int width = group.getWidth() - group.getPaddingLeft() - group.getPaddingRight();
        if (slots.size() < 2 || width <= 0) {
            return;
        }
        int resultWidth = Math.max(Math.round(width * MIN_RESULT_SHARE),
                Math.min(Math.round(width * MAX_RESULT_SHARE), neededWidth(slots.get(1), result)));
        setWidth(slots.get(0), width - resultWidth);
        setWidth(slots.get(1), resultWidth);
        for (int index = 2; index < slots.size(); index++) {
            slots.get(index).setVisibility(View.GONE);
        }
    }

    /** Width the slot needs to show {@code text} in full, measured with Gboard's own text style. */
    private static int neededWidth(View slot, String text) {
        TextView label = findLabel(slot);
        if (label == null) {
            return 0;
        }
        int padding = 0;
        for (View view = label; view != slot; view = (View) view.getParent()) {
            padding += view.getPaddingLeft() + view.getPaddingRight();
        }
        padding += slot.getPaddingLeft() + slot.getPaddingRight();
        return (int) Math.ceil(label.getPaint().measureText(text)) + padding;
    }

    private static TextView findLabel(View view) {
        if (view instanceof TextView label) {
            return label;
        }
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView label = findLabel(group.getChildAt(index));
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    private static void setWidth(View slot, int width) {
        ViewGroup.LayoutParams params = slot.getLayoutParams();
        if (params.width != width) {
            params.width = width;
            slot.setLayoutParams(params);
        }
    }

    /**
     * Called when Gboard has no suggestions for a keystroke and is about to clear the strip and
     * bring back the toolbar. Returns true to keep the calculator pair, refreshed if outdated.
     */
    public static boolean keepOnClear() {
        GboardCalculatorSuggestion.Candidate current = GboardCalculatorRuntime.currentCandidate();
        if (current == null) {
            shown = null;
            return false;
        }
        refreshIfOutdated(current);
        return shown != null;
    }

    /**
     * Asks Gboard's controller to show suggestions again when the current calculation is not the
     * one in the strip, for example after a keystroke Gboard reacted to before the editor applied
     * it, or a delete that brought the toolbar back.
     */
    static void refreshIfOutdated(GboardCalculatorSuggestion.Candidate current) {
        Runnable target = controller.get();
        if (current == null || target == null) {
            return;
        }
        View shownStrip = strip.get();
        if (shown != null && shownStrip != null && shownStrip.isShown()
                && shown.getExpression().equals(current.getExpression())
                && shown.getResult().equals(current.getResult())) {
            return;
        }
        refreshing = current;
        try {
            target.run();
        } catch (RuntimeException ignored) {
            // Gboard's next request for suggestions updates the strip anyway.
        } finally {
            refreshing = null;
        }
    }
}
