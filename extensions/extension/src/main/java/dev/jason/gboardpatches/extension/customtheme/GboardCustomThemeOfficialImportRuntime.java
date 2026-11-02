package dev.jason.gboardpatches.extension.customtheme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardCustomThemeOfficialImportRuntime {
    private static final int IMPORT_REQUEST_CODE = 0x67;
    private static final String NEW_THEME_FILE_EXTRA = "intent_extra_key_new_theme_file_name";
    private static final ThreadLocal<Boolean> CALLING_STOCK_PLUS = new ThreadLocal<Boolean>();

    private GboardCustomThemeOfficialImportRuntime() {
    }

    public static boolean interceptPlus(Object plusItem, Object peer, Object adapter, int position) {
        if (Boolean.TRUE.equals(CALLING_STOCK_PLUS.get()) || plusItem == null || peer == null) {
            return false;
        }
        try {
            Object rawContext = GboardCustomThemeTargetContract.contextFromPeer(peer);
            if (!(rawContext instanceof Activity activity)
                    || !GboardCustomThemeSettings.readEnabled(
                            GboardPatchesSettings.preferences(activity))) {
                return false;
            }
            Object[] stockArgs = new Object[]{peer, adapter, position};
            new AlertDialog.Builder(activity)
                    .setTitle(text(activity,
                            R.string.gboard_patches_custom_theme_source_chooser_title))
                    .setItems(new CharSequence[]{
                                    text(activity,
                                            R.string.gboard_patches_custom_theme_source_photo),
                                    text(activity,
                                            R.string.gboard_patches_custom_theme_source_zip)},
                            (ignored, which) -> {
                                if (which == 0) {
                                    invokeStockPlus(plusItem, stockArgs);
                                } else {
                                    launchZipPicker(peer, activity);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    public static boolean handleActivityResult(Object fragment, int requestCode,
            int resultCode, Intent result) {
        if (requestCode != IMPORT_REQUEST_CODE) return false;
        if (resultCode != Activity.RESULT_OK || result == null || result.getData() == null) {
            return true;
        }
        try {
            Object peer = GboardCustomThemeTargetContract.peerFromFragment(fragment);
            Activity activity = (Activity) GboardCustomThemeTargetContract.contextFromPeer(peer);
            GboardCustomThemeZipImporter.importUri(activity, result.getData(),
                    new GboardCustomThemeZipImporter.Completion() {
                        @Override public void onCommitted(String fileName) {
                            try {
                                GboardCustomThemeTargetContract.deliverImportedTheme(
                                        peer, new Intent().putExtra(
                                                NEW_THEME_FILE_EXTRA, fileName));
                                Toast.makeText(activity,
                                        text(activity,
                                                R.string.gboard_patches_custom_theme_imported),
                                        Toast.LENGTH_SHORT).show();
                            } catch (Throwable failure) {
                                showFailure(activity);
                            }
                        }

                        @Override public void onFailure(Throwable failure) {
                            showFailure(activity);
                        }
                    });
        } catch (Throwable failure) {
            // This request code belongs to the patch. Never hand a failed custom
            // result back to the stock handler, which does not understand its URI.
            showFailure(findActivity(fragment));
            return true;
        }
        return true;
    }

    private static void invokeStockPlus(Object plusItem, Object[] args) {
        try {
            CALLING_STOCK_PLUS.set(Boolean.TRUE);
            GboardCustomThemeTargetContract.invokeStockPlus(
                    plusItem, args[0], args[1], (Integer) args[2]);
        } catch (Throwable ignored) {
            // The source chooser remains safely dismissed if the stock route changes.
        } finally {
            CALLING_STOCK_PLUS.remove();
        }
    }

    private static void launchZipPicker(Object peer, Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream"})
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            GboardCustomThemeTargetContract.launchActivityForResult(
                    peer, intent, IMPORT_REQUEST_CODE, new Bundle());
        } catch (Throwable failure) {
            showFailure(activity);
        }
    }

    private static Activity findActivity(Object fragment) {
        try {
            Object peer = GboardCustomThemeTargetContract.peerFromFragment(fragment);
            Object context = GboardCustomThemeTargetContract.contextFromPeer(peer);
            return context instanceof Activity ? (Activity) context : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void showFailure(Activity activity) {
        if (activity == null) return;
        try {
            Toast.makeText(activity,
                    text(activity, R.string.gboard_patches_custom_theme_import_failed),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            // Result handling must remain consumed even if the UI is unavailable.
        }
    }

    private static String text(Activity activity, int id) {
        return GboardSettingsText.get(activity, id);
    }
}
