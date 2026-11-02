package dev.jason.gboardpatches.extension.swipedismiss;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Map;
import java.util.WeakHashMap;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardSwipeDismissRuntime {
    private static final Map<View, GboardSwipeDismissGesture> GESTURES = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> CANCELLING = ThreadLocal.withInitial(() -> false);
    private static final String HEADER_ID_NAME = "keyboard_header_view_holder";
    private static final long SETTINGS_CACHE_MS = 500L;
    private static volatile int headerId;
    private static long lastSettingsReadMs = -SETTINGS_CACHE_MS;
    private static boolean cachedEnabled;

    private GboardSwipeDismissRuntime() {
    }

    public static boolean onHeaderTouch(View holder, MotionEvent event) {
        if (holder == null || event == null) {
            return false;
        }
        if (!isHeaderHolder(holder) || Boolean.TRUE.equals(CANCELLING.get())) {
            return false;
        }
        // A cached enabled value or an in-flight gesture cannot bypass Direct Boot.
        if (!GboardPatchesSettings.isUserUnlocked(holder.getContext())) {
            reset();
            return false;
        }
        GboardSwipeDismissGesture gesture = GESTURES.get(holder);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (gesture == null) {
                gesture = new GboardSwipeDismissGesture();
                GESTURES.put(holder, gesture);
            }
            boolean admitted = holder.isShown() && isHeaderStart(holder, event)
                    && enabled(holder.getContext());
            gesture.begin(admitted, event.getPointerId(0), event.getRawX(), event.getRawY(),
                    event.getDownTime());
            return false;
        }
        if (gesture == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (gesture.isConsumed()) {
                return true;
            }
            gesture.reset();
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            boolean consumed = gesture.isConsumed();
            gesture.reset();
            return consumed;
        }
        if (action != MotionEvent.ACTION_MOVE || !gesture.isTracking()) {
            return gesture.isConsumed();
        }
        if (!holder.isShown()) {
            gesture.reset();
            return false;
        }
        if (gesture.isConsumed()) {
            return true;
        }
        int pointerIndex = event.findPointerIndex(event.getPointerId(0));
        if (event.getPointerCount() != 1 || pointerIndex < 0) {
            gesture.reset();
            return false;
        }
        float density = holder.getResources().getDisplayMetrics().density;
        ViewConfiguration vc = ViewConfiguration.get(holder.getContext());
        float touchSlop = vc.getScaledTouchSlop();
        long longPressTimeout = ViewConfiguration.getLongPressTimeout();
        if (!gesture.move(event.getPointerId(pointerIndex), event.getRawX(), event.getRawY(),
                density, touchSlop, event.getEventTime(), longPressTimeout)) {
            return false;
        }
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        CANCELLING.set(true);
        try {
            holder.dispatchTouchEvent(cancel);
        } finally {
            CANCELLING.remove();
            cancel.recycle();
        }
        InputMethodService service = resolveInputMethodService(holder);
        if (service != null) {
            service.requestHideSelf(0);
        }
        return true;
    }

    public static InputMethodService resolveInputMethodService(View view) {
        if (view == null) {
            return null;
        }
        Context current = view.getContext();
        while (current instanceof ContextWrapper wrapper) {
            if (current instanceof InputMethodService ims) {
                return ims;
            }
            Context base = wrapper.getBaseContext();
            if (base == current || base == null) {
                break;
            }
            current = base;
        }
        return null;
    }

    private static boolean enabled(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastSettingsReadMs < SETTINGS_CACHE_MS) {
            return cachedEnabled;
        }
        lastSettingsReadMs = now;
        cachedEnabled = GboardSwipeDismissSettings.readEnabled(context);
        return cachedEnabled;
    }

    private static boolean isHeaderHolder(View holder) {
        int expectedId = headerId;
        if (expectedId != 0) {
            return holder.getId() == expectedId;
        }
        if (holder.getId() == View.NO_ID) {
            return false;
        }
        try {
            if (HEADER_ID_NAME.equals(holder.getResources().getResourceEntryName(holder.getId()))) {
                headerId = holder.getId();
                return true;
            }
        } catch (Resources.NotFoundException ignored) {
        }
        return false;
    }

    private static boolean isHeaderStart(View holder, MotionEvent event) {
        int[] location = new int[2];
        holder.getLocationOnScreen(location);
        return GboardSwipeDismissGesture.isHeaderStart(
                event.getRawX(), event.getRawY(), location[0], location[1],
                location[0] + holder.getWidth(), location[1] + holder.getHeight());
    }

    public static void reset() {
        GESTURES.clear();
        headerId = 0;
        lastSettingsReadMs = -SETTINGS_CACHE_MS;
        cachedEnabled = false;
    }
}
