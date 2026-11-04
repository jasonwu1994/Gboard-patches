package dev.jason.gboardpatches.extension.editingaccesspoints;

import android.inputmethodservice.InputMethodService;
import android.content.Context;
import android.os.Looper;
import android.content.SharedPreferences;
import java.lang.ref.WeakReference;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

public final class GboardEditingAccessPointsRuntime {
    private static final EditorActionSession SESSION = new EditorActionSession();
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    // Keep the adapter alive, but never retain the service or its InputConnection strongly.
    private static EditorActionSession.Editor editor;

    private GboardEditingAccessPointsRuntime() {}

    public static boolean enabled(Context context, EditorAction action) {
        try {
            SharedPreferences preferences = GboardPatchesSettings.preferences(context);
            return GboardEditingAccessPointsSettings.read(preferences,
                    GboardEditingAccessPointsSettings.ENABLED)
                    && GboardEditingAccessPointsSettings.read(preferences,
                    GboardEditingAccessPointsSettings.key(action));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void start(Object receiver) {
        end();
        try {
            if (receiver instanceof InputMethodService ime) {
                service = new WeakReference<>(ime);
                WeakReference<InputMethodService> reference = service;
                editor = new EditorActionSession.Editor() {
                    @Override public android.view.inputmethod.InputConnection connection() {
                        InputMethodService current = reference.get();
                        return current == null ? null : current.getCurrentInputConnection();
                    }
                    @Override public InputMethodService inputMethodService() { return reference.get(); }
                };
                SESSION.begin(editor);
            }
        } catch (Throwable ignored) {
            end();
        }
    }

    public static void end() {
        SESSION.end();
        service.clear();
        editor = null;
    }

    public static void click(EditorAction action) {
        try {
            // Stock AP clicks are synchronous UI callbacks. Do not queue stale edits.
            if (Looper.myLooper() == Looper.getMainLooper() && enabled(service.get(), action)) {
                boolean selectAllWhenUnselected = GboardPatchesSettings.readRuntimeSetting(
                        service.get(), preferences -> GboardEditingAccessPointsSettings.read(
                                preferences, GboardEditingAccessPointsSettings.SELECT_ALL_WHEN_UNSELECTED),
                        false);
                SESSION.execute(action, selectAllWhenUnselected);
            }
        } catch (Throwable ignored) {
            // Descriptor callbacks must not escape into Gboard.
        }
    }
}
