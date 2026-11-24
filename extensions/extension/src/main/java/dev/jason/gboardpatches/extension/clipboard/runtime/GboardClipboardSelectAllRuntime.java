package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.clipboard.header.GboardClipboardHeaderActions;
import dev.jason.gboardpatches.extension.clipboard.header.GboardClipboardHeaderBinding;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardSettingsLocaleManager;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Adapts the clipboard panel lifecycle and native touch dispatch to the edit action. */
public final class GboardClipboardSelectAllRuntime {
    private static final String TAG = "GboardClipboardSelectAll";
    private static GboardClipboardHeaderBinding binding;
    private static final AtomicBoolean LOADING_SETTINGS = new AtomicBoolean();
    private static volatile Preferences preferences;
    private static volatile Snapshot snapshot;
    private static final SharedPreferences.OnSharedPreferenceChangeListener REFRESH =
            (changed, key) -> {
                if (key == null || GboardClipboardSettings.PREF_KEY_CLIPBOARD_ENABLED.equals(key)
                        || GboardClipboardSettings.PREF_KEY_SELECT_ALL.equals(key)
                        || GboardClipboardSettings.PREF_KEY_EXCLUDE_PINNED.equals(key)
                        || GboardPatchesSettings.PREF_KEY_SETTINGS_UI_LANGUAGE.equals(key)) {
                    publishSettings();
                }
            };

    private GboardClipboardSelectAllRuntime() { }

    public static void beforeOpenOrClose(Object keyboard) {
        try {
            GboardClipboardHeaderActions.close(keyboard);
        } catch (Throwable failure) {
            report(failure);
        }
    }

    public static void afterOpen(Object keyboard) {
        try {
            if (binding == null) {
                binding = new GboardClipboardHeaderBinding(keyboard.getClass().getClassLoader(),
                        GboardClipboardSelectAllRuntime::settings,
                        GboardClipboardSelectAllRuntime::report);
            }
            binding.attach(keyboard);
        } catch (Throwable failure) {
            report(failure);
        }
    }

    public static void afterState(Object keyboard, int state) {
        try {
            GboardClipboardHeaderActions.stateChanged(keyboard, state >= 1 && state <= 3);
        } catch (Throwable failure) {
            report(failure);
        }
    }

    public static void afterData(Object adapter) {
        try {
            if (binding != null) {
                GboardClipboardHeaderActions.update(binding.keyboardForAdapter(adapter));
            }
        } catch (Throwable failure) {
            report(failure);
        }
    }

    public static boolean interceptTouch(View header, MotionEvent event) {
        try {
            return GboardClipboardHeaderActions.onHeaderTouch(header, event);
        } catch (Throwable failure) {
            report(failure);
            return false;
        }
    }

    private static GboardClipboardHeaderActions.Settings settings(Context context) {
        Snapshot current = snapshot;
        if (current == null) {
            if (preferences == null) loadSettings(context);
            return null;
        }
        if (!current.enabled) return null;
        LocaleList locales = context.getResources().getConfiguration().getLocales();
        Locale deviceLocale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        String language = GboardSettingsLocaleManager.resolveEffectiveLanguageTag(
                current.languagePreference, deviceLocale);
        int emptyText = current.excludePinned
                ? R.string.gboard_clipboard_no_unpinned : R.string.gboard_clipboard_no_items;
        return new GboardClipboardHeaderActions.Settings(
                GboardSettingsText.getInLanguage(
                        R.string.gboard_patches_long_press_quick_actions_action_select_all, language),
                GboardSettingsText.getInLanguage(emptyText, language),
                current.excludePinned);
    }

    private static void loadSettings(Context context) {
        Context app = context.getApplicationContext();
        if (app == null || !GboardPatchesSettings.isUserUnlocked(app)
                || !LOADING_SETTINGS.compareAndSet(false, true)) return;
        try {
            new Thread(() -> {
                try {
                    SharedPreferences clipboard = GboardClipboardSettings.preferences(app);
                    SharedPreferences ui = GboardPatchesSettings.preferencesOrNull(app);
                    if (ui == null) {
                        LOADING_SETTINGS.set(false);
                        return;
                    }
                    // Await initial disk loads here. Later reads use these in-memory stores.
                    clipboard.getAll();
                    ui.getAll();
                    Handler main = new Handler(Looper.getMainLooper());
                    if (!main.post(() -> registerSettings(clipboard, ui))) {
                        LOADING_SETTINGS.set(false);
                    }
                } catch (Throwable failure) {
                    LOADING_SETTINGS.set(false);
                    report(failure);
                }
            }, "GboardClipboardSettings").start();
        } catch (Throwable failure) {
            LOADING_SETTINGS.set(false);
            report(failure);
        }
    }

    private static void registerSettings(SharedPreferences clipboard, SharedPreferences ui) {
        try {
            clipboard.registerOnSharedPreferenceChangeListener(REFRESH);
            ui.registerOnSharedPreferenceChangeListener(REFRESH);
            preferences = new Preferences(clipboard, ui);
            publishSettings();
        } catch (Throwable failure) {
            report(failure);
        } finally {
            LOADING_SETTINGS.set(false);
        }
    }

    private static void publishSettings() {
        try {
            Preferences current = preferences;
            if (current == null) return;
            snapshot = new Snapshot(
                    GboardClipboardSettings.readClipboardEnabled(current.clipboard)
                            && GboardClipboardSettings.readSelectAll(current.clipboard),
                    GboardClipboardSettings.readExcludePinned(current.clipboard),
                    GboardSettingsLocaleManager.readLanguagePreference(current.ui));
            GboardClipboardHeaderActions.settingsPublished();
        } catch (Throwable failure) {
            snapshot = null;
            report(failure);
            try {
                GboardClipboardHeaderActions.settingsPublished();
            } catch (Throwable cleanup) {
                report(cleanup);
            }
        }
    }

    private static final class Preferences {
        final SharedPreferences clipboard;
        final SharedPreferences ui;

        Preferences(SharedPreferences clipboard, SharedPreferences ui) {
            this.clipboard = clipboard;
            this.ui = ui;
        }
    }

    private static final class Snapshot {
        final boolean enabled;
        final boolean excludePinned;
        final String languagePreference;

        Snapshot(boolean enabled, boolean excludePinned, String languagePreference) {
            this.enabled = enabled;
            this.excludePinned = excludePinned;
            this.languagePreference = languagePreference;
        }
    }

    private static void report(Throwable failure) {
        try {
            Log.w(TAG, "Clipboard select all unavailable", failure);
        } catch (Throwable ignored) { }
    }
}
