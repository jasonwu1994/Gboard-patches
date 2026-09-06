package dev.jason.gboardpatches.extension.swipedismiss;

import android.content.Context;
import android.app.Dialog;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardSwipeDownDismissRuntime {
    private static final String TAG = "GboardPatches";
    private static final String LOG_PREFIX = "[swipe-down-dismiss] ";
    private static volatile boolean toolbarEditMode;

    private GboardSwipeDownDismissRuntime() {
    }

    public static void onInputViewStarted(Object inputMethodService) {
        toolbarEditMode = false;
        if (!(inputMethodService instanceof InputMethodService service)) {
            return;
        }
        installWindowCallback(service);
        View decorView = service.getWindow() == null
                ? null : service.getWindow().getWindow().getDecorView();
        if (decorView != null) {
            decorView.post(() -> installWindowCallback(service));
        }
    }

    public static void onToolbarEditModeChanged(boolean editing) {
        toolbarEditMode = editing;
    }

    static boolean isToolbarEditModeForTesting() {
        return toolbarEditMode;
    }

    private static void installWindowCallback(InputMethodService service) {
        try {
            Dialog softInputWindow = service.getWindow();
            Window window = softInputWindow == null ? null : softInputWindow.getWindow();
            if (window == null) {
                return;
            }
            Window.Callback current = window.getCallback();
            if (current == null) {
                return;
            }
            if (Proxy.isProxyClass(current.getClass())) {
                InvocationHandler handler = Proxy.getInvocationHandler(current);
                if (handler instanceof CallbackHandler callbackHandler) {
                    callbackHandler.service = service;
                    return;
                }
            }
            CallbackHandler handler = new CallbackHandler(service, current);
            Window.Callback wrapper = (Window.Callback) Proxy.newProxyInstance(
                    Window.Callback.class.getClassLoader(),
                    new Class<?>[] {Window.Callback.class},
                    handler);
            window.setCallback(wrapper);
        } catch (Throwable failure) {
            logFailure("failed to install window touch observer", failure);
        }
    }

    private static final class CallbackHandler implements InvocationHandler {
        private volatile InputMethodService service;
        private final Window.Callback delegate;
        private final GboardSwipeDownDismissGestureState gesture =
                new GboardSwipeDownDismissGestureState();

        CallbackHandler(InputMethodService service, Window.Callback delegate) {
            this.service = service;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName())
                    && arguments != null
                    && arguments.length == 1
                    && arguments[0] instanceof MotionEvent event) {
                return dispatchTouchEvent(event);
            }
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private boolean dispatchTouchEvent(MotionEvent event) {
            InputMethodService currentService = service;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    boolean enabled = isEnabled(currentService);
                    gesture.begin(enabled && beginsInHeader(currentService, event),
                            event.getRawX(), event.getRawY());
                    return delegate.dispatchTouchEvent(event);
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (toolbarEditMode) {
                        gesture.reset();
                        return delegate.dispatchTouchEvent(event);
                    }
                    if (gesture.isConsumed()) {
                        return true;
                    }
                    float density = currentService == null
                            ? 1f : currentService.getResources().getDisplayMetrics().density;
                    if (gesture.move(event.getRawX(), event.getRawY(), density)) {
                        cancelDelegateGesture(event);
                        dismiss(currentService);
                        return true;
                    }
                    return delegate.dispatchTouchEvent(event);
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gesture.isConsumed()) {
                        gesture.reset();
                        return true;
                    }
                    gesture.reset();
                    return delegate.dispatchTouchEvent(event);
                }
                case MotionEvent.ACTION_POINTER_DOWN -> {
                    if (gesture.isConsumed()) {
                        return true;
                    }
                    gesture.reset();
                    return delegate.dispatchTouchEvent(event);
                }
                default -> {
                    return gesture.isConsumed() || delegate.dispatchTouchEvent(event);
                }
            }
        }

        private void cancelDelegateGesture(MotionEvent source) {
            MotionEvent cancel = MotionEvent.obtain(source);
            try {
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                delegate.dispatchTouchEvent(cancel);
            } finally {
                cancel.recycle();
            }
        }
    }

    private static boolean isEnabled(Context context) {
        try {
            return context != null && GboardSwipeDownDismissSettings.readEnabled(
                    GboardPatchesSettings.preferences(context));
        } catch (Throwable failure) {
            return false;
        }
    }

    private static boolean beginsInHeader(InputMethodService service, MotionEvent event) {
        if (service == null || service.getWindow() == null
                || service.getWindow().getWindow() == null) {
            return false;
        }
        if (toolbarEditMode) {
            return false;
        }
        View root = service.getWindow().getWindow().getDecorView();
        return containsMatchingHeaderSurface(root, event.getRawX(), event.getRawY());
    }

    private static boolean containsMatchingHeaderSurface(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE || !contains(view, rawX, rawY)) {
            return false;
        }
        if (isHeaderSurface(view)) {
            return true;
        }
        if (view instanceof ViewGroup group) {
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                if (containsMatchingHeaderSurface(group.getChildAt(index), rawX, rawY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(View view, float rawX, float rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0] && rawX < location[0] + view.getWidth()
                && rawY >= location[1] && rawY < location[1] + view.getHeight();
    }

    private static boolean isHeaderSurface(View view) {
        String className = view.getClass().getName();
        if (className.endsWith("AccessPointsBar")
                || className.contains("CandidatesHolderView")
                || className.endsWith("ProactiveSuggestionsHolderView")) {
            return true;
        }
        Object tag = view.getTag();
        if (tag != null && tag.toString().contains("keyboard-header-area")) {
            return true;
        }
        int id = view.getId();
        if (id == View.NO_ID) {
            return false;
        }
        try {
            String name = view.getResources().getResourceEntryName(id).toLowerCase(Locale.ROOT);
            return name.contains("candidate") || name.contains("suggestion")
                    || name.contains("access_points") || name.contains("keyboard_header");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void dismiss(InputMethodService service) {
        if (service == null) {
            return;
        }
        try {
            service.requestHideSelf(0);
        } catch (Throwable failure) {
            logFailure("failed to dismiss keyboard", failure);
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, LOG_PREFIX + message, failure);
        } catch (Throwable ignored) {
            // Gesture handling must fail open.
        }
    }
}
