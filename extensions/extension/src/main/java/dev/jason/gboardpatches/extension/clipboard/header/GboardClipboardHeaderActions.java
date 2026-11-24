package dev.jason.gboardpatches.extension.clipboard.header;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.settings.GboardPatchesIcons;

/** Clipboard-only UI and gesture ownership; no Gboard private names or resource IDs. */
public final class GboardClipboardHeaderActions {
    public interface Access {
        boolean isCurrent(Object keyboard, View header) throws ReflectiveOperationException;
        /** Current action settings, or null while the action is disabled. */
        Settings settings(Context context);
        void report(Throwable failure);
    }

    public static final class Settings {
        private final String selectLabel;
        private final String emptyMessage;
        private final boolean excludePinned;

        public Settings(String selectLabel, String emptyMessage, boolean excludePinned) {
            this.selectLabel = selectLabel;
            this.emptyMessage = emptyMessage;
            this.excludePinned = excludePinned;
        }

        public String selectLabel() { return selectLabel; }
        public String emptyMessage() { return emptyMessage; }
        public boolean excludePinned() { return excludePinned; }

        @Override public boolean equals(Object other) {
            return other instanceof Settings value
                    && excludePinned == value.excludePinned
                    && Objects.equals(selectLabel, value.selectLabel)
                    && Objects.equals(emptyMessage, value.emptyMessage);
        }

        @Override public int hashCode() {
            return Objects.hash(selectLabel, emptyMessage, excludePinned);
        }
    }

    public interface Selection {
        /** False once a post-write contract check failed; the edit-mode action then stays hidden. */
        default boolean usable() { return true; }
        boolean hasSelectableItems(Object keyboard, boolean excludePinned) throws ReflectiveOperationException;
        void selectAll(Object keyboard, boolean excludePinned) throws ReflectiveOperationException;
    }

    // Values must also be weak: a session belongs to its header, never to a static registry.
    private static final Map<Object, WeakReference<Session>> KEYBOARDS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Session>> HEADERS = new WeakHashMap<>();
    private static final Map<Object, Boolean> EDITING = new WeakHashMap<>();

    private GboardClipboardHeaderActions() { }

    public static void attach(Object keyboard, View header, LinearLayout icons,
            View anchor, TextView label, View recycler, Access access, Selection selection) {
        removeSession(keyboard);
        if (icons.indexOfChild(anchor) < 0) return;
        Session session = new Session(keyboard, header, icons, anchor, label, recycler,
                access, selection);
        synchronized (KEYBOARDS) { KEYBOARDS.put(keyboard, new WeakReference<>(session)); }
        try {
            header.addOnAttachStateChangeListener(session);
            recycler.addOnAttachStateChangeListener(session);
            if (header.isAttachedToWindow()) session.onViewAttachedToWindow(header);
            else session.update();
        } catch (Throwable failure) {
            session.reportSafely(failure);
            removeSession(keyboard);
        }
    }

    public static void stateChanged(Object keyboard, boolean editing) {
        synchronized (EDITING) {
            if (editing) EDITING.put(keyboard, true);
            else EDITING.remove(keyboard);
        }
        update(keyboard);
    }

    public static void update(Object keyboard) {
        Session session;
        synchronized (KEYBOARDS) { session = dereference(KEYBOARDS.get(keyboard)); }
        if (session != null) session.update();
    }

    public static void close(Object keyboard) {
        removeSession(keyboard);
        synchronized (EDITING) { EDITING.remove(keyboard); }
    }

    private static void removeSession(Object keyboard) {
        Session old;
        synchronized (KEYBOARDS) { old = dereference(KEYBOARDS.remove(keyboard)); }
        if (old != null) old.dispose();
    }

    /** Called by the existing asynchronous settings publisher; no polling or remote I/O here. */
    public static void settingsPublished() {
        ArrayList<WeakReference<Session>> sessions;
        synchronized (KEYBOARDS) { sessions = new ArrayList<>(KEYBOARDS.values()); }
        for (WeakReference<Session> reference : sessions) {
            Session session = reference.get();
            if (session != null) session.header.post(session::update);
        }
    }

    /** Only a DOWN inside our button takes ownership; the entire remaining stream stays ours. */
    public static boolean onHeaderTouch(View header, MotionEvent event) {
        Session session;
        synchronized (HEADERS) { session = dereference(HEADERS.get(header)); }
        return session != null && event != null && session.touch(event);
    }

    private static Session dereference(WeakReference<Session> reference) {
        return reference == null ? null : reference.get();
    }

    private static final class Session implements View.OnAttachStateChangeListener {
        final WeakReference<Object> keyboard;
        final View header;
        final LinearLayout icons;
        final TextView label;
        final ColorStateList fallbackTint;
        final View anchor;
        final View recycler;
        final Access access;
        final Selection selection;
        final Rect bounds = new Rect();
        ImageButton button;
        Settings settings;
        PopupWindow explanation;
        final Runnable hideExplanation = () -> {
            try {
                dismissExplanation();
            } catch (Throwable failure) {
                reportSafely(failure);
            }
        };
        boolean active = true;
        boolean gesture;
        boolean cancelled;
        boolean explainEmpty;
        float downX, downY;
        final int touchSlop;

        Session(Object keyboard, View header, LinearLayout icons, View anchor, TextView label,
                View recycler, Access access, Selection selection) {
            this.keyboard = new WeakReference<>(keyboard);
            this.header = header;
            this.icons = icons;
            this.anchor = anchor;
            this.label = label;
            this.fallbackTint = label == null ? themeTextColors(header.getContext()) : null;
            this.recycler = recycler;
            this.access = access;
            this.selection = selection;
            touchSlop = ViewConfiguration.get(header.getContext()).getScaledTouchSlop();
        }

        private static ColorStateList themeTextColors(Context context) {
            TypedArray colors = context.obtainStyledAttributes(
                    new int[] { android.R.attr.textColorPrimary });
            try {
                ColorStateList tint = colors.getColorStateList(0);
                return tint != null ? tint : ColorStateList.valueOf(Color.GRAY);
            } finally {
                colors.recycle();
            }
        }

        private ColorStateList textColors() {
            return label != null ? label.getTextColors() : fallbackTint;
        }

        Settings availableSettings(Object owner) throws ReflectiveOperationException {
            boolean editing;
            synchronized (EDITING) { editing = EDITING.containsKey(owner); }
            if (!active || owner == null || !editing || !selection.usable()) return null;
            Settings current = access.settings(header.getContext());
            return current != null && access.isCurrent(owner, header) ? current : null;
        }

        void update() {
            if (!active) return;
            try {
                Object owner = keyboard.get();
                Settings current = availableSettings(owner);
                boolean visible = current != null;
                boolean enabled = visible && selection.hasSelectableItems(owner, current.excludePinned());
                if (visible && button == null) createButton();
                if (button == null) return;
                if (visible) {
                    if (!current.equals(settings)) {
                        dismissExplanation();
                        cancelTouch(null);
                    }
                    settings = current;
                    button.setAlpha(enabled ? 1f : 0.38f);
                    button.setContentDescription(enabled ? settings.selectLabel()
                            : settings.selectLabel() + "\n" + settings.emptyMessage());
                    button.setTooltipText(enabled ? settings.selectLabel() : settings.emptyMessage());
                    button.setImageTintList(textColors());
                }
                button.setVisibility(visible ? View.VISIBLE : View.GONE);
                button.setEnabled(enabled);
                if (!visible || enabled) dismissExplanation();
                if (!visible) cancelTouch(null);
            } catch (Throwable failure) {
                failSafely(failure);
            }
        }

        private void failSafely(Throwable failure) {
            reportSafely(failure);
            if (button != null) runCleanup(() -> button.setVisibility(View.GONE));
            runCleanup(this::dismissExplanation);
            runCleanup(() -> cancelTouch(null));
        }

        private void runCleanup(Runnable step) {
            try {
                step.run();
            } catch (Throwable failure) {
                reportSafely(failure);
            }
        }

        private void reportSafely(Throwable failure) {
            try {
                access.report(failure);
            } catch (Throwable ignored) {
                // Reporting is best-effort during view teardown.
            }
        }

        void createButton() {
            Context context = header.getContext();
            ImageButton created = new ImageButton(context);
            float density = context.getResources().getDisplayMetrics().density;
            created.setImageDrawable(GboardPatchesIcons.create(
                    GboardPatchesIcons.CLIPBOARD_SELECT_UNPINNED, Color.WHITE, density));
            created.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
            int size = Math.round(48 * density);
            int diameter = anchor.getHeight() > 0 ? Math.min(size, anchor.getHeight()) : size;
            int face = Math.min(diameter, Math.round(36 * density));
            int glyph = Math.min(diameter, Math.round(24 * density));
            created.setPadding((size - glyph) / 2, (diameter - glyph) / 2,
                    (size - glyph) / 2, (diameter - glyph) / 2);
            created.setMinimumHeight(size);
            GradientDrawable mask = new GradientDrawable();
            mask.setShape(GradientDrawable.OVAL);
            mask.setColor(Color.WHITE);
            int tint = label != null ? label.getCurrentTextColor() : fallbackTint.getDefaultColor();
            GradientDrawable surface = new GradientDrawable();
            surface.setShape(GradientDrawable.OVAL);
            surface.setColor((tint & 0x00ffffff) | 0x18000000);
            created.setBackground(new android.graphics.drawable.InsetDrawable(
                    new RippleDrawable(ColorStateList.valueOf(
                            (tint & 0x00ffffff) | 0x33000000), surface, mask),
                    (size - face) / 2, (diameter - face) / 2,
                    (size - face) / 2, (diameter - face) / 2));
            created.setOnClickListener(view -> performAction());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    size, diameter);
            params.gravity = Gravity.CENTER_VERTICAL;
            button = created;
            icons.addView(created, 0, params);
        }

        void performAction() {
            try {
                Object owner = keyboard.get();
                if (!header.isAttachedToWindow()) return;
                Settings current = availableSettings(owner);
                if (current != null && selection.hasSelectableItems(owner, current.excludePinned())) {
                    selection.selectAll(owner, current.excludePinned());
                    update();
                }
            } catch (Throwable failure) {
                failSafely(failure);
            }
        }

        void showEmptyExplanation() {
            dismissExplanation();
            Context context = header.getContext();
            float density = context.getResources().getDisplayMetrics().density;
            TextView message = new TextView(context);
            message.setText(settings.emptyMessage());
            message.setTextColor(textColors());
            int padding = Math.round(12 * density);
            message.setPadding(padding, padding / 2, padding, padding / 2);
            message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            GradientDrawable background = new GradientDrawable();
            TypedArray colors = context.obtainStyledAttributes(
                    new int[] { android.R.attr.colorBackground });
            try { background.setColor(colors.getColor(0, Color.DKGRAY)); }
            finally { colors.recycle(); }
            background.setCornerRadius(4 * density);
            explanation = new PopupWindow(message, LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, false);
            explanation.setBackgroundDrawable(background);
            explanation.setElevation(6 * density);
            explanation.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            explanation.setTouchable(false);
            explanation.showAsDropDown(button, 0, 0, Gravity.END);
            header.postDelayed(hideExplanation, 2_500L);
        }

        void dismissExplanation() {
            header.removeCallbacks(hideExplanation);
            PopupWindow popup = explanation;
            explanation = null;
            if (popup != null) popup.dismiss();
        }

        boolean touch(MotionEvent event) {
            int action = event.getActionMasked();
            try {
                return dispatchTouch(event, action);
            } catch (Throwable failure) {
                boolean owned = gesture;
                failSafely(failure);
                return owned;
            } finally {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gesture = false;
                    cancelled = false;
                }
            }
        }

        private boolean dispatchTouch(MotionEvent event, int action) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (gesture) runCleanup(() -> cancelTouch(event));
                gesture = false;
                cancelled = false;
                if (button == null || !button.isShown()) return false;
                bounds.set(0, 0, button.getWidth(), button.getHeight());
                ((android.view.ViewGroup) header).offsetDescendantRectToMyCoords(button, bounds);
                if (!bounds.contains((int) event.getX(), (int) event.getY())) return false;
                update();
                if (!button.isShown()) return false;
                gesture = true;
                explainEmpty = !button.isEnabled();
                downX = event.getX();
                downY = event.getY();
            }
            if (!gesture) return false;
            if (action == MotionEvent.ACTION_UP && !cancelled) update();
            if (explainEmpty && action == MotionEvent.ACTION_MOVE
                    && (Math.abs(event.getX() - downX) > touchSlop
                    || Math.abs(event.getY() - downY) > touchSlop)) {
                cancelTouch(event);
            }
            if (!active || button == null || !button.isShown()
                    || event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN) {
                cancelTouch(event);
            } else if (!cancelled) {
                MotionEvent local = MotionEvent.obtain(event);
                local.offsetLocation(-bounds.left, -bounds.top);
                try { button.dispatchTouchEvent(local); }
                finally { local.recycle(); }
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP && explainEmpty && !cancelled
                        && button != null && button.isShown() && !button.isEnabled()) {
                    try {
                        showEmptyExplanation();
                    } catch (android.view.WindowManager.BadTokenException windowClosed) {
                        dismissExplanation();
                    }
                }
            }
            return true;
        }

        void cancelTouch(MotionEvent source) {
            if (!gesture || cancelled || button == null) return;
            cancelled = true;
            MotionEvent cancel = source != null ? MotionEvent.obtain(source)
                    : MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            try { button.dispatchTouchEvent(cancel); }
            finally { cancel.recycle(); }
        }

        public void onViewAttachedToWindow(View view) {
            if (!active) return;
            try {
                if (view == header) {
                    synchronized (HEADERS) { HEADERS.put(header, new WeakReference<>(this)); }
                }
                update();
            } catch (Throwable failure) {
                failSafely(failure);
            }
        }

        public void onViewDetachedFromWindow(View view) {
            try {
                if (view == header) {
                    synchronized (HEADERS) { HEADERS.remove(header); }
                }
            } catch (Throwable failure) {
                reportSafely(failure);
            }
            runCleanup(this::dismissExplanation);
            runCleanup(() -> cancelTouch(null));
            // A live header must finish consuming the gesture if only its recycler detached.
            if (view == header) gesture = false;
        }

        void dispose() {
            active = false;
            onViewDetachedFromWindow(header);
            runCleanup(() -> header.removeOnAttachStateChangeListener(this));
            runCleanup(() -> recycler.removeOnAttachStateChangeListener(this));
            if (button != null) {
                runCleanup(() -> button.setOnClickListener(null));
                runCleanup(() -> icons.removeView(button));
            }
        }
    }
}
