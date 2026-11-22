package dev.jason.gboardpatches.extension.shiftdelete;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/**
 * Entry points for the Shift + Backspace forward-delete adapter. Every method runs on
 * the keyboard's UI thread and holds no keyboard-specific type, view or editor reference.
 *
 * <p>The two host-facing entries, {@link #remapInput} and {@link #onTouchEvent}, never throw.
 * A failed remap switches the feature off for the rest of the process, so a shape change
 * costs the feature instead of every key press.
 */
public final class GboardShiftDeleteRuntime {
    private static final ShiftDeleteSession SESSION = new ShiftDeleteSession();
    private static final ShiftDeleteEnabledCache ENABLED = new ShiftDeleteEnabledCache();

    private static volatile GboardShiftDeleteEventBinding binding;
    private static volatile Throwable failure;

    private GboardShiftDeleteRuntime() {}

    /**
     * Input event entry: returns the event to deliver, a copy with Delete turned into Forward
     * Delete while Shift is held, or the same event for everything else. The result always has
     * the type of {@code event}.
     */
    public static Object remapInput(Object service, Object event) {
        if (event == null || failure != null || !(service instanceof Context context)) return event;
        try {
            if (!isEnabled(context)) return event;
            Object mapped = bindingFor(event.getClass()).remap(event, SESSION::key);
            return mapped != null && mapped.getClass() == event.getClass() ? mapped : event;
        } catch (Throwable error) {
            disable(error);
            return event;
        }
    }

    /** Keyboard-root touch entry: a new stream or a cancelled one clears a stale Shift. */
    public static void onTouchEvent(MotionEvent event) {
        if (event == null || failure != null) return;
        try {
            onTouch(event.getActionMasked());
        } catch (Throwable error) {
            disable(error);
        }
    }

    /** The error that switched the feature off for this process, or null while it is usable. */
    public static Throwable failure() {
        return failure;
    }

    /** True once the setting has been read as on; locked or unavailable storage reads as off. */
    public static boolean isEnabled(Context context) {
        return ENABLED.get(SystemClock.elapsedRealtime(), () -> GboardPatchesSettings
                .readRuntimeSetting(context, GboardShiftDeleteSettings::enabled, (Boolean) null));
    }

    /** True after the first definite read, whatever its value. */
    public static boolean hasDefiniteSetting() {
        return ENABLED.cached() != null;
    }

    /** Returns the key code to deliver for one soft-key event; call only while enabled. */
    public static int mapKey(String action, int code) {
        return SESSION.key(action, code);
    }

    /** Keyboard-root touch stream: a new stream or a cancelled one clears a stale Shift. */
    public static void onTouch(int actionMasked) {
        if (!SESSION.isShiftHeld()) return;
        if (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_CANCEL) {
            SESSION.cancel();
        }
    }

    /** Drops held state after a failed remap so the next Backspace stays native. */
    public static void reset() {
        SESSION.cancel();
    }

    private static GboardShiftDeleteEventBinding bindingFor(Class<?> eventType)
            throws ReflectiveOperationException {
        GboardShiftDeleteEventBinding current = binding;
        if (current != null && current.eventType() == eventType) return current;
        current = GboardShiftDeleteEventBinding.resolve(eventType);
        binding = current;
        return current;
    }

    private static void disable(Throwable error) {
        failure = error;
        SESSION.cancel();
    }
}
